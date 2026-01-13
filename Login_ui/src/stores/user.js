// src/stores/user.js

import { defineStore } from 'pinia';

// 辅助函数：安全地解码 JWT 的 Payload (从上次回答中复制过来)
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


export const useUserStore = defineStore('user', {
    state: () => ({
        // 集中管理用户信息和 token
        userInfo: {
            id: null,
            username: null,
            email: null,
            studentNumber: null, // 与后端字段名保持一致
            avatarUrl: null, // 用户头像URL
            role: null, // 用户角色 (Admin/User)
            token: localStorage.getItem('token') || null, // 保存 Token
        },
        isAuthenticated: !!localStorage.getItem('token'), // 根据 token 判断是否登录
        loginType: localStorage.getItem('loginType') || null, // 记录登录入口：'user' 或 'admin'
    }),

    getters: {
        // 方便组件访问用户信息
        getCurrentUser: (state) => state.userInfo,
        // 检查登录状态
        isLoggedIn: (state) => state.isAuthenticated && !!state.userInfo.token,
        // 检查是否为管理员
        isAdmin: (state) => state.userInfo.role === 'Admin',
    },

    actions: {

        /**
         * 【推荐】从 Token 解码并设置用户数据
         * 用于登录成功后或应用初始化时
         * @param {string} token - JWT Token 字符串
         * @param {string} loginType - 登录入口类型：'user' 或 'admin'（可选）
         */
        decodeAndSetToken(token, loginType = null) {
            const payload = decodeJwtPayload(token);

            if (payload) {
                // 1. 设置 Token
                this.userInfo.token = token;
                localStorage.setItem('token', token);

                // 2. 提取 Payload 中的信息 (注意字段映射)
                this.userInfo.id = payload.userId || payload.id;
                this.userInfo.username = payload.sub || payload.username;
                this.userInfo.email = payload.email;
                this.userInfo.studentNumber = payload.studentNumber;
                this.userInfo.avatarUrl = payload.avatarUrl || null;
                this.userInfo.role = payload.role || 'User'; // 提取角色信息

                // 3. 设置登录入口类型（如果提供）
                if (loginType) {
                    this.loginType = loginType;
                    localStorage.setItem('loginType', loginType);
                }

                this.isAuthenticated = true;
                console.log('Pinia: Token 解析成功，用户数据已设置。角色:', this.userInfo.role, '登录入口:', this.loginType);
                return true;
            }
            this.clearUserData(); // 如果解码失败，则清除数据
            return false;
        },

        /**
         * 【可选】直接设置用户数据到 Store (例如：通过 /me 接口获取后调用)
         * @param {Object} user - 包含用户信息的对象 (来自 /me 接口)
         * @param {string} loginType - 登录入口类型：'user' 或 'admin'（可选）
         */
        setUserData(user, loginType = null) {
            // 确保使用后端 /me 接口返回的字段名
            this.userInfo.id = user.id;
            this.userInfo.username = user.username;
            this.userInfo.email = user.email;
            this.userInfo.studentNumber = user.studentNumber;
            this.userInfo.avatarUrl = user.avatarUrl || null;
            this.userInfo.role = user.role || 'User'; // 设置角色信息
            
            // 设置登录入口类型（如果提供）
            if (loginType) {
                this.loginType = loginType;
                localStorage.setItem('loginType', loginType);
            }
            
            this.isAuthenticated = true;

            console.log('Pinia: 用户数据已设置:', user.username, '角色:', this.userInfo.role, '登录入口:', this.loginType);
        },
        
        /**
         * 设置登录入口类型
         * @param {string} loginType - 'user' 或 'admin'
         */
        setLoginType(loginType) {
            this.loginType = loginType;
            localStorage.setItem('loginType', loginType);
            console.log('Pinia: 登录入口已设置:', loginType);
        },

        /**
         * 清除 Store 中的用户数据 (保留原函数名以匹配您组件中的调用)
         */
        clearUserData() {
            this.userInfo = {
                id: null,
                username: null,
                email: null,
                studentNumber: null,
                avatarUrl: null,
                role: null,
                token: null
            };
            this.isAuthenticated = false;
            this.loginType = null;
            localStorage.removeItem('token');
            localStorage.removeItem('loginType');
            console.log('Pinia: 用户数据已清除');
        },
    },
});