# flyBook — 飞书知识问答

> 基于 Kotlin + MVVM 架构的 Android AI 聊天应用，支持流式响应、多模型切换、语音输入/播放、图片 OCR、文件解析等功能。

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blueviolet.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/架构-MVVM-orange.svg)](https://developer.android.com/topic/architecture)

---

## 📖 目录

- [项目概述](#-项目概述)
- [功能特性](#-功能特性)
- [架构设计](#-架构设计)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [快速开始](#-快速开始)
- [配置说明](#-配置说明)
- [API 接口](#-api-接口)
- [数据库设计](#-数据库设计)
- [核心设计决策](#-核心设计决策)
- [性能优化](#-性能优化)

---

## 📋 项目概述

**flyBook** 是一款功能丰富的 AI 知识问答 Android 应用，设计灵感来源于飞书。支持与多个大语言模型（LLM）进行对话，具备流式响应、联网搜索增强、语音输入/输出、图片 OCR 识别、文件解析等能力，并通过本地 Room 数据库实现离线持久化。

本项目为课程作业项目，聚焦客户端开发，采用现代 Android 架构模式和最佳实践。

### 核心界面流程

```
登录页 → 主页（推荐话题 + 输入栏 + 侧边栏）
         │
         ├── 创建新对话 → 聊天页（消息列表 + 输入栏 + 侧边栏）
         ├── 选择历史对话 → 聊天页（加载历史消息）
         ├── 搜索对话 → 搜索页
         └── 回收站管理 → 回收站页
```

---

## ✨ 功能特性

### 🔐 用户认证
- **注册 & 登录** — 基于 Room 数据库的本地账户管理

### 💬 对话消息
- **多轮对话** — 完整的对话历史记录，支持分页加载（每批 20 条）
- **流式 AI 回复** — 基于 SSE（Server-Sent Events）的实时流式响应，配合打字机效果（约 30fps）
- **Markdown 渲染** — 完整支持代码块、语法高亮、表格、任务列表、图片、HTML 等
- **消息操作** — 复制、分享、点赞/点踩、重新生成
- **停止生成** — 随时中止正在进行的 AI 回复
- **选择复制** — 从 AI 回答中选择特定文本进行复制

### 🤖 多模型切换
支持在以下 AI 模型之间随时切换：

| 模型 ID | 显示名称 | API 名称 |
|:---|:---|:---|
| `qwen-3-32b` | Qwen3-32B | `Qwen/Qwen3-32B` |
| `deepseek-v2.5` | DeepSeek-V2.5 | `deepseek-ai/DeepSeek-V2.5` |
| `glm-4.6` | GLM-4.6 | `zai-org/GLM-4.6` |

每个模型可配置参数：`maxTokens`、`temperature`、`topP`、`topK`、`frequencyPenalty`、`enableThinking`（深度思考模式）。

### 🔍 联网搜索
- 每次对话可开关**联网搜索**功能
- 通过 Serper API 实时搜索互联网结果，增强 AI 回答质量
- 搜索结果在 AI 回答前内联展示

### 🎤 语音输入 & 播放
- **语音识别** — 基于科大讯飞 SDK 的语音转文字输入
- **语音合成** — 通过科大讯飞 TTS 引擎朗读 AI 回复
- 可调节发音人、语速、音量、音调
- 支持在线和离线合成模式

### 📎 多媒体输入
- **图片 OCR** — 使用 Google ML Kit 识别图片中的文字（支持中文）
- **文件解析** — 读取 PDF、DOCX、TXT 文件内容
- **附件预览** — 发送前可视化预览已选择的图片

### 📚 对话管理
- **历史列表** — 在侧边栏中浏览所有历史对话
- **创建、重命名、删除** — 完整的对话 CRUD 操作
- **置顶/取消置顶** — 将重要对话固定在列表顶部
- **软删除** — 删除的对话进入回收站，支持恢复
- **自动清理** — 回收站中超过 30 天的项目自动清除（通过 WorkManager）
- **关键字搜索** — 跨所有对话标题进行搜索
- **上下文切换** — 点击历史对话即时切换当前上下文
- **高性能** — 针对 1000+ 条对话进行了优化，滚动流畅

### 🎨 界面体验
- **Material Design** — Google Material Design 组件和主题
- **深色模式** — 完整支持暗色主题（`values-night/`）
- **侧边栏导航** — 基于 DrawerLayout 的侧边栏快速操作
- **打字指示器** — AI 生成时的动画圆点提示
- **自适应布局** — WindowInsets 适配系统栏和键盘
- **滑动操作** — 回收站中左滑显示删除/恢复操作
- **下拉刷新** — 下拉加载更多历史消息

---

## 🏗 架构设计

### MVVM + Repository 模式

```
┌─────────────────────────────────────────────────────┐
│                    UI 层 (View)                       │
│  Activity / Fragment / Adapter / Custom View         │
│  - 观察 ViewModel 的 LiveData                       │
│  - 将用户操作委托给 ViewModel                       │
│  - 不包含业务逻辑                                    │
└──────────────────────┬──────────────────────────────┘
                       │ LiveData / Listener
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel 层                         │
│  (5 个 ViewModel)                                    │
│  - AccountViewModel    ChatViewModel                │
│  - MainViewModel       HistoryViewModel             │
│  - InputBarViewModel                                 │
│  - 管理 UI 状态                                      │
│  - 协调 Repository 调用                              │
│  - 处理业务逻辑                                      │
└──────────────────────┬──────────────────────────────┘
                       │ Suspend 函数 / Flow
┌──────────────────────▼──────────────────────────────┐
│                 Repository 层                         │
│  (4 个 Repository)                                   │
│  - AccountRepository    ChatRepository              │
│  - HistoryRepository    InputBarRepository          │
│  - 数据单一来源                                      │
│  - 抽象数据源（数据库 / 网络）                       │
└──────────┬──────────────────────┬───────────────────┘
           │                      │
┌──────────▼──────────┐  ┌───────▼───────────────────┐
│   Room 数据库         │  │   网络层                   │
│   - MessageEntity    │  │   - ApiClient (SSE/Flow)  │
│   - Conversation     │  │   - WebSearchService       │
│   - Attachment       │  │   - HttpClientProvider      │
│   - Account          │  │   - SiliconFlow API        │
└──────────────────────┘  └───────────────────────────┘
```

### Fragment 组件化设计

两个可复用的 Fragment 通过 `activityViewModels()` 在多个 Activity 之间共享：

| Fragment | 复用场景 | 用途 |
|:---|:---|:---|
| `HistoryFragment` | MainActivity、ChatActivity | 侧边栏历史对话列表 |
| `InputBarFragment` | MainActivity、ChatActivity | 输入栏（文本、语音、附件） |

通信方式：`Listener` 接口回调 + 共享 `ViewModel`（通过 `activityViewModels()`）。

### 界面组件（5 个 Activity + 2 个 Fragment）

```
ui/
├── auth/LoginActivity          ← 入口页（启动页）
├── main/MainActivity           ← 主页（推荐话题 + 侧边栏 + 输入栏）
├── chat/ChatActivity           ← 聊天页（消息列表 + 侧边栏 + 输入栏）
├── search/SearchActivity       ← 搜索对话
├── history/view/TrashActivity  ← 回收站管理
├── history/HistoryFragment     ← 可复用侧边栏 Fragment
└── inputbar/InputBarFragment   ← 可复用输入栏 Fragment
```

### 基类继承体系

```
BaseActivity                     ← WindowInsets 处理
  └── BaseHistoryActivity        ← 历史对话默认处理逻辑
       └── BaseChatActivity      ← 侧边栏 + 输入栏 + 权限处理
            ├── MainActivity     ← 主页
            └── ChatActivity     ← 聊天页
```

### Manager 管理器模式

将跨模块的 UI 逻辑提取为可复用的管理器：

| 管理器 | 职责 |
|:---|:---|
| `SidebarManager` | 侧边栏开/关、项目选中、导航 |
| `ModelManager` | 模型选择对话框、持久化存储 |
| `AppNavigator` | 统一的 Intent 跳转，类型安全的导航方法 |

---

## 🛠 技术栈

| 类别 | 技术 | 版本 | 用途 |
|:---|:---|:---|:---|
| **开发语言** | Kotlin | 1.9.24 | 主要开发语言 |
| **构建工具** | Gradle + AGP | 8.6.0 | 构建系统 |
| **UI 框架** | Android SDK | minSdk 24, targetSdk 34 | 核心平台 |
| **架构模式** | MVVM + LiveData | — | UI 架构模式 |
| **数据库** | Room | 2.6.1 | 本地持久化存储 |
| **网络** | OkHttp | 4.12.0 | HTTP 客户端（支持 SSE） |
| **异步** | Kotlin Coroutines | 1.8.1 | 异步编程 |
| **生命周期** | Android Lifecycle | 2.8.7 | ViewModel、LiveData |
| **Markdown** | Markwon | 4.6.2 | Markdown 渲染 + 语法高亮 |
| **语法高亮** | Prism4j | 2.0.0 | 代码块语法高亮 |
| **图片加载** | Glide | 4.16.0 | 图片加载和缓存 |
| **OCR** | Google ML Kit | 16.0.0 | 中英文文字识别 |
| **语音** | 科大讯飞 MSC SDK | — | 语音识别 & 语音合成 |
| **滑动操作** | SwipeRevealLayout | 1.4.1 | 左滑显示操作按钮 |
| **后台任务** | WorkManager | 2.9.0 | 定时清理回收站 |
| **JSON** | Gson | 2.11.0 | JSON 序列化/反序列化 |
| **视图绑定** | Android ViewBinding | — | 类型安全的视图访问 |

---

## 📂 项目结构

```
com.example.myapplication/
├── config/
│   └── ModelConfig.kt              # AI 模型定义和注册中心
│
├── data/
│   ├── db/                          # Room 数据库层
│   │   ├── AppDatabase.kt           # 主数据库（单例）
│   │   ├── account/                 # 账户表
│   │   │   ├── AccountDao.kt
│   │   │   ├── AccountDatabase.kt
│   │   │   └── AccountEntity.kt
│   │   ├── chat/                    # 聊天相关表
│   │   │   ├── AttachmentDao.kt
│   │   │   ├── AttachmentEntity.kt
│   │   │   ├── ConversationDao.kt
│   │   │   ├── ConversationEntity.kt
│   │   │   ├── MessageDao.kt
│   │   │   └── MessageEntity.kt
│   │   └── migrations/             # 数据库迁移
│   │       └── Migrations.kt
│   ├── model/
│   │   └── RecommendedTopic.kt     # 话题数据模型
│   └── source/
│       └── DefaultTopics.kt        # 默认推荐话题数据
│
├── domain/                          # 领域层（纯 Kotlin）
│   ├── AccountInfo.kt
│   ├── ChatHistory.kt
│   └── ChatMessage.kt
│
├── network/                         # 网络层
│   ├── ApiClient.kt                # SSE 流式客户端（SiliconFlow API）
│   ├── ApiService.kt               # 网络服务接口
│   ├── HttpClientProvider.kt       # 共享 OkHttpClient 提供者
│   ├── WebSearchService.kt          # 联网搜索服务（Serper API）
│   └── model/
│       └── ApiModels.kt             # 请求/响应 DTO
│
├── ui/                              # UI 层（按功能模块划分）
│   ├── base/
│   │   ├── BaseActivity.kt         # 基础 Activity（WindowInsets）
│   │   └── BaseChatActivity.kt     # 聊天页抽象基类
│   ├── auth/                        # 认证模块
│   │   ├── LoginActivity.kt
│   │   ├── AccountViewModel.kt
│   │   └── AccountRepository.kt
│   ├── main/                        # 主页模块
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   └── adapters/
│   │       └── TopicAdapter.kt
│   ├── chat/                        # 聊天模块
│   │   ├── ChatActivity.kt
│   │   ├── ChatViewModel.kt        # 核心：流式响应、打字机效果、搜索
│   │   ├── ChatRepository.kt
│   │   └── adapters/
│   │       └── ChatMessageAdapter.kt
│   ├── history/                     # 历史记录侧边栏模块
│   │   ├── HistoryFragment.kt
│   │   ├── HistoryViewModel.kt
│   │   ├── HistoryRepository.kt
│   │   ├── TrashCleanupWorker.kt   # WorkManager 定时清理任务
│   │   ├── view/
│   │   │   ├── HistoryFragment.kt
│   │   │   └── TrashActivity.kt
│   │   ├── viewmodel/
│   │   │   ├── HistoryViewModel.kt
│   │   │   └── TrashViewModel.kt
│   │   └── adapters/
│   │       ├── HistoryAdapter.kt
│   │       └── TrashAdapter.kt
│   ├── inputbar/                    # 输入栏模块
│   │   ├── InputBarFragment.kt
│   │   ├── InputBarViewModel.kt
│   │   ├── InputBarRepository.kt   # OCR + 文件解析逻辑
│   │   ├── adapters/
│   │   │   ├── AttachmentPreviewAdapter.kt
│   │   │   └── ModelSelectorAdapter.kt
│   │   └── model/
│   │       └── SelectedMedia.kt
│   ├── search/                      # 搜索模块
│   │   ├── SearchActivity.kt
│   │   └── SearchViewModel.kt
│   └── common/                      # 公共 UI 组件
│       ├── dialogs/
│       │   ├── MessageActionsBottomSheet.kt
│       │   └── SelectTextDialogFragment.kt
│       ├── managers/
│       │   ├── ModelManager.kt     # 模型选择 & 持久化
│       │   └── SidebarManager.kt   # Drawer 管理
│       ├── navigation/
│       │   └── AppNavigator.kt     # 统一导航器
│       └── views/
│           └── TypingIndicatorView.kt  # 打字动画指示器
│
└── utils/                           # 工具类
    ├── MarkwonFactory.kt           # Markwon 实例工厂
    ├── MLKitDiagnostics.kt          # ML Kit 诊断工具
    ├── WindowInsetsHelper.kt        # 系统栏/键盘适配
    ├── XunfeiSpeechRecognizer.kt   # 科大讯飞语音识别封装
    └── XunfeiSpeechSynthesizer.kt  # 科大讯飞语音合成封装
```

**总计：53 个核心 Kotlin 源文件** + 布局 XML、drawable 资源和配置文件。

---

## 🚀 快速开始

### 环境要求

- **Android Studio** — Hedgehog (2023.1.1) 或更高版本
- **JDK** — 17 或更高
- **Gradle** — 8.6+（项目已包含 wrapper）
- **Android SDK** — API 34
- **设备/模拟器** — Android 7.0 (API 24) 或更高

### 构建 & 运行

```bash
# 1. 克隆仓库
git clone <仓库地址>
cd flybook

# 2. 配置 API 密钥（详见下方配置说明）
# 创建 local.properties 并填入密钥

# 3. Gradle 构建
./gradlew assembleDebug       # Linux/macOS
gradlew.bat assembleDebug     # Windows

# 4. 安装到设备/模拟器
./gradlew installDebug
```

或直接在 **Android Studio** 中打开项目，同步 Gradle，点击 **Run ▶**。

---

## ⚙️ 配置说明

### API 密钥

在项目根目录创建 `local.properties` 文件（已加入 `.gitignore`）：

```properties
# SiliconFlow API 密钥（必需 — 用于 AI 模型调用）
ai.api.key=sk-你的-siliconflow-api-key

# Serper API 密钥（可选 — 用于联网搜索）
serper.api.key=你的-serper-api-key

# 科大讯飞 AppID（可选 — 用于语音输入/播放）
xunfei.appid=你的-xunfei-app-id
```

### 密钥获取途径

| 服务 | 用途 | 注册地址 |
|:---|:---|:---|
| **SiliconFlow（硅基流动）** | AI 模型 API（Qwen、DeepSeek、GLM） | https://siliconflow.cn |
| **Serper** | Google 联网搜索 API | https://serper.dev |
| **科大讯飞** | 语音识别 & 语音合成 | https://www.xfyun.cn |

### 运行时权限

应用在运行时会请求以下权限：

| 权限 | 用途 |
|:---|:---|
| `RECORD_AUDIO` | 语音输入（语音识别） |
| `READ_MEDIA_IMAGES` | 选择图片进行 OCR |
| `READ_EXTERNAL_STORAGE` (≤ API 32) | 选择文件进行解析 |
| `INTERNET` | API 调用和联网搜索 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |

---

## 🔌 API 接口

### AI 对话接口（SiliconFlow API）

应用与 SiliconFlow API（兼容 OpenAI 格式）通信：

```
POST https://api.siliconflow.cn/v1/chat/completions
```

**请求格式**（流式）：

```json
{
  "model": "Qwen/Qwen3-32B",
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "stream": true,
  "max_tokens": 4096,
  "temperature": 0.7,
  "top_p": 0.7,
  "top_k": 50,
  "frequency_penalty": 0.5,
  "enable_thinking": false,
  "thinking_budget": 4096
}
```

**响应格式**：SSE 流，每行以 `data: ` 为前缀的 JSON 数据块，包含 `choices[0].delta.content`。

### 流式响应处理流程

```
用户输入
  → ChatViewModel.sendMessage()
    → ChatRepository.streamChat()
      → ApiClient.streamChat()                       [callbackFlow]
        → OkHttp POST 请求（SSE）
          → BufferedReader 逐行读取
            → 解析 "data: {...}" 数据块
              → emit(Delta) 发送到 Flow
                → ChatViewModel 收集并按批处理（每 4 个字符）
                  → 每批间隔 30ms 延迟
                    → LiveData → Adapter → UI 更新
```

### 联网搜索接口（Serper API）

```
POST https://google.serper.dev/search
Authorization: X-API-Key: <serper.api.key>
```

搜索结果将作为上下文信息，拼接到用户消息之前发送给 AI 模型。

---

## 🗄 数据库设计

**Room Database v3** — `chat_database`

### 数据表

| 表名 | 关键字段 | 用途 |
|:---|:---|:---|
| `AccountEntity` | `id`, `username`, `password` | 用户凭证 |
| `ConversationEntity` | `id` (UUID), `title`, `isPinned`, `isDeleted`, `createdAt` | 对话元数据 |
| `MessageEntity` | `id`, `conversationId` (FK), `content`, `isUser`, `timestamp`, `isLiked`, `isDisliked` | 聊天消息 |
| `AttachmentEntity` | `id`, `messageId` (FK), `type`, `uri`, `content` | 消息附件 |

### 数据库迁移历史

| 版本 | 变更内容 |
|:---|:---|
| 1 → 2 | Conversation 新增 `isDeleted`、`deletedAt` 字段 |
| 2 → 3 | Message 新增 `reasoningContent`、`isLiked`、`isDisliked` 字段 |

Schema 文件导出至 `app/schemas/` 目录，便于版本跟踪。

---

## 🎯 核心设计决策

### 1. MVVM + Repository 模式
每个功能模块都有完整的 MVVM 三元组（View → ViewModel → Repository），确保关注点分离、可测试性和可维护性。

### 2. Fragment 组件化
`HistoryFragment` 和 `InputBarFragment` 是独立的、可复用的组件，通过 `activityViewModels()` 共享数据。这避免了代码重复，并保持多页面间的状态同步。

### 3. Kotlin Flow + callbackFlow 流式处理
SSE 流式响应采用 `callbackFlow` 实现，将 OkHttp 的回调式 API 转换为干净的 `Flow<Delta>` 流。Flow 支持取消 — 取消协程会自动取消 HTTP 请求。

### 4. 打字机效果
AI 回复按批处理（每 4 个字符更新一次 UI），批次间间隔 30ms，实现流畅的约 30fps 打字机效果，同时最小化 UI 线程压力。局部更新使用 `notifyItemChanged(position, payload)` 实现高效的 RecyclerView 刷新。

### 5. 基类继承体系
`BaseActivity` → `BaseHistoryActivity` → `BaseChatActivity` → `MainActivity` / `ChatActivity`。将公共行为（侧边栏、输入栏、权限、导航）提取到抽象基类中，消除重复代码。

### 6. 软删除 + 自动清理
对话删除采用软删除（标记 `isDeleted = true`），移入回收站。`WorkManager` 定时任务自动清除超过 30 天的项目。

### 7. 防御性状态检查
`ChatViewModel` 在每次 UI 更新前会检查 AI 消息索引是否仍然有效，防止用户在生成过程中快速切换对话导致的崩溃。

---

## ⚡ 性能优化

| 优化项 | 详细说明 |
|:---|:---|
| **批量 UI 更新** | 每 4 个字符更新一次 RecyclerView（减少 75% 渲染调用） |
| **延迟控制** | 30ms 间隔控制实现流畅的约 33fps 打字机效果 |
| **局部刷新** | `PAYLOAD_CONTENT_UPDATE` 避免整个 item 重绘 |
| **分页加载** | 每页 20 条消息，基于游标偏移加载 |
| **连接池复用** | 共享的单例 `OkHttpClient` 复用 TCP 连接 |
| **协程取消** | 通过 `Job.cancel()` 立即停止流式生成 |
| **数据库索引** | 在 `conversationId` 和 `timestamp` 上建立索引，加速查询 |
| **Glide 缓存** | 磁盘 + 内存缓存附件预览图 |
| **延迟初始化** | API Key、Gson、OkHttpClient 使用 `by lazy` 懒加载 |

---

## 📄 许可证

本项目为课程作业项目。

---

## 🙏 致谢

- [SiliconFlow（硅基流动）](https://siliconflow.cn) — AI 模型 API 提供商
- [科大讯飞](https://www.xfyun.cn) — 语音识别 & 合成 SDK
- [Google ML Kit](https://developers.google.com/ml-kit) — 端侧 OCR
- [Markwon](https://github.com/noties/Markwon) — Android Markdown 渲染器
- [OkHttp](https://square.github.io/okhttp/) — HTTP 客户端
- [Room](https://developer.android.com/training/data-storage/room) — Android 持久化库
