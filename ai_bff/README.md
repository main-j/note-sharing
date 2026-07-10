# FOLIO AI BFF

独立的 AI BFF 与 Agent 运行时。

## 目标

- 只通过 Login_api 的公开接口读取内容
- 对外提供稳定的 AI 聊天、关键词抽取、相似问题提示
- 为 `Login_ui` 的 iframe 壳提供宿主页面

## 环境变量

- `AI_LOGIN_API_BASE_URL`：默认 `http://localhost:8080`
- `AI_MODEL_PROVIDER`：默认 `openai_compatible`
- `AI_MODEL_BASE_URL`：默认 `https://ark.cn-beijing.volces.com/api/coding/v3`
- `AI_MODEL_API_KEY`：默认留空，接入时填你的 API Key
- `AI_MODEL_NAME`：默认 `ark-code-latest`
- `AI_MODEL_TEMPERATURE`：默认 `0.3`
- `AI_MODEL_MAX_TOKENS`：默认 `1200`
- `AI_MODEL_TIMEOUT`：默认 `30`
- `AI_BFF_HOST`：默认 `0.0.0.0`
- `AI_BFF_PORT`：默认 `8003`（本地避开 Triton 8000）
- `AI_JWT_SECRET`：可选，用于验证 HS256 JWT
- `AI_ALLOWED_ORIGINS`：逗号分隔的前端来源，示例包含 `http://localhost:8080` 和 `http://localhost:8082`

如果 `ai_bff/.env` 存在，BFF 启动时会自动读取它，优先使用其中的配置值。

## 火山方舟接入示例

```env
AI_MODEL_PROVIDER=openai_compatible
AI_MODEL_BASE_URL=https://ark.cn-beijing.volces.com/api/coding/v3
AI_MODEL_API_KEY=
AI_MODEL_NAME=ark-code-latest
AI_MODEL_TEMPERATURE=0.3
AI_MODEL_MAX_TOKENS=1200
AI_MODEL_TIMEOUT=30
```

## 启动

**前置：** `Login_api` 已在 `http://localhost:8080` 运行。

1. 复制环境变量并填写密钥：

```powershell
copy ai_bff\.env.example ai_bff\.env
# 编辑 ai_bff/.env：
#   AI_MODEL_API_KEY=你的 DeepSeek/方舟 Key
#   AI_JWT_SECRET=与 Login_api application.yml 中 jwt.secret 相同
```

2. 默认端口 **8003**（避免与 Triton 的 8000 冲突）。前端默认也指向 `http://localhost:8003`。

3. 一键启动（推荐）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-ai-bff.ps1 -InstallDeps
```

或手动：

```bash
cd ai_bff
py -3 -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8003
```

4. 验证：

```powershell
Invoke-RestMethod http://localhost:8003/health
```

5. 前端（`Login_ui` 开发服默认 8082）需配置 BFF 地址（已提供 `.env.example`）：

```env
VUE_APP_AI_BFF_ORIGIN=http://localhost:8003
```

保存为 `Login_ui/.env.local` 后重启 `npm run serve`。
