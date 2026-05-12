# BPMN 翻译接入说明

## 定位

本模块只负责 BPMN.js / bpmn-js-properties-panel / Camunda properties panel 的中文翻译接入。

当前项目约定：

- 建模器页面通过 BPMN.js 官方 `translate` service 注入翻译能力。
- 页面组件不做 DOM 后处理、不写 `MutationObserver`、不直接改 `node_modules`。
- 稳定词条维护在 `src/utils/bpmn/i18n/**`。
- `addCustomTranslations()` 只作为排障或临时兜底入口，不建议在业务页面长期使用。

## 翻译优先级

`translateUtils.translate()` 的解析顺序如下：

1. 自定义词条：运行时临时覆盖，优先级最高。
2. 项目本地词典：`src/utils/bpmn/i18n/**`，用于沉淀稳定中文词条。
3. 官方中文兜底：加载 `bpmn-js-i18n/translations/zn.js`。
4. 原始英文：前三层都未命中时返回 BPMN.js 原文。

这个顺序保证项目业务用语不会被第三方包覆盖，同时又能用官方中文包补足常规 BPMN 词条。

## 页面接入方式

在 BPMN 建模器页面只注入翻译模块，不在页面内临时注册词条：

```ts
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { getTranslateModule } from '@/utils/bpmn/utils/translateUtils'

const translateModule = await getTranslateModule()

const modeler = new BpmnModeler({
  container: bpmnContainer.value,
  propertiesPanel: {
    parent: propertiesPanel.value,
  },
  additionalModules: [
    translateModule,
    // BpmnPropertiesPanelModule,
    // BpmnPropertiesProviderModule,
    // CamundaPlatformPropertiesProviderModule,
  ],
})
```

如需临时排查翻译来源，可以显式打开 debug：

```ts
const translateModule = await getTranslateModule(true)
```

正式页面默认不要传 `true`，避免生产控制台出现大量翻译调试信息。

## 新增词条流程

新增缺失翻译时按来源放到对应词典文件：

- BPMN 画布、上下文菜单、palette：`i18n/bpmn-js/**`
- 通用属性面板：`i18n/properties-panel/index.ts`
- Camunda 属性面板：`i18n/camunda-properties-panel/index.ts`
- Zeebe 属性面板：`i18n/zeebe-properties-panel/index.ts`

新增后建议同步补充单测：

```bash
npm run test:unit -- src/utils/bpmn/utils/translateUtils.test.ts
```

## 缓存与统计

翻译工具会缓存“模板文本到翻译模板”的解析结果。缓存不包含 replacements 的最终替换值，因此同一个模板使用不同参数仍能安全复用。

缓存会在以下情况自动清空：

- 添加或移除自定义词条
- 官方中文词典加载完成
- 手动导入翻译数据
- 切换官方兜底开关

可通过 `getPerformanceStats()` 查看：

```ts
import { getPerformanceStats } from '@/utils/bpmn/utils/translateUtils'

const stats = getPerformanceStats()
```

## 验证要点

推荐至少覆盖这些关键英文词条：

- `Create` -> `新增`
- `Create new list item` -> `新增列表项`
- `Start Event` -> `开始事件`
- `Sequence Flow` -> `顺序流`
- `Custom type` -> `自定义类型`
- `Open {element}` -> `打开 {element}`，用于验证官方中文兜底生效

真实页面验收时优先检查：

- palette / context pad 是否仍出现英文。
- properties panel 的按钮、title、hover 文案是否走 `translate(...)`。
- 新增 bpmn-js-properties-panel 版本后是否有新英文词条漏出。
