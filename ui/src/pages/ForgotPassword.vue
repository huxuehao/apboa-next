<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="ForgotPasswordPage">
/**
 * 忘记密码页面
 *
 * @author huxuehao
 */
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { AuthContainer } from '@/components/auth'

interface ForgotPasswordForm {
  email: string
  verificationCode: string
  newPassword: string
  confirmPassword: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const countdown = ref(0)
const timer = ref<number | null>(null)

const formState = reactive<ForgotPasswordForm>({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: Rule, value: string) => {
  if (value === '') {
    return Promise.reject('请再次输入密码')
  } else if (value !== formState.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  } else {
    return Promise.resolve()
  }
}

const rules: Record<string, Rule[]> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

const canSendCode = computed(() => {
  return countdown.value === 0 && formState.email !== ''
})

const sendCodeButtonText = computed(() => {
  return countdown.value > 0 ? `${countdown.value}秒后重试` : '发送验证码'
})

const handleSendCode = async () => {
  try {
    await formRef.value?.validateFields(['email'])

    // UI交互：开始倒计时60秒
    countdown.value = 60
    timer.value = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer.value) {
        clearInterval(timer.value)
        timer.value = null
      }
    }, 1000)

    message.success('验证码已发送至邮箱（UI占位）')
  } catch (error) {
    console.error('发送验证码失败:', error)
  }
}

const handleResetPassword = async () => {
  try {
    loading.value = true
    await formRef.value?.validate()

    // 仅UI占位，不对接实际业务
    message.info('功能开发中，敬请期待')
  } catch (error) {
    console.error('重置密码失败:', error)
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/login')
}

</script>

<template>
  <AuthContainer :show-back="true" back-to="/login">
    <h2 class="auth-card-title">重置密码</h2>
    <p class="auth-card-subtitle">通过邮箱验证重置你的密码</p>
    <AForm
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
      @finish="handleResetPassword"
    >
      <div class="auth-form-label">邮箱</div>
      <AFormItem name="email" class="auth-form-item">
        <AInput
          v-model:value="formState.email"
          size="large"
          placeholder="请输入邮箱"
        />
      </AFormItem>

      <div class="auth-form-label">验证码</div>
      <AFormItem name="verificationCode" class="auth-form-item">
        <div class="code-input-row">
          <AInput
            v-model:value="formState.verificationCode"
            size="large"
            placeholder="请输入验证码"
            style="flex: 1"
          />
          <AButton
            size="large"
            :disabled="!canSendCode"
            @click="handleSendCode"
            class="code-send-btn"
          >
            {{ sendCodeButtonText }}
          </AButton>
        </div>
      </AFormItem>

      <div class="auth-form-label">新密码</div>
      <AFormItem name="newPassword" class="auth-form-item">
        <AInputPassword
          v-model:value="formState.newPassword"
          size="large"
          placeholder="请输入新密码"
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

      <AFormItem>
        <AButton
          type="primary"
          html-type="submit"
          size="large"
          :loading="loading"
          class="auth-submit-btn"
        >
          重置密码
        </AButton>
      </AFormItem>
    </AForm>

    <div class="auth-footer-link">
      <a @click="goToLogin">返回登录</a>
    </div>
  </AuthContainer>
</template>

<style scoped lang="scss">
@use '@/styles/modules/auth' as *;

.forgot-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #999;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.2s ease;
  user-select: none;

  &:hover {
    color: #4F6EF7;
  }

  .anticon {
    font-size: 12px;
  }
}

.code-input-row {
  display: flex;
  gap: 10px;
}

.code-send-btn {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
