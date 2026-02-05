# AGENTS.md（项目 AI 协作说明）

> 目标：让“人 + AI”在同一套规则体系下协作；并且跨工具（Cursor/Copilot/Continue/Windsurf）通用。

---

## 0. 快速入口（给人）

- **规则唯一真相**：`.agent/src/**`
- **Cursor 生效入口（生成物）**：`.cursor/rules/**`
- **构建**：`.agent/build/build.sh --target cursor`
- **校验**：`.agent/build/validate.sh --target cursor --cursor-format mdc`

---

## 1. 项目概述（手写区）

> ✅ 这里只写“稳定信息”（长期不变 / 少变）

- **tiny-platform 定位**：插件化单体 + All-in-One + 多租户
- **关键模块**：auth / dict / workflow / plugin / tenant
- **强约束**：安全 / 权限 / 租户隔离不可破坏

---

## 2. 协作铁律（手写区｜短而硬）

1. **先假设后执行**：信息不确定必须写出假设与风险，再给方案
2. **修改最小化**：不做无关重构；每次改动必须可回滚
3. **安全/权限/多租户不可弱化**：任何削弱必须明确说明并请求确认
4. **输出必须可执行**：给出可执行命令/路径/文件清单
5. **产物禁止手改**：`.cursor/rules/**` 等生成物不手工编辑（只改 `.agent/src/**`）

---

## 2.1 冲突裁决顺序（手写区｜建议保留）

当不同规则冲突时，按以下顺序裁决：

1. **禁止** 优先于一切（Must Not > Must > Should > May）
2. **平台特定覆盖通用**：`90+` > `30+` > `10/20` > `00`
3. **更严格覆盖更宽松**
4. **仍不确定时**：必须说明假设并请求确认（或给默认策略）

---

## 3. 生成区（由脚本更新｜禁止手改）

> 本区块由 `.agent/build/agentsmd.sh` 或 build 流程更新  
> 允许脚本“只替换生成块”，不得覆盖手写区。

<!-- BEGIN GENERATED:AGENTS -->
## 生成区（自动生成）

- 规则系统版本：v2.3.1
- 构建命令：.agent/build/build.sh --target cursor
- 校验命令：.agent/build/validate.sh --target cursor --cursor-format mdc

> 注意：本区块由脚本生成，禁止手改。
<!-- END GENERATED:AGENTS -->

---

## 4. 项目特有补充（手写区）

> 写 tiny-platform 的“高频决策”，避免 AI 自作主张。

- 表命名：单数（user/resource）
- 认证：JWT / Session 混合策略（按客户端来源切换）
- 前端：Vue3 + Ant Design Vue
- 安全：RS256 JWT + JWK Set + MFA(TOTP)
