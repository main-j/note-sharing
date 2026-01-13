# 笔记分享系统 API 文档

本文档包含项目后端所有 API 接口的详细说明。

**基础路径**: 所有 API 的基础路径为 `/api/v1`

---

## 目录

1. [认证与用户管理](#1-认证与用户管理-apiv1auth)
2. [管理端](#2-管理端-apiv1admin)
3. [笔记管理](#3-笔记管理-apiv1notingnotes)
4. [评论管理](#4-评论管理-apiv1remark)
5. [关注关系](#5-关注关系-apiv1followrelation)
6. [标签管理](#6-标签管理-apiv1notingtags)
7. [笔记本管理](#7-笔记本管理-apiv1notingnotebooks)
8. [笔记空间管理](#8-笔记空间管理-apiv1notingspaces)
9. [搜索](#9-搜索-apiv1search)
10. [问答系统](#10-问答系统-apiv1qa)
11. [热门内容](#11-热门内容-apiv1hot)
12. [推荐系统](#12-推荐系统-apiv1recommend)
13. [收藏管理](#13-收藏管理-apiv1favorites)
14. [通知管理](#14-通知管理-apiv1notifications)
15. [私信会话](#15-私信会话-apiv1conversation)
16. [笔记统计](#16-笔记统计-apiv1notingnote-stats)
17. [WebSocket 聊天](#17-websocket-聊天)

---

## 1. 认证与用户管理 (`/api/v1/auth`)

### 1.1 用户登录
- **路径**: `POST /api/v1/auth/login`
- **功能**: 用户登录
- **请求体**: `LoginRequest`
- **响应**: `{ "token": "..." }`

### 1.2 管理员登录
- **路径**: `POST /api/v1/auth/admin/login`
- **功能**: 管理员登录
- **请求体**: `LoginRequest`
- **响应**: `{ "token": "..." }`

### 1.3 发送注册验证码
- **路径**: `POST /api/v1/auth/register/send-code`
- **功能**: 发送注册邮箱验证码
- **请求体**: `{ "email": "user@example.com" }`
- **响应**: `{ "message": "验证码已发送" }`

### 1.4 用户注册
- **路径**: `POST /api/v1/auth/register`
- **功能**: 注册新用户
- **请求体**: `RegisterRequest`
- **响应**: `{ "message": "注册成功" }`

### 1.5 发送重置密码验证码
- **路径**: `POST /api/v1/auth/password/send-code`
- **功能**: 发送重置密码邮箱验证码
- **请求体**: `{ "email": "user@example.com" }`
- **响应**: `{ "message": "验证码已发送" }`

### 1.6 重置密码
- **路径**: `POST /api/v1/auth/password/reset`
- **功能**: 使用邮箱验证码重置密码
- **请求体**: `ResetPasswordRequest`
- **响应**: `{ "message": "密码修改成功" }`

### 1.7 获取当前用户信息
- **路径**: `GET /api/v1/auth/me`
- **功能**: 获取当前登录用户信息
- **请求头**: `Authorization: Bearer {token}`
- **响应**: 用户信息对象

### 1.8 上传用户头像
- **路径**: `POST /api/v1/auth/avatar`
- **功能**: 上传用户头像
- **请求头**: `Authorization: Bearer {token}`
- **请求体**: `multipart/form-data` (file)
- **响应**: `{ "message": "头像上传成功", "avatarUrl": "..." }`

### 1.9 根据用户名获取用户信息
- **路径**: `GET /api/v1/auth/user/by-username?username={username}`
- **功能**: 根据用户名获取用户信息
- **参数**: `username` (查询参数)
- **响应**: 用户信息对象

### 1.10 根据用户ID获取用户信息
- **路径**: `GET /api/v1/auth/user/by-id?userId={userId}`
- **功能**: 根据用户ID获取用户信息
- **参数**: `userId` (查询参数)
- **响应**: 用户信息对象

### 1.11 更新用户名
- **路径**: `PUT /api/v1/auth/username`
- **功能**: 更新用户名
- **请求头**: `Authorization: Bearer {token}`
- **请求体**: `{ "username": "newUsername" }`
- **响应**: `{ "message": "用户名修改成功" }`

---

## 2. 管理端 (`/api/v1/admin`)

### 2.1 在线用户管理

#### 获取当前在线人数
- **路径**: `GET /api/v1/admin/online-count`
- **功能**: 获取当前在线用户数量
- **响应**: `{ "onlineCount": 10 }`

#### 获取所有在线用户
- **路径**: `GET /api/v1/admin/online-users`
- **功能**: 获取当前所有在线用户的详细信息
- **响应**: 在线用户列表

### 2.2 笔记管理

#### 统计笔记总数
- **路径**: `GET /api/v1/admin/notes/count`
- **功能**: 获取系统中笔记的总数
- **响应**: `{ "noteCount": 1000 }`

#### 获取所有笔记列表
- **路径**: `GET /api/v1/admin/notes/list`
- **功能**: 获取所有笔记列表（按数据库顺序）
- **响应**: 笔记列表

### 2.3 评论管理

#### 统计评论总数
- **路径**: `GET /api/v1/admin/remarks/count`
- **功能**: 获取系统中评论的总数
- **响应**: `{ "remarkCount": 5000 }`

#### 获取所有评论列表
- **路径**: `GET /api/v1/admin/remarks/list`
- **功能**: 获取所有评论列表（按数据库顺序）
- **响应**: 评论列表

### 2.4 敏感词检查

#### 检查纯文本敏感词
- **路径**: `POST /api/v1/admin/sensitive/check/text`
- **功能**: 检查纯文本中的敏感词
- **请求体**: `SensitiveTextCheckRequest`
- **响应**: `SensitiveCheckResult`

#### 检查笔记敏感词（快速模式）
- **路径**: `GET /api/v1/admin/sensitive/check/note/{noteId}`
- **功能**: 使用摘要快速检查笔记敏感词
- **参数**: `noteId` (路径参数)
- **响应**: `SensitiveCheckResult`

#### 检查笔记敏感词（全文模式）
- **路径**: `GET /api/v1/admin/sensitive/check/note/{noteId}/full`
- **功能**: 全文模式检查笔记敏感词
- **参数**: `noteId` (路径参数)
- **响应**: `SensitiveCheckResult`

#### 批量检查笔记敏感词
- **路径**: `POST /api/v1/admin/sensitive/check/batch`
- **功能**: 批量检查多个笔记的敏感词
- **请求体**: `SensitiveBatchCheckRequest`
- **响应**: 批量检查结果

### 2.5 敏感词过滤

#### 快速过滤检查文本
- **路径**: `POST /api/v1/admin/sensitive/fast-filter`
- **功能**: 使用Trie树快速过滤检查文本
- **请求体**: `{ "text": "待检查文本" }`
- **响应**: 过滤结果

#### 重新加载敏感词库
- **路径**: `POST /api/v1/admin/sensitive/reload`
- **功能**: 重新加载快速过滤敏感词库
- **响应**: `{ "message": "敏感词库已重新加载" }`

#### 深度检查文本
- **路径**: `POST /api/v1/admin/sensitive/deep-check`
- **功能**: 使用keywords.txt完整词库进行深度检查
- **请求体**: `{ "text": "待检查文本" }`
- **响应**: 深度检查结果

#### 重新加载深度检查词库
- **路径**: `POST /api/v1/admin/sensitive/deep-reload`
- **功能**: 重新加载深度检查词库（keywords.txt）
- **响应**: `{ "message": "深度检查词库已重新加载" }`

### 2.6 内容审查管理

#### 获取待处理的审查记录列表
- **路径**: `GET /api/v1/admin/moderation/pending`
- **功能**: 获取所有待处理的审查记录
- **响应**: 审查记录列表

#### 获取审查记录详情
- **路径**: `GET /api/v1/admin/moderation/{id}`
- **功能**: 根据ID获取审查记录详情
- **参数**: `id` (路径参数)
- **响应**: 审查记录详情

#### 获取笔记的审查历史
- **路径**: `GET /api/v1/admin/moderation/note/{noteId}`
- **功能**: 获取指定笔记的所有审查历史记录
- **参数**: `noteId` (路径参数)
- **响应**: 审查历史列表

#### 处理审查记录
- **路径**: `POST /api/v1/admin/moderation/{id}/handle`
- **功能**: 标记审查记录为已处理并添加管理员备注
- **参数**: `id` (路径参数)
- **请求体**: `HandleModerationRequest`
- **响应**: `{ "message": "处理成功" }`

---

## 3. 笔记管理 (`/api/v1/noting/notes`)

### 3.1 笔记CRUD操作

#### 创建笔记
- **路径**: `POST /api/v1/noting/notes/create`
- **功能**: 创建笔记（含文件）
- **请求体**: `multipart/form-data`
  - `meta`: JSON字符串 (NoteMeta)
  - `file`: 文件
- **响应**: `NoteVO`

#### 更新笔记
- **路径**: `PUT /api/v1/noting/notes/update`
- **功能**: 更新笔记
- **请求体**: `multipart/form-data`
  - `meta`: JSON字符串 (NoteUpdateMeta)
  - `file`: 文件（可选）
- **响应**: `NoteVO`

#### 删除笔记
- **路径**: `DELETE /api/v1/noting/notes`
- **功能**: 删除笔记
- **请求体**: `NoteDeleteRequest`
- **响应**: 成功响应

#### 移动笔记
- **路径**: `POST /api/v1/noting/notes/move`
- **功能**: 移动笔记到其他笔记本
- **请求体**: `NoteMoveRequest`
- **响应**: `NoteVO`

#### 根据笔记本获取笔记列表
- **路径**: `POST /api/v1/noting/notes/by-notebook`
- **功能**: 根据笔记本获取笔记列表
- **请求体**: `NoteListByNotebookRequest`
- **响应**: 笔记列表

#### 重命名笔记
- **路径**: `POST /api/v1/noting/notes/rename`
- **功能**: 重命名笔记
- **请求体**: `NoteRenameRequest`
- **响应**: `NoteVO`

#### 发布笔记
- **路径**: `POST /api/v1/noting/notes/publish`
- **功能**: 发布笔记
- **请求体**: `multipart/form-data`
  - `meta`: JSON字符串 (NoteUpdateMeta)
  - `file`: 文件
- **响应**: `NoteVO`

### 3.2 文件/图片操作

#### 上传附件
- **路径**: `POST /api/v1/noting/notes/files`
- **功能**: 上传附件
- **请求体**: `multipart/form-data`
  - `meta`: JSON字符串 (NoteMeta)
  - `file`: 文件（可选）
- **响应**: `NoteVO`

#### 获取文件访问URL
- **路径**: `POST /api/v1/noting/notes/files/url`
- **功能**: 获取文件访问URL
- **请求体**: `NoteFileUrlRequest`
- **响应**: 文件URL字符串

#### 根据noteId获取笔记
- **路径**: `POST /api/v1/noting/notes/files/id_url?noteId={noteId}`
- **功能**: 根据noteId获取笔记
- **参数**: `noteId` (查询参数)
- **响应**: `NoteShowVO`

#### 上传图片
- **路径**: `POST /api/v1/noting/notes/image`
- **功能**: 上传图片并返回访问URL
- **请求体**: `multipart/form-data` (file)
- **响应**: 图片URL字符串

---

## 4. 评论管理 (`/api/v1/remark`)

### 获取评论列表
- **路径**: `GET /api/v1/remark/note/list?loginUserId={userId}&...`
- **功能**: 根据笔记ID获取评论列表
- **参数**: 
  - `loginUserId`: 登录用户ID
  - 其他查询参数 (RemarkSelectByNoteDTO)
- **响应**: 评论列表

### 插入评论
- **路径**: `POST /api/v1/remark/insert`
- **功能**: 插入新评论
- **请求体**: `RemarkInsertDTO`
- **响应**: 布尔值

### 删除评论
- **路径**: `POST /api/v1/remark/delete`
- **功能**: 删除评论
- **请求体**: `RemarkDeleteDTO`
- **响应**: 布尔值

### 点赞评论
- **路径**: `POST /api/v1/remark/like?remarkId={remarkId}&loginUserId={userId}`
- **功能**: 点赞评论
- **参数**: 
  - `remarkId`: 评论ID
  - `loginUserId`: 登录用户ID
- **响应**: 布尔值

### 取消点赞评论
- **路径**: `POST /api/v1/remark/cancelLike?remarkId={remarkId}&loginUserId={userId}`
- **功能**: 取消点赞评论
- **参数**: 
  - `remarkId`: 评论ID
  - `loginUserId`: 登录用户ID
- **响应**: 布尔值

### 获取评论树
- **路径**: `GET /api/v1/remark/tree?remarkId={remarkId}&loginUserId={userId}`
- **功能**: 根据评论ID获取评论树
- **参数**: 
  - `remarkId`: 评论ID
  - `loginUserId`: 登录用户ID
- **响应**: 评论树对象

---

## 5. 关注关系 (`/api/v1/followRelation`)

### 获取关注列表
- **路径**: `GET /api/v1/followRelation/followings?userId={userId}`
- **功能**: 获取用户的关注列表
- **参数**: `userId` (查询参数)
- **响应**: 关注列表

### 获取粉丝列表
- **路径**: `GET /api/v1/followRelation/followers?userId={userId}`
- **功能**: 获取用户的粉丝列表
- **参数**: `userId` (查询参数)
- **响应**: 粉丝列表

### 关注用户
- **路径**: `POST /api/v1/followRelation/follow?userId={userId}&targetUserId={targetUserId}`
- **功能**: 关注用户
- **参数**: 
  - `userId`: 当前用户ID
  - `targetUserId`: 目标用户ID
- **响应**: 布尔值

### 取消关注
- **路径**: `POST /api/v1/followRelation/unfollow?userId={userId}&targetUserId={targetUserId}`
- **功能**: 取消关注用户
- **参数**: 
  - `userId`: 当前用户ID
  - `targetUserId`: 目标用户ID
- **响应**: 布尔值

### 检查是否关注
- **路径**: `GET /api/v1/followRelation/isFollowing?userId={userId}&targetUserId={targetUserId}`
- **功能**: 检查用户是否关注另一个用户
- **参数**: 
  - `userId`: 当前用户ID
  - `targetUserId`: 目标用户ID
- **响应**: 布尔值

### 检查是否互关
- **路径**: `GET /api/v1/followRelation/isMutualFollow?userId={userId}&targetUserId={targetUserId}`
- **功能**: 检查两个用户是否互相关注
- **参数**: 
  - `userId`: 用户1 ID
  - `targetUserId`: 用户2 ID
- **响应**: 布尔值

---

## 6. 标签管理 (`/api/v1/noting/tags`)

### 根据ID获取标签名称
- **路径**: `POST /api/v1/noting/tags/name`
- **功能**: 根据ID获取标签名称
- **请求体**: `TagNameRequest`
- **响应**: `TagVO`

---

## 7. 笔记本管理 (`/api/v1/noting/notebooks`)

### 创建笔记本
- **路径**: `POST /api/v1/noting/notebooks`
- **功能**: 创建笔记本
- **请求体**: `NotebookCreateRequest`
- **响应**: `NotebookVO`

### 更新笔记本
- **路径**: `PUT /api/v1/noting/notebooks`
- **功能**: 更新笔记本
- **请求体**: `NotebookUpdateRequest`
- **响应**: `NotebookVO`

### 删除笔记本
- **路径**: `DELETE /api/v1/noting/notebooks`
- **功能**: 删除笔记本
- **请求体**: `NotebookDeleteRequest`
- **响应**: 成功响应

### 移动笔记本
- **路径**: `PUT /api/v1/noting/notebooks/move-notebook`
- **功能**: 移动笔记本
- **请求体**: `NotebookMoveRequest`
- **响应**: `NotebookVO`

### 根据笔记空间获取笔记本列表
- **路径**: `POST /api/v1/noting/notebooks/by-space`
- **功能**: 根据笔记空间获取笔记本列表
- **请求体**: `NotebookListBySpaceRequest`
- **响应**: 笔记本列表

---

## 8. 笔记空间管理 (`/api/v1/noting/spaces`)

### 创建笔记空间
- **路径**: `POST /api/v1/noting/spaces`
- **功能**: 创建笔记空间
- **请求体**: `NoteSpaceCreateRequest`
- **响应**: `NoteSpaceVO`

### 重命名空间
- **路径**: `PUT /api/v1/noting/spaces`
- **功能**: 重命名笔记空间
- **请求体**: `NoteSpaceUpdateRequest`
- **响应**: `NoteSpaceVO`

### 删除空间
- **路径**: `DELETE /api/v1/noting/spaces`
- **功能**: 删除笔记空间
- **请求体**: `NoteSpaceDeleteRequest`
- **响应**: 成功响应

### 根据用户ID获取所有笔记空间
- **路径**: `POST /api/v1/noting/spaces/user`
- **功能**: 根据用户ID获取所有笔记空间
- **请求体**: `NoteSpaceListByUserRequest`
- **响应**: 笔记空间列表

---

## 9. 搜索 (`/api/v1/search`)

### 搜索笔记
- **路径**: `POST /api/v1/search/notes`
- **功能**: 搜索笔记（使用Elasticsearch，支持聚合和评分排序）
- **请求体**: `NoteSearchRequest`
- **响应**: 笔记搜索结果列表

### 搜索问题
- **路径**: `GET /api/v1/search/questions?keyword={keyword}&userId={userId}`
- **功能**: 搜索问题（QA），支持评分排序
- **参数**: 
  - `keyword`: 搜索关键词
  - `userId`: 用户ID（可选，用于记录搜索行为）
- **响应**: 问题列表

---

## 10. 问答系统 (`/api/v1/qa`)

### 10.1 问题管理

#### 创建问题
- **路径**: `POST /api/v1/qa/question`
- **功能**: 创建新问题
- **请求体**: `QuestionCreateDTO`
- **响应**: `QuestionVO`

#### 删除问题
- **路径**: `DELETE /api/v1/qa/question/{id}`
- **功能**: 删除问题
- **参数**: `id` (路径参数)
- **响应**: 成功响应

#### 点赞问题
- **路径**: `POST /api/v1/qa/question/like`
- **功能**: 点赞问题
- **请求体**: `LikeRequest`
- **响应**: 成功响应

#### 收藏问题
- **路径**: `POST /api/v1/qa/question/favorite`
- **功能**: 收藏问题
- **请求体**: `FavoriteRequest`
- **响应**: 成功响应

#### 获取问题详情
- **路径**: `GET /api/v1/qa/question/{id}`
- **功能**: 获取问题详情
- **参数**: `id` (路径参数)
- **响应**: `QuestionVO`

### 10.2 回答管理

#### 创建回答
- **路径**: `POST /api/v1/qa/answer`
- **功能**: 创建新回答
- **请求体**: `AnswerCreateDTO`
- **响应**: `AnswerVO`

#### 删除回答
- **路径**: `DELETE /api/v1/qa/answer`
- **功能**: 删除回答
- **请求体**: `DeleteAnswerRequest`
- **响应**: 成功响应

#### 点赞回答
- **路径**: `POST /api/v1/qa/answer/like`
- **功能**: 点赞回答
- **请求体**: `LikeRequest`
- **响应**: 成功响应

### 10.3 评论管理

#### 创建评论
- **路径**: `POST /api/v1/qa/comment`
- **功能**: 创建新评论
- **请求体**: `CommentCreateDTO`
- **响应**: `CommentVO`

#### 删除评论
- **路径**: `DELETE /api/v1/qa/comment`
- **功能**: 删除评论
- **请求体**: `DeleteCommentRequest`
- **响应**: 成功响应

#### 点赞评论
- **路径**: `POST /api/v1/qa/comment/like`
- **功能**: 点赞评论
- **请求体**: `LikeRequest`
- **响应**: 成功响应

### 10.4 回复管理

#### 创建回复
- **路径**: `POST /api/v1/qa/reply`
- **功能**: 创建新回复
- **请求体**: `ReplyCreateDTO`
- **响应**: `ReplyVO`

#### 删除回复
- **路径**: `DELETE /api/v1/qa/reply`
- **功能**: 删除回复
- **请求体**: `DeleteReplyRequest`
- **响应**: 成功响应

#### 点赞回复
- **路径**: `POST /api/v1/qa/reply/like`
- **功能**: 点赞回复
- **请求体**: `LikeRequest`
- **响应**: 成功响应

---

## 11. 热门内容 (`/api/v1/hot`)

### 获取热门笔记列表
- **路径**: `GET /api/v1/hot/notes`
- **功能**: 获取热门笔记列表
- **响应**: 热门笔记列表

---

## 12. 推荐系统 (`/api/v1/recommend`)

### 推荐笔记
- **路径**: `GET /api/v1/recommend/notes?userId={userId}&topN={topN}`
- **功能**: 根据用户关键词推荐笔记
- **参数**: 
  - `userId`: 用户ID
  - `topN`: 推荐数量（默认10）
- **响应**: 推荐笔记列表

### 推荐问答
- **路径**: `GET /api/v1/recommend/QAs?userId={userId}&topN={topN}`
- **功能**: 根据用户关键词推荐问答
- **参数**: 
  - `userId`: 用户ID
  - `topN`: 推荐数量（默认10）
- **响应**: 推荐问答列表

---

## 13. 收藏管理 (`/api/v1/favorites`)

### 获取用户收藏的笔记
- **路径**: `GET /api/v1/favorites/notes?userId={userId}`
- **功能**: 获取用户收藏的笔记列表
- **参数**: `userId` (查询参数)
- **响应**: 收藏笔记列表

### 获取用户收藏的问题
- **路径**: `GET /api/v1/favorites/questions?userId={userId}`
- **功能**: 获取用户收藏的问题列表
- **参数**: `userId` (查询参数)
- **响应**: 收藏问题列表

---

## 14. 通知管理 (`/api/v1/notifications`)

### 获取通知列表
- **路径**: `GET /api/v1/notifications/list?userId={userId}`
- **功能**: 获取当前用户最近的通知列表（最多50条）
- **参数**: `userId` (查询参数)
- **响应**: 通知列表

### 获取未读通知数量
- **路径**: `GET /api/v1/notifications/unread/total?userId={userId}`
- **功能**: 获取当前用户未读通知数量
- **参数**: `userId` (查询参数)
- **响应**: 未读数量

### 标记单条通知为已读
- **路径**: `POST /api/v1/notifications/read?notificationId={notificationId}`
- **功能**: 将单条通知标记为已读
- **参数**: `notificationId` (查询参数)
- **响应**: 成功响应

### 标记所有通知为已读
- **路径**: `POST /api/v1/notifications/read/all?userId={userId}`
- **功能**: 将当前用户所有通知标记为已读
- **参数**: `userId` (查询参数)
- **响应**: 成功响应

---

## 15. 私信会话 (`/api/v1/conversation`)

### 获取会话列表
- **路径**: `GET /api/v1/conversation/list?userId={userId}`
- **功能**: 获取用户的会话列表
- **参数**: `userId` (查询参数)
- **响应**: 会话列表

### 获取会话详情
- **路径**: `GET /api/v1/conversation/detail?conversationId={conversationId}`
- **功能**: 获取完整会话详情
- **参数**: `conversationId` (查询参数)
- **响应**: 会话详情

### 获取特定会话未读数量
- **路径**: `GET /api/v1/conversation/unread?userId={userId}&conversationId={conversationId}`
- **功能**: 获取特定会话的未读消息数量
- **参数**: 
  - `userId`: 用户ID
  - `conversationId`: 会话ID
- **响应**: 未读数量

### 获取所有会话未读数量
- **路径**: `GET /api/v1/conversation/unread/all?userId={userId}`
- **功能**: 获取用户所有会话的未读消息数量
- **参数**: `userId` (查询参数)
- **响应**: 未读数量映射

### 标记会话为已读
- **路径**: `POST /api/v1/conversation/read?userId={userId}&conversationId={conversationId}`
- **功能**: 标记会话为已读
- **参数**: 
  - `userId`: 用户ID
  - `conversationId`: 会话ID
- **响应**: 成功响应

### 获取用户总未读数量
- **路径**: `GET /api/v1/conversation/unread/total?userId={userId}`
- **功能**: 获取用户所有会话的总未读消息数量
- **参数**: `userId` (查询参数)
- **响应**: 总未读数量

---

## 16. 笔记统计 (`/api/v1/noting/note-stats`)

### 增减笔记统计字段
- **路径**: `POST /api/v1/noting/note-stats/change?noteId={noteId}&userId={userId}&field={field}&delta={delta}`
- **功能**: 增减笔记统计字段（浏览量、点赞数、收藏数、评论数）
- **参数**: 
  - `noteId`: 笔记ID
  - `userId`: 用户ID
  - `field`: 字段名（views/likes/favorites/comments）
  - `delta`: 变化量（默认1）
- **响应**: `NoteStatsVO`

### 获取笔记统计数据
- **路径**: `GET /api/v1/noting/note-stats/{noteId}`
- **功能**: 获取笔记统计数据
- **参数**: `noteId` (路径参数)
- **响应**: `NoteStatsVO`

---

## 17. WebSocket 聊天

### 发送私信消息
- **WebSocket路径**: `/chat.send`
- **功能**: 发送私信消息（需互关）
- **消息格式**: `ChatMessageRequest`
- **说明**: 
  - 发送者和接收者必须互相关注
  - 如果接收者在线，消息会实时推送
  - 如果接收者离线，会增加未读计数

---

## API 统计

- **认证与用户管理**: 11个接口
- **管理端**: 18个接口
- **笔记管理**: 10个接口
- **评论管理**: 6个接口
- **关注关系**: 6个接口
- **标签管理**: 1个接口
- **笔记本管理**: 5个接口
- **笔记空间管理**: 4个接口
- **搜索**: 2个接口
- **问答系统**: 13个接口
- **热门内容**: 1个接口
- **推荐系统**: 2个接口
- **收藏管理**: 2个接口
- **通知管理**: 4个接口
- **私信会话**: 6个接口
- **笔记统计**: 2个接口
- **WebSocket**: 1个接口

**总计**: 约 **99个 API 接口**

---

## 通用说明

### 响应格式
所有API使用统一的响应格式 `StandardResponse`:
```json
{
  "code": 200,
  "message": "成功",
  "data": { ... }
}
```

### 认证方式
需要认证的接口需要在请求头中携带：
```
Authorization: Bearer {token}
```

### 错误处理
- `401`: 未授权/Token无效
- `404`: 资源不存在
- `400`: 请求参数错误
- `500`: 服务器内部错误

---

**文档生成时间**: 2024年
**项目**: 笔记分享系统
