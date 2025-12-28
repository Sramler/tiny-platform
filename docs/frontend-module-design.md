# tiny-platform 前端模块化设计方案（从问题发现到落地）

> 📋 **文档说明**：本文档按“发现问题 → 分析问题 → 设计方案 → 技术实现 → 实施路径 → 总结决策”的顺序，系统梳理 tiny-platform 前端模块化的完整推导过程。

## 📋 目录

- [一、问题发现与目标](#一问题发现与目标) <!-- 发现问题 & 明确要解决什么 -->
- [二、可行性分析](#二可行性分析) <!-- 能不能做、值不值得做 -->
- [三、架构设计](#三架构设计) <!-- 方案长什么样 -->
  - [3.1 模块划分](#31-模块划分)
  - [3.2 模块职责](#32-模块职责)
- [四、技术实现方案](#四技术实现方案) <!-- 具体怎么做 -->
  - [4.1 模块化方案选择](#41-模块化方案选择)
  - [4.2 模块导出方式](#42-模块导出方式)
  - [4.3 路由整合方案](#43-路由整合方案)
  - [4.4 构建配置](#44-构建配置)
  - [4.5 权限 / 菜单 / 路由 / 模块统一治理](#45-权限--菜单--路由--模块统一治理)
    - [4.5.1 统一模型与分层原则](#451-统一模型与分层原则)
    - [4.5.2 ModuleMeta：模块统一声明模型](#452-modulemeta模块统一声明模型)
    - [4.5.3 Feature Toggle：后端治理，前端消费](#453-feature-toggle后端治理前端消费)
    - [4.5.4 后端权限模型与前端对齐](#454-后端权限模型与前端对齐)
    - [4.5.5 前端权限消费与声明规范](#455-前端权限消费与声明规范)
    - [4.5.6 CI 一致性校验（前后端权限对齐）](#456-ci-一致性校验前后端权限对齐)
  - [4.6 插件市场 & SaaS 多租户扩展设计](#46-插件市场--saas-多租户扩展设计)
    - [4.6.1 设计目标与核心原则](#461-设计目标与核心原则)
    - [4.6.2 统一控制模型（四层控制）](#462-统一控制模型四层控制)
    - [4.6.3 插件与模块模型定义](#463-插件与模块模型定义)
    - [4.6.4 最小可运行示例仓库结构](#464-最小可运行示例仓库结构)
    - [4.6.5 前端插件装配与运行机制](#465-前端插件装配与运行机制)
    - [4.6.6 后端 SaaS 多租户表设计](#466-后端-saas-多租户表设计)
    - [4.6.7 后端统一拦截顺序（强制）](#467-后端统一拦截顺序强制)
    - [4.6.8 Controller 统一注解规范](#468-controller-统一注解规范)
    - [4.6.9 为什么我们这样设计](#469-为什么我们这样设计)
    - [4.6.10 最终效果](#4610-最终效果)
- [五、业务模块示例](#五业务模块示例) <!-- 用真实模块验证设计 -->
- [六、部署方案](#六部署方案) <!-- 如何上线 & 部署形态 -->
- [七、迁移计划](#七迁移计划) <!-- 项目迁移步骤 -->
- [八、优势与收益](#八优势与收益) <!-- 技术 & 业务收益 -->
- [九、注意事项](#九注意事项) <!-- 版本、依赖、构建、兼容性 -->
- [十、潜在问题与优化建议](#十潜在问题与优化建议) <!-- 风险与改进点 -->
- [十一、实施计划](#十一实施计划) <!-- 项目落地步骤与排期 -->
- [十二、SaaS 平台演进路线图](#十二saas-平台演进路线图) <!-- 当前状态分析 & 分步演进计划 -->
  - [12.1 当前项目结构分析](#121-当前项目结构分析)
  - [12.2 SaaS 化进度评估](#122-saas-化进度评估)
  - [12.3 SaaS 平台演进路线图（分步骤）](#123-saas-平台演进路线图分步骤)
  - [12.4 演进时间线](#124-演进时间线)
  - [12.5 关键里程碑](#125-关键里程碑)
  - [12.6 风险与应对](#126-风险与应对)
  - [12.7 成功标准](#127-成功标准)
- [十三、总结与决策](#十三总结与决策) <!-- 关键结论与后续行动 -->

---

## 一、问题发现与目标

### 1.1 当前架构现状

当前 `tiny-oauth-server` 下的 `webapp` 目录包含了完整的前端应用代码，包括：

- 核心 UI 组件（布局、路由、认证等）
- 业务功能页面（字典管理、用户管理、流程管理、Export 管理等）
- 与后端紧密耦合的配置

**架构说明**：

- ⚠️ `tiny-oauth-server` 从命名上看是"OAuth 认证服务器"，但实际上包含了大量管理功能（用户管理、角色管理、工作流、导出等）
- ⚠️ 这是典型的"单体应用"架构，所有功能耦合在一个模块中，属于历史遗留问题
- ✅ 前端模块化是第一步，为未来的后端拆分做准备
- 📌 当前结构合理，符合前端模块化需求，未来可以考虑后端拆分

### 1.2 目标

通过前端模块化设计，实现：

1. **All-in-one 部署**：小用户体量场景，前后端集中部署，简化运维
2. **多应用部署**：大用户体量场景，前后端分离，支持独立扩展和部署
3. **代码复用**：核心 UI 和业务 UI 模块可被多个应用复用
4. **灵活组合**：根据业务需求灵活组合不同的 UI 模块

## 二、可行性分析

### 2.1 技术可行性 ✅

#### 2.1.1 当前技术栈支持

**前端技术栈**：

- ✅ Vue 3.5.25（支持 Composition API、TypeScript）
- ✅ Vite 7.2.4（支持 Monorepo、workspace）
- ✅ TypeScript 5.9.3（支持项目引用）
- ✅ Ant Design Vue 4.2.6（组件库）
- ✅ Vue Router 4.6.3（支持动态路由）

**构建工具支持**：

- ✅ npm/pnpm workspace（支持 Monorepo）
- ✅ Vite 支持库模式构建（`build.lib`）
- ✅ TypeScript 项目引用（`tsconfig.json` 的 `references`）

**结论**：技术栈完全支持模块化架构。

#### 2.1.2 项目结构兼容性

**当前结构**：

```
tiny-platform/                    # Maven 多模块项目
├── pom.xml                        # Maven 父 POM
├── tiny-oauth-server/            # Spring Boot 应用
│   └── src/main/webapp/         # 前端代码
└── tiny-core-dict-web/          # 后端模块（已有）
```

**目标结构**：

```
tiny-platform/
├── pom.xml                       # Maven 父 POM（保持不变）
├── package.json                  # npm workspace 根配置（新增）
├── pnpm-workspace.yaml          # pnpm workspace 配置（新增）
├── packages/                     # 前端模块包（新增）
│   ├── tiny-core-ui/
│   └── tiny-core-dict-ui/
└── tiny-oauth-server/           # Spring Boot 应用（保持）
    └── src/main/webapp/         # 前端代码（重构后）
```

**架构说明**：

- ✅ 前端模块化结构清晰，`packages/` 目录独立，不影响 Maven 结构
- ⚠️ `tiny-oauth-server` 命名不够准确（实际是管理平台），但可以接受（历史原因）
- 📌 当前结构合理，符合前端模块化需求
- 🔮 未来可以考虑后端拆分，创建独立的服务和管理控制台

**架构演进方向**：

- **当前阶段**：完成前端模块化，保持单体应用架构
- **未来阶段**：可以考虑拆分后端服务（用户服务、工作流服务等），创建独立的管理控制台应用
- **演进路径**：单体应用 → 前端模块化 → 后端拆分 → 微服务架构（每一步都是合理的演进）

**兼容性分析**：

- ✅ Maven 和 npm/pnpm 可以共存（不同构建系统）
- ✅ 前端模块可以放在 `packages/` 目录，不影响 Maven 结构
- ⚠️ 需要处理前端构建产物如何打包到 JAR 的问题

**结论**：项目结构兼容，但需要处理构建集成问题。

### 2.2 实施可行性 ✅

#### 2.2.1 代码迁移复杂度

**需要迁移的代码**：

- **tiny-core-ui 模块**（约 20-30 个文件）：布局、组件、认证、路由、工具等
- **tiny-core-dict-ui 模块**（约 10-15 个文件）：字典页面、API、路由等

**迁移复杂度**：⭐⭐⭐（中等）

- 代码量不大，主要是文件移动和导入路径调整
- 需要处理依赖关系和类型定义
- 需要测试功能完整性

#### 2.2.2 构建流程复杂度

**当前构建流程**：

1. 手动执行 `npm run build:prod`（前端）
2. 手动复制 `dist/` 到 `src/main/resources/static/`
3. 执行 `mvn clean package`（后端）

**目标构建流程**：

1. 构建前端模块（`packages/tiny-core-ui`、`packages/tiny-core-dict-ui`）
2. 构建前端应用（`tiny-oauth-server/src/main/webapp`）
3. 自动复制构建产物到 JAR

**复杂度分析**：

- ⚠️ 需要配置 Maven 插件集成前端构建
- ⚠️ 需要配置 workspace 依赖关系
- ⚠️ 需要处理开发环境的 HMR（热更新）

**结论**：构建流程需要额外配置，但可行。

### 2.3 风险分析

| 风险项       | 风险等级 | 影响             | 缓解措施                    |
| ------------ | -------- | ---------------- | --------------------------- |
| 依赖版本冲突 | 🟡 中    | 可能导致构建失败 | 使用 workspace 统一版本管理 |
| 路由冲突     | 🟡 中    | 可能导致路由错误 | 实施路由前缀机制和冲突检测  |
| 样式冲突     | 🟢 低    | 可能导致样式异常 | 使用 scoped 样式和统一主题  |
| 构建集成问题 | 🟡 中    | 可能导致部署失败 | 使用 Maven 插件自动化构建   |
| 功能回归     | 🟡 中    | 可能影响现有功能 | 充分测试，分阶段迁移        |

**总体风险评估**：🟢 **低风险**，技术方案成熟，风险可控。

### 2.4 收益分析

#### 2.4.1 短期收益（1-3 个月）

- ✅ 代码组织更清晰
- ✅ 核心 UI 可复用
- ✅ 开发体验提升（模块化开发）

#### 2.4.2 长期收益（3-12 个月）

- ✅ 支持多应用场景
- ✅ 支持前后端分离部署
- ✅ 降低维护成本
- ✅ 提升开发效率

**结论**：✅ **方案可行**，收益大于风险。

## 三、架构设计

### 3.1 模块划分

```
tiny-platform/
├── tiny-core-ui/                    # 核心 UI 模块（基础框架）
│   ├── src/
│   │   ├── layouts/                 # 布局组件
│   │   │   ├── BasicLayout.vue
│   │   │   ├── HeaderBar.vue
│   │   │   ├── Sider.vue
│   │   │   └── TagTabs.vue
│   │   ├── components/              # 通用组件
│   │   │   ├── Icon.vue
│   │   │   └── IconSelect.vue
│   │   ├── composables/             # 组合式函数
│   │   │   └── useAuth.ts
│   │   ├── router/                  # 路由核心
│   │   │   └── core.ts
│   │   ├── auth/                    # 认证模块
│   │   │   ├── auth.ts
│   │   │   ├── config.ts
│   │   │   └── oidc.ts
│   │   ├── utils/                   # 工具函数
│   │   │   ├── auth-utils.ts
│   │   │   ├── request.ts
│   │   │   └── traceId.ts
│   │   └── types/                    # 类型定义
│   ├── package.json
│   └── vite.config.ts
│
├── tiny-core-dict-ui/               # 字典管理 UI 模块
│   ├── src/
│   │   ├── views/                   # 字典相关页面
│   │   │   ├── DictManagement.vue
│   │   │   ├── DictItemForm.vue
│   │   │   └── DictTypeForm.vue
│   │   ├── api/                     # API 封装
│   │   │   └── dict.ts
│   │   ├── composables/             # 字典相关组合式函数
│   │   │   └── useDict.ts
│   │   └── router/                  # 字典路由配置
│   │       └── routes.ts
│   ├── package.json
│   └── vite.config.ts
│
├── tiny-oauth-server/               # OAuth Server 应用
│   ├── src/main/webapp/             # All-in-one 模式的前端入口
│   │   ├── src/
│   │   │   ├── main.ts              # 应用入口，组合各模块
│   │   │   ├── App.vue
│   │   │   ├── router/              # 应用路由配置
│   │   │   │   └── index.ts         # 整合各模块路由
│   │   │   └── views/               # 应用特定页面
│   │   │       ├── Login.vue
│   │   │       ├── Dashboard.vue
│   │   │       └── ...
│   │   ├── package.json             # 依赖 tiny-core-ui、tiny-core-dict-ui
│   │   └── vite.config.ts
│   └── src/main/java/               # 后端代码
│
└── tiny-app-frontend/               # 独立前端应用（可选）
    ├── src/
    │   ├── main.ts                  # 独立应用入口
    │   ├── App.vue
    │   └── router/
    └── package.json                 # 依赖 tiny-core-ui、tiny-core-dict-ui
```

### 3.2 模块职责

#### 3.2.1 tiny-core-ui（核心 UI 模块）

**职责**：

- 提供基础布局组件（BasicLayout、HeaderBar、Sider 等）
- 提供认证相关功能（OIDC、登录、权限控制）
- 提供路由核心功能（动态路由加载、路由守卫）
- 提供通用工具函数（请求封装、日志、TraceId 等）
- 提供通用组件（Icon、IconSelect 等）

**特点**：

- 不包含具体业务逻辑
- 可被所有应用复用
- 提供插件化扩展机制

#### 3.2.2 tiny-core-dict-ui（字典管理 UI 模块）

**职责**：

- 提供字典管理相关页面组件
- 提供字典相关的 API 封装
- 提供字典相关的组合式函数
- 提供字典路由配置

**特点**：

- 依赖 `tiny-core-ui`
- 可独立使用或集成到应用中
- 通过路由配置和组件导出方式集成

#### 3.2.3 tiny-oauth-server/webapp（All-in-one 应用）

**职责**：

- 整合所有 UI 模块
- 提供应用特定的页面和配置
- 配置应用级别的路由和菜单
- 打包为静态资源嵌入后端

**特点**：

- 依赖 `tiny-core-ui` 和 `tiny-core-dict-ui`
- 构建后作为静态资源打包到后端 JAR
- 支持开发环境的前后端分离（Vite dev server）

#### 3.2.4 tiny-app-frontend（独立前端应用，可选）

**职责**：

- 独立的前端应用
- 通过 API 与后端通信
- 可独立部署和扩展

**特点**：

- 完全前后端分离
- 可独立部署到 CDN 或静态服务器
- 适合大用户体量场景

## 四、技术实现方案

### 4.1 模块化方案选择

#### 方案 A：Monorepo + npm workspace（推荐）⭐⭐⭐⭐⭐

**结构**：

```
tiny-platform/
├── packages/
│   ├── tiny-core-ui/
│   ├── tiny-core-dict-ui/
│   └── ...
├── applications/
│   ├── tiny-oauth-server/
│   │   └── src/main/webapp/
│   └── tiny-app-frontend/
├── package.json                    # 根 package.json，workspace 配置
└── pnpm-workspace.yaml             # pnpm workspace 配置
```

**优点**：

- ✅ 统一的依赖管理
- ✅ 代码共享方便（通过 workspace 引用）
- ✅ 统一的构建和发布流程
- ✅ 版本管理简单
- ✅ 支持 TypeScript 项目引用

**实现方式**：

```json
// 根 package.json
{
  "name": "tiny-platform",
  "private": true,
  "workspaces": [
    "packages/*",
    "applications/*"
  ]
}

// tiny-oauth-server/webapp/package.json
{
  "name": "@tiny/oauth-server-webapp",
  "dependencies": {
    "@tiny/core-ui": "workspace:*",
    "@tiny/core-dict-ui": "workspace:*"
  }
}
```

#### 方案 B：独立 npm 包（适合多仓库场景）⭐⭐⭐

**结构**：

- `tiny-core-ui` 作为独立 npm 包发布
- `tiny-core-dict-ui` 作为独立 npm 包发布
- 各应用通过 npm 安装依赖

**优点**：

- ✅ 完全解耦
- ✅ 版本管理清晰
- ✅ 可独立发布和更新

**缺点**：

- ⚠️ 需要 npm 发布流程
- ⚠️ 本地开发需要 link 或发布到私有仓库

### 4.2 模块导出方式

#### 4.2.1 tiny-core-ui 导出

```typescript
// packages/tiny-core-ui/src/index.ts
export { default as BasicLayout } from "./layouts/BasicLayout.vue";
export { default as HeaderBar } from "./layouts/HeaderBar.vue";
export { default as Sider } from "./layouts/Sider.vue";
export { default as TagTabs } from "./layouts/TagTabs.vue";

export { useAuth } from "./auth/auth";
export { initAuth } from "./auth/auth";

export { createCoreRouter } from "./router/core";
export type { CoreRouterConfig } from "./router/core";

export { default as Icon } from "./components/Icon.vue";
export { default as IconSelect } from "./components/IconSelect.vue";

export * from "./utils/request";
export * from "./utils/auth-utils";
export * from "./types";
```

#### 4.2.2 tiny-core-dict-ui 导出

```typescript
// packages/tiny-core-dict-ui/src/index.ts
export { default as DictManagement } from "./views/DictManagement.vue";
export { default as DictItemForm } from "./views/DictItemForm.vue";
export { default as DictTypeForm } from "./views/DictTypeForm.vue";

export { useDict } from "./composables/useDict";
export { dictRoutes } from "./router/routes";

export * from "./api/dict";
export * from "./types";
```

### 4.3 路由整合方案

#### 4.3.1 核心路由模块（tiny-core-ui）

```typescript
// packages/tiny-core-ui/src/router/core.ts
import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import type { NavigationGuard } from "vue-router";

export interface CoreRouterConfig {
  routes?: RouteRecordRaw[];
  beforeEach?: NavigationGuard;
  afterEach?: NavigationGuard;
}

export function createCoreRouter(config: CoreRouterConfig = {}) {
  const router = createRouter({
    history: createWebHistory(),
    routes: config.routes || [],
  });

  if (config.beforeEach) {
    router.beforeEach(config.beforeEach);
  }

  if (config.afterEach) {
    router.afterEach(config.afterEach);
  }

  return router;
}
```

#### 4.3.2 字典路由模块（tiny-core-dict-ui）

```typescript
// packages/tiny-core-dict-ui/src/router/routes.ts
import type { RouteRecordRaw } from "vue-router";
import DictManagement from "../views/DictManagement.vue";

export const dictRoutes: RouteRecordRaw[] = [
  {
    path: "/dict",
    name: "DictManagement",
    component: DictManagement,
    meta: { title: "字典管理", requiresAuth: true },
  },
  // ... 其他字典相关路由
];
```

#### 4.3.3 应用路由整合（tiny-oauth-server/webapp）

```typescript
// applications/tiny-oauth-server/src/main/webapp/src/router/index.ts
import { createCoreRouter, type CoreRouterConfig } from "@tiny/core-ui";
import { dictRoutes } from "@tiny/core-dict-ui";
import { useAuth, initPromise } from "@tiny/core-ui";
import BasicLayout from "@/layouts/BasicLayout.vue";
import Login from "@/views/Login.vue";
import Dashboard from "@/views/Dashboard.vue";

// 应用特定路由
const appRoutes: RouteRecordRaw[] = [
  { path: "/login", name: "Login", component: Login },
  {
    path: "/",
    component: BasicLayout,
    children: [
      { path: "", name: "Dashboard", component: Dashboard },
      ...dictRoutes, // 整合字典路由
      // ... 其他业务路由
    ],
  },
];

// 创建路由实例
const routerConfig: CoreRouterConfig = {
  routes: appRoutes,
  beforeEach: async (to, from, next) => {
    await initPromise;
    const { isAuthenticated, checkPermission } = useAuth();
    const { isFeatureEnabled } = useFeatureToggle();

    // 1. 认证检查
    if (to.meta.requiresAuth && !isAuthenticated.value) {
      next("/login");
      return;
    }

    // 2. Feature Toggle 检查（优先级高于权限）
    if (to.meta.feature && !isFeatureEnabled(to.meta.feature)) {
      next("/exception/404"); // Feature 未启用，返回 404
      return;
    }

    // 3. 权限检查
    if (to.meta.permission && !checkPermission(to.meta.permission)) {
      next("/exception/403");
      return;
    }

    next();
  },
};

export default createCoreRouter(routerConfig);
```

### 4.4 构建配置

#### 4.4.1 tiny-core-ui 构建配置

```typescript
// packages/tiny-core-ui/vite.config.ts
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "TinyCoreUI",
      fileName: "tiny-core-ui",
      formats: ["es", "cjs"],
    },
    rollupOptions: {
      external: ["vue", "vue-router", "pinia", "ant-design-vue"],
      output: {
        globals: {
          vue: "Vue",
          "vue-router": "VueRouter",
          pinia: "Pinia",
          "ant-design-vue": "AntDesignVue",
        },
      },
    },
  },
});
```

#### 4.4.2 应用构建配置（All-in-one）

```typescript
// tiny-oauth-server/src/main/webapp/vite.config.ts
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@tiny/core-ui": resolve(
        __dirname,
        "../../../../packages/tiny-core-ui/src"
      ),
      "@tiny/core-dict-ui": resolve(
        __dirname,
        "../../../../packages/tiny-core-dict-ui/src"
      ),
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          "vue-vendor": ["vue", "vue-router", "pinia"],
          "antd-vendor": ["ant-design-vue", "@ant-design/icons-vue"],
          "core-ui": ["@tiny/core-ui"],
          "dict-ui": ["@tiny/core-dict-ui"],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
```

#### 4.4.3 Maven 构建集成配置

**方案 A：使用 frontend-maven-plugin（推荐）**

```xml
<!-- tiny-oauth-server/pom.xml -->
<build>
  <plugins>
    <!-- 前端构建插件 -->
    <plugin>
      <groupId>com.github.eirslett</groupId>
      <artifactId>frontend-maven-plugin</artifactId>
      <version>1.15.0</version>
      <configuration>
        <workingDirectory>src/main/webapp</workingDirectory>
        <installDirectory>${project.build.directory}/node</installDirectory>
      </configuration>
      <executions>
        <!-- 安装 Node.js 和 npm -->
        <execution>
          <id>install node and npm</id>
          <goals>
            <goal>install-node-and-npm</goal>
          </goals>
          <configuration>
            <nodeVersion>v20.10.0</nodeVersion>
            <npmVersion>10.2.4</npmVersion>
          </configuration>
        </execution>
        <!-- 安装依赖（需要在根目录执行 pnpm install） -->
        <execution>
          <id>pnpm install</id>
          <goals>
            <goal>pnpm</goal>
          </goals>
          <configuration>
            <workingDirectory>${project.basedir}/..</workingDirectory>
            <arguments>install</arguments>
          </configuration>
        </execution>
        <!-- 构建前端模块 -->
        <execution>
          <id>build modules</id>
          <goals>
            <goal>pnpm</goal>
          </goals>
          <configuration>
            <workingDirectory>${project.basedir}/..</workingDirectory>
            <arguments>run build:modules</arguments>
          </configuration>
        </execution>
        <!-- 构建前端应用 -->
        <execution>
          <id>build webapp</id>
          <goals>
            <goal>pnpm</goal>
          </goals>
          <configuration>
            <workingDirectory>src/main/webapp</workingDirectory>
            <arguments>run build:prod</arguments>
          </configuration>
        </execution>
      </executions>
    </plugin>

    <!-- 复制构建产物到 resources/static -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-resources-plugin</artifactId>
      <version>3.3.1</version>
      <executions>
        <execution>
          <id>copy-frontend-resources</id>
          <phase>process-resources</phase>
          <goals>
            <goal>copy-resources</goal>
          </goals>
          <configuration>
            <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
            <resources>
              <resource>
                <directory>src/main/webapp/dist</directory>
                <includes>
                  <include>**/*</include>
                </includes>
              </resource>
            </resources>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**方案 B：使用 exec-maven-plugin（备选）**

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.1.0</version>
  <executions>
    <execution>
      <id>pnpm-install</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>exec</goal>
      </goals>
      <configuration>
        <executable>pnpm</executable>
        <workingDirectory>${project.basedir}/..</workingDirectory>
        <arguments>
          <argument>install</argument>
        </arguments>
      </configuration>
    </execution>
    <execution>
      <id>pnpm-build</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>exec</goal>
      </goals>
      <configuration>
        <executable>pnpm</executable>
        <workingDirectory>src/main/webapp</workingDirectory>
        <arguments>
          <argument>run</argument>
          <argument>build:prod</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**推荐**：方案 A（frontend-maven-plugin），功能更完善，自动安装 Node.js。

#### 4.4.4 Monorepo 配置

**根 package.json**：

```json
{
  "name": "tiny-platform",
  "version": "1.0.0",
  "private": true,
  "workspaces": ["packages/*", "tiny-oauth-server/src/main/webapp"],
  "scripts": {
    "build:modules": "pnpm -r --filter './packages/*' build",
    "build:webapp": "pnpm --filter webapp build:prod",
    "build:all": "pnpm run build:modules && pnpm run build:webapp",
    "dev:webapp": "pnpm --filter webapp dev"
  },
  "devDependencies": {
    "typescript": "^5.9.3",
    "vite": "^7.2.4"
  }
}
```

**pnpm-workspace.yaml**：

```yaml
packages:
  - "packages/*"
  - "tiny-oauth-server/src/main/webapp"
```

**packages/tiny-core-ui/package.json**：

```json
{
  "name": "@tiny/core-ui",
  "version": "1.0.0",
  "private": true,
  "main": "./src/index.ts",
  "types": "./src/index.ts",
  "exports": {
    ".": "./src/index.ts",
    "./styles": "./src/styles/*.css"
  },
  "peerDependencies": {
    "vue": "^3.5.0",
    "vue-router": "^4.6.0",
    "pinia": "^3.0.0",
    "ant-design-vue": "^4.2.0"
  },
  "devDependencies": {
    "vue": "^3.5.25",
    "vue-router": "^4.6.3",
    "pinia": "^3.0.4",
    "ant-design-vue": "^4.2.6"
  }
}
```

**tiny-oauth-server/src/main/webapp/package.json**：

```json
{
  "name": "webapp",
  "version": "0.0.0",
  "private": true,
  "dependencies": {
    "@tiny/core-ui": "workspace:*",
    "@tiny/core-dict-ui": "workspace:*"
  }
}
```

#### 4.4.5 TypeScript 项目引用配置

**根 tsconfig.json**：

```json
{
  "files": [],
  "references": [
    { "path": "./packages/tiny-core-ui" },
    { "path": "./packages/tiny-core-dict-ui" },
    { "path": "./tiny-oauth-server/src/main/webapp" }
  ]
}
```

**packages/tiny-core-ui/tsconfig.json**：

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "jsx": "preserve",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "allowImportingTsExtensions": true,
    "strict": true,
    "noEmit": true,
    "composite": true,
    "declaration": true,
    "declarationMap": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*"]
}
```

**tiny-oauth-server/src/main/webapp/tsconfig.json**：

```json
{
  "extends": "@vue/tsconfig/tsconfig.web.json",
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"],
      "@tiny/core-ui": ["../../../../packages/tiny-core-ui/src"],
      "@tiny/core-dict-ui": ["../../../../packages/tiny-core-dict-ui/src"]
    }
  },
  "references": [
    { "path": "../../../../packages/tiny-core-ui" },
    { "path": "../../../../packages/tiny-core-dict-ui" }
  ],
  "include": ["src/**/*"]
}
```

#### 4.4.6 开发环境配置

**Vite 配置支持源码引用**：

```typescript
// tiny-oauth-server/src/main/webapp/vite.config.ts
export default defineConfig({
  resolve: {
    alias: {
      "@tiny/core-ui": resolve(
        __dirname,
        "../../../../packages/tiny-core-ui/src"
      ),
      "@tiny/core-dict-ui": resolve(
        __dirname,
        "../../../../packages/tiny-core-dict-ui/src"
      ),
    },
  },
  optimizeDeps: {
    exclude: ["@tiny/core-ui", "@tiny/core-dict-ui"], // 不预构建，使用源码
  },
});
```

**开发环境启动**：

```bash
# 在根目录执行
pnpm install
pnpm dev:webapp
```

### 3.5 权限 / 菜单 / 路由 / 模块统一治理

> 本小节把“前端模块化架构”和“权限 / 菜单 / 路由治理总册”的核心内容合并到一处，作为前端侧的统一治理规范入口。

#### 3.5.1 统一模型与分层原则

- **Module（模块）**：一个业务域的完整 UI 单元，例如 `dict` / `user` / `flow` / `export`。  
  模块 = 路由集合 + 菜单定义 + 权限声明。
- **Page（页面）**：路由与组件的最小单元，天然具备访问权限语义。
- **Permission（权限）**：使用“业务域 + 资源 + 动作”模型，例如：
  - `sys:dict:view`
  - `sys:dict:edit`
  - `sys:user:create`

**前端分层铁律**（与前文 Core/Biz/App 对齐）：

1. **Core 层（`tiny-core-ui`）**：只做“通用 + 无业务语义”，禁止出现任何业务 API 与业务页面。
2. **Biz 层（`tiny-core-dict-ui` / `tiny-core-user-ui` 等）**：单一业务域 UI 模块，必须依赖 Core，不得反向依赖 App。
3. **App 层（`tiny-oauth-server` webapp / 其他前端应用）**：应用装配层，负责组合模块、注册路由、生成菜单、接入权限。

依赖方向：**App → Biz UI → Core UI**（只允许自上而下）。

#### 4.5.2 ModuleMeta：模块统一声明模型

**设计原则**：将核心元数据（路由、权限）与 UI 表现（菜单）分离，为 Feature Toggle、灰度发布、SaaS 定制预留空间。

Core UI 提供统一类型：

```typescript
export interface ModuleMeta {
  // === 核心元数据（必须）===
  key: string; // 模块唯一标识，如 'dict'
  name: string; // 模块名称，如 '字典管理'
  order?: number; // 排序
  routes: RouteRecordRaw[]; // 路由源：模块提供的所有路由
  permissions?: PermissionMeta[]; // 权限源：模块声明的所有权限

  // === UI 表现（可选）===
  ui?: {
    menu?: MenuMeta; // 菜单结构：仅在有菜单时提供
  };
}

export interface MenuMeta {
  title: string;
  icon?: string;
  routeName?: string;
  permission?: string;
  children?: MenuMeta[];
}

export interface PermissionMeta {
  code: string; // 如 'sys:dict:view'
  name: string; // 如 '查看字典'
}
```

**设计理由**：

1. **有些模块可能没有菜单**：API 模块、隐藏模块、纯后台服务模块
2. **Feature Toggle 可能影响菜单，但不影响路由**：路由始终存在，但菜单可根据 Feature 动态显示/隐藏
3. **SaaS 定制场景**：不同租户可能看到不同的菜单结构，但路由和权限保持一致

字典模块在 Biz UI 中只需要定义一次：

```typescript
// packages/tiny-core-dict-ui/src/module.ts
import type { ModuleMeta } from "@tiny/core-ui";

export const DictModule: ModuleMeta = {
  key: "dict",
  name: "字典管理",
  order: 10,

  // 核心元数据：路由和权限
  routes: [
    {
      path: "/sys/dict",
      name: "SysDict",
      component: () => import("./views/DictManagement.vue"),
      meta: {
        title: "字典管理",
        permission: "sys:dict:view",
        feature: "dict.v2", // Feature Toggle：后端治理，前端只消费
      },
    },
  ],

  permissions: [
    { code: "sys:dict:view", name: "查看字典" },
    { code: "sys:dict:edit", name: "编辑字典" },
  ],

  // UI 表现：菜单（可选）
  ui: {
    menu: {
      title: "系统管理",
      icon: "SettingOutlined",
      children: [
        {
          title: "字典管理",
          routeName: "SysDict",
          permission: "sys:dict:view",
        },
      ],
    },
  },
};
```

**无菜单模块示例**（API 模块、隐藏模块）：

```typescript
// packages/tiny-core-api-module/src/module.ts
export const ApiModule: ModuleMeta = {
  key: "api",
  name: "API 管理",
  order: 20,

  routes: [
    {
      path: "/api/docs",
      name: "ApiDocs",
      component: () => import("./views/ApiDocs.vue"),
      meta: { title: "API 文档", permission: "api:docs:view" },
    },
  ],

  permissions: [{ code: "api:docs:view", name: "查看 API 文档" }],

  // 无 ui.menu：此模块不显示在菜单中
};
```

应用层只做**装配**：

```typescript
// app/modules.ts
import { DictModule } from "@tiny/core-dict-ui";
import { UserModule } from "@tiny/core-user-ui";
import { ApiModule } from "@tiny/core-api-module";

export const appModules = [DictModule, UserModule, ApiModule];
```

基于 `appModules`：

- 自动生成路由（`m.routes`）
- 自动生成菜单（`m.ui?.menu`，仅当存在时）
- 自动收集权限（`m.permissions`）

做到“一次声明，多处生效”。

**应用层菜单生成示例**（支持 Feature Toggle）：

```typescript
// app/menu-generator.ts
import { appModules } from "./modules";
import { useFeatureToggle } from "@tiny/core-ui";

export function generateMenus() {
  const { isFeatureEnabled } = useFeatureToggle();

  return appModules
    .filter((m) => {
      // 1. 必须有菜单定义
      if (!m.ui?.menu) return false;

      // 2. Feature Toggle 检查（例如：dict.v2 功能开关）
      const featureKey = `${m.key}.menu`;
      if (!isFeatureEnabled(featureKey)) return false;

      // 3. 权限检查
      const menuPermission = m.ui.menu.permission;
      if (menuPermission && !hasPermission(menuPermission)) return false;

      return true;
    })
    .map((m) => m.ui!.menu!)
    .sort((a, b) => (a.order || 0) - (b.order || 0));
}
```

**SaaS 定制场景示例**（不同租户看到不同菜单）：

```typescript
// app/tenant-menu-generator.ts
import { appModules } from "./modules";
import { getTenantConfig } from "./tenant-config";

export function generateTenantMenus(tenantId: string) {
  const tenantConfig = getTenantConfig(tenantId);

  return appModules
    .filter((m) => {
      // 1. 必须有菜单定义
      if (!m.ui?.menu) return false;

      // 2. 租户配置检查（租户可能禁用某些模块）
      if (tenantConfig.disabledModules?.includes(m.key)) return false;

      // 3. 权限检查
      const menuPermission = m.ui.menu.permission;
      if (menuPermission && !hasPermission(menuPermission)) return false;

      return true;
    })
    .map((m) => {
      // 租户可以自定义菜单标题、图标等
      const customMenu = tenantConfig.customMenus?.[m.key];
      return customMenu || m.ui!.menu!;
    });
}
```

**设计优势总结**：

1. ✅ **路由与菜单解耦**：路由始终存在，菜单可根据 Feature/租户配置动态显示
2. ✅ **支持无菜单模块**：API 模块、隐藏模块、纯后台服务模块不需要菜单
3. ✅ **Feature Toggle 友好**：可以单独控制菜单显示，不影响路由注册
4. ✅ **SaaS 定制友好**：不同租户可以有不同的菜单结构，但路由和权限保持一致

#### 4.5.3 Feature Toggle：后端治理，前端消费

**Feature Toggle 设计原则**：

- **Feature 是后端治理**：Feature 的启用/禁用由后端控制，前端只消费
- **Feature 在路由/菜单/页面级生效**：通过路由 `meta.feature` 声明，在 router guard 中检查
- **Feature 优先级高于权限**：先检查 Feature，再检查 Permission

**路由 Meta 类型定义**：

```typescript
// packages/tiny-core-ui/src/types/router.ts
declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    requiresAuth?: boolean;
    permission?: string; // 权限 code，如 'sys:dict:view'
    feature?: string; // Feature key，如 'dict.v2'
  }
}
```

**Feature Toggle Composable 实现**：

```typescript
// packages/tiny-core-ui/src/composables/useFeatureToggle.ts
import { computed } from "vue";
import { useAuthStore } from "../store/auth";

export function useFeatureToggle() {
  const auth = useAuthStore();

  /**
   * 检查 Feature 是否启用
   * Feature 列表由后端在登录/刷新 Token 时返回
   */
  const isFeatureEnabled = (featureKey: string): boolean => {
    if (!featureKey) return true; // 未声明 Feature，默认启用
    return auth.features.includes(featureKey);
  };

  return { isFeatureEnabled };
}
```

**Router Guard 中的 Feature 检查**（已在 `createCoreRouter` 中实现）：

```typescript
// packages/tiny-core-ui/src/router/core.ts
router.beforeEach(async (to, from, next) => {
  const { isFeatureEnabled } = useFeatureToggle();

  // Feature Toggle 检查（优先级最高）
  if (to.meta.feature && !isFeatureEnabled(to.meta.feature)) {
    next("/exception/404"); // Feature 未启用，返回 404
    return;
  }

  // 继续权限检查...
  next();
});
```

**决策顺序**（Feature → Permission → Data Scope）：

1. **Feature 检查**：Feature 未启用 → 404
2. **Permission 检查**：无权限 → 403
3. **数据权限检查**：无数据访问权限 → 403（具体数据）

**架构定调语**：

- **权限是法律**：长期稳定，可审计
- **Feature 是行政命令**：快速上线/下线，灰度发布

#### 4.5.4 后端权限模型与前端对齐

**后端是唯一权限真相源（Authority of Truth）**，前端只“消费 + 声明使用”：

- 统一采用：**权限 = 业务域 + 资源 + 动作**
  - 示例：`sys:dict:view`、`sys:dict:edit`、`sys:user:create`

后端核心表设计（简化版）：

- `permission(code, name, module, resource, action, status, created_at)`
- `role(id, code, name)`
- `role_permission(role_id, permission_code)`
- `user_role(user_id, role_id)`

后端在登录 / 刷新 Token 时返回：

```json
{
  "user": { "id": 1, "name": "admin" },
  "permissions": ["sys:dict:view", "sys:dict:edit", "sys:user:view"],
  "features": ["dict.v2", "workflow.newEngine"]
}
```

**约束**：

- ❌ 不返回角色树、不下发复杂权限结构
- ✅ 前端只关心 `permission.code` 的列表
- ✅ 前端只关心 `feature` 的列表（Feature Toggle）

#### 4.5.5 前端权限消费与声明规范

前端权限的**唯一职责**：

- 控制入口可见性（路由 / 菜单 / 按钮）
- 减少无效请求
- 提示无权限信息

统一提供 `usePermission()`：

```typescript
// packages/tiny-core-ui/src/composables/usePermission.ts
import { computed } from "vue";
import { useAuthStore } from "../store/auth";

export function usePermission() {
  const auth = useAuthStore();

  const hasPermission = (code?: string) => {
    if (!code) return true;
    return auth.permissions.includes(code);
  };

  const hasAnyPermission = (codes: string[]) =>
    codes.some((c) => auth.permissions.includes(c));

  return { hasPermission, hasAnyPermission };
}
```

并提供统一指令 `v-permission`：

```typescript
// packages/tiny-core-ui/src/directives/permission.ts
import type { Directive } from "vue";
import { usePermission } from "../composables/usePermission";

export const permissionDirective: Directive = {
  mounted(el, binding) {
    const { hasPermission } = usePermission();
    const code = binding.value;

    if (!hasPermission(code)) {
      el.parentNode && el.parentNode.removeChild(el);
    }
  },
};
```

使用规范：

- 路由（同时支持 Feature 和 Permission）：

```typescript
meta: {
  permission: "sys:dict:view",
  feature: "dict.v2" // Feature Toggle：后端治理，前端只消费
}
```

- 菜单：

```typescript
{ title: '字典管理', routeName: 'SysDict', permission: 'sys:dict:view' }
```

- 按钮：

```vue
<Button v-permission="'sys:dict:edit'">编辑</Button>
```

**权限字符串只允许出现在三处**：

1. `ModuleMeta.permissions`
2. `Route.meta.permission`
3. 按钮 / 操作类声明（`v-permission` 或 `hasPermission()` 调用）

禁止：

- ❌ 在任意业务逻辑中手写字符串判断
- ❌ 在 API 封装中硬编码权限

#### 4.5.6 CI 一致性校验（前后端权限对齐）

为避免“前端写了权限，后端没这个权限”或反之，建议：

1. 后端提供导出接口：`/api/sys/permissions/export`，返回所有 `permission(code, name, module, resource, action)`。
2. 前端开发态拉取，生成 `permissions.generated.ts`（只读）。
3. CI 中做 diff：
   - 扫描所有模块的 `ModuleMeta.permissions`
   - 与导出的权限集合做对比
   - 如不一致则 **CI 失败**（强制治理，而不是靠约定）。

通过这一机制，把“权限语义单一来源”落实到工程实践：

- 权限只在后端“治理”，
- 前端任何变更必须与后端权限表保持一致。

### 4.6 插件市场 & SaaS 多租户扩展设计

> 📋 **设计目标**：在既有"模块 / 菜单 / 路由 / 权限 / Feature"统一模型基础上，扩展支持插件市场和 SaaS 多租户能力。

#### 4.6.1 设计目标与核心原则

**设计目标**：

1. **插件市场（Plugin Marketplace）**

   - 模块可独立交付、上架、启停
   - 支持按租户安装插件

2. **SaaS 多租户隔离**

   - 同一套代码，不同租户可用能力不同

3. **权限、Feature、插件、租户四者统一治理**
   - 不引入第二套控制模型
   - 不产生隐式规则

**核心原则**：

- **插件决定「模块是否存在」**：租户未安装插件 → 模块不存在
- **Feature 决定「模块是否启用」**：Feature 未启用 → 模块不可用
- **Permission 决定「用户是否可操作」**：权限不足 → 仅隐藏操作入口
- **Tenant 决定「对谁生效」**：所有控制都在租户维度生效

#### 4.6.2 统一控制模型（四层控制）

**控制判断顺序（不可改变）**：

```
Tenant
  ↓
Plugin
  ↓
Feature
  ↓
Permission
```

**语义说明**：

- **Tenant**：当前请求属于哪个租户
- **Plugin**：租户是否已安装该插件
- **Feature**：插件中的功能是否启用/灰度
- **Permission**：当前用户是否有操作权限

**任何一层失败，直接拒绝访问。**

**决策顺序示例**：

```typescript
// 前端路由守卫（完整版）
router.beforeEach(async (to, from, next) => {
  // 1. Tenant 检查（已在请求头/上下文中）
  const tenantId = getTenantId();

  // 2. Plugin 检查（仅业务插件需要检查，核心模块跳过）
  const pluginKey = to.meta.plugin;
  if (pluginKey) {
    // 只有声明了 plugin 字段的路由才进行插件检查
    // 核心模块（core-user、core-dict）不声明 plugin 字段，直接跳过
    if (!isPluginInstalled(tenantId, pluginKey)) {
      next("/exception/404"); // 插件未安装
      return;
    }
  }

  // 3. Feature 检查
  if (to.meta.feature && !isFeatureEnabled(tenantId, to.meta.feature)) {
    next("/exception/404"); // Feature 未启用
    return;
  }

  // 4. Permission 检查
  if (to.meta.permission && !hasPermission(to.meta.permission)) {
    next("/exception/403"); // 无权限
    return;
  }

  next();
});
```

#### 4.6.3 插件与模块模型定义

**1. ModuleMeta（模块最小语义单元）**

`ModuleMeta` 描述一个业务模块对平台的全部暴露能力：

```typescript
// packages/tiny-core-dict-ui/src/module.meta.ts
// 核心模块示例：数据字典（所有租户默认拥有）
export const DictModuleMeta: ModuleMeta = {
  key: "dict",
  name: "数据字典",
  // 注意：核心模块不声明 plugin 字段，表示所有租户默认拥有

  permissions: [
    { code: "sys:dict:view", name: "查看字典" },
    { code: "sys:dict:create", name: "创建字典" },
    { code: "sys:dict:edit", name: "编辑字典" },
  ],

  routes: [
    {
      path: "/sys/dict",
      name: "SysDict",
      component: () => import("./pages/DictList.vue"),
      meta: {
        permission: "sys:dict:view",
        feature: "dict.v1",
        // 核心模块：不声明 plugin 字段，路由守卫跳过插件检查
      },
    },
  ],

  ui: {
    menu: {
      title: "数据字典",
      icon: "BookOutlined",
      permission: "sys:dict:view",
    },
  },
};
```

```typescript
// packages/tiny-plugin-workflow-ui/src/module.meta.ts
// 业务插件示例：工作流（租户可选安装）
export const WorkflowModuleMeta: ModuleMeta = {
  key: "workflow",
  name: "工作流管理",
  // 业务插件：必须声明 plugin 字段

  permissions: [
    { code: "workflow:view", name: "查看工作流" },
    { code: "workflow:create", name: "创建工作流" },
  ],

  routes: [
    {
      path: "/workflow",
      name: "WorkflowList",
      component: () => import("./pages/WorkflowList.vue"),
      meta: {
        permission: "workflow:view",
        feature: "workflow.v1",
        plugin: "plugin.workflow", // 业务插件：必须声明 plugin 字段
      },
    },
  ],

  ui: {
    menu: {
      title: "工作流管理",
      icon: "DeploymentUnitOutlined",
      permission: "workflow:view",
    },
  },
};
```

**2. PluginMeta（插件 = 商业交付单元）**

> ⚠️ **重要**：只有业务插件才需要定义 `PluginMeta`，核心模块不需要。

插件是 `ModuleMeta` 的集合，是租户安装的最小单位：

```typescript
// packages/tiny-plugin-workflow-ui/src/plugin.meta.ts
export interface PluginMeta {
  key: string; // 插件唯一标识，如 'plugin.workflow'
  name: string; // 插件名称
  version: string; // 插件版本
  modules: ModuleMeta[]; // 插件包含的模块
  defaultFeatures: string[]; // 默认启用的 Feature
  requiredPermissions: string[]; // 插件需要的权限（可选）
  category?: string; // 插件分类（可选）
  description?: string; // 插件描述（可选）
}

export const WorkflowPlugin: PluginMeta = {
  key: "plugin.workflow",
  name: "工作流管理插件",
  version: "1.0.0",
  category: "业务工具",
  description: "提供工作流设计、执行、监控能力",

  modules: [WorkflowModuleMeta],

  defaultFeatures: ["workflow.v1"],

  requiredPermissions: ["workflow:view"],
};
```

**核心模块 vs 业务插件对比**：

| 特性                    | 核心模块（core-\*）      | 业务插件（plugin-\*）              |
| ----------------------- | ------------------------ | ---------------------------------- |
| **是否需要 PluginMeta** | ❌ 不需要                | ✅ 必须                            |
| **路由 meta.plugin**    | ❌ 不声明                | ✅ 必须声明                        |
| **租户默认拥有**        | ✅ 是                    | ❌ 否（需安装）                    |
| **是否可卸载**          | ❌ 不可卸载              | ✅ 可卸载                          |
| **示例**                | `core-user`、`core-dict` | `plugin-workflow`、`plugin-report` |

#### 4.6.4 最小可运行示例仓库结构

> 📋 **说明**：以下结构是"最小可运行示例"的理想结构，用于演示插件市场和 SaaS 多租户的完整实现。实际项目可以根据现有结构渐进式演进。

> ⚠️ **重要设计决策**：区分"平台核心模块"和"业务插件"
>
> - **平台核心模块**：所有租户必须拥有，不可卸载（如用户管理、基础配置）
> - **业务插件**：租户可选安装（如高级报表、工作流引擎、数据分析）

**理想结构（最小可运行示例，优化版）**：

```
tiny-platform/
├── backend/
│   └── server/
│       ├── src/main/java/com/tiny/platform
│       │   ├── infrastructure/        # 基础设施层（系统运行必需，不可抽离）
│       │   │   ├── exception/         # 异常处理
│       │   │   ├── auth/              # 认证授权基础设施
│       │   │   │   ├── user/          # 用户管理（核心基础设施）
│       │   │   │   ├── role/          # 角色管理（核心基础设施）
│       │   │   │   └── resource/      # 资源管理（权限控制核心）
│       │   │   ├── menu/              # 菜单基础设施
│       │   │   ├── plugin/            # 插件管理基础设施
│       │   │   ├── feature/           # Feature Toggle 基础设施
│       │   │   ├── security/         # 安全拦截
│       │   │   └── config/           # 配置
│       │   │
│       │   ├── core/                  # 核心业务层（平台核心能力，可抽离但重要）
│       │   │   ├── tenant/            # 租户管理
│       │   │   ├── dict/              # 数据字典（平台核心能力）
│       │   │   ├── oauth/             # OAuth2 核心
│       │   │   └── workflow/         # 工作流核心
│       │   │
│       │   └── business/              # 业务模块层（可抽离为独立模块）
│       │       ├── export/            # 导出插件
│       │       └── scheduling/        # 调度插件
│       └── pom.xml
│
├── frontend/
│   ├── packages/
│   │   ├── core/                       # 平台核心（无业务）
│   │   │   ├── auth/                   # 权限 / Feature / Tenant
│   │   │   ├── plugin/                 # 插件装配逻辑
│   │   │   ├── router/                 # Router Factory
│   │   │   └── index.ts
│   │   │
│   │   ├── core-user/                   # 用户管理（平台核心模块）
│   │   │   ├── module.meta.ts          # 模块声明（无 plugin 字段）
│   │   │   └── pages/UserList.vue
│   │   │
│   │   ├── core-dict/                   # 数据字典（平台核心模块）
│   │   │   ├── module.meta.ts          # 模块声明（无 plugin 字段）
│   │   │   └── pages/DictList.vue
│   │   │
│   │   ├── plugin-workflow/             # 工作流插件（业务插件）
│   │   │   ├── module.meta.ts           # 模块声明
│   │   │   ├── plugin.meta.ts          # 插件声明
│   │   │   └── pages/WorkflowList.vue
│   │   │
│   │   └── app-main/                   # 应用装配层
│   │       ├── main.ts
│   │       ├── router.ts
│   │       └── App.vue
│   │
│   └── package.json
│
└── README.md
```

**关键设计说明**：

1. **基础设施 vs 核心业务 vs 业务插件**

   - **基础设施（infrastructure）**：系统运行必需，不可抽离
     - `infrastructure.auth.*`：认证授权核心（User、Role、Resource）
     - `infrastructure.menu`：菜单核心
     - `infrastructure.plugin`：插件管理基础设施
     - `infrastructure.feature`：Feature Toggle 基础设施
   - **核心业务（core）**：平台核心能力，可抽离但重要
     - `core.dict`：数据字典（平台核心能力）
     - `core.tenant`：租户管理
     - `core.oauth`：OAuth2 核心
     - `core.workflow`：工作流核心
   - **业务插件（business）**：可抽离为独立模块
     - `business.export`：导出插件
     - `business.scheduling`：调度插件

2. **模块声明差异**

   - **基础设施模块**：`ModuleMeta` 中**不声明 `plugin` 字段**，路由守卫跳过插件检查
   - **核心业务模块**：`ModuleMeta` 中**不声明 `plugin` 字段**，路由守卫跳过插件检查
   - **业务插件**：`ModuleMeta` 中**必须声明 `plugin` 字段**，路由守卫进行插件检查

3. **后端目录结构**
   - `infrastructure/` 目录：基础设施层，系统运行必需
   - `core/` 目录：核心业务层，平台核心能力
   - `business/` 目录：业务模块层，可抽离的插件

**结构合理性分析**：

✅ **优点**：

1. **前后端分离清晰**：`backend/` 和 `frontend/` 目录分离，职责明确
2. **核心与插件分离**：明确区分平台核心模块和业务插件，设计更合理
3. **前端模块化完整**：`packages/core`（平台核心）、`packages/core-*`（核心模块）、`packages/plugin-*`（业务插件）、`packages/app-*`（应用装配层）四层结构清晰
4. **后端模块化清晰**：`core/`（核心模块）和 `plugin/`（业务插件）分离
5. **符合 Monorepo 最佳实践**：前后端统一管理，但构建系统独立

⚠️ **需要注意的问题**：

1. **与现有项目结构不一致**：

   - 现有项目是 Maven 多模块结构（`tiny-oauth-server`、`tiny-core-dict-web` 等）
   - 理想结构是 `backend/server` 单模块结构
   - **建议**：保持现有 Maven 结构，前端部分可以按理想结构组织

2. **前端目录命名不一致**：

   - 理想结构示例：`packages/core`、`packages/core-dict`、`packages/plugin-workflow`（仅用于演示）
   - 实际项目统一使用：`packages/tiny-core-ui`、`packages/tiny-core-dict-ui`、`packages/tiny-plugin-workflow-ui` 等
   - **建议**：统一命名规范，使用 `tiny-core-ui`、`tiny-core-dict-ui` 等

3. **构建系统混合**：
   - 后端：Maven（`pom.xml`）
   - 前端：npm/pnpm（`package.json`）
   - **建议**：在根目录同时保留 `pom.xml` 和 `package.json`，这是合理的

**核心设计改进**：

1. ✅ **增加基础设施层**：明确区分基础设施（`infrastructure/`）和核心业务（`core/`）
2. ✅ **user/role/resource 是基础设施**：认证授权是系统运行必需，应放在 `infrastructure.auth.*`
3. ✅ **dict 是核心业务能力**：数据字典是平台核心能力，放在 `core.dict`，但仍然是可抽离的
4. ✅ **区分基础设施、核心业务、业务插件**：
   - 基础设施（`infrastructure.*`）：系统运行必需，不可卸载
   - 核心业务（`core.*`）：平台核心能力，可抽离但重要
   - 业务插件（`business.*`）：可抽离为独立模块

**推荐的渐进式演进结构**（基于现有项目，优化版）：

```
tiny-platform/
├── pom.xml                           # Maven 父 POM（保持不变）
├── package.json                      # npm workspace 根配置
├── pnpm-workspace.yaml              # pnpm workspace 配置
│
├── backend/                          # 后端模块（Maven）
│   ├── tiny-oauth-server/           # OAuth Server（保持现有）
│   │   └── src/main/java/com/tiny/platform/
│   │       ├── infrastructure/      # 基础设施层
│   │       │   ├── exception/       # 异常处理
│   │       │   ├── auth/          # 认证授权（User, Role, Resource）
│   │       │   ├── menu/          # 菜单
│   │       │   ├── plugin/        # 插件管理（NEW）
│   │       │   ├── feature/       # Feature Toggle（NEW）
│   │       │   ├── security/      # 安全拦截
│   │       │   └── config/        # 配置
│   │       ├── core/               # 核心业务层
│   │       │   ├── tenant/        # 租户管理
│   │       │   ├── dict/          # 数据字典（平台核心能力）
│   │       │   ├── oauth/         # OAuth2 核心
│   │       │   └── workflow/      # 工作流核心
│   │       ├── business/          # 业务模块层
│   │       │   ├── export/        # 导出插件
│   │       │   └── scheduling/    # 调度插件
│   │       └── application/       # 应用层
│   │           └── controller/    # 控制器
│   ├── tiny-core-dict-web/         # 字典后端模块（保持现有）
│   └── ...
│
├── frontend/                        # 前端模块（npm workspace）
│   ├── packages/
│   │   ├── tiny-core-ui/            # 平台核心（统一命名）
│   │   │   ├── src/auth/
│   │   │   ├── src/plugin/
│   │   │   ├── src/router/
│   │   │   └── src/index.ts
│   │   │
│   │   ├── tiny-core-dict-ui/       # 字典业务插件（统一命名）
│   │   │   ├── src/module.meta.ts
│   │   │   ├── src/plugin.meta.ts
│   │   │   └── src/pages/
│   │   │
│   │   └── app-main/                # 应用装配层
│   │       ├── src/main.ts
│   │       ├── src/router.ts
│   │       └── src/App.vue
│   │
│   └── package.json
│
└── README.md
```

**关键改进点**：

1. ✅ **增加基础设施层**：明确区分 `infrastructure/`（基础设施）、`core/`（核心业务）、`business/`（业务插件）
2. ✅ **保持现有 Maven 结构**：不强制要求 `backend/server` 单模块，保持 `tiny-oauth-server`、`tiny-core-dict-web` 等多模块结构
3. ✅ **统一前端命名**：使用 `tiny-core-ui`、`tiny-core-dict-ui` 等，与文档其他部分一致
4. ✅ **前后端分离但统一管理**：`backend/` 和 `frontend/` 目录分离，但都在同一个 Monorepo 中
5. ✅ **构建系统共存**：根目录同时有 `pom.xml` 和 `package.json`，这是合理的

**结论**：

- ✅ **理想结构已优化**：增加基础设施层，明确区分基础设施、核心业务、业务插件
- ✅ **推荐使用渐进式演进结构**，保持现有 Maven 多模块结构，前端部分按理想结构组织
- ✅ **命名规范统一**：前端模块使用 `tiny-core-*` 前缀，与现有命名保持一致
- ✅ **结构更清晰**：基础设施（不可抽离）、核心业务（可抽离但重要）、业务插件（可抽离）三层结构清晰

#### 4.6.5 前端插件装配与运行机制

**1. 前端启动流程**

```
App 启动
  ↓
加载租户信息（从请求头/上下文）
  ↓
加载平台核心模块（core-user、core-dict 等，所有租户默认拥有）
  ↓
加载租户已安装插件（从后端 API，仅业务插件）
  ↓
加载租户 Feature 配置（从后端 API）
  ↓
加载用户权限（从登录 Token）
  ↓
装配模块 / 菜单 / 路由
  ↓
  - 核心模块：直接装配（无需插件检查）
  - 业务插件：检查插件安装状态后装配
```

**2. 模块装配器（核心逻辑）**

```typescript
// packages/tiny-core-ui/src/plugin/resolver.ts
import type { PluginMeta, ModuleMeta } from "../types";

interface TenantContext {
  tenantId: string;
  plugins: Array<{ key: string; enabled: boolean }>;
  features: string[];
  permissions: string[];
}

/**
 * 模块装配逻辑：
 * 1. 核心模块（core-*）：所有租户默认拥有，直接装配
 * 2. 业务插件（plugin-*）：检查租户是否已安装，已安装则装配
 */
export function resolveModules(ctx: TenantContext): ModuleMeta[] {
  // 1. 加载核心模块（所有租户默认拥有）
  const coreModules = [
    getCoreModule("core-user"),
    getCoreModule("core-dict"),
    // ... 其他核心模块
  ];

  // 2. 加载业务插件模块（仅已安装的插件）
  const pluginModules = ctx.plugins
    .filter((p) => p.enabled) // 插件已安装且启用
    .flatMap((p) => {
      const pluginMeta = getPluginMeta(p.key);
      return pluginMeta.modules;
    });

  // 3. 合并所有模块
  const allModules = [...coreModules, ...pluginModules];

  // 4. Feature 检查：模块的所有 Feature 都必须启用
  return allModules.filter((m) => {
    const moduleFeatures = m.features || [];
    return moduleFeatures.every((f) => ctx.features.includes(f));
  });
}
```

**说明**：

- 插件未安装 → 模块不存在（不会出现在路由/菜单中）
- Feature 未开启 → 模块不可用（路由返回 404）
- 权限不足 → 仅隐藏操作入口（路由/菜单/按钮）

**3. 应用层插件装配示例**

```typescript
// app-main/src/main.ts
import { createApp } from "vue";
import { createPinia } from "pinia";
import { resolveModules, createCoreRouter } from "@tiny/core-ui";
import { DictPlugin } from "@tiny/core-dict-ui";

async function bootstrap() {
  const app = createApp(App);
  app.use(createPinia());

  // 1. 加载租户上下文
  const tenantContext = await loadTenantContext();

  // 2. 注册所有可用插件（代码层面）
  const availablePlugins = [DictPlugin /* ... 其他插件 */];

  // 3. 根据租户已安装的插件，解析可用模块
  const installedPlugins = tenantContext.plugins.map((p) =>
    availablePlugins.find((ap) => ap.key === p.key)
  );
  const modules = resolveModules({
    ...tenantContext,
    plugins: installedPlugins,
  });

  // 4. 创建路由
  const router = createCoreRouter({
    routes: modules.flatMap((m) => m.routes),
  });
  app.use(router);

  app.mount("#app");
}

bootstrap();
```

#### 4.6.6 后端 SaaS 多租户表设计

**1. tenant（租户表）**

```sql
CREATE TABLE tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) UNIQUE NOT NULL COMMENT '租户编码',
  name VARCHAR(128) NOT NULL COMMENT '租户名称',
  status TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**2. tenant_plugin（租户插件表）**

```sql
CREATE TABLE tenant_plugin (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  plugin_key VARCHAR(128) NOT NULL COMMENT '插件标识',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  installed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_plugin (tenant_id, plugin_key),
  FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
```

**3. tenant_feature（租户 Feature 表）**

```sql
CREATE TABLE tenant_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  feature_key VARCHAR(128) NOT NULL COMMENT 'Feature 标识',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  rollout INT DEFAULT 100 COMMENT '灰度比例：0-100',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_feature (tenant_id, feature_key),
  FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
```

**说明**：

- **权限仍为用户级**，不复制到租户表（用户权限通过 `user_role` → `role_permission` 关联）
- **插件、Feature 为租户级**，每个租户独立配置

#### 4.6.7 后端统一拦截顺序（强制）

**拦截顺序（不可改变）**：

```
Request
  ↓
TenantContextFilter（提取租户 ID）
  ↓
PluginInstallFilter（检查插件是否安装）
  ↓
FeatureToggleFilter（检查 Feature 是否启用）
  ↓
PermissionInterceptor（检查用户权限）
  ↓
Controller
```

**实现示例**：

```java
// TenantContextFilter
@Component
public class TenantContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantId = httpRequest.getHeader("X-Tenant-ID");
        TenantContext.set(tenantId);
        chain.doFilter(request, response);
    }
}

// PluginInstallFilter
@Component
public class PluginInstallFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String tenantId = TenantContext.get();
        String pluginKey = extractPluginKey(request);

        if (pluginKey != null && !isPluginInstalled(tenantId, pluginKey)) {
            throw new PluginNotInstalledException();
        }

        chain.doFilter(request, response);
    }
}
```

#### 4.6.8 Controller 统一注解规范

**示例**：

```java
@RestController
@RequestMapping("/api/dict")
public class DictController {

    @Plugin("plugin.dict")           // 插件检查
    @Feature("dict.v1")               // Feature 检查
    @Permission("dict:view")          // 权限检查
    @GetMapping
    public List<DictDTO> list() {
        // Controller 内禁止写权限判断
        // 所有控制都在注解层面完成
        return dictService.list();
    }
}
```

**任何一个条件不满足**：

- 直接返回 403 / 404
- Controller 内禁止写权限判断
- 防止系统熵增

#### 4.6.9 为什么我们这样设计

**1. 插件 ≠ 权限**

- **插件解决"是否交付"**：商业层面的能力交付
- **权限解决"是否允许"**：安全层面的操作控制
- **混用会导致商业与安全语义混乱**

**2. Feature ≠ 租户**

- **Feature 用于灰度、实验、版本切换**：技术层面的功能控制
- **租户是商业实体**：业务层面的客户隔离
- **混用将导致灰度失控**

**3. 前端权限只负责"可见性"**

- 所有安全边界必须在后端
- 前端权限是体验优化而非安全机制

**4. App 层只做装配**

- 不写业务逻辑
- 不写权限判断
- 防止系统熵增

#### 4.6.10 最终效果

该体系可以支撑：

- ✅ **插件市场**：模块可独立交付、上架、启停
- ✅ **SaaS 多租户**：同一套代码，不同租户可用能力不同
- ✅ **模块级灰度**：通过 Feature Toggle 控制功能启用
- ✅ **权限审计**：权限语义单一来源，可审计
- ✅ **长期演进不崩盘**：统一控制模型，不产生隐式规则

**目标不是"能跑"，而是**：

系统在 3 年后仍然：

- ✅ **敢改**：清晰的模块边界，改动影响可控
- ✅ **敢升级**：插件化架构，升级风险可控
- ✅ **敢扩展**：统一控制模型，扩展成本可控

## 五、业务模块示例

### 5.1 字典管理模块（tiny-core-dict-ui）

#### 5.1.0 模块化决策分析

**当前状态**：

- 字典管理界面当前位于 `tiny-oauth-server/src/main/webapp/src/views/dict/`
- 包含 DictManagement.vue、DictTypeForm.vue、DictItemForm.vue 等组件
- API 封装在 `api/dict.ts`，组合式函数在 `composables/useDict.ts`

**问题分析**：

- ❌ **耦合度高**：字典管理界面绑定到 `tiny-oauth-server` 应用
- ❌ **复用困难**：如果其他应用也需要字典管理界面，需要复制代码
- ❌ **职责不清**：`tiny-oauth-server` 是 OAuth 认证服务器，不应该包含字典管理功能
- ❌ **不符合模块化**：字典是平台级基础设施，管理界面应该独立

**模块化方案**：

- ✅ **创建独立模块**：`tiny-core-dict-ui` 作为独立的前端模块
- ✅ **职责清晰**：字典 UI 独立，不绑定到具体应用
- ✅ **易于复用**：其他应用可以直接引用
- ✅ **符合模块化**：前后端分离，模块独立

**实施建议**：

- **当前阶段**：完成前端模块化，提取字典 UI 到 `tiny-core-dict-ui`
- **未来优化**：如果多个应用需要，可以考虑发布为 npm 包

#### 5.1.1 模块结构

```
packages/tiny-core-dict-ui/
├── src/
│   ├── views/                   # 字典相关页面
│   │   ├── DictManagement.vue
│   │   ├── DictItemForm.vue
│   │   └── DictTypeForm.vue
│   ├── api/                     # API 封装
│   │   └── dict.ts
│   ├── composables/             # 字典相关组合式函数
│   │   └── useDict.ts
│   └── router/                  # 字典路由配置
│       └── routes.ts
├── package.json
└── vite.config.ts
```

#### 5.1.2 路由配置

```typescript
// packages/tiny-core-dict-ui/src/router/routes.ts
import type { RouteRecordRaw } from "vue-router";
import DictManagement from "../views/DictManagement.vue";

const MODULE_PREFIX = "/dict";

export const dictRoutes: RouteRecordRaw[] = [
  {
    path: `${MODULE_PREFIX}/management`,
    name: "DictManagement",
    component: DictManagement,
    meta: {
      title: "字典管理",
      requiresAuth: true,
      module: "dict",
    },
  },
  // ... 其他路由
];
```

#### 5.1.3 使用示例

```typescript
// 应用路由整合
import { dictRoutes } from "@tiny/core-dict-ui";

const appRoutes = [
  {
    path: "/",
    component: BasicLayout,
    children: [
      ...dictRoutes, // 整合字典路由
    ],
  },
];
```

### 5.2 Export 管理模块（tiny-core-export-ui）

#### 5.2.0 模块化决策分析

**当前状态**：

- Export 前端页面位于 `tiny-oauth-server/src/main/webapp/src/views/export/`
- 包含 ExportTask.vue、ExportTaskExamples.vue 等组件
- API 封装在 `api/export.ts`
- Export 后端模块已平台化（独立的后端模块）

**前端处理策略对比**：

**方案 A：保留在应用中（不推荐）**：

- 前端页面保留在 `tiny-oauth-server` 中
- 如果多个应用需要，会有代码重复
- 更新需要同步到多个地方

**方案 B：提取为前端模块（推荐）** ⭐⭐⭐⭐⭐：

- 创建 `tiny-core-export-ui` 独立模块
- 与后端 Export 平台模块对应
- 可以独立发布和版本管理
- 多个应用可以复用

**模块化方案**：

- ✅ **创建独立模块**：`tiny-core-export-ui` 作为独立的前端模块
- ✅ **与后端对齐**：前端模块与后端 Export 平台模块对应
- ✅ **易于复用**：其他应用可以直接引用
- ✅ **符合架构**：前后端分离，模块独立

**实施建议**：

- **当前阶段**：完成前端模块化，提取 Export UI 到 `tiny-core-export-ui`
- **与后端配合**：Export 后端已平台化，前端也应该模块化

#### 5.2.1 当前状态分析

**前端页面位置**：

```
tiny-oauth-server/src/main/webapp/src/
├── views/export/
│   ├── ExportTask.vue           # 导出任务管理页面（主要）
│   ├── ExportTaskExamples.vue   # 示例页面
│   └── testData.vue             # 测试数据页面
├── api/
│   └── export.ts                # Export API 调用
```

**功能特性**：

- 导出任务列表查询
- 任务状态管理（PENDING/RUNNING/SUCCESS/FAILED）
- 任务进度展示
- 文件下载
- 任务取消
- 列设置和排序

#### 5.2.2 模块化方案

**目标结构**：

```
packages/tiny-core-export-ui/
├── src/
│   ├── views/                   # Export 相关页面
│   │   ├── ExportTask.vue
│   │   ├── ExportTaskExamples.vue
│   │   └── components/         # 子组件
│   │       ├── ExportTaskTable.vue
│   │       └── ExportTaskDetail.vue
│   ├── api/                     # API 封装
│   │   └── export.ts
│   ├── composables/             # Composition API
│   │   └── useExport.ts
│   └── router/                  # Export 路由配置
│       └── routes.ts
├── package.json
└── vite.config.ts
```

#### 5.2.3 路由配置

```typescript
// packages/tiny-core-export-ui/src/router/routes.ts
import type { RouteRecordRaw } from "vue-router";
import ExportTask from "../views/ExportTask.vue";
import ExportTaskExamples from "../views/ExportTaskExamples.vue";

const MODULE_PREFIX = "/export";

export const exportRoutes: RouteRecordRaw[] = [
  {
    path: `${MODULE_PREFIX}/task`,
    name: "ExportTask",
    component: ExportTask,
    meta: {
      title: "导出任务管理",
      requiresAuth: true,
      module: "export",
    },
  },
  {
    path: `${MODULE_PREFIX}/examples`,
    name: "ExportTaskExamples",
    component: ExportTaskExamples,
    meta: {
      title: "导出示例",
      requiresAuth: true,
      module: "export",
    },
  },
];
```

#### 5.2.4 API 封装

```typescript
// packages/tiny-core-export-ui/src/api/export.ts
import { request } from "@tiny/core-ui";

export interface ExportTask {
  taskId: string;
  userId: string;
  username?: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
  progress?: number;
  totalRows?: number;
  processedRows?: number;
  filePath?: string;
  downloadUrl?: string;
  errorMsg?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const exportApi = {
  // 获取任务列表
  listTasks: (params?: any) =>
    request.get<ExportTask[]>("/export/task", { params }),

  // 获取任务详情
  getTask: (taskId: string) =>
    request.get<ExportTask>(`/export/task/${taskId}`),

  // 取消任务
  cancelTask: (taskId: string) => request.post(`/export/task/${taskId}/cancel`),

  // 下载文件
  downloadFile: (taskId: string) =>
    request.get(`/export/task/${taskId}/download`, { responseType: "blob" }),
};
```

#### 5.2.5 使用示例

```typescript
// 应用路由整合
import { exportRoutes } from "@tiny/core-export-ui";

const appRoutes = [
  {
    path: "/",
    component: BasicLayout,
    children: [
      ...exportRoutes, // 整合 Export 路由
    ],
  },
];
```

```vue
<!-- 使用 Export 组件 -->
<template>
  <ExportTaskList />
</template>

<script setup lang="ts">
import { ExportTaskList } from "@tiny/core-export-ui";
</script>
```

#### 5.2.6 与后端的关系

- **API 调用**：通过 `/export/task` 等 REST API 调用后端服务
- **路由配置**：通过动态菜单系统加载，路径由后端菜单配置决定
- **业务耦合**：前端页面主要展示导出任务，业务逻辑在后端

**后端模块化背景**：

- Export 后端模块已平台化（独立的后端模块：`tiny-export-platform`）
- 包含 export-core、export-service、export-web 等模块
- 前端 UI 模块化与后端平台化对应，保持架构一致性
- 前后端分离，前端通过 REST API 调用后端服务

### 5.3 其他业务模块

未来可以按照相同的模式创建其他业务模块：

- `tiny-core-user-ui`：用户管理模块
- `tiny-core-process-ui`：流程管理模块
- `tiny-core-role-ui`：角色管理模块
- 等等...

**模块创建原则**：

1. 遵循统一的模块结构
2. 使用路由前缀机制（`/{module-name}/...`）
3. 导出路由配置和组件
4. 提供 API 封装和组合式函数

## 六、部署方案

### 6.1 All-in-one 部署（小用户体量）

**架构**：

```
┌─────────────────────────────────┐
│   tiny-oauth-server.jar         │
│   ┌───────────────────────────┐ │
│   │  Spring Boot Backend      │ │
│   └───────────────────────────┘ │
│   ┌───────────────────────────┐ │
│   │  Static Resources (/dist) │ │
│   │  - index.html             │ │
│   │  - assets/*.js            │ │
│   │  - assets/*.css           │ │
│   └───────────────────────────┘ │
└─────────────────────────────────┘
```

**特点**：

- ✅ 单一 JAR 包部署，运维简单
- ✅ 前后端版本一致，避免版本不匹配
- ✅ 适合小用户体量场景
- ✅ 减少网络请求（同域）

**构建流程**：

```bash
# 1. 构建前端
cd applications/tiny-oauth-server/src/main/webapp
npm run build:prod

# 2. 构建后端（前端资源会自动打包到 JAR）
cd applications/tiny-oauth-server
mvn clean package

# 3. 部署
java -jar tiny-oauth-server.jar
```

### 6.2 前后端分离部署（大用户体量）

**架构**：

```
┌─────────────────┐         ┌─────────────────┐
│  Nginx/CDN       │         │  Backend Server  │
│  ┌─────────────┐ │         │  ┌─────────────┐ │
│  │ Frontend    │ │ ──────► │  │ Spring Boot │ │
│  │ (静态资源)   │ │  API    │  │   API       │ │
│  └─────────────┘ │         │  └─────────────┘ │
└─────────────────┘         └─────────────────┘
```

**特点**：

- ✅ 前后端独立扩展
- ✅ 前端可部署到 CDN，提升性能
- ✅ 后端可水平扩展
- ✅ 适合大用户体量场景

**构建流程**：

```bash
# 1. 构建独立前端应用
cd applications/tiny-app-frontend
npm run build:prod

# 2. 部署前端到 Nginx/CDN
# 前端资源部署到静态服务器

# 3. 构建后端（不包含前端资源）
cd applications/tiny-oauth-server
mvn clean package -Dskip.frontend.build

# 4. 部署后端
java -jar tiny-oauth-server.jar
```

### 6.3 混合部署（渐进式迁移）

**场景**：

- 初期使用 All-in-one 部署
- 随着用户增长，逐步迁移到前后端分离

**实现**：

- 通过配置控制前端资源来源
- 支持从 JAR 内静态资源切换到外部 CDN

```yaml
# application.yaml
frontend:
  mode: embedded # embedded | external
  external-url: https://cdn.example.com # external 模式使用
```

## 七、迁移计划

### 7.1 阶段一：模块提取（1-2 周）

1. **创建 tiny-core-ui 模块**

   - 提取布局组件
   - 提取认证模块
   - 提取路由核心
   - 提取通用工具

2. **创建 tiny-core-dict-ui 模块**

   - 提取字典相关页面
   - 提取字典 API 封装
   - 提取字典路由配置

3. **配置 Monorepo**
   - 设置 workspace
   - 配置依赖关系
   - 配置构建脚本

### 7.2 阶段二：应用重构（1-2 周）

1. **重构 tiny-oauth-server/webapp**

   - 引入 tiny-core-ui
   - 引入 tiny-core-dict-ui
   - 重构路由配置
   - 测试功能完整性

2. **创建独立前端应用（可选）**
   - 创建 tiny-app-frontend
   - 配置独立构建和部署
   - 测试前后端分离部署

### 7.3 阶段三：优化和完善（1 周）

1. **文档完善**

   - 模块使用文档
   - 部署文档
   - 开发指南

2. **CI/CD 优化**
   - 配置自动化构建
   - 配置自动化测试
   - 配置自动化部署

## 八、优势与收益

### 8.1 技术优势

1. **代码复用**：核心 UI 和业务 UI 可被多个应用复用
2. **模块化**：清晰的模块边界，易于维护和扩展
3. **灵活性**：支持 All-in-one 和前后端分离两种部署模式
4. **可扩展性**：易于添加新的 UI 模块和应用

### 8.2 业务优势

1. **快速开发**：新应用可快速集成现有 UI 模块
2. **统一体验**：多个应用使用相同的核心 UI，用户体验一致
3. **成本优化**：小用户体量使用 All-in-one，大用户体量使用分离部署
4. **渐进式演进**：支持从集中部署平滑迁移到分离部署

## 九、注意事项

### 9.1 版本管理

- 使用语义化版本号
- 核心模块版本变更需要谨慎
- 应用依赖固定版本或版本范围

### 9.2 依赖管理

- 避免循环依赖
- 统一管理第三方依赖版本
- 使用 peerDependencies 声明外部依赖

### 9.3 构建优化

- 使用 Tree-shaking 减少打包体积
- 合理使用代码分割
- 优化静态资源加载

### 9.4 兼容性

- 确保 Vue 3 版本一致
- 确保 Ant Design Vue 版本一致
- 确保 TypeScript 版本一致

## 十、潜在问题与优化建议

### 10.1 版本管理和依赖冲突

#### 问题描述

核心模块版本更新可能导致应用兼容性问题，特别是在多应用场景下，不同应用可能依赖不同版本的核心模块。

#### 解决方案

**1. 使用 workspace 固定版本**

在根 `package.json` 中统一管理核心模块版本：

```json
// 根 package.json
{
  "name": "tiny-platform",
  "private": true,
  "workspaces": ["packages/*", "applications/*"],
  "resolutions": {
    "@tiny/core-ui": "workspace:*",
    "@tiny/core-dict-ui": "workspace:*",
    "vue": "^3.5.25",
    "vue-router": "^4.6.3",
    "ant-design-vue": "^4.2.6"
  }
}
```

**2. 使用 peerDependencies 控制外部依赖**

核心模块使用 `peerDependencies` 声明外部依赖，避免版本冲突：

```json
// packages/tiny-core-ui/package.json
{
  "name": "@tiny/core-ui",
  "version": "1.0.0",
  "peerDependencies": {
    "vue": "^3.5.0",
    "vue-router": "^4.6.0",
    "pinia": "^3.0.0",
    "ant-design-vue": "^4.2.0"
  },
  "peerDependenciesMeta": {
    "vue": {
      "optional": false
    },
    "vue-router": {
      "optional": false
    },
    "pinia": {
      "optional": false
    },
    "ant-design-vue": {
      "optional": false
    }
  }
}
```

**3. 版本锁定策略**

```json
// applications/tiny-oauth-server/src/main/webapp/package.json
{
  "name": "@tiny/oauth-server-webapp",
  "dependencies": {
    "@tiny/core-ui": "1.0.0", // 固定版本，避免自动升级
    "@tiny/core-dict-ui": "1.0.0"
  }
}
```

**4. 版本兼容性检查脚本**

```typescript
// scripts/check-dependency-versions.ts
import { readFileSync } from "fs";
import { join } from "path";

interface PackageJson {
  dependencies?: Record<string, string>;
  peerDependencies?: Record<string, string>;
}

function checkVersions() {
  const rootPkg = JSON.parse(
    readFileSync(join(__dirname, "../package.json"), "utf-8")
  ) as PackageJson;

  // 检查各模块的依赖版本是否一致
  // ...
}

checkVersions();
```

### 10.2 路由冲突与整合复杂度

#### 问题描述

多模块路由整合时可能出现命名或路径冲突，例如多个模块都定义了 `/user` 路由。

#### 解决方案

**1. 路由命名规范**

建立统一的路由命名规范：

```typescript
// packages/tiny-core-ui/src/router/naming-convention.ts
/**
 * 路由命名规范：
 * - 模块路由名称格式：{ModuleName}_{PageName}
 * - 路由路径格式：/{module}/{page}
 *
 * 示例：
 * - DictManagement -> /dict/management
 * - UserList -> /user/list
 * - ProcessDefinition -> /process/definition
 */
export const ROUTE_NAMING_CONVENTION = {
  MODULE_PREFIX: {
    DICT: "dict",
    USER: "user",
    PROCESS: "process",
    ROLE: "role",
    RESOURCE: "resource",
  },
} as const;
```

**2. 路由前缀机制**

各业务模块路由自动添加前缀：

```typescript
// packages/tiny-core-dict-ui/src/router/routes.ts
import type { RouteRecordRaw } from "vue-router";
import DictManagement from "../views/DictManagement.vue";
import DictItemForm from "../views/DictItemForm.vue";
import DictTypeForm from "../views/DictTypeForm.vue";

const MODULE_PREFIX = "/dict";

export const dictRoutes: RouteRecordRaw[] = [
  {
    path: `${MODULE_PREFIX}/management`,
    name: "DictManagement",
    component: DictManagement,
    meta: {
      title: "字典管理",
      requiresAuth: true,
      module: "dict", // 标识模块来源
    },
  },
  {
    path: `${MODULE_PREFIX}/item/form`,
    name: "DictItemForm",
    component: DictItemForm,
    meta: { title: "字典项表单", requiresAuth: true, module: "dict" },
  },
  {
    path: `${MODULE_PREFIX}/type/form`,
    name: "DictTypeForm",
    component: DictTypeForm,
    meta: { title: "字典类型表单", requiresAuth: true, module: "dict" },
  },
];
```

**3. 路由冲突检测工具**

```typescript
// packages/tiny-core-ui/src/router/route-validator.ts
import type { RouteRecordRaw } from "vue-router";

interface RouteConflict {
  path: string;
  modules: string[];
}

export function detectRouteConflicts(
  routes: RouteRecordRaw[]
): RouteConflict[] {
  const pathMap = new Map<string, string[]>();

  function collectPaths(routes: RouteRecordRaw[], prefix = "") {
    routes.forEach((route) => {
      const fullPath = prefix + route.path;
      const module = (route.meta?.module as string) || "unknown";

      if (pathMap.has(fullPath)) {
        pathMap.get(fullPath)!.push(module);
      } else {
        pathMap.set(fullPath, [module]);
      }

      if (route.children) {
        collectPaths(route.children, fullPath);
      }
    });
  }

  collectPaths(routes);

  const conflicts: RouteConflict[] = [];
  pathMap.forEach((modules, path) => {
    if (modules.length > 1) {
      conflicts.push({ path, modules: [...new Set(modules)] });
    }
  });

  return conflicts;
}

// 使用示例
export function validateRoutes(routes: RouteRecordRaw[]): void {
  const conflicts = detectRouteConflicts(routes);
  if (conflicts.length > 0) {
    console.error("路由冲突检测:", conflicts);
    throw new Error(`发现 ${conflicts.length} 个路由冲突`);
  }
}
```

**4. 路由整合辅助函数**

```typescript
// packages/tiny-core-ui/src/router/route-merger.ts
import type { RouteRecordRaw } from "vue-router";

interface ModuleRoutes {
  module: string;
  routes: RouteRecordRaw[];
}

export function mergeModuleRoutes(
  modules: ModuleRoutes[],
  baseRoutes: RouteRecordRaw[] = []
): RouteRecordRaw[] {
  const mergedRoutes = [...baseRoutes];
  const pathSet = new Set<string>();

  // 收集所有路径
  function collectPaths(routes: RouteRecordRaw[]): string[] {
    const paths: string[] = [];
    routes.forEach((route) => {
      paths.push(route.path);
      if (route.children) {
        paths.push(...collectPaths(route.children));
      }
    });
    return paths;
  }

  // 检查冲突
  modules.forEach(({ module, routes }) => {
    const paths = collectPaths(routes);
    paths.forEach((path) => {
      if (pathSet.has(path)) {
        throw new Error(`路由路径冲突: ${path} (模块: ${module})`);
      }
      pathSet.add(path);
    });
  });

  // 合并路由
  modules.forEach(({ routes }) => {
    mergedRoutes.push(...routes);
  });

  return mergedRoutes;
}
```

**5. 应用路由整合示例**

```typescript
// applications/tiny-oauth-server/src/main/webapp/src/router/index.ts
import { createCoreRouter, type CoreRouterConfig } from "@tiny/core-ui";
import { dictRoutes } from "@tiny/core-dict-ui";
import { mergeModuleRoutes, validateRoutes } from "@tiny/core-ui";
import { useAuth, initPromise } from "@tiny/core-ui";
import BasicLayout from "@/layouts/BasicLayout.vue";
import Login from "@/views/Login.vue";
import Dashboard from "@/views/Dashboard.vue";

// 应用特定路由
const appRoutes: RouteRecordRaw[] = [
  { path: "/login", name: "Login", component: Login },
  {
    path: "/",
    component: BasicLayout,
    children: [{ path: "", name: "Dashboard", component: Dashboard }],
  },
];

// 整合模块路由
const moduleRoutes = [
  { module: "dict", routes: dictRoutes },
  // 未来可以添加更多模块
  // { module: 'user', routes: userRoutes },
  // { module: 'process', routes: processRoutes },
];

const allRoutes = mergeModuleRoutes(moduleRoutes, appRoutes);

// 验证路由冲突
validateRoutes(allRoutes);

// 创建路由实例
const routerConfig: CoreRouterConfig = {
  routes: allRoutes,
  beforeEach: async (to, from, next) => {
    await initPromise;
    const { isAuthenticated, checkPermission } = useAuth();

    if (to.meta.requiresAuth && !isAuthenticated.value) {
      next("/login");
      return;
    }

    // Feature Toggle 检查（优先级高于权限）
    const { isFeatureEnabled } = useFeatureToggle();
    if (to.meta.feature && !isFeatureEnabled(to.meta.feature)) {
      next("/exception/404"); // Feature 未启用，返回 404
      return;
    }

    // 权限检查
    if (to.meta.permission && !checkPermission(to.meta.permission)) {
      next("/exception/403");
      return;
    }

    next();
  },
};

export default createCoreRouter(routerConfig);
```

### 10.3 样式隔离问题

#### 问题描述

多个模块使用 Ant Design Vue 组件时，可能出现样式覆盖问题，特别是全局样式和主题配置。

#### 解决方案

**1. 统一主题配置**

在核心 UI 模块中统一管理主题配置：

```typescript
// packages/tiny-core-ui/src/styles/theme.ts
import { ConfigProvider } from "ant-design-vue";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import type { ThemeConfig } from "ant-design-vue";

export const defaultThemeConfig: ThemeConfig = {
  token: {
    colorPrimary: "#1890ff",
    colorSuccess: "#52c41a",
    colorWarning: "#faad14",
    colorError: "#f5222d",
    borderRadius: 6,
    fontSize: 14,
  },
  components: {
    Button: {
      borderRadius: 6,
    },
    Table: {
      borderRadius: 6,
    },
  },
};

export const defaultLocale = zhCN;

// 主题配置提供者组件
export function createThemeProvider(app: any) {
  app.use(ConfigProvider, {
    theme: defaultThemeConfig,
    locale: defaultLocale,
  });
}
```

**2. CSS 变量统一管理**

```css
/* packages/tiny-core-ui/src/styles/variables.css */
:root {
  /* 主色系 */
  --tiny-color-primary: #1890ff;
  --tiny-color-success: #52c41a;
  --tiny-color-warning: #faad14;
  --tiny-color-error: #f5222d;

  /* 间距 */
  --tiny-spacing-xs: 4px;
  --tiny-spacing-sm: 8px;
  --tiny-spacing-md: 16px;
  --tiny-spacing-lg: 24px;
  --tiny-spacing-xl: 32px;

  /* 字体 */
  --tiny-font-size-sm: 12px;
  --tiny-font-size-md: 14px;
  --tiny-font-size-lg: 16px;

  /* 圆角 */
  --tiny-border-radius-sm: 4px;
  --tiny-border-radius-md: 6px;
  --tiny-border-radius-lg: 8px;
}
```

**3. Scoped 样式和 CSS Modules**

业务模块使用 scoped 样式或 CSS Modules：

```vue
<!-- packages/tiny-core-dict-ui/src/views/DictManagement.vue -->
<template>
  <div class="dict-management">
    <!-- 内容 -->
  </div>
</template>

<script setup lang="ts">
// ...
</script>

<style scoped>
/* 使用 scoped 确保样式隔离 */
.dict-management {
  padding: var(--tiny-spacing-md);
}

/* 如果需要覆盖 Ant Design 组件样式，使用深度选择器 */
.dict-management :deep(.ant-table) {
  /* 自定义样式 */
}
</style>
```

**4. CSS Modules 配置**

```typescript
// packages/tiny-core-dict-ui/vite.config.ts
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [
    vue({
      style: {
        modules: {
          // CSS Modules 配置
          generateScopedName: "[name]__[local]___[hash:base64:5]",
        },
      },
    }),
  ],
  css: {
    modules: {
      localsConvention: "camelCase",
    },
  },
});
```

**5. 样式命名空间**

为每个模块添加样式命名空间：

```vue
<!-- packages/tiny-core-dict-ui/src/views/DictManagement.vue -->
<template>
  <div class="tiny-dict-management">
    <!-- 内容 -->
  </div>
</template>

<style>
/* 使用命名空间避免冲突 */
.tiny-dict-management {
  /* 样式 */
}
</style>
```

### 10.4 构建优化

#### 问题描述

All-in-one 构建可能打包体积较大，首次加载时间较长。

#### 解决方案

**1. 代码分割配置**

使用 Vite 的 `manualChunks` 进行模块拆分：

```typescript
// applications/tiny-oauth-server/src/main/webapp/vite.config.ts
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          // 核心框架
          "vue-vendor": ["vue", "vue-router", "pinia"],
          // UI 框架
          "antd-vendor": ["ant-design-vue", "@ant-design/icons-vue"],
          // 核心 UI 模块
          "core-ui": ["@tiny/core-ui"],
          // 字典 UI 模块
          "dict-ui": ["@tiny/core-dict-ui"],
          // 其他大型依赖
          "bpmn-vendor": ["bpmn-js", "bpmn-js-properties-panel"],
        },
      },
    },
    // 启用 gzip 压缩
    minify: "terser",
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    // 设置 chunk 大小警告限制
    chunkSizeWarningLimit: 1000,
  },
});
```

**2. 路由懒加载**

```typescript
// packages/tiny-core-dict-ui/src/router/routes.ts
import type { RouteRecordRaw } from "vue-router";

const MODULE_PREFIX = "/dict";

export const dictRoutes: RouteRecordRaw[] = [
  {
    path: `${MODULE_PREFIX}/management`,
    name: "DictManagement",
    // 使用动态导入实现懒加载
    component: () => import("../views/DictManagement.vue"),
    meta: { title: "字典管理", requiresAuth: true, module: "dict" },
  },
  {
    path: `${MODULE_PREFIX}/item/form`,
    name: "DictItemForm",
    component: () => import("../views/DictItemForm.vue"),
    meta: { title: "字典项表单", requiresAuth: true, module: "dict" },
  },
];
```

**3. 组件懒加载**

```vue
<!-- 使用 defineAsyncComponent 实现组件懒加载 -->
<script setup lang="ts">
import { defineAsyncComponent } from "vue";

const DictManagement = defineAsyncComponent(
  () => import("@tiny/core-dict-ui/views/DictManagement.vue")
);
</script>
```

**4. 资源压缩和 CDN**

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    // 启用资源内联阈值
    assetsInlineLimit: 4096,
    // 压缩图片
    assetsDir: "assets",
  },
  // 生产环境使用 CDN
  base:
    process.env.NODE_ENV === "production" ? "https://cdn.example.com/" : "/",
});
```

**5. 预加载关键资源**

```html
<!-- index.html -->
<head>
  <!-- 预加载关键资源 -->
  <link rel="preload" href="/assets/vue-vendor.js" as="script" />
  <link rel="preload" href="/assets/core-ui.js" as="script" />
  <!-- DNS 预解析 -->
  <link rel="dns-prefetch" href="https://cdn.example.com" />
</head>
```

### 10.5 文档和开发指南

#### 问题描述

各模块独立，开发者需要明确模块使用方式、路由整合方式等。

#### 解决方案

**1. 模块接入指南**

创建统一的模块接入文档：

````markdown
# 模块接入指南

## 安装依赖

```bash
npm install @tiny/core-ui @tiny/core-dict-ui
```

## 基础配置
````

**基础配置**

```typescript
// main.ts
import { createApp } from "vue";
import { createPinia } from "pinia";
import { initAuth } from "@tiny/core-ui";
import App from "./App.vue";

const app = createApp(App);
app.use(createPinia());

// 初始化认证
await initAuth();

app.mount("#app");
```

**路由整合**

```typescript
// router/index.ts
import { createCoreRouter } from "@tiny/core-ui";
import { dictRoutes } from "@tiny/core-dict-ui";
import { mergeModuleRoutes } from "@tiny/core-ui";

const moduleRoutes = [{ module: "dict", routes: dictRoutes }];

const allRoutes = mergeModuleRoutes(moduleRoutes, baseRoutes);
export default createCoreRouter({ routes: allRoutes });
```

**使用组件**

```vue
<template>
  <BasicLayout>
    <DictManagement />
  </BasicLayout>
</template>

<script setup lang="ts">
import { BasicLayout } from "@tiny/core-ui";
import { DictManagement } from "@tiny/core-dict-ui";
</script>
```

````

**2. API 文档生成**

使用 TypeDoc 生成 API 文档：

```json
// packages/tiny-core-ui/package.json
{
  "scripts": {
    "docs:build": "typedoc --out docs src/index.ts",
    "docs:serve": "serve docs"
  },
  "devDependencies": {
    "typedoc": "^0.25.0"
  }
}
````

**3. 路由整合示例**

创建路由整合示例代码：

```typescript
// examples/route-integration-example.ts
/**
 * 路由整合示例
 *
 * 此示例展示如何整合多个模块的路由
 */

import { createCoreRouter } from "@tiny/core-ui";
import { dictRoutes } from "@tiny/core-dict-ui";
import { mergeModuleRoutes, validateRoutes } from "@tiny/core-ui";

// 1. 定义应用基础路由
const baseRoutes = [
  {
    path: "/login",
    name: "Login",
    component: () => import("./views/Login.vue"),
  },
];

// 2. 定义模块路由
const moduleRoutes = [
  { module: "dict", routes: dictRoutes },
  // 添加更多模块...
];

// 3. 合并路由
const allRoutes = mergeModuleRoutes(moduleRoutes, baseRoutes);

// 4. 验证路由冲突
validateRoutes(allRoutes);

// 5. 创建路由实例
export default createCoreRouter({
  routes: allRoutes,
  beforeEach: async (to, from, next) => {
    // 路由守卫逻辑
    next();
  },
});
```

**4. 开发指南文档**

```markdown
# 开发指南

**创建新模块**

1. 在 `packages/` 目录下创建新模块
2. 配置 `package.json`，设置 `peerDependencies`
3. 导出模块 API（组件、路由、工具函数等）
4. 编写文档和示例

**模块命名规范**

- 模块名称：`tiny-core-{module-name}-ui`
- 包名称：`@tiny/core-{module-name}-ui`
- 路由前缀：`/{module-name}`

**路由命名规范**

- 路由名称：`{ModuleName}{PageName}`
- 路由路径：`/{module-name}/{page-name}`
```

### 10.6 可扩展性考虑

#### 问题描述

后续可能会新增更多业务模块（如用户管理、流程管理），需要预留模块注册机制。

#### 解决方案

**1. 模块注册机制**

创建统一的模块注册系统：

```typescript
// packages/tiny-core-ui/src/module-registry.ts
import type { RouteRecordRaw } from "vue-router";
import type { Component } from "vue";

export interface ModuleDefinition {
  /** 模块名称 */
  name: string;
  /** 模块版本 */
  version: string;
  /** 模块路由 */
  routes?: RouteRecordRaw[];
  /** UI 表现（可选） */
  ui?: {
    /** 模块菜单配置 */
    menu?: MenuItem;
  };
  /** 模块初始化函数 */
  init?: () => Promise<void> | void;
  /** 模块卸载函数 */
  destroy?: () => Promise<void> | void;
}

export interface MenuItem {
  key: string;
  label: string;
  icon?: string;
  path?: string;
  children?: MenuItem[];
}

class ModuleRegistry {
  private modules = new Map<string, ModuleDefinition>();

  /**
   * 注册模块
   */
  register(module: ModuleDefinition): void {
    if (this.modules.has(module.name)) {
      console.warn(`模块 ${module.name} 已存在，将被覆盖`);
    }
    this.modules.set(module.name, module);
  }

  /**
   * 获取模块
   */
  get(name: string): ModuleDefinition | undefined {
    return this.modules.get(name);
  }

  /**
   * 获取所有模块
   */
  getAll(): ModuleDefinition[] {
    return Array.from(this.modules.values());
  }

  /**
   * 获取所有路由
   */
  getAllRoutes(): RouteRecordRaw[] {
    const routes: RouteRecordRaw[] = [];
    this.modules.forEach((module) => {
      if (module.routes) {
        routes.push(...module.routes);
      }
    });
    return routes;
  }

  /**
   * 获取所有菜单
   * 仅返回有 UI 菜单定义的模块
   */
  getAllMenus(): MenuItem[] {
    const menus: MenuItem[] = [];
    this.modules.forEach((module) => {
      // 使用新的 ui.menu 结构
      if (module.ui?.menu) {
        menus.push(module.ui.menu);
      }
    });
    return menus;
  }

  /**
   * 初始化所有模块
   */
  async initAll(): Promise<void> {
    const initPromises = Array.from(this.modules.values())
      .filter((module) => module.init)
      .map((module) => module.init!());
    await Promise.all(initPromises);
  }

  /**
   * 卸载所有模块
   */
  async destroyAll(): Promise<void> {
    const destroyPromises = Array.from(this.modules.values())
      .filter((module) => module.destroy)
      .map((module) => module.destroy!());
    await Promise.all(destroyPromises);
  }
}

export const moduleRegistry = new ModuleRegistry();
```

**2. 模块定义示例**

> **注意**：`ModuleDefinition` 是模块注册机制使用的接口，与 `ModuleMeta` 对齐，使用 `ui.menu` 结构。

```typescript
// packages/tiny-core-dict-ui/src/module.ts
import { moduleRegistry, type ModuleDefinition } from "@tiny/core-ui";
import { dictRoutes } from "./router/routes";

const dictModule: ModuleDefinition = {
  name: "dict",
  version: "1.0.0",
  routes: dictRoutes,
  // 使用 ui.menu 结构（可选）
  ui: {
    menu: {
      key: "dict",
      label: "字典管理",
      icon: "BookOutlined",
      path: "/dict/management",
      children: [
        {
          key: "dict-type",
          label: "字典类型",
          path: "/dict/type",
        },
        {
          key: "dict-item",
          label: "字典项",
          path: "/dict/item",
        },
      ],
    },
  },
  init: async () => {
    console.log("字典模块初始化");
    // 模块初始化逻辑
  },
};

// 自动注册模块
moduleRegistry.register(dictModule);

export default dictModule;
```

**3. 应用中使用模块注册**

```typescript
// applications/tiny-oauth-server/src/main/webapp/src/main.ts
import { createApp } from "vue";
import { createPinia } from "pinia";
import { moduleRegistry, createCoreRouter } from "@tiny/core-ui";
import "@tiny/core-dict-ui"; // 自动注册字典模块
// import '@tiny/core-user-ui'; // 未来添加用户模块
// import '@tiny/core-process-ui'; // 未来添加流程模块

import App from "./App.vue";

async function bootstrap() {
  const app = createApp(App);
  app.use(createPinia());

  // 初始化所有模块
  await moduleRegistry.initAll();

  // 创建路由
  const router = createCoreRouter({
    routes: moduleRegistry.getAllRoutes(),
  });
  app.use(router);

  app.mount("#app");
}

bootstrap();
```

**4. 动态模块加载（可选）**

支持运行时动态加载模块：

```typescript
// packages/tiny-core-ui/src/module-loader.ts
export async function loadModule(
  moduleName: string
): Promise<ModuleDefinition> {
  // 动态导入模块
  const module = await import(`@tiny/core-${moduleName}-ui`);

  // 注册模块
  if (module.default) {
    moduleRegistry.register(module.default);
  }

  return module.default;
}

// 使用示例
await loadModule("dict");
await loadModule("user");
```

**5. 模块生命周期管理**

```typescript
// packages/tiny-core-ui/src/module-lifecycle.ts
export interface ModuleLifecycle {
  onBeforeInit?: () => Promise<void> | void;
  onInit?: () => Promise<void> | void;
  onAfterInit?: () => Promise<void> | void;
  onBeforeDestroy?: () => Promise<void> | void;
  onDestroy?: () => Promise<void> | void;
  onAfterDestroy?: () => Promise<void> | void;
}

export class ModuleLifecycleManager {
  async executeLifecycle(
    module: ModuleDefinition & ModuleLifecycle,
    phase: "init" | "destroy"
  ): Promise<void> {
    if (phase === "init") {
      await module.onBeforeInit?.();
      await module.init?.();
      await module.onAfterInit?.();
    } else {
      await module.onBeforeDestroy?.();
      await module.destroy?.();
      await module.onAfterDestroy?.();
    }
  }
}
```

## 十一、实施计划

### 11.1 阶段一：准备工作（1-2 天）

**任务清单**：

- [ ] 确认 Node.js 版本（推荐 v20+）
- [ ] 安装 pnpm（`npm install -g pnpm`）
- [ ] 确认 Maven 版本（3.8+）
- [ ] 创建 `packages/` 目录
- [ ] 创建根 `package.json` 和 `pnpm-workspace.yaml`
- [ ] 创建根 `tsconfig.json`

**验收标准**：

- ✅ 所有工具版本符合要求
- ✅ workspace 配置正确
- ✅ 可以执行 `pnpm install`

### 11.2 阶段二：创建核心 UI 模块（3-5 天）

**任务清单**：

- [ ] 创建 `packages/tiny-core-ui/` 目录结构
- [ ] 迁移 `layouts/` 目录（4 个组件）
- [ ] 迁移 `components/` 目录（2 个组件）
- [ ] 迁移 `auth/` 目录（3 个文件）
- [ ] 迁移 `router/core.ts`（路由核心）
- [ ] 迁移 `utils/` 目录（工具函数）
- [ ] 创建 `src/index.ts` 导出所有内容
- [ ] 配置 `vite.config.ts`（库模式）
- [ ] 配置 `tsconfig.json`（项目引用）
- [ ] 测试构建（`pnpm build`）

**验收标准**：

- ✅ 所有文件迁移完成
- ✅ 类型检查通过
- ✅ 可以正常构建和导出

### 11.3 阶段三：创建业务 UI 模块（2-3 天）

**任务清单**：

- [ ] 创建 `packages/tiny-core-dict-ui/` 目录结构
- [ ] 迁移字典相关页面和 API
- [ ] 创建路由配置（使用路由前缀）
- [ ] 创建 `packages/tiny-core-export-ui/` 目录结构（可选）
- [ ] 迁移 Export 相关页面和 API
- [ ] 配置模块依赖关系
- [ ] 测试模块引用

**验收标准**：

- ✅ 所有文件迁移完成
- ✅ 可以正常引用 `@tiny/core-ui`
- ✅ 路由配置正确

### 11.4 阶段四：重构应用（3-5 天）

**任务清单**：

- [ ] 更新 `tiny-oauth-server/src/main/webapp/package.json`（添加依赖）
- [ ] 更新 `vite.config.ts`（配置别名）
- [ ] 更新 `tsconfig.json`（项目引用）
- [ ] 引入 `@tiny/core-ui` 的路由核心
- [ ] 引入各业务模块的路由
- [ ] 实现路由整合和冲突检测
- [ ] 替换组件引用
- [ ] 修复所有导入错误
- [ ] 测试功能完整性

**验收标准**：

- ✅ 所有引用更新完成
- ✅ 路由整合成功
- ✅ 无功能回归

### 11.5 阶段五：构建集成（2-3 天）

**任务清单**：

- [ ] 添加 `frontend-maven-plugin` 配置
- [ ] 配置构建顺序（先构建模块，再构建应用）
- [ ] 配置资源复制（dist → static）
- [ ] 测试 Maven 构建
- [ ] 配置开发环境脚本
- [ ] 配置 Vite 源码引用
- [ ] 配置 HMR 支持
- [ ] 测试开发环境

**验收标准**：

- ✅ Maven 构建成功
- ✅ 前端资源正确打包到 JAR
- ✅ 开发环境正常启动

### 11.6 阶段六：优化和完善（2-3 天）

**任务清单**：

- [ ] 实施路由冲突检测工具
- [ ] 实施路由整合辅助函数
- [ ] 实施统一主题配置
- [ ] 实施构建优化（代码分割）
- [ ] 实施模块注册机制
- [ ] 编写模块接入指南
- [ ] 配置 API 文档生成
- [ ] 编写路由整合示例
- [ ] 编写开发指南
- [ ] 端到端测试
- [ ] 性能测试

**验收标准**：

- ✅ 所有优化方案实施完成
- ✅ 文档完整
- ✅ 所有测试通过

### 11.7 时间估算

| 阶段                     | 预估时间 | 累计时间 |
| ------------------------ | -------- | -------- |
| 阶段一：准备工作         | 1-2 天   | 1-2 天   |
| 阶段二：创建核心 UI 模块 | 3-5 天   | 4-7 天   |
| 阶段三：创建业务 UI 模块 | 2-3 天   | 6-10 天  |
| 阶段四：重构应用         | 3-5 天   | 9-15 天  |
| 阶段五：构建集成         | 2-3 天   | 11-18 天 |
| 阶段六：优化和完善       | 2-3 天   | 13-21 天 |

**总时间估算**：13-21 天（约 3-4 周）  
**含风险缓冲**：16-27 天（约 3-5 周）

## 十二、SaaS 平台演进路线图

> 📋 **本章节目标**：系统分析 tiny-platform 当前 SaaS 化现状，评估各模块的 SaaS 能力成熟度，并制定分步骤的 SaaS 平台演进计划。

### 12.1 当前项目结构分析

#### 12.1.1 整体架构概览

**项目定位**：`tiny-platform` 是一个多模块企业级平台，包含业务应用和基础设施组件。

**当前模块结构**：

```
tiny-platform/
├── 业务应用模块
│   ├── tiny-oauth-server          # OAuth2 授权服务器（含前端 webapp）
│   ├── tiny-oauth-client          # OAuth2 客户端
│   ├── tiny-oauth-resource        # OAuth2 资源服务器
│   └── tiny-web                   # Web 应用
│
├── 基础设施模块
│   ├── tiny-idempotent-platform   # 幂等平台（基础设施）
│   ├── tiny-idempotent-starter     # 幂等 Starter
│   ├── tiny-common-exception      # 通用异常处理
│   └── tiny-core-governance        # 平台治理能力
│
└── 数据字典模块（SaaS 化进行中）
    ├── tiny-core                   # 核心模型（纯 Java）
    ├── tiny-core-dict-starter      # 自动配置
    ├── tiny-core-dict-repository-jpa # JPA 实现
    ├── tiny-core-dict-cache-memory # 内存缓存
    ├── tiny-core-dict-cache-redis  # Redis 缓存
    ├── tiny-core-dict-web          # REST API
    └── tiny-core-governance        # 治理能力（Level1/Level2）
```

#### 12.1.2 技术栈现状

| 技术领域       | 当前技术栈                 | SaaS 适配性         |
| -------------- | -------------------------- | ------------------- |
| **后端框架**   | Spring Boot 3.5.8          | ✅ 优秀             |
| **数据持久化** | Spring Data JPA + MySQL    | ✅ 支持多租户       |
| **缓存**       | Redis / 内存缓存           | ✅ 支持租户隔离     |
| **前端框架**   | Vue 3 + Ant Design Vue 4.x | ✅ 模块化设计       |
| **构建工具**   | Maven + pnpm (Monorepo)    | ✅ 支持模块化       |
| **认证授权**   | OAuth2 + JWT               | ✅ 支持多租户 Token |

#### 12.1.3 基础设施能力评估

**✅ 已具备的 SaaS 基础设施**：

1. **多租户数据隔离**

   - ✅ 数据字典模块：`dict_type`、`dict_item` 表包含 `tenant_id` 字段
   - ✅ 调度模块：所有核心表包含 `tenant_id` 字段
   - ✅ 租户上下文：`TenantContext`（ThreadLocal）机制

2. **缓存隔离**

   - ✅ 字典缓存：按 `tenantId:dictCode` 隔离
   - ✅ Redis 缓存：支持租户维度缓存键

3. **权限体系**

   - ✅ OAuth2 认证授权
   - ✅ 基于角色的权限控制（RBAC）
   - ✅ 前后端权限对齐机制

4. **模块化架构**
   - ✅ 前端模块化设计（`tiny-core-ui`、`tiny-core-dict-ui`）
   - ✅ 后端模块化设计（Maven 多模块）
   - ✅ 插件化扩展机制（设计完成）

**⚠️ 待完善的 SaaS 基础设施**：

1. **租户管理**

   - ❌ 缺少统一的租户管理模块（`tenant` 表）
   - ❌ 缺少租户注册/激活流程
   - ❌ 缺少租户配额管理（资源限制）

2. **插件市场**

   - ❌ 缺少 `tenant_plugin` 表（租户插件安装记录）
   - ❌ 缺少插件安装/卸载 API
   - ❌ 缺少插件版本管理

3. **Feature Toggle**

   - ❌ 缺少 `tenant_feature` 表（租户 Feature 配置）
   - ❌ 缺少 Feature 启用/禁用 API
   - ❌ 缺少 Feature 灰度发布机制

4. **计费与配额**

   - ❌ 缺少租户配额管理（用户数、存储、API 调用量）
   - ❌ 缺少使用量统计
   - ❌ 缺少计费规则引擎

5. **多租户治理**
   - ⚠️ 数据字典治理（Level1/Level2）已设计，待完善
   - ❌ 缺少全局多租户治理策略
   - ❌ 缺少租户级别的审计日志

### 12.2 SaaS 化进度评估

#### 12.2.1 模块 SaaS 成熟度矩阵

| 模块           | 多租户隔离  | 插件化      | Feature Toggle | 治理能力             | 成熟度 | 优先级 |
| -------------- | ----------- | ----------- | -------------- | -------------------- | ------ | ------ |
| **数据字典**   | ✅ 已完成   | ⚠️ 设计完成 | ⚠️ 设计完成    | ⚠️ Level1/2 设计完成 | 🟢 70% | P0     |
| **调度模块**   | ✅ 已完成   | ❌ 未开始   | ❌ 未开始      | ❌ 未开始            | 🟡 40% | P1     |
| **OAuth 服务** | ⚠️ 部分支持 | ❌ 未开始   | ❌ 未开始      | ❌ 未开始            | 🟡 30% | P2     |
| **幂等平台**   | ❌ 未开始   | ❌ 未开始   | ❌ 未开始      | ❌ 未开始            | 🔴 10% | P3     |
| **前端模块**   | ⚠️ 设计完成 | ✅ 设计完成 | ✅ 设计完成    | ⚠️ 设计完成          | 🟡 50% | P0     |

**成熟度说明**：

- 🟢 70%+：可投入生产使用
- 🟡 40-70%：核心功能完成，待完善
- 🔴 <40%：设计阶段或未开始

#### 12.2.2 基础设施 SaaS 化进度

| 基础设施能力       | 设计状态    | 实现状态    | 完成度 |
| ------------------ | ----------- | ----------- | ------ |
| **租户管理**       | ✅ 已设计   | ❌ 未实现   | 20%    |
| **数据隔离**       | ✅ 已设计   | ✅ 部分实现 | 60%    |
| **插件市场**       | ✅ 已设计   | ❌ 未实现   | 30%    |
| **Feature Toggle** | ✅ 已设计   | ❌ 未实现   | 30%    |
| **权限体系**       | ✅ 已设计   | ✅ 已实现   | 80%    |
| **缓存隔离**       | ✅ 已设计   | ✅ 已实现   | 70%    |
| **前端模块化**     | ✅ 已设计   | ⚠️ 部分实现 | 50%    |
| **治理能力**       | ✅ 已设计   | ⚠️ 部分实现 | 40%    |
| **计费配额**       | ❌ 未设计   | ❌ 未实现   | 0%     |
| **审计日志**       | ⚠️ 部分设计 | ⚠️ 部分实现 | 30%    |

**总体 SaaS 化进度**：**约 40%**

### 12.3 SaaS 平台演进路线图（分步骤）

> 📋 **演进原则**：
>
> 1. **渐进式演进**：不破坏现有功能，逐步增强 SaaS 能力
> 2. **基础设施优先**：先搭建 SaaS 基础设施，再扩展业务能力
> 3. **模块化推进**：按模块优先级逐步 SaaS 化
> 4. **向后兼容**：保证现有单租户场景正常工作

#### 阶段一：SaaS 基础设施搭建（4-6 周）🎯 **P0 - 最高优先级**

**目标**：搭建 SaaS 平台的核心基础设施，为后续模块 SaaS 化提供基础。

**任务清单**：

1. **租户管理模块**（1-2 周）

   - [ ] 创建 `tenant` 表（租户基本信息）
   - [ ] 创建 `tenant_config` 表（租户配置）
   - [ ] 实现租户注册/激活 API
   - [ ] 实现租户查询/更新 API
   - [ ] 实现 `TenantService` 和 `TenantRepository`

2. **租户上下文增强**（1 周）

   - [ ] 完善 `TenantContext`（支持从请求头/Token 提取）
   - [ ] 实现 `TenantInterceptor`（自动注入租户上下文）
   - [ ] 实现租户上下文验证（防止租户越权）

3. **插件市场基础**（1-2 周）

   - [ ] 创建 `plugin` 表（插件定义）
   - [ ] 创建 `tenant_plugin` 表（租户插件安装记录）
   - [ ] 实现插件安装/卸载 API
   - [ ] 实现插件查询 API（按租户）
   - [ ] 实现 `PluginService` 和插件检查拦截器

4. **Feature Toggle 基础**（1 周）
   - [ ] 创建 `feature` 表（Feature 定义）
   - [ ] 创建 `tenant_feature` 表（租户 Feature 配置）
   - [ ] 实现 Feature 启用/禁用 API
   - [ ] 实现 `FeatureService` 和 Feature 检查拦截器
   - [ ] 前端集成 `useFeatureToggle` composable

**交付物**：

- ✅ 租户管理 REST API（CRUD）
- ✅ 插件管理 REST API（安装/卸载/查询）
- ✅ Feature Toggle REST API（启用/禁用/查询）
- ✅ 后端统一拦截顺序（Tenant → Plugin → Feature → Permission）
- ✅ 前端路由守卫增强（支持 Plugin/Feature 检查）

**验收标准**：

- [ ] 可以创建和管理租户
- [ ] 可以为租户安装/卸载插件
- [ ] 可以为租户启用/禁用 Feature
- [ ] 后端 API 自动进行租户/插件/Feature 检查
- [ ] 前端路由自动进行插件/Feature 检查

---

#### 阶段二：数据字典模块 SaaS 化完善（2-3 周）🎯 **P0 - 最高优先级**

**目标**：完善数据字典模块的 SaaS 能力，使其成为第一个完全 SaaS 化的业务模块。

**任务清单**：

1. **插件化改造**（1 周）

   - [ ] 创建 `plugin.dict` 插件定义
   - [ ] 将字典模块注册为插件
   - [ ] 实现字典模块的插件检查（后端 + 前端）
   - [ ] 测试插件安装/卸载对字典功能的影响

2. **Feature Toggle 集成**（0.5 周）

   - [ ] 定义字典模块的 Feature（如 `dict.v1`、`dict.v2`）
   - [ ] 在路由中声明 Feature 依赖
   - [ ] 测试 Feature 启用/禁用对字典功能的影响

3. **治理能力完善**（1-1.5 周）
   - [ ] 完善 Level1 严格校验（`DictValidationService`）
   - [ ] 完善 Level2 FORCE 变更（`DictForceService`）
   - [ ] 实现审批流程（`DictApprovalService`）
   - [ ] 实现 CI 校验工具（`DictChecker`）

**交付物**：

- ✅ 数据字典插件（可安装/卸载）
- ✅ 数据字典 Feature Toggle（可启用/禁用）
- ✅ 完整的治理能力（Level1/Level2）
- ✅ CI 校验工具

**验收标准**：

- [ ] 租户可以安装/卸载字典插件
- [ ] 租户可以启用/禁用字典 Feature
- [ ] 字典数据按租户隔离
- [ ] 治理能力正常工作（校验/审批）

---

#### 阶段三：前端模块化实施（3-4 周）🎯 **P0 - 最高优先级**

**目标**：实施前端模块化架构，支持插件化前端模块的加载和运行。

**任务清单**：

1. **核心 UI 模块创建**（1-2 周）

   - [ ] 创建 `packages/tiny-core-ui` 模块
   - [ ] 提取布局组件（`BasicLayout`、`HeaderBar`、`Sider`）
   - [ ] 提取认证模块（`useAuth`、`initAuth`）
   - [ ] 提取路由核心（`createCoreRouter`）
   - [ ] 实现插件装配逻辑（`resolveModules`）

2. **字典 UI 模块创建**（1 周）

   - [ ] 创建 `packages/tiny-core-dict-ui` 模块
   - [ ] 迁移字典管理页面
   - [ ] 实现 `DictModuleMeta` 和 `DictPluginMeta`
   - [ ] 导出路由和组件

3. **应用层重构**（1 周）
   - [ ] 重构 `tiny-oauth-server/webapp` 使用模块化架构
   - [ ] 集成 `tiny-core-ui` 和 `tiny-core-dict-ui`
   - [ ] 实现路由整合和插件装配
   - [ ] 测试前端插件化运行机制

**交付物**：

- ✅ `packages/tiny-core-ui` 模块
- ✅ `packages/tiny-core-dict-ui` 模块
- ✅ 重构后的 `tiny-oauth-server/webapp`
- ✅ Monorepo 配置（`pnpm-workspace.yaml`）

**验收标准**：

- [ ] 前端模块可以独立开发和构建
- [ ] 应用可以按需加载插件模块
- [ ] 路由和菜单按插件/Feature 动态显示
- [ ] 构建流程正常工作

---

#### 阶段四：调度模块 SaaS 化（2-3 周）🎯 **P1 - 高优先级**

**目标**：将调度模块改造为 SaaS 插件，支持按租户安装和使用。

**任务清单**：

1. **插件化改造**（1 周）

   - [ ] 创建 `plugin.scheduling` 插件定义
   - [ ] 将调度模块注册为插件
   - [ ] 实现插件检查拦截器
   - [ ] 前端创建 `tiny-core-scheduling-ui` 模块

2. **Feature Toggle 集成**（0.5 周）

   - [ ] 定义调度模块的 Feature
   - [ ] 集成 Feature 检查

3. **治理能力**（1-1.5 周）
   - [ ] 实现调度任务的租户级别治理
   - [ ] 实现任务执行配额管理

**交付物**：

- ✅ 调度插件（可安装/卸载）
- ✅ 调度 Feature Toggle
- ✅ 调度 UI 模块

**验收标准**：

- [ ] 租户可以安装/卸载调度插件
- [ ] 调度任务按租户隔离
- [ ] 支持租户级别的配额管理

---

#### 阶段五：OAuth 服务 SaaS 化（2-3 周）🎯 **P2 - 中优先级**

**目标**：将 OAuth 服务改造为支持多租户的 SaaS 服务。

**任务清单**：

1. **多租户改造**（1-2 周）

   - [ ] OAuth 客户端表增加 `tenant_id` 字段
   - [ ] OAuth Token 表增加 `tenant_id` 字段
   - [ ] 实现租户级别的客户端管理
   - [ ] 实现租户级别的 Token 隔离

2. **插件化改造**（1 周）
   - [ ] 创建 `plugin.oauth` 插件定义
   - [ ] 实现插件检查

**交付物**：

- ✅ 多租户 OAuth 服务
- ✅ OAuth 插件

**验收标准**：

- [ ] OAuth 客户端按租户隔离
- [ ] Token 按租户隔离
- [ ] 支持租户级别的 OAuth 配置

---

#### 阶段六：计费与配额管理（3-4 周）🎯 **P1 - 高优先级**

**目标**：实现 SaaS 平台的计费和配额管理能力。

**任务清单**：

1. **配额管理**（2 周）

   - [ ] 创建 `tenant_quota` 表（租户配额定义）
   - [ ] 创建 `tenant_usage` 表（租户使用量统计）
   - [ ] 实现配额检查服务（`QuotaService`）
   - [ ] 实现使用量统计服务（`UsageService`）
   - [ ] 实现配额超限拦截器

2. **计费规则**（1-2 周）
   - [ ] 创建 `billing_plan` 表（计费方案）
   - [ ] 创建 `tenant_billing` 表（租户计费记录）
   - [ ] 实现计费规则引擎
   - [ ] 实现账单生成服务

**交付物**：

- ✅ 配额管理 API
- ✅ 使用量统计 API
- ✅ 计费规则引擎
- ✅ 账单生成服务

**验收标准**：

- [ ] 可以为租户设置配额（用户数、存储、API 调用量）
- [ ] 可以统计租户使用量
- [ ] 配额超限时自动拦截
- [ ] 可以生成账单

---

#### 阶段七：全局治理与审计（2-3 周）🎯 **P2 - 中优先级**

**目标**：实现 SaaS 平台的全局治理和审计能力。

**任务清单**：

1. **全局治理**（1-2 周）

   - [ ] 完善 `tiny-core-governance` 模块
   - [ ] 实现全局治理策略配置
   - [ ] 实现租户级别的治理策略

2. **审计日志**（1 周）
   - [ ] 创建全局审计日志表
   - [ ] 实现审计日志服务
   - [ ] 实现审计日志查询 API

**交付物**：

- ✅ 全局治理策略配置
- ✅ 审计日志服务
- ✅ 审计日志查询 API

**验收标准**：

- [ ] 可以配置全局治理策略
- [ ] 可以配置租户级别的治理策略
- [ ] 所有关键操作都有审计日志
- [ ] 可以查询审计日志

---

### 12.4 演进时间线

| 阶段       | 时间   | 优先级 | 关键交付物                         |
| ---------- | ------ | ------ | ---------------------------------- |
| **阶段一** | 4-6 周 | P0     | SaaS 基础设施（租户/插件/Feature） |
| **阶段二** | 2-3 周 | P0     | 数据字典 SaaS 化完善               |
| **阶段三** | 3-4 周 | P0     | 前端模块化实施                     |
| **阶段四** | 2-3 周 | P1     | 调度模块 SaaS 化                   |
| **阶段五** | 2-3 周 | P2     | OAuth 服务 SaaS 化                 |
| **阶段六** | 3-4 周 | P1     | 计费与配额管理                     |
| **阶段七** | 2-3 周 | P2     | 全局治理与审计                     |

**总时间估算**：18-26 周（约 4.5-6.5 个月）

**含风险缓冲**：22-32 周（约 5.5-8 个月）

### 12.5 关键里程碑

| 里程碑                    | 时间点           | 验收标准                   |
| ------------------------- | ---------------- | -------------------------- |
| **M1：SaaS 基础设施就绪** | 阶段一完成       | 租户/插件/Feature 管理可用 |
| **M2：第一个 SaaS 模块**  | 阶段二完成       | 数据字典完全 SaaS 化       |
| **M3：前端模块化完成**    | 阶段三完成       | 前端支持插件化加载         |
| **M4：核心模块 SaaS 化**  | 阶段四完成       | 调度模块 SaaS 化           |
| **M5：计费能力就绪**      | 阶段六完成       | 配额和计费功能可用         |
| **M6：SaaS 平台 MVP**     | 阶段一+二+三完成 | 最小可用的 SaaS 平台       |

### 12.6 风险与应对

| 风险           | 影响 | 应对措施                       |
| -------------- | ---- | ------------------------------ |
| **时间超期**   | 高   | 分阶段交付，每个阶段独立可验收 |
| **技术复杂度** | 中   | 优先使用成熟方案，避免过度设计 |
| **向后兼容**   | 高   | 保持单租户模式兼容，渐进式迁移 |
| **性能影响**   | 中   | 缓存优化，数据库索引优化       |
| **数据迁移**   | 中   | 提供数据迁移工具和脚本         |

### 12.7 成功标准

**SaaS 平台 MVP 成功标准**（阶段一+二+三完成后）：

- [ ] ✅ 可以创建和管理多个租户
- [ ] ✅ 可以为租户安装/卸载插件
- [ ] ✅ 可以为租户启用/禁用 Feature
- [ ] ✅ 数据字典模块完全 SaaS 化（多租户隔离 + 插件化 + Feature Toggle）
- [ ] ✅ 前端支持插件化模块加载
- [ ] ✅ 后端统一拦截顺序正常工作
- [ ] ✅ 前端路由守卫正常工作

**完整 SaaS 平台成功标准**（所有阶段完成后）：

- [ ] ✅ 所有核心模块 SaaS 化
- [ ] ✅ 完整的插件市场
- [ ] ✅ 完整的 Feature Toggle 能力
- [ ] ✅ 完整的计费和配额管理
- [ ] ✅ 完整的治理和审计能力
- [ ] ✅ 支持从单租户平滑迁移到多租户

---

## 十三、总结与决策

通过将 `tiny-oauth-server/webapp` 抽象为 `tiny-core-ui` 和 `tiny-core-dict-ui`，可以实现：

### 13.1 核心优势

1. ✅ **灵活的部署模式**：支持 All-in-one 和前后端分离
2. ✅ **代码复用**：核心 UI 和业务 UI 可被多个应用复用
3. ✅ **渐进式演进**：支持从集中部署平滑迁移到分离部署
4. ✅ **统一体验**：多个应用使用相同的核心 UI
5. ✅ **模块化扩展**：通过模块注册机制，易于添加新业务模块

### 13.2 关键优化点

1. **版本管理**：使用 workspace 固定版本 + peerDependencies 控制外部依赖
2. **路由管理**：统一路由命名规范 + 路由前缀机制 + 冲突检测工具
3. **样式隔离**：统一主题配置 + CSS 变量管理 + Scoped 样式
4. **构建优化**：代码分割 + 路由懒加载 + 资源压缩
5. **文档完善**：模块接入指南 + API 文档 + 开发指南
6. **可扩展性**：模块注册机制 + 动态加载 + 生命周期管理

### 13.3 实施建议

**优先级排序**：

1. **高优先级**（必须实施）：

   - 版本管理和依赖冲突解决（8.1）
   - 路由冲突检测和整合方案（8.2）
   - 样式隔离方案（8.3）

2. **中优先级**（建议实施）：

   - 构建优化（8.4）
   - 模块注册机制（8.6）

3. **低优先级**（可选实施）：
   - 文档和开发指南完善（8.5）
   - 动态模块加载（8.6.4）

### 13.4 架构价值

这是一个既满足当前需求（All-in-one），又为未来扩展（多应用、前后端分离）做好准备的架构设计。通过模块化、标准化和工具化的方式，实现了：

- **开发效率提升**：新应用可快速集成现有模块
- **维护成本降低**：统一的代码和规范，减少重复工作
- **扩展性增强**：模块化设计支持灵活组合和扩展
- **质量保障**：版本管理、路由冲突检测等工具保障代码质量

### 13.5 后续规划（概览）

> 详细任务和时间安排见第 8 节实施计划，这里只保留高层视图。

- **短期（1-2 个月）**：完成核心模块提取和基础治理（版本管理、路由管理、样式隔离）
- **中期（3-6 个月）**：进行构建优化、引入模块注册机制并完善文档体系
- **长期（6 个月以上）**：根据实际需要，逐步引入动态模块加载和更丰富的模块生态

### 13.6 可行性结论

**技术可行性**：✅ **完全可行**（详见第 2.1 节）  
当前技术栈、项目结构和构建工具均能良好支撑前端模块化方案。

**实施可行性**：✅ **可行**（详见第 2.2 节）  
代码迁移复杂度中等，构建流程可通过 Maven 集成解决，整体风险可控、收益明显。

**总体评估**：✅ **方案可行，建议实施**

### 13.7 关键决策点

#### 13.7.1 目录结构决策

**推荐**：保持现有结构（`tiny-oauth-server/src/main/webapp`）

- 优点：改动小，风险低
- 缺点：结构不够清晰（但可接受）

#### 13.7.2 构建工具决策

**推荐**：使用 `frontend-maven-plugin`

- 优点：功能完善，自动安装 Node.js
- 缺点：需要下载 Node.js，构建时间较长

#### 13.7.3 包管理器决策

**推荐**：使用 `pnpm`

- 优点：速度快，节省磁盘空间，支持 workspace
- 缺点：需要额外安装

### 13.8 下一步行动

**立即行动项**：

1. ✅ **确认方案**：与团队讨论，确认技术方案和时间表
2. ✅ **环境准备**：安装必要工具（pnpm、Node.js 等）
3. ✅ **创建分支**：创建功能分支 `feature/frontend-modularization`
4. ✅ **开始实施**：按照实施计划（第 8 节）开始实施

**需要确认的问题**：

1. 目录结构：是否保持现有结构？
2. 构建工具：使用 frontend-maven-plugin 还是 exec-maven-plugin？
3. 包管理器：使用 pnpm 还是 npm？
4. 时间安排：是否可以接受 3-5 周的开发时间？
