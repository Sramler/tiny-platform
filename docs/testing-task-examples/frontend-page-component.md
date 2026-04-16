# Frontend Page Component Test Example

```text
请为 tiny-platform 的前端页面改动补测试。

必须遵守：
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/20-vue.rules.md
- .agent/src/rules/50-testing.rules.md

页面范围：
- ...

风险点：
- 按钮禁用态
- 表单校验
- 权限按钮
- 路由跳转
- 请求参数
- 弹窗确认
- 第三方组件运行时契约（如 Ant Design Vue Table / Form / Drawer）
- 菜单点击路径 vs direct deep-link / 刷新路径

测试层级：
- 组件测试
- 如涉及真实登录、真实租户上下文或真实状态收敛，再补 E2E

必须覆盖：
- 按钮可见性与禁用原因
- 加载态
- 错误提示
- 提交参数
- 路由跳转
- confirm 之后的真实动作
- 如果页面由菜单或动态路由驱动：菜单点击进入、direct deep-link、浏览器刷新三者中至少覆盖前两者，并明确第三者是否同构
- 如果页面依赖第三方组件高阶能力：锁住真实 prop / event 契约（例如 `@expand`、`expandedRowKeys`、`@finish`、`open` / `close`）

禁止：
- 不要用 wrapper.vm 改内部状态
- 不要手工 $emit 到真实 disabled 的按钮
- 不要用失真的 Ant Design Vue stub
- 不要把运行时代码依赖的顶层 props / emits 偷换成测试专用结构
- 不要只验证“从菜单点进去可以”，却漏掉 direct deep-link / 浏览器刷新

交付：
- 测试文件清单
- 执行命令
- 组件测试和 E2E 的边界说明
- 哪些行为已用真实浏览器验证，哪些仍是组件层契约验证
```
