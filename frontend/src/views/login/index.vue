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
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";

interface LoginForm {
  username: string;
  password: string;
}

const router = useRouter();
const loginFormRef = ref<FormInstance>();
const loading = ref(false);

const loginForm = reactive<LoginForm>({
  username: "",
  password: "",
});

const loginRules = reactive<FormRules<LoginForm>>({
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
});

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  const valid = await loginFormRef.value.validate();
  if (!valid) return;

  loading.value = true;

  try {
    // 调用真实API登录
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: loginForm.username,
        password: loginForm.password,
      }),
    });

    if (!response.ok) {
      throw new Error("登录失败");
    }

    const data = await response.json();

    // 存储用户信息和token
    localStorage.setItem("token", data.token || "demo-token");
    localStorage.setItem(
      "userInfo",
      JSON.stringify(
        data.user || {
          username: loginForm.username,
          role: "user",
        },
      ),
    );

    ElMessage.success("登录成功");
    router.push("/dashboard");
  } catch (error) {
    console.error("登录错误:", error);
    ElMessage.error("登录失败，请检查用户名和密码");
  } finally {
    loading.value = false;
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
}
</style>
