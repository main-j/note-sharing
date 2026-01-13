<template>
  <div class="form-container">
    <h2>创建账号</h2>

    <div class="input-group">
      <label>用户名</label>
      <input v-model="username" type="text" placeholder="输入用户名" />
    </div>

    <div class="input-group">
      <label>邮箱</label>
      <input v-model="email" type="email" placeholder="输入邮箱" />
    </div>

    <div class="input-group">
      <label>学号</label>
      <input v-model="studentNumber" type="text" placeholder="输入学号" />
    </div>

    <div class="input-group">
      <label>密码</label>
      <input v-model="password" type="password" placeholder="输入密码" />
    </div>

    <div class="input-group">
      <label>确认密码</label>
      <input v-model="confirmPassword" type="password" placeholder="再次输入密码" />
    </div>

    <div class="input-group verification-group">
      <label>验证码</label>
      <div class="input-with-button">
        <input v-model="verificationCode" type="text" placeholder="输入6位验证码" />
        <button
            @click="sendCode"
            :disabled="isCodeButtonDisabled"
            class="btn-code"
        >
          {{ codeButtonText }}
        </button>
      </div>
    </div>

    <button class="btn" @click="register">注册</button>

    <div class="links">
      <router-link to="/login">已有账号？去登录</router-link>
    </div>

    <!-- 消息提示组件 -->
    <MessageToast
      v-if="showToast"
      :message="toastMessage"
      :type="toastType"
      :redirect-to="toastRedirect"
      :duration="toastRedirect ? 2000 : 1500"
      :auto-close="true"
      @close="showToast = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import api from "../api/request";
import MessageToast from "./MessageToast.vue";

const router = useRouter();
const userStore = useUserStore();

const username = ref("");
const email = ref("");
const studentNumber = ref("");
const password = ref("");
const confirmPassword = ref("");
const verificationCode = ref("");

// 倒计时逻辑
const countdown = ref(0);
const isSending = ref(false);
let timer = null;

// 消息提示相关
const showToast = ref(false);
const toastMessage = ref("");
const toastType = ref("success");
const toastRedirect = ref(null);

// 显示消息提示
const showMessage = (message, type = "success", redirectTo = null) => {
  toastMessage.value = message;
  toastType.value = type;
  toastRedirect.value = redirectTo;
  showToast.value = true;
};

const isCodeButtonDisabled = computed(() => countdown.value > 0 || isSending.value || !email.value);
const codeButtonText = computed(() => {
  if (isSending.value) return "发送中...";
  if (countdown.value > 0) return `${countdown.value}s 后重发`;
  return "获取验证码";
});

const sendCode = async () => {
  if (!email.value) {
    showMessage("请输入邮箱！", "error");
    return;
  }
  isSending.value = true;
  try {
    await api.post("/auth/register/send-code", { email: email.value });
    showMessage("验证码已发送到邮箱", "success");
    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        clearInterval(timer);
        timer = null;
      }
    }, 1000);
  } catch (e) {
    showMessage("发送失败：" + (e.response?.data?.error || e.message), "error");
  } finally {
    isSending.value = false;
  }
};

// 辅助函数：从 JWT token 中解码 payload
function decodeJwtPayload(token) {
  if (!token) return null;
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    console.error('JWT 解码失败:', e);
    return null;
  }
}

// 根据 token 中的角色决定跳转路径
function getRedirectPathByToken(token) {
  const payload = decodeJwtPayload(token);
  if (payload && payload.role === 'Admin') {
    return '/admin/main';
  }
  return '/main';
}

const register = async () => {
  if (!username.value || !email.value || !studentNumber.value || !password.value || !verificationCode.value) {
    showMessage("请填写完整信息！", "error");
    return;
  }
  if (password.value !== confirmPassword.value) {
    showMessage("两次密码不一致！", "error");
    return;
  }
  try {
    // 1. 先完成注册
    await api.post("/auth/register", {
      username: username.value,
      studentNumber: studentNumber.value,
      email: email.value,
      password: password.value,
      code: verificationCode.value,
    });
    
    // 2. 注册成功后，自动登录获取 token
    try {
      const loginRes = await api.post("/auth/login", {
        email: email.value,
        password: password.value,
      });
      
      const token = loginRes.data?.token || loginRes.data;
      
      if (token) {
        // 3. 保存 token 到 store 和 localStorage
        userStore.decodeAndSetToken(token);
        
        // 4. 根据 token 中的角色决定跳转路径
        const payload = decodeJwtPayload(token);
        const redirectPath = payload && payload.role === 'Admin' ? '/admin/main' : '/main';
        
        showMessage("注册成功！正在跳转...", "success", redirectPath);
      } else {
        // 如果没有获取到 token，默认跳转到用户登录页
        showMessage("注册成功！", "success", "/login");
      }
    } catch (loginError) {
      // 自动登录失败，但注册成功，跳转到用户登录页
      console.warn("自动登录失败，但注册成功:", loginError);
      showMessage("注册成功！请手动登录", "success", "/login");
    }
  } catch (e) {
    showMessage("注册失败：" + (e.response?.data?.error || e.message), "error");
  }
};

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.form-container {
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.input-group {
  margin-bottom: 15px;
}
.input-group label {
  font-size: 14px;
  margin-bottom: 4px;
  display: block;
}
.input-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 15px;
}
.input-with-button {
  display: flex;
  gap: 8px;
}
.btn-code {
  flex-shrink: 0;
  padding: 10px 15px;
  border: none;
  background-color: #4e54c8;
  color: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
}
.btn-code:disabled {
  background-color: #999;
  cursor: not-allowed;
}
.btn {
  padding: 12px;
  margin-top: 10px;
  border: none;
  background-color: #4e54c8;
  color: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
}
.btn:hover {
  background-color: #3c40a8;
}
.links {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}
.links a {
  color: #4e54c8;
  font-size: 14px;
}
</style>
