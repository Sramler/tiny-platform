#!/usr/bin/env node

import fs from "node:fs";

const WAIVER_LABEL = "ci-pr-description-waived";
const ALLOWED_ASSOCIATIONS = new Set(["OWNER", "MEMBER", "COLLABORATOR"]);

function fail(message, guidance = { author: [], reviewer: [] }) {
  const lines = [
    "Waiver reviewer 审批检查未通过：",
    `- ${message}`,
    "",
    "请参考：",
    "- docs/TINY_PLATFORM_CI_WAIVER_POLICY.md",
    "- .github/PULL_REQUEST_TEMPLATE.md",
  ];
  if (guidance.author.length > 0) {
    lines.push("", "作者下一步：", ...guidance.author);
  }
  if (guidance.reviewer.length > 0) {
    lines.push("", "Reviewer 下一步：", ...guidance.reviewer);
  }
  console.error(lines.join("\n"));
  if (process.env.GITHUB_STEP_SUMMARY) {
    const summaryLines = [
      "## Waiver Reviewer 审批检查未通过",
      "",
      "### 发现的问题",
      `- ${message}`,
      "",
    ];
    if (guidance.author.length > 0) {
      summaryLines.push("### 作者下一步", ...guidance.author, "");
    }
    if (guidance.reviewer.length > 0) {
      summaryLines.push("### Reviewer 下一步", ...guidance.reviewer, "");
    }
    summaryLines.push(
      "### 参考入口",
      "- `docs/TINY_PLATFORM_CI_WAIVER_POLICY.md`",
      "- `docs/TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md`",
      "- `.github/PULL_REQUEST_TEMPLATE.md`",
      "",
    );
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summaryLines.join("\n")}\n`);
  }
  process.exit(1);
}

function pass(result) {
  console.log(result.message);
  if (process.env.GITHUB_STEP_SUMMARY) {
    const summaryLines = [
      result.skipped ? "## Waiver Reviewer 审批检查已跳过" : "## Waiver Reviewer 审批检查通过",
      "",
      `- ${result.message}`,
      "",
    ];
    if (result.issueRef || result.reviewer || result.sourceLabel) {
      summaryLines.push("### 审批结果");
      if (result.issueRef) {
        summaryLines.push(`- 命中的 waiver issue：\`${result.issueRef}\``);
      }
      if (result.reviewer) {
        summaryLines.push(`- 批准 reviewer：\`@${result.reviewer}\``);
      }
      if (result.sourceLabel) {
        summaryLines.push(`- 批准来源：${result.sourceLabel}`);
      }
      summaryLines.push("");
    }
    summaryLines.push(
      "### 参考入口",
      "- `docs/TINY_PLATFORM_CI_WAIVER_POLICY.md`",
      "- `docs/TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md`",
      "- `.github/PULL_REQUEST_TEMPLATE.md`",
      "",
    );
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summaryLines.join("\n")}\n`);
  }
}

function readJsonFile(path) {
  return JSON.parse(fs.readFileSync(path, "utf8"));
}

function readLabels() {
  return new Set(
    (process.env.PR_LABELS ?? "")
      .split(",")
      .map((label) => label.trim())
      .filter(Boolean),
  );
}

function parseWaiverIssueRef(body) {
  const match = body.match(/^-\s*关联 waiver issue：\s*(.+)$/m);
  return match ? match[1].trim() : "";
}

function normalizeIssueRef(rawRef, repository) {
  if (!rawRef) {
    return "";
  }

  const ref = rawRef.trim();
  if (/^#\d+$/.test(ref)) {
    return `${repository}${ref}`;
  }
  if (/^[\w.-]+\/[\w.-]+#\d+$/.test(ref)) {
    return ref;
  }
  const urlMatch = ref.match(/^https:\/\/github\.com\/([\w.-]+\/[\w.-]+)\/issues\/(\d+)$/);
  if (urlMatch) {
    return `${urlMatch[1]}#${urlMatch[2]}`;
  }
  return "";
}

function buildApprovalPatterns(normalizedRef) {
  const shortRefMatch = normalizedRef.match(/#\d+$/);
  const shortRef = shortRefMatch ? shortRefMatch[0] : normalizedRef;
  return [
    `Waiver-Approved: ${normalizedRef}`,
    `Waiver-Approved: ${shortRef}`,
    `批准豁免：${normalizedRef}`,
    `批准豁免：${shortRef}`,
  ];
}

function bodyContainsApproval(body, patterns) {
  if (!body) {
    return false;
  }
  return patterns.some((pattern) => body.includes(pattern));
}

function bodyContainsApprovalKeyword(body) {
  if (!body) {
    return false;
  }
  return body.includes("Waiver-Approved:") || body.includes("批准豁免：");
}

async function githubGet(url) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${process.env.GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
    },
  });

  if (!response.ok) {
    throw new Error(`GitHub API request failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

async function loadIssueComments(repository, prNumber) {
  if (process.env.PR_COMMENTS_PATH) {
    return readJsonFile(process.env.PR_COMMENTS_PATH);
  }
  const apiBase = process.env.GITHUB_API_URL ?? "https://api.github.com";
  return githubGet(`${apiBase}/repos/${repository}/issues/${prNumber}/comments?per_page=100`);
}

async function loadReviews(repository, prNumber) {
  if (process.env.PR_REVIEWS_PATH) {
    return readJsonFile(process.env.PR_REVIEWS_PATH);
  }
  const apiBase = process.env.GITHUB_API_URL ?? "https://api.github.com";
  return githubGet(`${apiBase}/repos/${repository}/pulls/${prNumber}/reviews?per_page=100`);
}

function isAllowedReviewer(actor, prAuthor) {
  return (
    actor &&
    actor.user &&
    actor.user.login &&
    actor.user.login !== prAuthor &&
    ALLOWED_ASSOCIATIONS.has(actor.author_association ?? "NONE")
  );
}

function findMatchingIssueCommentApproval(comments, prAuthor, approvalPatterns) {
  for (const comment of comments) {
    if (!isAllowedReviewer(comment, prAuthor)) {
      continue;
    }
    if (bodyContainsApproval(comment.body, approvalPatterns)) {
      return {
        source: "issue_comment",
        reviewer: comment.user.login,
        body: comment.body,
      };
    }
  }
  return null;
}

function findMatchingApprovedReview(reviews, prAuthor, approvalPatterns) {
  for (const review of reviews) {
    if (review.state !== "APPROVED") {
      continue;
    }
    if (!isAllowedReviewer(review, prAuthor)) {
      continue;
    }
    if (bodyContainsApproval(review.body, approvalPatterns)) {
      return {
        source: "approved_review",
        reviewer: review.user.login,
        body: review.body,
      };
    }
  }
  return null;
}

function findApprovalNearMiss(comments, reviews, prAuthor) {
  for (const comment of comments) {
    if (!isAllowedReviewer(comment, prAuthor)) {
      continue;
    }
    if (bodyContainsApprovalKeyword(comment.body)) {
      return {
        source: "issue_comment",
        reviewer: comment.user.login,
        body: comment.body,
      };
    }
  }

  for (const review of reviews) {
    if (review.state !== "APPROVED") {
      continue;
    }
    if (!isAllowedReviewer(review, prAuthor)) {
      continue;
    }
    if (bodyContainsApprovalKeyword(review.body)) {
      return {
        source: "approved_review",
        reviewer: review.user.login,
        body: review.body,
      };
    }
  }

  return null;
}

const labels = readLabels();
if (!labels.has(WAIVER_LABEL)) {
  pass({
    message: "未检测到 PR 描述豁免标签，跳过 reviewer waiver 审批检查。",
    skipped: true,
  });
  process.exit(0);
}

const prBody = process.env.PR_BODY ?? "";
const repository = process.env.GITHUB_REPOSITORY ?? "";
const prNumber = process.env.PR_NUMBER ?? "";
const prAuthor = process.env.PR_AUTHOR ?? "";

if (!prBody || !repository || !prNumber || !prAuthor) {
  fail(
    "缺少 PR_BODY / GITHUB_REPOSITORY / PR_NUMBER / PR_AUTHOR，无法校验 reviewer waiver 审批。",
    {
      author: [],
      reviewer: ["- 检查 workflow 传入的 PR 元数据是否完整。"],
    },
  );
}

const normalizedIssueRef = normalizeIssueRef(parseWaiverIssueRef(prBody), repository);
if (!normalizedIssueRef) {
  fail(
    "PR 描述中的“关联 waiver issue”无效，无法进行 reviewer waiver 审批校验。",
    {
      author: [
        "- 先在 PR 描述的“门禁豁免”里填写有效的 issue 编号或链接，例如 `#123`。",
      ],
      reviewer: [
        "- 等作者补齐 `关联 waiver issue` 后，再发送批准评论。",
      ],
    },
  );
}

const approvalPatterns = buildApprovalPatterns(normalizedIssueRef);
const shortIssueRef = normalizedIssueRef.match(/#\d+$/)?.[0] ?? normalizedIssueRef;
const issueComments = await loadIssueComments(repository, prNumber);
const reviews = await loadReviews(repository, prNumber);

const matchingIssueCommentApproval = findMatchingIssueCommentApproval(
  issueComments,
  prAuthor,
  approvalPatterns,
);
const matchingApprovedReview = findMatchingApprovedReview(
  reviews,
  prAuthor,
  approvalPatterns,
);
const matchedApproval = matchingApprovedReview ?? matchingIssueCommentApproval;
const approvalNearMiss = matchedApproval
  ? null
  : findApprovalNearMiss(issueComments, reviews, prAuthor);

if (!matchedApproval) {
  const reviewerSpecificGuidance = approvalNearMiss
    ? [
        `- 已发现 reviewer @${approvalNearMiss.reviewer} 提交了审批语句，但引用的 waiver issue 与 PR 描述不一致，请改成当前 issue。`,
      ]
    : [];
  fail(
    `带有“${WAIVER_LABEL}”标签的 PR，必须由非作者 reviewer 在 PR 评论或 APPROVED review 中显式写出 “Waiver-Approved: ${normalizedIssueRef}” 或 “批准豁免：${normalizedIssueRef}”。`,
    {
      author: [
        "- 确认 `关联 waiver issue`、标签和 PR 描述内容已经对齐。",
        "- 如 reviewer 还未审批，请直接把下面的批准模板发给 reviewer。",
      ],
      reviewer: [
        ...reviewerSpecificGuidance,
        "- 确认 reviewer 不是 PR 作者，且具备 OWNER / MEMBER / COLLABORATOR 关联身份。",
        "- 在 PR 评论或 APPROVED review 中直接复制以下任一语句：",
        "```text",
        `Waiver-Approved: ${shortIssueRef}`,
        "```",
        "```text",
        `批准豁免：${shortIssueRef}`,
        "```",
        "- 评论或 review 提交后，workflow 会自动重新校验。",
      ],
    },
  );
}

const approvalSourceLabel =
  matchedApproval.source === "approved_review" ? "APPROVED review" : "PR 评论";
pass(
  {
    message: `Waiver reviewer 审批检查通过，已找到针对 ${normalizedIssueRef} 的 reviewer 显式批准记录。reviewer=@${matchedApproval.reviewer}，来源=${approvalSourceLabel}。`,
    issueRef: normalizedIssueRef,
    reviewer: matchedApproval.reviewer,
    sourceLabel: approvalSourceLabel,
    skipped: false,
  },
);
