<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="RegisterPage">
/**
 * 注册页面 -- 步骤式引导：先选择组织归属方式，再填写个人信息
 *
 * @author huxuehao
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { RegisterRequest, TenantDiscoveryVO } from '@/types'
import { AuthContainer } from '@/components/auth'
import { useAccountStore } from '@/stores'
import { md5 } from 'js-md5'
import * as tenantApi from '@/api/tenant'
import { CheckCircleOutlined, AppstoreAddOutlined, UsergroupAddOutlined } from '@ant-design/icons-vue'

interface RegisterForm extends RegisterRequest {
  confirmPassword: string
  registerMode: 'create' | 'join'
}

const router = useRouter()
const accountStore = useAccountStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const joinLoading = ref(false)

/** 当前步骤索引：0-选择组织，1-填写信息 */
const currentStep = ref(0)

/** 可加入的组织列表 */
const discoverableTenants = ref<TenantDiscoveryVO[]>([])
/** 选中的要加入的组织 */
const selectedTenant = ref<TenantDiscoveryVO | null>(null)

const formState = reactive<RegisterForm>({
  nickname: '',
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  createTenant: false,
  tenantName: '',
  tenantCode: '',
  tenantDescription: '',
  registerMode: 'create',
  joinMessage: '',
})

const validateConfirmPassword = (_rule: Rule, value: string) => {
  if (value === '') {
    return Promise.reject('请再次输入密码')
  } else if (value !== formState.password) {
    return Promise.reject('两次输入的密码不一致')
  } else {
    return Promise.resolve()
  }
}

const validateTenantName = (_rule: Rule, value: string) => {
  if (formState.registerMode === 'create' && !value) {
    return Promise.reject('请输入组织名称')
  }
  return Promise.resolve()
}

const validateTenantCode = (_rule: Rule, value: string) => {
  if (formState.registerMode === 'create') {
    if (!value) {
      return Promise.reject('请输入组织编码')
    }
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(value)) {
      return Promise.reject('仅允许英文字母、数字、下划线，且数字不能开头')
    }
  }
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度为2-20个字符', trigger: 'blur' },
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  tenantName: [
    { validator: validateTenantName, trigger: 'blur' },
  ],
  tenantCode: [
    { validator: validateTenantCode, trigger: 'blur' },
  ],
}

/** 获取可加入的组织列表 */
async function fetchDiscoverableTenants() {
  try {
    joinLoading.value = true
    const res = await tenantApi.listPassAuthDiscoverable()
    discoverableTenants.value = res.data.data || []
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    joinLoading.value = false
  }
}

/** 选择要加入的组织 */
function selectTenant(tenant: TenantDiscoveryVO) {
  selectedTenant.value = tenant
}

/** 进入下一步 */
function nextStep() {
  if (formState.registerMode === 'create') {
    if (!formState.tenantName) {
      message.warning('请输入组织名称')
      return
    }
    if (!formState.tenantCode) {
      message.warning('请输入组织编码')
      return
    }
  } else {
    if (!selectedTenant.value) {
      message.warning('请选择要加入的组织')
      return
    }
  }
  currentStep.value = 1
}

/** 返回上一步 */
function prevStep() {
  currentStep.value = 0
}

const handleRegister = async () => {
  try {
    loading.value = true
    await formRef.value?.validate()

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { confirmPassword: _cp, registerMode, ...data } = formState

    const payload: RegisterRequest = {
      nickname: data.nickname,
      username: data.username,
      email: data.email,
      password: md5(data.password),
    }

    if (registerMode === 'create') {
      payload.createTenant = true
      payload.tenantName = data.tenantName
      payload.tenantCode = data.tenantCode
      payload.tenantDescription = data.tenantDescription
    } else {
      payload.joinTenantId = selectedTenant.value!.id
      payload.joinMessage = data.joinMessage || ''
    }

    await accountStore.register(payload)

    if (registerMode === 'create') {
      message.success('注册成功，组织已创建，请登录')
    } else {
      message.success('注册成功，请登录查看加入状态')
    }
    await router.push('/login')
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/login')
}

onMounted(() => {
  fetchDiscoverableTenants()
})
</script>

<template>
  <AuthContainer :show-back="true" back-to="/login">
    <div class="auth-card-header">
      <h2 class="auth-card-title">创建账号</h2>
    </div>
    <p class="auth-card-subtitle">注册 Apboa Next 智能体平台</p>

    <!-- 步骤指示器 -->
    <ASteps :current="currentStep" size="small" class="register-steps">
      <AStep title="选择组织" />
      <AStep title="填写信息" />
    </ASteps>

    <AForm
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
      @finish="handleRegister"
    >
      <!-- 步骤1：选择组织归属方式 -->
      <div v-show="currentStep === 0" class="step-content">
        <!-- 模式选择卡片 -->
        <div class="mode-cards">
          <div
            class="mode-card mode-create"
            :class="{ active: formState.registerMode === 'create' }"
            @click="formState.registerMode = 'create'"
          >
            <div class="mode-card-icon">
              <AppstoreAddOutlined />
            </div>
            <div class="mode-card-title">创建我的组织</div>
          </div>
          <div
            class="mode-card mode-join"
            :class="{ active: formState.registerMode === 'join' }"
            @click="formState.registerMode = 'join'"
          >
            <div class="mode-card-icon">
              <UsergroupAddOutlined />
            </div>
            <div class="mode-card-title">加入已有组织</div>
          </div>
        </div>

        <!-- 创建组织模式 -->
        <div v-if="formState.registerMode === 'create'" class="tenant-section">
          <AFormItem name="tenantName" class="auth-form-item">
            <AInput
              v-model:value="formState.tenantName"
              size="large"
              placeholder="请输入组织名称"
            />
          </AFormItem>
          <AFormItem name="tenantCode" class="auth-form-item">
            <AInput
              v-model:value="formState.tenantCode"
              size="large"
              placeholder="组织编码（英文/数字/下划线）"
            />
          </AFormItem>
          <AFormItem name="tenantDescription" class="auth-form-item">
            <ATextarea
              v-model:value="formState.tenantDescription"
              :rows="1"
              placeholder="组织描述（可选）"
            />
          </AFormItem>
        </div>

        <!-- 加入已有组织模式 -->
        <div v-if="formState.registerMode === 'join'" class="tenant-section join-wrapper">
          <div class="join-scroll-area">
            <div v-if="discoverableTenants.length === 0 && !joinLoading" class="empty-hint">
              暂无可加入的组织，你可以选择「创建我的组织」
            </div>
            <div class="tenant-select-list">
              <div
                v-for="tenant in discoverableTenants"
                :key="tenant.id"
                class="tenant-select-item"
                :class="{ active: selectedTenant?.id === tenant.id }"
                @click="selectTenant(tenant)"
              >
                <div class="tenant-select-info">
                  <div class="tenant-select-name">{{ tenant.name }}</div>
                  <div class="tenant-select-code">{{ tenant.code }}</div>
                </div>
                <div class="tenant-select-badge" v-if="selectedTenant?.id === tenant.id">
                  <CheckCircleOutlined />
                </div>
              </div>
            </div>
          </div>
        </div>

        <AButton type="primary" size="large" class="auth-submit-btn" @click="nextStep">
          下一步
        </AButton>
      </div>

      <!-- 步骤2：填写个人信息 -->
      <div v-show="currentStep === 1" class="step-content">
        <div class="auth-form-label">昵称</div>
        <AFormItem name="nickname" class="auth-form-item">
          <AInput
            v-model:value="formState.nickname"
            size="large"
            placeholder="请输入昵称"
          />
        </AFormItem>

        <div class="auth-form-label">用户名</div>
        <AFormItem name="username" class="auth-form-item">
          <AInput
            v-model:value="formState.username"
            size="large"
            placeholder="请输入用户名"
          />
        </AFormItem>

        <div class="auth-form-label">邮箱</div>
        <AFormItem name="email" class="auth-form-item">
          <AInput
            v-model:value="formState.email"
            size="large"
            placeholder="请输入邮箱"
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

        <div class="auth-form-label">确认密码</div>
        <AFormItem name="confirmPassword" class="auth-form-item">
          <AInputPassword
            v-model:value="formState.confirmPassword"
            size="large"
            placeholder="请再次输入密码"
          />
        </AFormItem>

        <div class="step-actions">
          <AButton size="large" @click="prevStep">上一步</AButton>
          <AButton
            type="primary"
            html-type="submit"
            size="large"
            :loading="loading"
            class="auth-submit-btn"
          >
            注册
          </AButton>
        </div>
      </div>
    </AForm>

    <div class="auth-footer-link">
      已有账号？
      <a @click="goToLogin">去登录</a>
    </div>
  </AuthContainer>
</template>

<style scoped lang="scss">
@use '@/styles/modules/auth' as *;

.register-steps {
  margin-bottom: 24px;
}

.step-content {
  min-height: 200px;
}

.step-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;

  .ant-btn {
    flex: 1;
  }
}

.mode-cards {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.mode-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 12px 16px;
  border: 1.5px solid var(--border-color, #e8e8e8);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
  background: #fff;
}

/* 创建组织 -- 蓝色主题 */
.mode-create {
  &:hover {
    border-color: #4F6EF7;
  }

  &.active {
    border-color: #4F6EF7;
    background: #f0f4ff;
  }
}

/* 加入组织 -- 绿色主题 */
.mode-join {
  &:hover {
    border-color: #52c41a;
  }

  &.active {
    border-color: #52c41a;
    background: #f6ffed;
  }
}

.mode-card-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--text-tertiary, #bbb);
  background: var(--bg-secondary, #f5f5f5);
  margin-bottom: 12px;
  transition: all 0.25s ease;
}

.mode-create .mode-card-icon {
  color: #4F6EF7;
  background: rgba(79, 110, 247, 0.08);
}

.mode-create.active .mode-card-icon {
  color: #fff;
  background: #4F6EF7;
}

.mode-join .mode-card-icon {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.08);
}

.mode-join.active .mode-card-icon {
  color: #fff;
  background: #52c41a;
}

.mode-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1f1f1f);
  margin-bottom: 6px;
  transition: color 0.25s ease;
}

.tenant-section {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px dashed var(--border-color, #d9d9d9);
  border-radius: 8px;
  background: var(--bg-secondary, #fafafa);
}

.join-wrapper {
  max-height: none;
  overflow: visible;
}

.join-scroll-area {
  max-height: 210px;
  overflow-y: auto;
}

.empty-hint {
  text-align: center;
  color: var(--text-tertiary, #999);
  font-size: 13px;
  padding: 24px 0;
}

.tenant-select-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tenant-select-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid var(--border-color, #e8e8e8);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fff;

  &:hover {
    border-color: #4F6EF7;
    background: #f0f4ff;
  }

  &.active {
    border-color: #4F6EF7;
    background: #eef2ff;
  }
}

.tenant-select-info {
  flex: 1;
  min-width: 0;
}

.tenant-select-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1f1f1f);
}

.tenant-select-code {
  font-size: 12px;
  color: var(--text-tertiary, #999);
  margin-top: 2px;
}

.tenant-select-badge {
  color: #4F6EF7;
  font-size: 18px;
  flex-shrink: 0;
  margin-left: 8px;
}
</style>
