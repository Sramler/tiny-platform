<template>
  <div class="workflow-design">
    <div class="main-content">
      <div ref="bpmnContainer" class="canvas">
        <!-- 浮动工具栏 -->
        <div class="floating-toolbar">
          <a-button type="primary" size="small" @click="createNewBpmn">
            <template #icon>
              <PlusOutlined />
            </template>
            创建BPMN
          </a-button>

          <a-button type="primary" size="small" @click="openLocalFile">
            <template #icon>
              <FolderOpenOutlined />
            </template>
            打开BPMN
          </a-button>

          <a-button type="primary" size="small" @click="showSaveDialog" :disabled="!modeler">
            <template #icon>
              <RocketOutlined />
            </template>
            部署流程
          </a-button>

          <a-button type="primary" size="small" @click="exportBpmn">
            <template #icon>
              <DownloadOutlined />
            </template>
            导出BPMN
          </a-button>

          <a-button type="primary" size="small" @click="exportSvg">
            <template #icon>
              <FileImageOutlined />
            </template>
            导出SVG
          </a-button>
        </div>
      </div>
      <div ref="propertiesPanel" class="properties-panel"></div>
    </div>

    <!-- 部署流程对话框 -->
    <a-modal v-model:open="saveDialogVisible" title="部署流程" :width="600" @ok="handleSaveProcess"
      @cancel="handleCancelSave" :confirm-loading="saveLoading" :zIndex="20001" :centered="true" :draggable="true">
      <a-form :model="saveFormData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }" :rules="saveFormRules"
        ref="saveFormRef">
        <a-form-item label="部署名称" name="deploymentName">
          <a-input v-model:value="saveFormData.deploymentName" placeholder="请输入部署名称" :maxlength="100" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
    <!-- 部署结果弹窗（本页展示，让用户选择跳转） -->
    <ProcessDeployResultModal v-model:open="saveResultOpen"
      :result="{ deploymentId: lastSaveResult.deploymentId, deploymentName: lastSaveResult.deploymentName, description: lastSaveResult.description }"
      :actions="nextActions" @run-action="onRunAction" @go-deployment="() => router.push('/deployment')"
      @go-definition="() => router.push('/process/definition')" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, onUnmounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  RocketOutlined,
  DownloadOutlined,
  FileImageOutlined,
  FolderOpenOutlined,
  PlusOutlined
} from '@ant-design/icons-vue'
// bpmn-js 及属性面板相关依赖
import BpmnModeler from 'bpmn-js/lib/Modeler'
// 使用旧版本包提供完整的 BPMN.js 属性面板模块（新版本包只提供基础组件）
import {
  BpmnPropertiesPanelModule,
  BpmnPropertiesProviderModule,
  CamundaPlatformPropertiesProviderModule
} from 'bpmn-js-properties-panel'
import minimapModule from 'diagram-js-minimap';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json'
// i18n - 使用简化的翻译模块
import { getTranslateModule, translateUtils } from '@/utils/bpmn/utils/translateUtils'
import ProcessDeployResultModal from '@/components/process/ProcessDeployResultModal.vue'

// 声明全局变量类型
declare global {
  interface Window {
    untranslatedItems?: Set<string>
  }
}

// 导入必要的 CSS 样式
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css";
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
// 使用新版本包的 CSS 样式（旧版本包没有 CSS 文件）
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'
// 导入 minimap 样式
import 'diagram-js-minimap/assets/diagram-js-minimap.css'

// 定义组件事件
type ProcessInfoInput = {
  deploymentName: string
  description?: string
  key?: string
  deploymentId?: string
}

const emit = defineEmits<{
  (e: 'save', bpmnXml: string, processInfo: ProcessInfoInput): void
  (e: 'cancel'): void
}>()

const bpmnContainer = ref<HTMLDivElement | null>(null)
const propertiesPanel = ref<HTMLDivElement | null>(null)
const modeler = ref<BpmnModeler | null>(null)

// 初始化路由
const router = useRouter()

// 保存对话框相关
const saveDialogVisible = ref(false)
const saveLoading = ref(false)
const saveFormRef = ref()

// 部署结果弹窗相关
const saveResultOpen = ref(false)
const lastSaveResult = reactive({ deploymentId: '', deploymentName: '', description: '' })

// 部署结果后的后续操作（按你原先定义）
const nextActions = ref([
  {
    icon: '🚀',
    color: '#52c41a',
    title: '启动流程实例',
    description: '立即启动一个流程实例进行测试',
    action: () => startProcessInstance()
  },
  {
    icon: '👥',
    color: '#1890ff',
    title: '分配任务处理人',
    description: '为流程中的用户任务分配具体的处理人',
    action: () => assignTaskUsers()
  },
  {
    icon: '📊',
    color: '#722ed1',
    title: '查看流程监控',
    description: '监控流程实例的执行状态和性能指标',
    action: () => viewProcessMonitoring()
  },
  {
    icon: '⚙️',
    color: '#fa8c16',
    title: '配置流程参数',
    description: '设置流程的全局参数和业务规则',
    action: () => configureProcessParams()
  }
])

const onRunAction = (index: number) => {
  const item = (nextActions.value || [])[index]
  if (item && typeof item.action === 'function') {
    item.action()
  }
}

// 占位实现：在建模页仅提示，实际功能可在对应页面完善
async function startProcessInstance() {
  try {
    const { instanceApi } = await import('@/api/process')
    if (!currentProcessKey.value) {
      message.warning('请先部署流程或确保流程 Key 已存在')
      return
    }
    const result = await instanceApi.startProcess({
      processKey: currentProcessKey.value,
      variables: {}
    })
    message.success(`流程实例启动成功！实例ID: ${result.instanceId}`)
  } catch (error: unknown) {
    console.error('启动流程实例失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    message.error('启动流程实例失败：' + errorMessage)
  }
}
function assignTaskUsers() {
  message.info('请在任务管理页进行任务分配')
}
function viewProcessMonitoring() {
  message.info('请前往监控页查看流程指标')
}
function configureProcessParams() {
  message.info('请在流程参数配置页进行设置')
}

// 保存表单数据
const saveFormData = reactive({
  deploymentName: '',
  description: ''
})
const currentProcessKey = ref('')

// 表单验证规则
const saveFormRules = {
  deploymentName: [
    { required: true, message: '请输入部署名称', trigger: 'blur' },
    { min: 2, max: 100, message: '部署名称长度应在2-100个字符之间', trigger: 'blur' }
  ],
  description: [
    { max: 500, message: '流程描述不能超过500个字符', trigger: 'blur' }
  ]
}

// 翻译模块将在 onMounted 中异步加载
 
let customTranslateModule: unknown = null

// 处理保存 XML 的通用函数
 
const handleSaveXML = async (err: Error | null, xml: string, processInfo: ProcessInfoInput) => {
  console.log('🔍 handleSaveXML 被调用')
  console.log('🔍 err:', err)
  console.log('🔍 xml 长度:', xml ? xml.length : 'null')
  console.log('🔍 processInfo:', processInfo)
  if (processInfo?.key) {
    currentProcessKey.value = processInfo.key
  }

  if (err) {
    console.error('❌ saveXML 错误:', err)
    message.error('保存失败：' + err.message)
    return
  }

  console.log('✅ saveXML 成功，开始验证流程信息...')

  // 验证必填信息
  console.log('🔍 验证流程信息...')
  console.log('🔍 processInfo.deploymentName:', processInfo.deploymentName)
  // 已移除流程Key校验

  if (!processInfo.deploymentName?.trim()) {
    console.error('❌ 部署名称为空')
    message.error('请输入部署名称')
    return
  }
  // 已移除流程Key校验

  console.log('✅ 流程信息验证通过')

  try {
    console.log('🔍 开始调用后端 API...')
    // 调用后端 API 保存流程
    const { deploymentApi } = await import('@/api/process')
    console.log('✅ deploymentApi 导入成功:', deploymentApi)

    const saveData = {
      bpmnXml: xml,
      source: 'custom-tool',
      deploymentName: processInfo.deploymentName.trim(),
      key: processInfo.key ?? currentProcessKey.value
    }

    console.log('🔍 准备发送的数据:', saveData)
    console.log('🔍 XML 内容预览:', xml.substring(0, 200) + '...')

    const loadingMessage = message.loading('正在部署流程...')
    console.log('🔍 显示加载消息')

    try {
      console.log('🔍 开始调用 deployProcessWithInfo...')
      const result = await deploymentApi.deployProcessWithInfo(saveData)
      console.log('✅ API 调用成功，返回结果:', result)

      loadingMessage()
      message.success(`流程部署成功！部署ID: ${result.deploymentId}`)

      // 在当前页展示结果弹窗，由用户选择跳转位置
      lastSaveResult.deploymentId = result.deploymentId
      lastSaveResult.deploymentName = processInfo.deploymentName
      lastSaveResult.description = processInfo.description || ''
      saveResultOpen.value = true

      // 触发保存事件（可选）
      emit('save', xml, { ...processInfo, deploymentId: result.deploymentId })

       
    } catch (apiError: any) {
      loadingMessage()
      console.error('部署流程到后端失败:', apiError)

      const errorMessage = apiError.response?.data?.error || apiError.message || '部署失败'
      message.error('部署到后端失败：' + errorMessage)

      // 即使后端部署失败，也触发保存事件（用于本地保存）
      emit('save', xml, { ...processInfo })
    }
  } catch (error) {
    console.error('部署流程失败:', error)
    message.error('部署失败：' + (error as Error).message)
  }
}

// 显示部署对话框
const showSaveDialog = () => {
  console.log('🔍 显示部署对话框')

  if (!modeler.value) {
    console.error('❌ 流程设计器未初始化')
    message.error('流程设计器未初始化')
    return
  }

  // 初始化表单数据
  saveFormData.deploymentName = '新建部署'
  saveFormData.description = ''

  saveDialogVisible.value = true
}

// 处理部署流程
const handleSaveProcess = async () => {
  console.log('🔍 处理部署流程')

  try {
    // 验证表单
    await saveFormRef.value.validate()

    saveLoading.value = true

    // 获取 BPMN XML
    await getBpmnXmlAndSave(saveFormData)

  } catch (error) {
    console.error('❌ 表单验证失败:', error)
    // 表单验证失败，不关闭对话框
  } finally {
    saveLoading.value = false
  }
}

// 取消部署
const handleCancelSave = () => {
  console.log('🔍 取消部署')
  saveDialogVisible.value = false
  // 重置表单
  saveFormRef.value?.resetFields()
}

// 获取 BPMN XML 并部署
 
const getBpmnXmlAndSave = async (processInfo: ProcessInfoInput) => {
  console.log('🔍 开始获取 BPMN XML 并部署')
  console.log('🔍 processInfo:', processInfo)

  if (!modeler.value) {
    console.error('❌ 流程设计器未初始化')
    message.error('流程设计器未初始化')
    return
  }

  try {
    console.log('🔍 开始调用 saveXML...')
    console.log('🔍 modeler.value:', modeler.value)
     
    console.log('🔍 modeler.value.saveXML:', (modeler.value as any).saveXML)

    // 检查 saveXML 方法是否存在
     
    if (typeof (modeler.value as any).saveXML !== 'function') {
      console.error('❌ saveXML 方法不存在或不是函数')
      console.log('🔍 尝试使用 getXML 方法...')

      // 使用 getXML 作为备用方法
      try {
         
        const xml = await (modeler.value as any).getXML({ format: true })
        console.log('✅ getXML 成功，XML 长度:', xml.length)
        await handleSaveXML(null, xml, processInfo)
        saveDialogVisible.value = false // 部署成功后关闭对话框
        return
      } catch (error) {
        console.error('❌ getXML 也失败了:', error)
        message.error('无法获取 BPMN XML：' + (error as Error).message)
        return
      }
    }

    console.log('🔍 准备调用 saveXML 方法...')

    try {
       
      const result = await (modeler.value as any).saveXML({ format: true })
      console.log('✅ saveXML 方法调用成功')
      console.log('🔍 返回结果:', result)

      // 处理返回结果
      if (result && result.xml) {
        console.log('🔍 XML 长度:', result.xml.length)
        await handleSaveXML(null, result.xml, processInfo)
        saveDialogVisible.value = false // 部署成功后关闭对话框
      } else {
        console.error('❌ saveXML 返回结果格式不正确:', result)
        message.error('获取 BPMN XML 失败：返回结果格式不正确')
      }
    } catch (error) {
      console.error('❌ saveXML 方法调用失败:', error)
      message.error('获取 BPMN XML 失败：' + (error as Error).message)
    }
  } catch (error) {
    message.error('保存失败：' + (error as Error).message)
  }
}



// 导出BPMN文件
const exportBpmn = async () => {
  if (!modeler.value) {
    message.error('流程设计器未初始化')
    return
  }

  try {
    console.log('🔍 开始导出BPMN文件...')

    // 使用Promise方式获取XML
     
    const result = await (modeler.value as any).saveXML({ format: true })
    console.log('✅ BPMN XML获取成功，长度:', result.xml.length)

    // 创建下载链接
    const blob = new Blob([result.xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${saveFormData.deploymentName || 'process'}.bpmn`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    message.success('BPMN文件导出成功')
    console.log('✅ BPMN文件导出完成')
  } catch (error) {
    console.error('❌ BPMN导出失败:', error)
    message.error('BPMN导出失败：' + (error as Error).message)
  }
}

// 导出SVG文件
const exportSvg = async () => {
  if (!modeler.value) {
    message.error('流程设计器未初始化')
    return
  }

  try {
    console.log('🔍 开始导出SVG文件...')

    // 使用Promise方式获取SVG
     
    const result = await (modeler.value as any).saveSVG()
    console.log('✅ SVG获取成功，长度:', result.svg.length)

    // 创建下载链接
    const blob = new Blob([result.svg], { type: 'image/svg+xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${saveFormData.deploymentName || 'process'}.svg`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    message.success('SVG文件导出成功')
    console.log('✅ SVG文件导出完成')
  } catch (error) {
    console.error('❌ SVG导出失败:', error)
    message.error('SVG导出失败：' + (error as Error).message)
  }
}

// 打开本地BPMN文件
const openLocalFile = () => {
  if (!modeler.value) {
    message.error('流程设计器未初始化')
    return
  }

  try {
    console.log('🔍 开始打开本地文件...')

    // 创建文件输入元素
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.bpmn,.xml'
    input.style.display = 'none'

    // 添加文件选择事件监听器
    input.addEventListener('change', async (event) => {
      const target = event.target as HTMLInputElement
      const file = target.files?.[0]

      if (!file) {
        console.log('❌ 未选择文件')
        return
      }

      console.log('🔍 选择的文件:', file.name, '大小:', file.size, 'bytes')

      try {
        // 读取文件内容
        const fileContent = await readFileAsText(file)
        console.log('✅ 文件读取成功，内容长度:', fileContent.length)

        // 验证是否为有效的BPMN XML
        if (!fileContent.includes('<bpmn:definitions') && !fileContent.includes('<definitions')) {
          message.error('选择的文件不是有效的BPMN文件')
          return
        }

        // 导入BPMN XML到设计器
        await modeler.value!.importXML(fileContent)
        console.log('✅ BPMN文件导入成功')

        // 自动调整视图以适应内容
         
        const canvas = modeler.value!.get('canvas') as any
        canvas.zoom('fit-viewport')

        message.success(`文件 "${file.name}" 导入成功`)

        // 更新表单数据中的流程名称（从文件名推断）
        const fileName = file.name.replace(/\.(bpmn|xml)$/i, '')
        saveFormData.deploymentName = fileName

      } catch (error) {
        console.error('❌ 文件导入失败:', error)
        message.error('文件导入失败：' + (error as Error).message)
      } finally {
        // 清理文件输入元素
        document.body.removeChild(input)
      }
    })

    // 添加到DOM并触发点击
    document.body.appendChild(input)
    input.click()

  } catch (error) {
    console.error('❌ 打开文件失败:', error)
    message.error('打开文件失败：' + (error as Error).message)
  }
}

// 读取文件为文本内容
const readFileAsText = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = (event) => {
      const result = event.target?.result
      if (typeof result === 'string') {
        resolve(result)
      } else {
        reject(new Error('文件读取结果不是字符串'))
      }
    }

    reader.onerror = () => {
      reject(new Error('文件读取失败'))
    }

    reader.readAsText(file, 'UTF-8')
  })
}

// 创建新的BPMN流程
const createNewBpmn = async () => {
  if (!modeler.value) {
    message.error('流程设计器未初始化')
    return
  }

  try {
    console.log('🔍 开始创建新的BPMN流程...')

    // 创建一个简单的空白BPMN流程
    const newBpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn"
                  exporter="Camunda Modeler"
                  exporterVersion="5.0.0">
  <bpmn:process id="NewProcess" name="新建流程" isExecutable="true" camunda:historyTimeToLive="30">
    <!-- 开始事件 -->
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- 结束事件 -->
    <bpmn:endEvent id="EndEvent_1" name="结束">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- 流程连线 -->
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" name=""/>
  </bpmn:process>
  
  <!-- 图形布局 -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="NewProcess">
      <!-- 开始事件 -->
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="158" y="145" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      
      <!-- 结束事件 -->
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="240" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="246" y="145" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      
      <!-- 连线 -->
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="188" y="120"/>
        <di:waypoint x="240" y="120"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

    // 导入新的BPMN XML到设计器
    await modeler.value.importXML(newBpmnXml)
    console.log('✅ 新BPMN流程创建成功')

    // 自动调整视图以适应内容
     
    const canvas = modeler.value.get('canvas') as any
    canvas.zoom('fit-viewport')

    // 重置表单数据
    saveFormData.deploymentName = '新建部署'
    saveFormData.description = ''

    message.success('新BPMN流程创建成功')
    console.log('✅ 画布已重置，可以开始设计新流程')

  } catch (error) {
    console.error('❌ 创建新BPMN流程失败:', error)
    message.error('创建新流程失败：' + (error as Error).message)
  }
}












onMounted(async () => {
  console.log('🔍 开始初始化工作流设计器...')
  console.log('🔍 当前 modeler 状态:', modeler.value)

  // 等待 DOM 完全渲染
  await new Promise(resolve => setTimeout(resolve, 100))

  console.log('🔍 DOM 渲染等待完成')
  console.log('🔍 bpmnContainer.value:', bpmnContainer.value)
  console.log('🔍 propertiesPanel.value:', propertiesPanel.value)

  if (!bpmnContainer.value || !propertiesPanel.value) {
    console.error('❌ Container elements not found')
    console.error('❌ bpmnContainer.value:', bpmnContainer.value)
    console.error('❌ propertiesPanel.value:', propertiesPanel.value)
    message.error('容器元素未找到，请刷新页面重试')
    return
  }

  console.log('✅ 容器元素检查通过')





  // 尝试加载翻译模块，如果失败则使用默认配置
  try {
    customTranslateModule = await getTranslateModule(true)
    translateUtils.addCustomTranslations({
      'Test Translation': '测试翻译',
      'Custom Task': '自定义任务'
    })
    console.log('✅ 翻译模块加载成功')
  } catch (error) {
    console.warn('⚠️ 翻译模块加载失败，使用默认配置:', error)
    customTranslateModule = null
  }

  // 创建 BPMN Modeler
  try {
    // 构建 additionalModules 数组
    const additionalModules = [
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      CamundaPlatformPropertiesProviderModule,
      minimapModule
    ]

    if (customTranslateModule) {
      additionalModules.push(customTranslateModule)
    }

    modeler.value = new BpmnModeler({
      container: bpmnContainer.value,
      propertiesPanel: {
        parent: propertiesPanel.value
      },
      additionalModules: additionalModules,
      moddleExtensions: {
        camunda: camundaModdleDescriptor
      },
      minimap: {
        open: true,
        height: 280,
        width: 280
      }
    })

    console.log('✅ BPMN Modeler 初始化成功')
    console.log('🔍 创建后的 modeler 对象:', modeler.value)
    console.log('🔍 modeler 类型:', typeof modeler.value)
    console.log('🔍 modeler 是否为 null:', modeler.value === null)
    console.log('🔍 modeler 是否为 undefined:', modeler.value === undefined)



    // 加载一个简化的请假审批流程
    console.log('Loading BPMN XML...')
    modeler.value.importXML(`<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn"
                  exporter="Camunda Modeler"
                  exporterVersion="5.0.0">
  <bpmn:process id="SimpleLeaveProcess" name="简单请假流程" isExecutable="true" camunda:historyTimeToLive="30">
    <!-- 开始事件 -->
    <bpmn:startEvent id="StartEvent_1" name="开始申请">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- 填写申请表单 -->
    <bpmn:userTask id="UserTask_1" name="填写请假申请" camunda:candidateGroups="employees" camunda:priority="50">
      <bpmn:documentation>员工填写请假申请，请在1天内完成</bpmn:documentation>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 主管审批 -->
    <bpmn:userTask id="UserTask_2" name="主管审批" camunda:candidateGroups="managers" camunda:priority="80">
      <bpmn:documentation>直接主管审批请假申请，请在2天内完成</bpmn:documentation>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 结束事件 -->
    <bpmn:endEvent id="EndEvent_1" name="申请完成">
      <bpmn:incoming>Flow_3</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- 流程连线 -->
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1" name="开始申请"/>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="UserTask_1" targetRef="UserTask_2" name="提交申请"/>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="UserTask_2" targetRef="EndEvent_1" name="审批完成"/>
  </bpmn:process>
  
  <!-- 图形布局 -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="SimpleLeaveProcess">
      <!-- 开始事件 -->
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="158" y="145" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      
      <!-- 填写申请 -->
      <bpmndi:BPMNShape id="UserTask_1_di" bpmnElement="UserTask_1">
        <dc:Bounds x="240" y="80" width="100" height="80"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>
      
      <!-- 主管审批 -->
      <bpmndi:BPMNShape id="UserTask_2_di" bpmnElement="UserTask_2">
        <dc:Bounds x="400" y="80" width="100" height="80"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>
      
      <!-- 结束事件 -->
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="560" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="566" y="145" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      
      <!-- 连线 -->
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="188" y="120"/>
        <di:waypoint x="240" y="120"/>
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="340" y="120"/>
        <di:waypoint x="400" y="120"/>
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="500" y="120"/>
        <di:waypoint x="560" y="120"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`).then(() => {
      console.log('✅ BPMN XML imported successfully - 简单请假流程已加载')
      console.log('🔍 Modeler 在 importXML 后:', modeler.value)
      console.log('🔍 Modeler 在 importXML 后是否为 null:', modeler.value === null)

      // 自动调整视图以适应内容
      if (modeler.value) {
         
        const canvas = modeler.value.get('canvas') as any
        canvas.zoom('fit-viewport')
        console.log('🔍 视图已调整')
        console.log('🎉 初始化完成，部署按钮应该可用')
      } else {
        console.error('❌ importXML 后 modeler 为 null')
      }
    }).catch((error: unknown) => {
      console.error('❌ Error importing BPMN XML:', error)
      const errorObj = error as Error
      console.error('Error details:', errorObj?.message || 'Unknown error')
      console.error('Error stack:', errorObj?.stack || 'No stack trace')
    })

  } catch (error) {
    console.error('❌ BPMN Modeler 初始化失败:', error)
    message.error('BPMN Modeler 初始化失败：' + (error as Error).message)
    modeler.value = null
  }
})



onBeforeUnmount(() => {
  if (modeler.value) {
    modeler.value.destroy()
    modeler.value = null
  }
})

onUnmounted(() => {
  // 清理工作已完成
})
</script>

<style scoped>
.workflow-design {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fafafa;
  position: relative;
}

.floating-toolbar {
  position: absolute;
  bottom: 20px;
  left: 20px;
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
  backdrop-filter: none;
  z-index: 1000;
  display: flex;
  gap: 12px;
}

/* 调整按钮内图标和文本的间距 */
.floating-toolbar :deep(.ant-btn) {
  display: flex;
  align-items: center;
  gap: 0;
}

.floating-toolbar :deep(.ant-btn .anticon) {
  margin-right: 0;
}

.main-content {
  flex: 1;
  display: flex;
  height: 100%;
  min-height: 0;
}

.canvas {
  flex: 1;
  border: 1px solid #ccc;
  min-width: 0;
  background: white;
  position: relative;
  overflow: hidden;
}

.properties-panel {
  width: 350px;
  border-left: 1px solid #ccc;
  background: white;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* 确保属性面板内容正确显示 */
.properties-panel :deep(.bio-properties-panel) {
  height: 100%;
  overflow: auto;
  flex: 1;
}

.properties-panel :deep(.bio-properties-panel__group) {
  margin-bottom: 10px;
}

.properties-panel :deep(.bio-properties-panel__group-header) {
  font-weight: bold;
  padding: 8px 12px;
  background: #f5f5f5;
  border-bottom: 1px solid #e8e8e8;
}

.properties-panel :deep(.bio-properties-panel__entry) {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.properties-panel :deep(.bio-properties-panel__entry-label) {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.properties-panel :deep(.bio-properties-panel__entry-field) {
  width: 100%;
}

.properties-panel :deep(input),
.properties-panel :deep(select),
.properties-panel :deep(textarea) {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
}

/* 添加一些调试样式 */
.properties-panel:empty::after {
  content: '属性面板加载中...';
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 14px;
}

/* 确保 BPMN 编辑器正确显示 */
.canvas :deep(.djs-container) {
  height: 100%;
}

.canvas :deep(.djs-palette) {
  position: absolute;
  left: 20px;
  top: 20px;
}

/* minimap 样式 - 与官方示例保持一致 */
.canvas :deep(.djs-minimap) {
  position: absolute;
  right: 20px;
  top: 20px;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #ccc;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  max-height: 300px;
  max-width: 300px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.canvas :deep(.djs-minimap.closed) {
  max-height: 32px;
  max-width: 85px;
  min-height: 32px;
  min-width: 85px;
  width: 85px !important;
  height: 32px !important;
}

.canvas :deep(.djs-minimap .djs-minimap-canvas) {
  display: block;
  width: 100%;
  height: 100%;
}

.canvas :deep(.djs-minimap .viewport-dom) {
  border: 2px solid #1890ff;
  background: rgba(24, 144, 255, 0.1);
}

.canvas :deep(.djs-minimap .djs-minimap-toggle) {
  background: #f5f5f5 !important;
  border-bottom: 1px solid #e8e8e8 !important;
  padding: 4px 8px !important;
  cursor: pointer !important;
  font-size: 11px !important;
  text-align: center !important;
  font-weight: 500 !important;
  color: #333 !important;
  transition: background-color 0.2s ease !important;
  line-height: 1.2 !important;
  height: 32px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.canvas :deep(.djs-minimap .djs-minimap-toggle:hover) {
  background: #e6f7ff !important;
  color: #1890ff !important;
}

.canvas :deep(.djs-minimap .djs-minimap-toggle::before) {
  content: "🗺️";
  margin-right: 3px;
  font-size: 12px;
}

/* 确保 minimap 文字颜色正确 */
.canvas :deep(.djs-minimap .djs-minimap-toggle),
.canvas :deep(.djs-minimap .djs-minimap-toggle span),
.canvas :deep(.djs-minimap .djs-minimap-toggle div),
.canvas :deep(.djs-minimap .djs-minimap-toggle *),
.canvas :deep(.djs-minimap .djs-minimap-toggle) * {
  color: #333 !important;
  font-weight: 500 !important;
}

/* 强制覆盖所有可能的文字颜色 */
.canvas :deep(.djs-minimap) * {
  color: #333 !important;
}

.canvas :deep(.djs-minimap .djs-minimap-toggle) {
  color: #333 !important;
}

.canvas :deep(.djs-direct-editing-parent) {
  z-index: 1000;
}

/* 确保 BPMN 弹窗和配置选项正确显示 */
.canvas :deep(.djs-popup),
.canvas :deep(.djs-context-pad),
.canvas :deep(.djs-overlay-context-pad),
.canvas :deep(.djs-overlay),
.canvas :deep(.djs-popup-container),
.canvas :deep(.djs-popup-wrapper) {
  background: #ffffff !important;
  color: #262626 !important;
  border: 1px solid #d9d9d9 !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
  z-index: 10000 !important;
  /* 确保不透明 */
  opacity: 1 !important;
  backdrop-filter: none !important;
}

/* 确保弹窗内容不透明 */
.canvas :deep(.djs-popup .djs-popup-body),
.canvas :deep(.djs-context-pad .djs-context-pad-body),
.canvas :deep(.djs-overlay-context-pad .djs-overlay-context-pad-body) {
  background: #ffffff !important;
  color: #262626 !important;
}

/* 确保搜索框和选项正确显示 */
.canvas :deep(.djs-popup input),
.canvas :deep(.djs-popup select),
.canvas :deep(.djs-popup textarea) {
  background: #ffffff !important;
  color: #262626 !important;
  border: 1px solid #d9d9d9 !important;
}

/* 确保选项列表正确显示 */
.canvas :deep(.djs-popup .djs-popup-options),
.canvas :deep(.djs-popup .djs-popup-option) {
  background: #ffffff !important;
  color: #262626 !important;
}

.canvas :deep(.djs-popup .djs-popup-option:hover) {
  background: #f5f5f5 !important;
}

/* 特别针对"更改元素"弹窗的样式 */
.canvas :deep(.djs-popup[data-action="replace"]) {
  background: #ffffff !important;
  color: #262626 !important;
  border: 1px solid #d9d9d9 !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
  z-index: 10001 !important;
  opacity: 1 !important;
  backdrop-filter: none !important;
}

/* 确保搜索框正确显示 */
.canvas :deep(.djs-popup .djs-popup-search) {
  background: #ffffff !important;
  color: #262626 !important;
  border: 1px solid #d9d9d9 !important;
}

/* 确保选项列表正确显示 */
.canvas :deep(.djs-popup .djs-popup-options) {
  background: #ffffff !important;
  color: #262626 !important;
  max-height: 200px !important;
  overflow-y: auto !important;
}

/* 调试面板样式已移至 DebugI18nPanel 组件中 */
/* 保证 bio-properties-panel-checkbox 内部内容一行展示并居中 */
:deep(.bio-properties-panel-checkbox) {
  display: flex !important;
  align-items: center !important;
  gap: 8px;
  max-width: 100%;
  white-space: nowrap;
}

:deep(.bio-properties-panel-checkbox input[type="checkbox"]) {
  flex: 0 0 auto;
  width: 16px;
  min-width: 16px;
  max-width: 20px;
}

:deep(.bio-properties-panel-checkbox label) {
  flex: 0 1 auto;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
