<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="LoginPage">
/**
 * 登录页面 — 支持多租户选择、审批等待状态
 *
 * @author huxuehao
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { LoginRequest, TenantInfo, PendingApprovalInfo } from '@/types'
import { AuthContainer } from '@/components/auth'
import { useAccountStore } from '@/stores'
import { md5 } from 'js-md5'
import { RoutePaths } from '@/router/constants.ts'
import {
  ExclamationCircleOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'

interface LoginForm extends LoginRequest {
  remember: boolean
}

const router = useRouter()
const accountStore = useAccountStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 当前登录方式 tab: account | sso */
const activeTab = ref<'account' | 'sso'>('account')

/** 是否显示租户选择弹窗 */
const showTenantModal = ref(false)
/** 待选择的租户列表 */
const pendingTenants = ref<TenantInfo[]>([])
/** 租户选择加载中 */
const selectingTenant = ref(false)

/** 登录被阻断 -- 待审批视图 */
const showBlockedView = ref(false)
/** 待审批的申请列表 */
const pendingApprovals = ref<PendingApprovalInfo[]>([])

const formState = reactive<LoginForm>({
  username: '',
  password: '',
  remember: false,
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

/**
 * 租户角色中文映射
 */
function tenantRoleLabel(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: '拥有者',
    TENANT_ADMIN: '管理员',
    TENANT_EDITOR: '编辑者',
    TENANT_VIEWER: '查看者'
  }
  return map[role] || role
}

/**
 * 租户角色颜色映射
 */
function tenantRoleColor(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: 'purple',
    TENANT_ADMIN: 'blue',
    TENANT_EDITOR: 'cyan',
    TENANT_VIEWER: 'default',
  }
  return map[role] || '#8c8c8c'
}

/**
 * 申请状态中文映射
 */
function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销'
  }
  return map[status] || status
}

const handleLogin = async () => {
  try {
    loading.value = true
    await formRef.value?.validate()

    const { remember, ...loginData } = formState
    const result = await accountStore.login({ ...loginData, password: md5(loginData.password) })

    if (result.blocked) {
      // 登录被阻断，显示待审批视图
      pendingApprovals.value = result.pendingApprovals || []
      showBlockedView.value = true
      return
    }

    if (result.needSelectTenant) {
      // 需要选择租户，显示选择弹窗
      pendingTenants.value = result.tenants
      showTenantModal.value = true
      return
    }

    // 无需选择租户，直接完成登录
    completeLogin(remember)
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 完成登录后的跳转
 */
function completeLogin(remember: boolean) {
  message.success('登录成功')
  if (remember) {
    localStorage.setItem('remember', 'true')
    localStorage.setItem('username', formState.username)
  } else {
    localStorage.removeItem('remember')
    localStorage.removeItem('username')
  }
  location.reload()
  setTimeout(() => {
    router.push(RoutePaths.AGENT)
  }, 100)
}

/**
 * 选择租户并完成登录（携带账号密码和租户ID重新登录）
 */
async function handleSelectTenant(tenant: TenantInfo) {
  try {
    selectingTenant.value = true
    const result = await accountStore.login({
      username: formState.username,
      password: md5(formState.password),
      tenantId: tenant.tenantId,
    })
    showTenantModal.value = false
    if (!result.blocked) {
      completeLogin(formState.remember)
    }
  } catch (error) {
    console.error('选择租户登录失败:', error)
    message.error('选择租户登录失败，请重试')
  } finally {
    selectingTenant.value = false
  }
}

/**
 * 返回登录表单
 */
function backToLogin() {
  showBlockedView.value = false
  showTenantModal.value = false
}

const goToRegister = () => {
  router.push(RoutePaths.REGISTER)
}

const goToForgotPassword = () => {
  router.push(RoutePaths.FORGOT_PASSWORD)
}
</script>

<template>
  <AuthContainer>
    <Transition name="auth-view" mode="out-in">
      <!-- 待审批阻断视图 -->
      <div v-if="showBlockedView" key="blocked" class="view-wrapper">
        <div class="blocked-container">
          <div class="blocked-header">
            <ExclamationCircleOutlined class="blocked-icon" />
            <h3 class="blocked-title">申请审批中</h3>
            <p class="blocked-desc">
              你已提交加入组织的申请，请等待管理员审批。
            </p>
          </div>

          <div class="blocked-list">
            <div
              v-for="approval in pendingApprovals"
              :key="approval.requestId"
              class="blocked-item"
            >
              <div class="blocked-item-info">
                <div class="blocked-item-name">{{ approval.tenantName }}</div>
                <div class="blocked-item-meta">
                  <span class="blocked-item-code">{{ approval.tenantCode }}</span>
                </div>
              </div>
              <ATag :bordered="false">{{ statusLabel(approval.status) }}</ATag>
            </div>
          </div>

          <div class="blocked-actions">
            <ADivider>或者</ADivider>
            <ASpace direction="vertical" style="width: 100%" :size="12">
              <AButton
                type="primary"
                block
                size="large"
                @click="goToRegister"
              >
                新开账号，自建组织
              </AButton>
              <AButton
                block
                size="large"
                @click="backToLogin"
              >
                返回登录
              </AButton>
            </ASpace>
          </div>
        </div>
      </div>

      <!-- 租户选择视图 -->
      <div v-else-if="showTenantModal" key="tenant" class="view-wrapper">
        <div class="tenant-select-view">
          <div class="tenant-select-back" @click="backToLogin">
            <LeftOutlined />
            <span>返回登录</span>
          </div>

          <div class="tenant-select-header">
            <h3 class="tenant-select-title">选择组织</h3>
            <p class="tenant-select-desc">您属于多个组织，请选择要进入的组织</p>
          </div>

          <div class="tenant-cards-scroll">
            <div class="tenant-cards-list">
              <div
                v-for="tenant in pendingTenants"
                :key="tenant.tenantId"
                class="tenant-card"
                :class="{ 'tenant-card-loading': selectingTenant }"
                @click="handleSelectTenant(tenant)"
              >
                <div class="tenant-card-avatar">
                  {{ tenant.tenantName.charAt(0).toUpperCase() }}
                </div>
                <div class="tenant-card-body">
                  <div class="tenant-card-name" :title="tenant.tenantName">{{ tenant.tenantName }}</div>
                  <div class="tenant-card-code" :title="tenant.tenantCode">{{ tenant.tenantCode }}</div>
                </div>
                <div class="tenant-card-right">
                  <ATag class="tenant-card-role" :bordered="false" :color="tenantRoleColor(tenant.role)">{{ tenantRoleLabel(tenant.role) }}</ATag>
                  <div class="tenant-card-arrow">
                    <RightOutlined />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 登录表单 -->
      <div v-else key="login" class="view-wrapper">
        <!-- 标题区 -->
        <div class="auth-card-header">
          <h2 class="auth-card-title">欢迎回来 👋</h2>
          <span class="auth-lang-select">
            <img src="@/assets/logo/logo_2.png" alt="APBOA·NEXT" width="90px"/>
          </span>
        </div>
        <p class="auth-card-subtitle">登录 Apboa Next 智能体平台</p>

        <AForm
          ref="formRef"
          :model="formState"
          :rules="rules"
          layout="vertical"
          @finish="handleLogin"
        >
          <div class="auth-form-label">账号 / 邮箱</div>
          <AFormItem name="username" class="auth-form-item">
            <AInput
              v-model:value="formState.username"
              size="large"
              placeholder="请输入账号或邮箱"
            />
          </AFormItem>

          <div class="auth-form-label">密码</div>
          <AFormItem name="password" class="auth-form-item">
            <AInputPassword
              v-model:value="formState.password"
              size="large"
              placeholder="请输入密码"
            />
          </AFormItem>

          <AFormItem class="auth-form-item">
            <div class="flex justify-between items-center">
              <ACheckbox v-model:checked="formState.remember">
                记住我
              </ACheckbox>
              <a @click="goToForgotPassword" style="color: #4F6EF7; font-size: 14px; cursor: pointer;">
                忘记密码?
              </a>
            </div>
          </AFormItem>

          <AFormItem>
            <AButton
              type="primary"
              html-type="submit"
              size="large"
              :loading="loading"
              class="auth-submit-btn"
            >
              登录
            </AButton>
          </AFormItem>
        </AForm>

        <!-- 仓库地址 -->
        <div class="auth-divider">Apboa Next 项目仓库地址</div>
        <div class="auth-social-login">
          <div class="auth-social-icon">
            <a href="https://github.com/huxuehao/apboa-next" target="_blank" rel="noopener noreferrer">
              <img src="https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/github.svg" alt="GitHub" width="24">
            </a>
          </div>
          <div class="auth-social-icon">
            <a href="https://gitee.com/studioustiger/apboa-next" target="_blank" rel="noopener noreferrer">
              <img src="https://gitee.com/static/images/logo-en.svg" alt="Gitee" width="24">
            </a>
          </div>
        </div>

        <!-- 注册链接 -->
        <div class="auth-footer-link">
          还没有账号？
          <a @click="goToRegister">立即注册</a>
        </div>
      </div>
    </Transition>
  </AuthContainer>
</template>

<style scoped lang="scss">
@use '@/styles/modules/auth' as *;

// ========== 视图切换过渡动画 ==========
.auth-view-enter-active,
.auth-view-leave-active {
  transition: opacity 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-view-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

.auth-view-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(0.98);
}

.view-wrapper {
  width: 100%;
}

// ========== SSO占位 ==========
.sso-placeholder {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sso-empty {
  text-align: center;
  color: #bbb;

  .sso-empty-icon {
    font-size: 48px;
    margin-bottom: 12px;
    display: block;
  }

  p {
    font-size: 14px;
    margin: 0;
  }
}

// ========== 阻断视图样式 ==========
.blocked-container {
  padding: 8px 0;
}

.blocked-header {
  text-align: center;
  margin-bottom: 24px;
}

.blocked-icon {
  font-size: 48px;
  color: var(--warning-color, #faad14);
  margin-bottom: 12px;
}

.blocked-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #1f1f1f);
  margin: 0 0 8px 0;
}

.blocked-desc {
  font-size: 14px;
  color: var(--text-secondary, #666);
  margin: 0;
  line-height: 1.6;
}

.blocked-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.blocked-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-secondary, #fafafa);
  border: 1px solid var(--border-color, #e8e8e8);
  border-radius: 8px;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--primary-color, #1677ff);
  }
}

.blocked-item-info {
  flex: 1;
  min-width: 0;
}

.blocked-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1f1f1f);
  margin-bottom: 4px;
}

.blocked-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.blocked-item-code {
  font-size: 12px;
  color: var(--text-tertiary, #999);
}

.blocked-actions {
  text-align: center;
}

// ========== 租户选择视图 ==========
.tenant-select-view {
  padding: 4px 0;
}

.tenant-select-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #999;
  cursor: pointer;
  padding: 6px 2px;
  margin-bottom: 20px;
  transition: color 0.25s ease;
  user-select: none;

  &:hover {
    color: #4F6EF7;
  }

  .anticon {
    font-size: 13px;
    transition: transform 0.25s ease;
  }

  &:hover .anticon {
    transform: translateX(-3px);
  }
}

.tenant-select-header {
  text-align: center;
  margin-bottom: 28px;
}

.tenant-select-title {
  font-size: 22px;
  font-weight: 600;
  color: #1f1f1f;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
}

.tenant-select-desc {
  font-size: 14px;
  color: #999;
  margin: 0;
  line-height: 1.5;
}

// 滚动容器
.tenant-cards-scroll {
  max-height: 380px;
  overflow-y: auto;
  padding-right: 6px;
  margin-right: -6px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 2px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
    transition: background 0.2s;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: #bbb;
  }
}

// Firefox 滚动条
.tenant-cards-scroll {
  scrollbar-width: thin;
  scrollbar-color: #d9d9d9 transparent;
}

.tenant-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

// 租户卡片
.tenant-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.2s ease;

  &:hover {
    border-color: #b8d4ff;

    .tenant-card-avatar {
      background: #4F6EF7;
      color: #fff;
    }

    .tenant-card-arrow {
      opacity: 1;
      transform: translateX(0);
    }

    .tenant-card-name {
      color: #4F6EF7;
    }
  }

  &:active {
    transition: all 0.1s ease;
  }

  &.tenant-card-loading {
    pointer-events: none;
    opacity: 0.7;
  }
}

.tenant-card-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #f0f4ff;
  color: #4F6EF7;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.tenant-card-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.tenant-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f1f1f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s ease;
  line-height: 1.4;
}

.tenant-card-code {
  font-size: 12px;
  color: #bbb;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.tenant-card-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.tenant-card-role {
  font-size: 11px;
  border-radius: 6px;
  padding: 0 8px;
  line-height: 22px;
}

.tenant-card-arrow {
  color: #4F6EF7;
  font-size: 12px;
  opacity: 0;
  transform: translateX(-8px);
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
}
</style>
