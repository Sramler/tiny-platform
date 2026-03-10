#!/usr/bin/env node

import fs from "node:fs";

const WAIVER_LABEL = "ci-pr-description-waived";
const WAIVER_REFERENCE = "已豁免，见门禁豁免";

function readBody() {
  const file = process.argv[2];
  if (file) {
    return fs.readFileSync(file, "utf8");
  }
  return process.env.PR_BODY ?? "";
}

function readLabels() {
  return new Set(
    (process.env.PR_LABELS ?? "")
      .split(",")
      .map((label) => label.trim())
      .filter(Boolean),
  );
}

function fail(issues, guidance = { author: [], reviewer: [] }) {
  const lines = [
    "PR 描述检查未通过：",
    ...issues.map((issue) => `- ${issue}`),
    "",
    "请参考：",
    "- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md",
    "- docs/TINY_PLATFORM_TESTING_PR_CHECKLIST.md",
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
      "## PR 描述检查未通过",
      "",
      "### 发现的问题",
      ...issues.map((issue) => `- ${issue}`),
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
      "- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`",
      "- `docs/TINY_PLATFORM_TESTING_PR_CHECKLIST.md`",
      "- `docs/TINY_PLATFORM_CI_WAIVER_POLICY.md`",
      "- `.github/PULL_REQUEST_TEMPLATE.md`",
      "",
    );
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summaryLines.join("\n")}\n`);
  }
  process.exit(1);
}

function writePassSummary(summary) {
  console.log(summary.message);
  if (process.env.GITHUB_STEP_SUMMARY) {
    const summaryLines = [
      "## PR 描述检查通过",
      "",
      `- ${summary.message}`,
      "",
      "### 命中的影响范围",
      ...summary.impacts.map((label) => `- ${label}`),
      "",
      "### 测试层级",
      ...summary.testLevels.map((label) => `- ${label}`),
      "",
    ];
    if (summary.e2eLevels.length > 0) {
      summaryLines.push("### E2E 等级", ...summary.e2eLevels.map((label) => `- ${label}`), "");
    }
    summaryLines.push(
      "### Reviewer 建议重点",
      ...summary.reviewerFocus.map((item) => `- ${item}`),
      "",
      "### 豁免状态",
      `- ${summary.waiverStatus}`,
      "",
      "### 参考入口",
      "- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`",
      "- `docs/TINY_PLATFORM_TESTING_PR_CHECKLIST.md`",
      "- `docs/TINY_PLATFORM_CI_WAIVER_POLICY.md`",
      "- `.github/PULL_REQUEST_TEMPLATE.md`",
      "",
    );
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summaryLines.join("\n")}\n`);
  }
}

function isWaiverReference(value) {
  return value?.trim() === WAIVER_REFERENCE;
}

function escapeRegex(input) {
  return input.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function parseSections(markdown) {
  const normalized = markdown.replace(/\r\n/g, "\n");
  const matches = [...normalized.matchAll(/^##\s+(.+)$/gm)];
  const sections = new Map();

  for (let index = 0; index < matches.length; index += 1) {
    const current = matches[index];
    const next = matches[index + 1];
    const title = current[1].trim();
    const start = current.index + current[0].length;
    const end = next ? next.index : normalized.length;
    sections.set(title, normalized.slice(start, end).trim());
  }

  return sections;
}

function meaningfulLine(line) {
  const cleaned = line
    .replace(/^\s*[-*]\s*/, "")
    .replace(/^\s*\d+\.\s*/, "")
    .replace(/^\s*>\s*/, "")
    .trim();

  if (!cleaned) {
    return "";
  }

  if (
    cleaned === "-" ||
    cleaned === "..." ||
    cleaned === "1." ||
    cleaned === "1)" ||
    cleaned === "无" ||
    cleaned === "未涉及" ||
    cleaned === "不适用"
  ) {
    return cleaned;
  }

  return cleaned;
}

function hasMeaningfulContent(content) {
  const lines = content
    .split("\n")
    .map((line) => meaningfulLine(line))
    .filter(Boolean);

  if (lines.length === 0) {
    return false;
  }

  return lines.some((line) => !["-", "...", "1."].includes(line));
}

function extractLabeledValue(sectionContent, label) {
  for (const rawLine of sectionContent.split("\n")) {
    const line = rawLine.replace(/^\s*[-*]\s*/, "").trim();
    if (line.startsWith(label)) {
      return line.slice(label.length).trim();
    }
  }
  return null;
}

function looksLikeIssueReference(value) {
  if (!value) {
    return false;
  }
  const trimmed = value.trim();
  return (
    /^#\d+$/.test(trimmed) ||
    /^[\w.-]+\/[\w.-]+#\d+$/.test(trimmed) ||
    /^https:\/\/github\.com\/.+\/issues\/\d+/.test(trimmed)
  );
}

function isChecked(markdown, label) {
  const pattern = new RegExp(`- \\[(x|X)\\]\\s+${escapeRegex(label)}\\s*$`, "m");
  return pattern.test(markdown);
}

function hasAnyChecked(markdown, labels) {
  return labels.some((label) => isChecked(markdown, label));
}

function getCheckedLabels(markdown, labels) {
  return labels.filter((label) => isChecked(markdown, label));
}

function extractCodeBlock(sectionContent) {
  const match = sectionContent.match(/```(?:\w+)?\n([\s\S]*?)```/);
  return match ? match[1].trim() : "";
}

function buildFailureGuidance(issues, sections, hasWaiverLabel) {
  const guidance = {
    author: [
      "- 先从 `.github/PULL_REQUEST_TEMPLATE.md` 重新复制完整模板，避免缺章节。",
    ],
    reviewer: [],
  };

  const hasWaiverIssue = issues.some((issue) => issue.includes("关联 waiver issue"));
  const hasWaiverMention = issues.some(
    (issue) => issue.includes("豁免") || issue.includes(WAIVER_LABEL),
  );

  if (issues.some((issue) => issue.includes("自动化验证"))) {
    guidance.author.push("- 在“自动化验证”代码块里填写真实命令或 CI job，不要保留占位注释。");
  }

  if (issues.some((issue) => issue.includes("E2E / 真实链路说明"))) {
    guidance.author.push("- 如果这次改动涉及 E2E 或高风险边界，请补齐身份来源、测试租户/client、seed/reset、mock 边界、剩余缺口。");
  }

  if (hasWaiverMention) {
    const waiverSection = sections.get("门禁豁免（如适用）");
    const currentIssueRef =
      extractLabeledValue(waiverSection ?? "", "关联 waiver issue：") || "#123";
    guidance.author.push("- 如果确需豁免，请完整走受控流程：填写“门禁豁免”章节、关联 waiver issue、让维护者添加标签。");
    guidance.author.push("```text");
    guidance.author.push(`关联 waiver issue：${hasWaiverIssue ? "#123" : currentIssueRef}`);
    guidance.author.push("申请豁免项：...");
    guidance.author.push("豁免原因：...");
    guidance.author.push("补跑计划：...");
    guidance.author.push("批准依据：...");
    guidance.author.push("```");
    if (!hasWaiverLabel) {
      guidance.author.push(`- 当前你已经使用了豁免占位，但还缺少标签 \`${WAIVER_LABEL}\`。`);
    }
    guidance.reviewer.push("- 如果同意本次豁免，请在 PR 评论或 APPROVED review 中显式写批准语句。");
    guidance.reviewer.push("```text");
    guidance.reviewer.push(`Waiver-Approved: ${hasWaiverIssue ? "#123" : currentIssueRef}`);
    guidance.reviewer.push("```");
  }

  return guidance;
}

function unique(items) {
  return [...new Set(items)];
}

function buildReviewerFocus(impacts, testLevels, e2eLevels, hasWaiverLabel) {
  const focus = [];

  if (impacts.includes("后端逻辑")) {
    focus.push("核对核心业务分支、异常路径和新增自动化测试是否覆盖真实行为。");
  }
  if (impacts.includes("前端页面 / 交互")) {
    focus.push("核对截图/录屏、禁用态、确认弹层、错误态和手动验证路径是否和 PR 描述一致。");
  }
  if (impacts.includes("数据库 / Migration")) {
    focus.push("核对 migration 顺序、兼容/回滚方案，以及新老版本共存时的数据安全。");
  }
  if (impacts.includes("配置 / 环境变量")) {
    focus.push("核对默认值、缺省行为、环境变量升级路径和生产开关变更。");
  }
  if (impacts.includes("安全 / 权限 / 多租户")) {
    focus.push("核对允许路径、拒绝路径、跨租户拒绝和权限边界没有被削弱。");
  }
  if (impacts.includes("认证 / OIDC / MFA")) {
    focus.push("核对真实登录链路、client/tenant 切换、MFA/OIDC/session-JWT 边界。");
  }
  if (impacts.includes("编排 / 工作流 / 调度 / 统计 DAG")) {
    focus.push("核对并行归并、串行推进、DAG/run/node 作用域，以及失败重试/取消/暂停恢复语义。");
  }
  if (impacts.includes("发布 / 部署行为")) {
    focus.push("核对发布顺序、灰度/回滚策略和部署期间兼容性。");
  }
  if (testLevels.includes("集成测试")) {
    focus.push("核对依赖服务、数据库、队列或外部系统边界是否覆盖到位。");
  }
  if (testLevels.includes("E2E / smoke")) {
    focus.push("核对真实链路说明、允许 mock 的边界和未覆盖缺口是否写清楚。");
  }
  if (e2eLevels.includes("isolated real-link") || e2eLevels.includes("shared-env smoke")) {
    focus.push("核对真实身份、测试租户/client、seed/reset 和环境隔离是否已说明。");
  }
  if (hasWaiverLabel) {
    focus.push("本次使用了受控豁免，核对 waiver issue、补跑计划和 reviewer 审批记录是否齐全。");
  }

  return unique(focus);
}

function buildPassSummary(markdown, hasWaiverLabel) {
  const e2eLevelLabels = [
    "mock-assisted UI",
    "isolated real-link",
    "shared-env smoke",
    "nightly/full-chain",
  ];
  const impacts = getCheckedLabels(markdown, impactLabels);
  const testLevels = getCheckedLabels(markdown, testLevelLabels);
  const e2eLevels = getCheckedLabels(markdown, e2eLevelLabels);
  const reviewerFocus = buildReviewerFocus(impacts, testLevels, e2eLevels, hasWaiverLabel);

  return {
    message: hasWaiverLabel
      ? `PR 描述检查通过。已应用受控豁免标签“${WAIVER_LABEL}”。`
      : "PR 描述检查通过。",
    impacts,
    testLevels,
    e2eLevels,
    reviewerFocus: reviewerFocus.length > 0 ? reviewerFocus : ["按命中的最高风险影响范围优先审查。"],
    waiverStatus: hasWaiverLabel
      ? `已启用受控豁免标签 \`${WAIVER_LABEL}\`，合并前仍需 reviewer 显式批准。`
      : "未使用 PR 描述门禁豁免。",
  };
}

const body = readBody().replace(/\r\n/g, "\n");
const labels = readLabels();
const hasWaiverLabel = labels.has(WAIVER_LABEL);
const issues = [];

function addIssue(message) {
  if (!issues.includes(message)) {
    issues.push(message);
  }
}

if (!body.trim()) {
  fail(
    ["PR 描述为空"],
    {
      author: [
        "- 从 `.github/PULL_REQUEST_TEMPLATE.md` 复制完整模板后再填写。",
        "- 至少补齐：变更内容、变更原因、风险与回滚、测试层级、验证方式。",
      ],
      reviewer: [],
    },
  );
}

const sections = parseSections(body);
const requiredSections = [
  "变更内容",
  "变更原因",
  "影响范围",
  "风险与回滚",
  "测试层级",
  "验证方式",
  "测试与审查检查清单",
];

for (const title of requiredSections) {
  if (!sections.has(title)) {
    addIssue(`缺少必填章节：## ${title}`);
  }
}

const impactLabels = [
  "后端逻辑",
  "前端页面 / 交互",
  "数据库 / Migration",
  "配置 / 环境变量",
  "安全 / 权限 / 多租户",
  "认证 / OIDC / MFA",
  "编排 / 工作流 / 调度 / 统计 DAG",
  "发布 / 部署行为",
];

const testLevelLabels = [
  "单元测试",
  "组件测试",
  "集成测试",
  "E2E / smoke",
];

const waiverSection = sections.get("门禁豁免（如适用）");
const waiverFields = [
  "关联 waiver issue：",
  "申请豁免项：",
  "豁免原因：",
  "补跑计划：",
  "批准依据：",
];

function validateWaiverSection() {
  if (!waiverSection) {
    addIssue(`存在豁免标记时，必须填写“门禁豁免（如适用）”章节，并由维护者添加标签“${WAIVER_LABEL}”`);
    return;
  }
  for (const field of waiverFields) {
    const value = extractLabeledValue(waiverSection, field);
    if (!value) {
      addIssue(`“门禁豁免（如适用）”中的“${field}”不能为空`);
    } else if (field === "关联 waiver issue：" && !looksLikeIssueReference(value)) {
      addIssue("“门禁豁免（如适用）”中的“关联 waiver issue：”必须填写 GitHub issue 编号或链接，例如 `#123`");
    }
  }
}

if (!hasAnyChecked(body, impactLabels)) {
  addIssue("“影响范围”至少需要勾选一项");
}

if (!hasAnyChecked(body, testLevelLabels)) {
  addIssue("“测试层级”至少需要勾选一项");
}

for (const title of ["变更内容", "变更原因"]) {
  const content = sections.get(title);
  if (content && !hasMeaningfulContent(content)) {
    addIssue(`章节“${title}”不能为空或只保留占位符`);
  }
}

const riskSection = sections.get("风险与回滚");
if (riskSection) {
  const riskValue = extractLabeledValue(riskSection, "风险：");
  const rollbackValue = extractLabeledValue(riskSection, "回滚：");

  if (!riskValue) {
    addIssue("“风险与回滚”中的“风险”不能为空");
  }
  if (!rollbackValue) {
    addIssue("“风险与回滚”中的“回滚”不能为空");
  }
}

const verificationSection = sections.get("验证方式");
if (verificationSection) {
  const autoVerificationBlock = extractCodeBlock(verificationSection);
  if (!autoVerificationBlock) {
    addIssue("“自动化验证”代码块不能为空");
  } else if (autoVerificationBlock.includes("填写本地执行过的命令或对应 CI job")) {
    addIssue("“自动化验证”仍是模板占位文本，请填写真实命令或 CI job");
  } else if (isWaiverReference(autoVerificationBlock)) {
    if (!hasWaiverLabel) {
      addIssue(`“自动化验证”使用了豁免占位，但 PR 没有标签“${WAIVER_LABEL}”`);
    }
    validateWaiverSection();
  }
}

const requiresFrontendDetail = isChecked(body, "前端页面 / 交互");
const frontendSection = sections.get("前端验证（如适用）");
if (requiresFrontendDetail) {
  if (!frontendSection) {
    addIssue("涉及前端页面 / 交互时，必须填写“前端验证（如适用）”章节");
  } else {
    const screenshotValue = extractLabeledValue(frontendSection, "截图 / 录屏 / 预览链接：");
    const interactionValue = extractLabeledValue(frontendSection, "关键交互验证：");
    if (!screenshotValue) {
      addIssue("涉及前端页面 / 交互时，“截图 / 录屏 / 预览链接”不能为空，可写“无”并说明原因");
    } else if (isWaiverReference(screenshotValue)) {
      if (!hasWaiverLabel) {
        addIssue(`“截图 / 录屏 / 预览链接”使用了豁免占位，但 PR 没有标签“${WAIVER_LABEL}”`);
      }
      validateWaiverSection();
    }
    if (!interactionValue) {
      addIssue("涉及前端页面 / 交互时，“关键交互验证”不能为空");
    } else if (isWaiverReference(interactionValue)) {
      if (!hasWaiverLabel) {
        addIssue(`“关键交互验证”使用了豁免占位，但 PR 没有标签“${WAIVER_LABEL}”`);
      }
      validateWaiverSection();
    }
  }
}

const e2eRelevant =
  isChecked(body, "E2E / smoke") ||
  isChecked(body, "安全 / 权限 / 多租户") ||
  isChecked(body, "认证 / OIDC / MFA") ||
  isChecked(body, "编排 / 工作流 / 调度 / 统计 DAG");

const e2eSection = sections.get("E2E / 真实链路说明（如适用）");
if (e2eRelevant) {
  if (!e2eSection) {
    addIssue("当前 PR 涉及 E2E 或高风险边界，必须填写“E2E / 真实链路说明（如适用）”章节");
  } else {
    const e2eLabels = [
      "身份来源：",
      "测试租户 / client：",
      "seed/reset：",
      "允许 mock 的边界：",
      "未覆盖的真实链路缺口：",
    ];
    for (const label of e2eLabels) {
      const value = extractLabeledValue(e2eSection, label);
      if (!value) {
        addIssue(`“E2E / 真实链路说明（如适用）”中的“${label}”不能为空，可写“不适用”并说明原因`);
      } else if (isWaiverReference(value)) {
        if (!hasWaiverLabel) {
          addIssue(`“E2E / 真实链路说明（如适用）”中的“${label}”使用了豁免占位，但 PR 没有标签“${WAIVER_LABEL}”`);
        }
        validateWaiverSection();
      }
    }
  }
}

const checklistSection = sections.get("测试与审查检查清单");
if (checklistSection && !/- \[[xX ]\]/.test(checklistSection)) {
  addIssue("“测试与审查检查清单”格式不正确，请保留 markdown checkbox");
}

if (hasWaiverLabel) {
  validateWaiverSection();
}

if (issues.length > 0) {
  fail(issues, buildFailureGuidance(issues, sections, hasWaiverLabel));
}

writePassSummary(buildPassSummary(body, hasWaiverLabel));
