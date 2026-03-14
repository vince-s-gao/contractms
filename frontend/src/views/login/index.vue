<template>
  <div class="login-container">
    <div class="login-form">
      <div class="login-header">
        <h1>合同管理系统</h1>
        <p>欢迎登录</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        class="form-content"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button text class="register-link" @click="registerDialogVisible = true">
            没有账号？去注册
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-dialog
      v-model="registerDialogVisible"
      title="用户名注册"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-width="90px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="4-20位字母/数字/下划线"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="8-32位，需包含字母和数字"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="registerLoading" @click="handleRegister">
          注册
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { login as loginApi, register as registerApi } from "@/api/user";
import { useUserStore } from "@/stores/user";
import { extractErrorMessage } from "@/utils/error";

interface LoginForm {
  username: string;
  password: string;
}

interface RegisterForm {
  username: string;
  password: string;
  confirmPassword: string;
}

const router = useRouter();
const userStore = useUserStore();
const loginFormRef = ref<FormInstance>();
const registerFormRef = ref<FormInstance>();
const loading = ref(false);
const registerLoading = ref(false);
const registerDialogVisible = ref(false);

onMounted(() => {
  userStore.clearUserInfo();
});

const loginForm = reactive<LoginForm>({
  username: "",
  password: "",
});
const registerForm = reactive<RegisterForm>({
  username: "",
  password: "",
  confirmPassword: "",
});

const loginRules = reactive<FormRules<LoginForm>>({
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
});
const registerRules = reactive<FormRules<RegisterForm>>({
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_]{4,20}$/,
      message: "用户名仅支持4-20位字母、数字、下划线",
      trigger: "blur",
    },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d_\-!@#$%^&*().,?]{8,32}$/,
      message: "密码需8-32位且包含字母和数字",
      trigger: "blur",
    },
  ],
  confirmPassword: [
    { required: true, message: "请确认密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error("两次输入密码不一致"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
});

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  const valid = await loginFormRef.value.validate();
  if (!valid) return;

  loading.value = true;

  try {
    const data = await loginApi({
      username: loginForm.username,
      password: loginForm.password,
    });
    if (!data?.token) {
      throw new Error("登录响应缺少令牌");
    }

    userStore.setToken(data.token);
    const userInfo = data.user || {
      username: loginForm.username,
      role: "ROLE_USER",
    };
    userStore.setUserInfo(userInfo);

    ElMessage.success("登录成功");
    router.push("/dashboard");
  } catch (error) {
    console.error("登录错误:", error);
    ElMessage.error(extractErrorMessage(error, "登录失败，请检查用户名和密码"));
  } finally {
    loading.value = false;
  }
};

const handleRegister = async () => {
  if (!registerFormRef.value) return;
  const valid = await registerFormRef.value.validate();
  if (!valid) return;

  registerLoading.value = true;
  try {
    await registerApi({
      username: registerForm.username.trim().toLowerCase(),
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
    });
    ElMessage.success("注册成功，请登录");
    registerDialogVisible.value = false;
    loginForm.username = registerForm.username.trim().toLowerCase();
    registerForm.username = "";
    registerForm.password = "";
    registerForm.confirmPassword = "";
  } catch (error) {
    console.error("注册失败:", error);
    ElMessage.error(extractErrorMessage(error, "注册失败"));
  } finally {
    registerLoading.value = false;
  }
};
</script>

<style lang="scss" scoped>
.login-container {
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-form {
  width: 400px;
  background: white;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;

  h1 {
    color: #333;
    margin-bottom: 10px;
    font-size: 24px;
  }

  p {
    color: #666;
    font-size: 14px;
  }
}

.form-content {
  .login-btn {
    width: 100%;
    margin-top: 10px;
  }

  .register-link {
    width: 100%;
    justify-content: center;
  }
}
</style>
