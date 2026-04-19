<template>
  <div class="content-container">
    <div ref="constraintPageRef" class="content-card platform-page-shell">
      <div v-if="!canView" class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>RBAC3 约束管理需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:role:constraint:view</code> 或管理员权限的用户，才能查看角色约束规则。
        </p>
      </div>

      <a-tabs
        v-else
        v-model:activeKey="activeTab"
        class="boundary-tabs"
        destroy-inactive-tab-pane
        @change="handleTabChange"
      >
        <a-tab-pane key="hierarchy" tab="角色继承">
          <div class="tab-workspace">
            <div class="form-container">
              <a-form layout="inline" :model="hierarchyQuery">
                <a-form-item label="角色">
                  <a-input v-model:value="hierarchyQuery.keyword" placeholder="父角色ID / 当前角色ID / 角色名称" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="handleHierarchySearch">搜索</a-button>
                  <a-button class="ml-2" @click="handleHierarchyReset">重置</a-button>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">角色继承</div>
              <div class="table-actions">
                <a-button v-if="canEditConstraint" type="link" @click="openHierarchyModal">
                  <template #icon><PlusOutlined /></template>
                  新建
                </a-button>
                <a-tooltip title="刷新">
                  <span class="action-icon" @click="loadHierarchies">
                    <ReloadOutlined :spin="hierarchyLoading" />
                  </span>
                </a-tooltip>
              </div>
            </div>

            <div class="table-container">
              <div class="table-scroll-container">
                <a-table
                  :columns="hierarchyColumns"
                  :data-source="pagedHierarchies"
                  :pagination="false"
                  :row-key="hierarchyRowKey"
                  bordered
                  :loading="hierarchyLoading"
                  :scroll="{ x: 'max-content', y: tableBodyHeight }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'parentRoleName'">
                      {{ formatRoleName(record.parentRoleId) }}
                    </template>
                    <template v-else-if="column.dataIndex === 'childRoleName'">
                      {{ formatRoleName(record.childRoleId) }}
                    </template>
                    <template v-else-if="column.dataIndex === 'relationDescription'">
                      {{ formatHierarchyDescription(record) }}
                    </template>
                    <template v-else-if="column.dataIndex === 'action'">
                      <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('hierarchy', record)">
                        <template #icon><DeleteOutlined /></template>
                        删除
                      </a-button>
                    </template>
                  </template>
                </a-table>
              </div>

              <div class="pagination-container">
                <div class="pagination-spacer"></div>
                <a-pagination
                  v-model:current="hierarchyPagination.current"
                  :page-size="hierarchyPagination.pageSize"
                  :total="hierarchyPagination.total"
                  show-size-changer
                  :page-size-options="LOCAL_PAGE_SIZE_OPTIONS"
                  :show-total="(total: number) => `共 ${total} 条`"
                  @change="handleHierarchyPageChange"
                  @showSizeChange="handleHierarchyPageChange"
                />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="mutex" tab="互斥约束">
          <div class="tab-workspace">
            <div class="form-container">
              <a-form layout="inline" :model="mutexQuery">
                <a-form-item label="角色">
                  <a-input v-model:value="mutexQuery.keyword" placeholder="角色A ID / 角色B ID" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="handleMutexSearch">搜索</a-button>
                  <a-button class="ml-2" @click="handleMutexReset">重置</a-button>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">互斥约束</div>
              <div class="table-actions">
                <a-button v-if="canEditConstraint" type="link" @click="openMutexModal">
                  <template #icon><PlusOutlined /></template>
                  新建
                </a-button>
                <a-tooltip title="刷新">
                  <span class="action-icon" @click="loadMutexes">
                    <ReloadOutlined :spin="mutexLoading" />
                  </span>
                </a-tooltip>
              </div>
            </div>

            <div class="table-container">
              <div class="table-scroll-container">
                <a-table
                  :columns="mutexColumns"
                  :data-source="pagedMutexes"
                  :pagination="false"
                  :row-key="mutexRowKey"
                  bordered
                  :loading="mutexLoading"
                  :scroll="{ x: 'max-content', y: tableBodyHeight }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'action'">
                      <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('mutex', record)">
                        <template #icon><DeleteOutlined /></template>
                        删除
                      </a-button>
                    </template>
                  </template>
                </a-table>
              </div>

              <div class="pagination-container">
                <div class="pagination-spacer"></div>
                <a-pagination
                  v-model:current="mutexPagination.current"
                  :page-size="mutexPagination.pageSize"
                  :total="mutexPagination.total"
                  show-size-changer
                  :page-size-options="LOCAL_PAGE_SIZE_OPTIONS"
                  :show-total="(total: number) => `共 ${total} 条`"
                  @change="handleMutexPageChange"
                  @showSizeChange="handleMutexPageChange"
                />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="prerequisite" tab="先决条件">
          <div class="tab-workspace">
            <div class="form-container">
              <a-form layout="inline" :model="prerequisiteQuery">
                <a-form-item label="角色">
                  <a-input v-model:value="prerequisiteQuery.keyword" placeholder="目标角色ID / 前置角色ID" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="handlePrerequisiteSearch">搜索</a-button>
                  <a-button class="ml-2" @click="handlePrerequisiteReset">重置</a-button>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">先决条件</div>
              <div class="table-actions">
                <a-button v-if="canEditConstraint" type="link" @click="openPrerequisiteModal">
                  <template #icon><PlusOutlined /></template>
                  新建
                </a-button>
                <a-tooltip title="刷新">
                  <span class="action-icon" @click="loadPrerequisites">
                    <ReloadOutlined :spin="prerequisiteLoading" />
                  </span>
                </a-tooltip>
              </div>
            </div>

            <div class="table-container">
              <div class="table-scroll-container">
                <a-table
                  :columns="prerequisiteColumns"
                  :data-source="pagedPrerequisites"
                  :pagination="false"
                  :row-key="prerequisiteRowKey"
                  bordered
                  :loading="prerequisiteLoading"
                  :scroll="{ x: 'max-content', y: tableBodyHeight }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'action'">
                      <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('prerequisite', record)">
                        <template #icon><DeleteOutlined /></template>
                        删除
                      </a-button>
                    </template>
                  </template>
                </a-table>
              </div>

              <div class="pagination-container">
                <div class="pagination-spacer"></div>
                <a-pagination
                  v-model:current="prerequisitePagination.current"
                  :page-size="prerequisitePagination.pageSize"
                  :total="prerequisitePagination.total"
                  show-size-changer
                  :page-size-options="LOCAL_PAGE_SIZE_OPTIONS"
                  :show-total="(total: number) => `共 ${total} 条`"
                  @change="handlePrerequisitePageChange"
                  @showSizeChange="handlePrerequisitePageChange"
                />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="cardinality" tab="基数限制">
          <div class="tab-workspace">
            <div class="form-container">
              <a-form layout="inline" :model="cardinalityQuery">
                <a-form-item label="角色">
                  <a-input v-model:value="cardinalityQuery.keyword" placeholder="角色ID / 作用域类型" />
                </a-form-item>
                <a-form-item label="作用域">
                  <a-select v-model:value="cardinalityQuery.scopeType" allow-clear placeholder="全部" style="width: 140px">
                    <a-select-option v-for="scopeType in scopeTypeOptions" :key="scopeType" :value="scopeType">
                      {{ scopeType }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="handleCardinalitySearch">搜索</a-button>
                  <a-button class="ml-2" @click="handleCardinalityReset">重置</a-button>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">基数限制</div>
              <div class="table-actions">
                <a-button v-if="canEditConstraint" type="link" @click="openCardinalityModal">
                  <template #icon><PlusOutlined /></template>
                  新建
                </a-button>
                <a-tooltip title="刷新">
                  <span class="action-icon" @click="loadCardinalities">
                    <ReloadOutlined :spin="cardinalityLoading" />
                  </span>
                </a-tooltip>
              </div>
            </div>

            <div class="table-container">
              <div class="table-scroll-container">
                <a-table
                  :columns="cardinalityColumns"
                  :data-source="pagedCardinalities"
                  :pagination="false"
                  :row-key="cardinalityRowKey"
                  bordered
                  :loading="cardinalityLoading"
                  :scroll="{ x: 'max-content', y: tableBodyHeight }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'scopeType'">
                      <a-tag color="blue">{{ record.scopeType }}</a-tag>
                    </template>
                    <template v-else-if="column.dataIndex === 'action'">
                      <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('cardinality', record)">
                        <template #icon><DeleteOutlined /></template>
                        删除
                      </a-button>
                    </template>
                  </template>
                </a-table>
              </div>

              <div class="pagination-container">
                <div class="pagination-spacer"></div>
                <a-pagination
                  v-model:current="cardinalityPagination.current"
                  :page-size="cardinalityPagination.pageSize"
                  :total="cardinalityPagination.total"
                  show-size-changer
                  :page-size-options="LOCAL_PAGE_SIZE_OPTIONS"
                  :show-total="(total: number) => `共 ${total} 条`"
                  @change="handleCardinalityPageChange"
                  @showSizeChange="handleCardinalityPageChange"
                />
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane v-if="canViewViolations" key="violations" tab="违规记录">
          <div class="tab-workspace">
            <div class="form-container">
              <a-form layout="inline" :model="violationQuery">
                <a-form-item label="关键词">
                  <a-input v-model:value="violationQuery.keyword" placeholder="违规编码 / 主体 / 详情" />
                </a-form-item>
                <a-form-item label="违规类型">
                  <a-input v-model:value="violationQuery.violationType" placeholder="违规类型" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="handleViolationSearch">搜索</a-button>
                  <a-button class="ml-2" @click="handleViolationReset">重置</a-button>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">违规记录</div>
              <div class="table-actions">
                <a-tooltip title="刷新">
                  <span class="action-icon" @click="loadViolations">
                    <ReloadOutlined :spin="violationLoading" />
                  </span>
                </a-tooltip>
              </div>
            </div>

            <div class="table-container">
              <div class="table-scroll-container">
                <a-table
                  :columns="violationColumns"
                  :data-source="filteredViolations"
                  :pagination="false"
                  :row-key="(r: any) => String(r.id)"
                  bordered
                  :loading="violationLoading"
                  :scroll="{ x: 'max-content', y: tableBodyHeight }"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'violationType'">
                      <a-tag>{{ record.violationType }}</a-tag>
                    </template>
                    <template v-else-if="column.dataIndex === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
                  </template>
                </a-table>
              </div>

              <div class="pagination-container">
                <div class="pagination-spacer"></div>
                <a-pagination
                  v-model:current="violationPagination.current"
                  :page-size="violationPagination.pageSize"
                  :total="violationPagination.total"
                  show-size-changer
                  :page-size-options="['10', '20', '50']"
                  :show-total="(total: number) => `共 ${total} 条`"
                  @change="handleViolationPageChange"
                  @showSizeChange="handleViolationPageSizeChange"
                />
              </div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- Hierarchy Modal -->
    <a-modal v-model:open="hierarchyModalVisible" title="新建角色继承" @ok="submitHierarchy" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="父角色ID" required>
          <a-input-number v-model:value="hierarchyForm.parentRoleId" :min="1" style="width: 100%" placeholder="请输入父角色ID" />
        </a-form-item>
        <a-form-item label="子角色ID" required>
          <a-input-number v-model:value="hierarchyForm.childRoleId" :min="1" style="width: 100%" placeholder="请输入子角色ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Mutex Modal -->
    <a-modal v-model:open="mutexModalVisible" title="新建互斥约束" @ok="submitMutex" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色A ID" required>
          <a-input-number v-model:value="mutexForm.roleIdA" :min="1" style="width: 100%" placeholder="请输入角色A ID" />
        </a-form-item>
        <a-form-item label="角色B ID" required>
          <a-input-number v-model:value="mutexForm.roleIdB" :min="1" style="width: 100%" placeholder="请输入角色B ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Prerequisite Modal -->
    <a-modal v-model:open="prerequisiteModalVisible" title="新建先决条件" @ok="submitPrerequisite" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色ID" required>
          <a-input-number v-model:value="prerequisiteForm.roleId" :min="1" style="width: 100%" placeholder="请输入角色ID" />
        </a-form-item>
        <a-form-item label="前置角色ID" required>
          <a-input-number v-model:value="prerequisiteForm.requiredRoleId" :min="1" style="width: 100%" placeholder="请输入前置角色ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Cardinality Modal -->
    <a-modal v-model:open="cardinalityModalVisible" title="新建基数限制" @ok="submitCardinality" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色ID" required>
          <a-input-number v-model:value="cardinalityForm.roleId" :min="1" style="width: 100%" placeholder="请输入角色ID" />
        </a-form-item>
        <a-form-item label="作用域类型" required>
          <a-select v-model:value="cardinalityForm.scopeType" placeholder="请选择作用域类型">
            <a-select-option v-for="scopeType in scopeTypeOptions" :key="scopeType" :value="scopeType">
              {{ scopeType }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="最大分配数" required>
          <a-input-number v-model:value="cardinalityForm.maxAssignments" :min="1" style="width: 100%" placeholder="请输入最大分配数" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onActivated, onBeforeUnmount, onMounted, watch } from 'vue'
import { useAuth } from '@/auth/auth'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { Key } from 'ant-design-vue/es/_util/type'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  ROLE_CONSTRAINT_VIEW,
  ROLE_CONSTRAINT_EDIT,
  ROLE_CONSTRAINT_VIOLATION_VIEW,
} from '@/constants/permission'
import { getAllRoles, type Role } from '@/api/role'
import {
  listHierarchies, createHierarchy, deleteHierarchy,
  listMutexes, createMutex, deleteMutex,
  listPrerequisites, createPrerequisite, deletePrerequisite,
  listCardinalities, createCardinality, deleteCardinality,
  listViolations,
} from '@/api/roleConstraint'
import type { RoleHierarchy, RoleMutex, RolePrerequisite, RoleCardinality, RoleViolation } from '@/api/roleConstraint'

const { user } = useAuth()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAuthority(perm: string) {
  return authorities.value.has(perm)
}

const canView = computed(() => hasAuthority(ROLE_CONSTRAINT_VIEW))
const canEditConstraint = computed(() => hasAuthority(ROLE_CONSTRAINT_EDIT))
const canViewViolations = computed(() => hasAuthority(ROLE_CONSTRAINT_VIOLATION_VIEW))

const LOCAL_PAGE_SIZE_OPTIONS = ['10', '20', '30', '40', '50']
const constraintPageRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(360)

function normalizeText(value: unknown) {
  return String(value ?? '').trim().toLowerCase()
}

function includesKeyword(keyword: string, ...candidates: unknown[]) {
  const normalizedKeyword = normalizeText(keyword)
  if (!normalizedKeyword) {
    return true
  }
  return candidates.some((candidate) => normalizeText(candidate).includes(normalizedKeyword))
}

type LocalPaginationState = {
  current: number
  pageSize: number
  total: number
}

function createLocalPagination(): LocalPaginationState {
  return {
    current: 1,
    pageSize: 10,
    total: 0,
  }
}

function normalizeLocalPagination(pagination: LocalPaginationState, total: number) {
  pagination.total = total
  const maxPage = Math.max(1, Math.ceil(total / pagination.pageSize))
  if (pagination.current > maxPage) {
    pagination.current = maxPage
  }
}

function sliceLocalPage<T>(items: T[], pagination: LocalPaginationState) {
  const start = (pagination.current - 1) * pagination.pageSize
  return items.slice(start, start + pagination.pageSize)
}

function updateLocalPage(pagination: LocalPaginationState, page: number, pageSize: number) {
  pagination.current = page
  pagination.pageSize = pageSize
}

function updateTableBodyHeight() {
  nextTick(() => {
    const root = constraintPageRef.value
    const activePane = root?.querySelector('.ant-tabs-tabpane-active') as HTMLElement | null
    const tableContainer = activePane?.querySelector('.table-container') as HTMLElement | null
    const tableScroll = activePane?.querySelector('.table-scroll-container') as HTMLElement | null
    const pagination = activePane?.querySelector('.pagination-container') as HTMLElement | null
    const tableHeader =
      (tableScroll?.querySelector('.ant-table-header') as HTMLElement | null)
      || (tableScroll?.querySelector('.ant-table-thead') as HTMLElement | null)

    if (!tableContainer) {
      return
    }

    const containerHeight = tableContainer.clientHeight
    const paginationHeight = pagination?.clientHeight ?? 56
    const tableHeaderHeight = tableHeader?.clientHeight ?? 55
    const nextHeight = Math.max(containerHeight - paginationHeight - tableHeaderHeight - 24, 220)
    tableBodyHeight.value = nextHeight
  })
}

function scheduleTableBodyHeightUpdate() {
  updateTableBodyHeight()
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(() => {
      updateTableBodyHeight()
    })
  }
}

const activeTab = ref('hierarchy')
const submitting = ref(false)

const roleOptions = ref<Role[]>([])
const roleById = computed(() => {
  const roleMap = new Map<number, Role>()
  for (const role of roleOptions.value) {
    const roleId = Number(role.id)
    if (Number.isFinite(roleId) && roleId > 0) {
      roleMap.set(roleId, role)
    }
  }
  return roleMap
})

function formatRoleName(roleId: number) {
  const role = roleById.value.get(roleId)
  if (!role) {
    return '-'
  }
  return role.name || role.code || '-'
}

function formatHierarchyDescription(record: RoleHierarchy | Record<string, any>) {
  const parentRoleName = formatRoleName(Number(record?.parentRoleId))
  const childRoleName = formatRoleName(Number(record?.childRoleId))
  return `当前角色 ${childRoleName} 继承父角色 ${parentRoleName} 的权限能力`
}

async function loadRoleOptions() {
  if (!canView.value) {
    roleOptions.value = []
    return
  }
  try {
    const result = await getAllRoles()
    roleOptions.value = Array.isArray(result) ? result : []
  } catch {
    roleOptions.value = []
  }
}

// ─── Hierarchy ───
const hierarchies = ref<RoleHierarchy[]>([])
const hierarchyQuery = ref({ keyword: '' })
const hierarchyPagination = ref<LocalPaginationState>(createLocalPagination())
const hierarchyLoading = ref(false)
const hierarchyModalVisible = ref(false)
const hierarchyForm = ref({ parentRoleId: undefined as number | undefined, childRoleId: undefined as number | undefined })
const hierarchyColumns = [
  { title: '父角色ID', dataIndex: 'parentRoleId', width: 120 },
  { title: '父角色名称', dataIndex: 'parentRoleName', width: 180 },
  { title: '当前角色ID', dataIndex: 'childRoleId', width: 120 },
  { title: '角色名称', dataIndex: 'childRoleName', width: 180 },
  { title: '相关描述', dataIndex: 'relationDescription', width: 360 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const hierarchyRowKey = (record: RoleHierarchy) => `${record.childRoleId}-${record.parentRoleId}`

async function loadHierarchies() {
  hierarchyLoading.value = true
  try {
    const res = await listHierarchies()
    hierarchies.value = Array.isArray(res) ? res : []
  } catch { hierarchies.value = [] }
  finally {
    normalizeLocalPagination(hierarchyPagination.value, hierarchies.value.length)
    hierarchyLoading.value = false
  }
}

const filteredHierarchies = computed(() =>
  hierarchies.value.filter((record) =>
    includesKeyword(
      hierarchyQuery.value.keyword,
      record.parentRoleId,
      formatRoleName(record.parentRoleId),
      record.childRoleId,
      formatRoleName(record.childRoleId),
      formatHierarchyDescription(record),
    ),
  ),
)
const pagedHierarchies = computed(() => sliceLocalPage(filteredHierarchies.value, hierarchyPagination.value))

function handleHierarchyPageChange(page: number, pageSize: number) {
  updateLocalPage(hierarchyPagination.value, page, pageSize)
}

function handleHierarchySearch() {
  hierarchyPagination.value.current = 1
}

function handleHierarchyReset() {
  hierarchyQuery.value.keyword = ''
  hierarchyPagination.value.current = 1
}

function openHierarchyModal() {
  hierarchyForm.value = { parentRoleId: undefined, childRoleId: undefined }
  hierarchyModalVisible.value = true
}

async function submitHierarchy() {
  if (!hierarchyForm.value.parentRoleId || !hierarchyForm.value.childRoleId) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createHierarchy({ parentRoleId: hierarchyForm.value.parentRoleId, childRoleId: hierarchyForm.value.childRoleId })
    message.success('创建成功')
    hierarchyModalVisible.value = false
    loadHierarchies()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Mutex ───
const mutexes = ref<RoleMutex[]>([])
const mutexQuery = ref({ keyword: '' })
const mutexPagination = ref<LocalPaginationState>(createLocalPagination())
const mutexLoading = ref(false)
const mutexModalVisible = ref(false)
const mutexForm = ref({ roleIdA: undefined as number | undefined, roleIdB: undefined as number | undefined })
const mutexColumns = [
  { title: '角色A ID', dataIndex: 'leftRoleId', width: 120 },
  { title: '角色B ID', dataIndex: 'rightRoleId', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const mutexRowKey = (record: RoleMutex) => `${record.leftRoleId}-${record.rightRoleId}`

async function loadMutexes() {
  mutexLoading.value = true
  try {
    const res = await listMutexes()
    mutexes.value = Array.isArray(res) ? res : []
  } catch { mutexes.value = [] }
  finally {
    normalizeLocalPagination(mutexPagination.value, mutexes.value.length)
    mutexLoading.value = false
  }
}

const filteredMutexes = computed(() =>
  mutexes.value.filter((record) =>
    includesKeyword(mutexQuery.value.keyword, record.leftRoleId, record.rightRoleId),
  ),
)
const pagedMutexes = computed(() => sliceLocalPage(filteredMutexes.value, mutexPagination.value))

function handleMutexPageChange(page: number, pageSize: number) {
  updateLocalPage(mutexPagination.value, page, pageSize)
}

function handleMutexSearch() {
  mutexPagination.value.current = 1
}

function handleMutexReset() {
  mutexQuery.value.keyword = ''
  mutexPagination.value.current = 1
}

function openMutexModal() {
  mutexForm.value = { roleIdA: undefined, roleIdB: undefined }
  mutexModalVisible.value = true
}

async function submitMutex() {
  if (!mutexForm.value.roleIdA || !mutexForm.value.roleIdB) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createMutex({ roleIdA: mutexForm.value.roleIdA, roleIdB: mutexForm.value.roleIdB })
    message.success('创建成功')
    mutexModalVisible.value = false
    loadMutexes()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Prerequisite ───
const prerequisites = ref<RolePrerequisite[]>([])
const prerequisiteQuery = ref({ keyword: '' })
const prerequisitePagination = ref<LocalPaginationState>(createLocalPagination())
const prerequisiteLoading = ref(false)
const prerequisiteModalVisible = ref(false)
const prerequisiteForm = ref({ roleId: undefined as number | undefined, requiredRoleId: undefined as number | undefined })
const prerequisiteColumns = [
  { title: '角色ID', dataIndex: 'roleId', width: 120 },
  { title: '前置角色ID', dataIndex: 'requiredRoleId', width: 140 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const prerequisiteRowKey = (record: RolePrerequisite) => `${record.roleId}-${record.requiredRoleId}`

async function loadPrerequisites() {
  prerequisiteLoading.value = true
  try {
    const res = await listPrerequisites()
    prerequisites.value = Array.isArray(res) ? res : []
  } catch { prerequisites.value = [] }
  finally {
    normalizeLocalPagination(prerequisitePagination.value, prerequisites.value.length)
    prerequisiteLoading.value = false
  }
}

const filteredPrerequisites = computed(() =>
  prerequisites.value.filter((record) =>
    includesKeyword(prerequisiteQuery.value.keyword, record.roleId, record.requiredRoleId),
  ),
)
const pagedPrerequisites = computed(() => sliceLocalPage(filteredPrerequisites.value, prerequisitePagination.value))

function handlePrerequisitePageChange(page: number, pageSize: number) {
  updateLocalPage(prerequisitePagination.value, page, pageSize)
}

function handlePrerequisiteSearch() {
  prerequisitePagination.value.current = 1
}

function handlePrerequisiteReset() {
  prerequisiteQuery.value.keyword = ''
  prerequisitePagination.value.current = 1
}

function openPrerequisiteModal() {
  prerequisiteForm.value = { roleId: undefined, requiredRoleId: undefined }
  prerequisiteModalVisible.value = true
}

async function submitPrerequisite() {
  if (!prerequisiteForm.value.roleId || !prerequisiteForm.value.requiredRoleId) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createPrerequisite({ roleId: prerequisiteForm.value.roleId, requiredRoleId: prerequisiteForm.value.requiredRoleId })
    message.success('创建成功')
    prerequisiteModalVisible.value = false
    loadPrerequisites()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Cardinality ───
const cardinalities = ref<RoleCardinality[]>([])
const cardinalityQuery = ref({
  keyword: '',
  scopeType: undefined as string | undefined,
})
const cardinalityPagination = ref<LocalPaginationState>(createLocalPagination())
const cardinalityLoading = ref(false)
const cardinalityModalVisible = ref(false)
const scopeTypeOptions = ['TENANT', 'ORG', 'DEPT']
const cardinalityForm = ref({
  roleId: undefined as number | undefined,
  scopeType: 'TENANT',
  maxAssignments: undefined as number | undefined,
})
const cardinalityColumns = [
  { title: '角色ID', dataIndex: 'roleId', width: 120 },
  { title: '作用域类型', dataIndex: 'scopeType', width: 140 },
  { title: '最大分配数', dataIndex: 'maxAssignments', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const cardinalityRowKey = (record: RoleCardinality) => `${record.roleId}-${record.scopeType}`

async function loadCardinalities() {
  cardinalityLoading.value = true
  try {
    const res = await listCardinalities()
    cardinalities.value = Array.isArray(res) ? res : []
  } catch { cardinalities.value = [] }
  finally {
    normalizeLocalPagination(cardinalityPagination.value, cardinalities.value.length)
    cardinalityLoading.value = false
  }
}

const filteredCardinalities = computed(() =>
  cardinalities.value.filter((record) => {
    if (cardinalityQuery.value.scopeType && record.scopeType !== cardinalityQuery.value.scopeType) {
      return false
    }
    return includesKeyword(cardinalityQuery.value.keyword, record.roleId, record.scopeType, record.maxAssignments)
  }),
)
const pagedCardinalities = computed(() => sliceLocalPage(filteredCardinalities.value, cardinalityPagination.value))

function handleCardinalityPageChange(page: number, pageSize: number) {
  updateLocalPage(cardinalityPagination.value, page, pageSize)
}

function handleCardinalitySearch() {
  cardinalityPagination.value.current = 1
}

function handleCardinalityReset() {
  cardinalityQuery.value.keyword = ''
  cardinalityQuery.value.scopeType = undefined
  cardinalityPagination.value.current = 1
}

function openCardinalityModal() {
  cardinalityForm.value = { roleId: undefined, scopeType: 'TENANT', maxAssignments: undefined }
  cardinalityModalVisible.value = true
}

async function submitCardinality() {
  if (!cardinalityForm.value.roleId || !cardinalityForm.value.scopeType || !cardinalityForm.value.maxAssignments) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createCardinality({
      roleId: cardinalityForm.value.roleId,
      scopeType: cardinalityForm.value.scopeType,
      maxAssignments: cardinalityForm.value.maxAssignments,
    })
    message.success('创建成功')
    cardinalityModalVisible.value = false
    loadCardinalities()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Violations ───
const violations = ref<RoleViolation[]>([])
const violationQuery = ref({
  keyword: '',
  violationType: '',
})
const violationLoading = ref(false)
const violationPagination = ref({ current: 1, pageSize: 10, total: 0 })
const violationColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '违规类型', dataIndex: 'violationType', width: 160 },
  { title: '违规编码', dataIndex: 'violationCode', width: 200 },
  { title: '主体类型', dataIndex: 'principalType', width: 120 },
  { title: '主体ID', dataIndex: 'principalId', width: 100 },
  { title: '作用域类型', dataIndex: 'scopeType', width: 120 },
  { title: '作用域ID', dataIndex: 'scopeId', width: 100 },
  { title: '详情', dataIndex: 'details', width: 280 },
  { title: '时间', dataIndex: 'createdAt', width: 180 },
]
const filteredViolations = computed(() =>
  violations.value.filter((record) => {
    const matchesType = includesKeyword(violationQuery.value.violationType, record.violationType)
    if (!matchesType) {
      return false
    }
    return includesKeyword(
      violationQuery.value.keyword,
      record.violationCode,
      record.violationType,
      record.principalType,
      record.principalId,
      record.scopeType,
      record.scopeId,
      record.details,
      record.directRoleIds,
      record.effectiveRoleIds,
    )
  }),
)

async function loadViolations() {
  violationLoading.value = true
  try {
    const res = await listViolations({
      page: violationPagination.value.current - 1,
      size: violationPagination.value.pageSize,
    })
    violations.value = Array.isArray(res.content) ? res.content : []
    violationPagination.value.total = Number(res.totalElements) || 0
  } catch {
    violations.value = []
    violationPagination.value.total = 0
  }
  finally { violationLoading.value = false }
}

function handleViolationPageChange(page: number) {
  violationPagination.value.current = page
  loadViolations()
}

function handleViolationPageSizeChange(_current: number, size: number) {
  violationPagination.value.pageSize = size
  violationPagination.value.current = 1
  loadViolations()
}

function handleViolationSearch() {
  violationPagination.value.current = 1
  loadViolations()
}

function handleViolationReset() {
  violationQuery.value.keyword = ''
  violationQuery.value.violationType = ''
  violationPagination.value.current = 1
  loadViolations()
}

// ─── Shared ───
type ConstraintType = 'hierarchy' | 'mutex' | 'prerequisite' | 'cardinality'
const deleteActions: Record<ConstraintType, (record: any) => Promise<any>> = {
  hierarchy: (record: RoleHierarchy) => deleteHierarchy({
    childRoleId: record.childRoleId,
    parentRoleId: record.parentRoleId,
  }),
  mutex: (record: RoleMutex) => deleteMutex({
    roleIdA: record.leftRoleId,
    roleIdB: record.rightRoleId,
  }),
  prerequisite: (record: RolePrerequisite) => deletePrerequisite({
    roleId: record.roleId,
    requiredRoleId: record.requiredRoleId,
  }),
  cardinality: (record: RoleCardinality) => deleteCardinality({
    roleId: record.roleId,
    scopeType: record.scopeType,
  }),
}
const loadActions: Record<ConstraintType, () => void> = {
  hierarchy: loadHierarchies,
  mutex: loadMutexes,
  prerequisite: loadPrerequisites,
  cardinality: loadCardinalities,
}

function confirmDelete(type: ConstraintType, record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除该${typeLabel(type)}规则吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteActions[type](record).then(() => {
        message.success('删除成功')
        loadActions[type]()
      }).catch((e: any) => {
        message.error('删除失败: ' + (e.message || '未知错误'))
        return Promise.reject(e)
      })
    },
  })
}

function typeLabel(type: ConstraintType): string {
  const labels: Record<ConstraintType, string> = { hierarchy: '角色继承', mutex: '互斥约束', prerequisite: '先决条件', cardinality: '基数限制' }
  return labels[type]
}

function handleTabChange(key: Key) {
  if (String(key) === 'violations' && !canViewViolations.value) {
    activeTab.value = 'hierarchy'
    return
  }
  activeTab.value = String(key)
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    return new Date(dateTime).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  } catch {
    return '-'
  }
}

function activateCurrentTab() {
  if (!canView.value && !canViewViolations.value) {
    scheduleTableBodyHeightUpdate()
    return
  }

  const loaders: Record<string, () => void> = {
    hierarchy: loadHierarchies,
    mutex: loadMutexes,
    prerequisite: loadPrerequisites,
    cardinality: loadCardinalities,
    violations: () => {
      if (canViewViolations.value) {
        loadViolations()
      }
    },
  }

  loaders[activeTab.value]?.()
  scheduleTableBodyHeightUpdate()
}

watch(
  () => canView.value,
  (nextCanView) => {
    if (nextCanView) {
      void loadRoleOptions()
      activateCurrentTab()
      return
    }
    roleOptions.value = []
  },
  { immediate: true },
)

watch(
  () => canViewViolations.value,
  () => {
    if (activeTab.value === 'violations') {
      activateCurrentTab()
    }
  },
)

watch(
  () => activeTab.value,
  () => {
    activateCurrentTab()
  },
)

onMounted(() => {
  scheduleTableBodyHeightUpdate()
  window.addEventListener('resize', updateTableBodyHeight)
})

onActivated(() => {
  activateCurrentTab()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(
  () => [
    activeTab.value,
    filteredHierarchies.value.length,
    filteredMutexes.value.length,
    filteredPrerequisites.value.length,
    filteredCardinalities.value.length,
    hierarchyPagination.value.pageSize,
    mutexPagination.value.pageSize,
    prerequisitePagination.value.pageSize,
    cardinalityPagination.value.pageSize,
    violationPagination.value.pageSize,
  ],
  () => {
    normalizeLocalPagination(hierarchyPagination.value, filteredHierarchies.value.length)
    normalizeLocalPagination(mutexPagination.value, filteredMutexes.value.length)
    normalizeLocalPagination(prerequisitePagination.value, filteredPrerequisites.value.length)
    normalizeLocalPagination(cardinalityPagination.value, filteredCardinalities.value.length)
    scheduleTableBodyHeightUpdate()
  },
)
</script>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.content-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.platform-page-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.platform-guard-card {
  margin: 24px;
  min-height: 420px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 40px 32px;
  border: 1px dashed #d0d7e2;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(22, 119, 255, 0.08), transparent 45%),
    linear-gradient(180deg, #fafcff 0%, #f5f7fb 100%);
}

.platform-guard-kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}

.platform-guard-card h3 {
  margin: 0;
  font-size: 24px;
  color: #1f2937;
}

.platform-guard-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: #4b5563;
}

.boundary-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
}

:deep(.boundary-tabs > .ant-tabs-nav) {
  margin-bottom: 0;
  padding: 0 24px;
}

:deep(.boundary-tabs > .ant-tabs-nav::before) {
  border-bottom-color: #e5e7eb;
}

:deep(.boundary-tabs > .ant-tabs-nav .ant-tabs-tab) {
  padding: 10px 4px 14px;
  font-weight: 600;
}

:deep(.boundary-tabs > .ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-content) {
  height: 100%;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-tabpane) {
  height: 100%;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-tabpane-active) {
  display: flex;
  flex-direction: column;
}

:deep(.boundary-tabs .ant-tabs-tabpane-hidden) {
  display: none !important;
}

.tab-workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ml-2 {
  margin-left: 8px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 8px;
  transition: color 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

.table-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.table-scroll-container {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 24px;
}

.pagination-container {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  min-height: 56px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

.pagination-spacer {
  min-height: 32px;
}

@media (max-width: 960px) {
  :deep(.boundary-tabs > .ant-tabs-nav) {
    padding: 0 16px;
  }

  .platform-guard-card {
    margin: 16px;
    min-height: 320px;
    padding: 28px 20px;
  }

  .toolbar-container,
  .form-container,
  .table-scroll-container,
  .pagination-container {
    padding-left: 16px;
    padding-right: 16px;
  }

  .toolbar-container,
  .pagination-container {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
