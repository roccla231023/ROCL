<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="App Icon" width="100" />
  <h1>ROCL</h1>

基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 的个人二开版本 · 自用为主

A personal fork of [RikkaHub](https://github.com/rikkahub/rikkahub) with a few custom additions.
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

> [!IMPORTANT]
> **这不是 RikkaHub 官方版本。** 底座是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)（AGPL-3.0），ROCL 在它之上做定制。
> 上游相关的问题请不要反馈到这里；使用第三方分支请自行注意隐私与权限风险。

## 🧩 相比上游的改动

- 🤖 **多智能体群聊**：按席位（角色）单独配置模型与工具作用域；跨席位的工具执行结果可透传，便于席位之间互相审查
- 🧠 **会话记忆**：会话记忆的抽取、组装与检索接入生成链路；本地向量检索，缺少嵌入模型或检索超时时自动降级为关键词检索
- 🎨 **高级外观**：全局背景图与透明度 / 虚化，聊天页可单独开关，玻璃主题下弹层与过渡保持不透明
- 🗂️ **存储管理**：集中查看与清理会话记录、文件与图片
- ⚙️ **工程化**：GitHub Actions 自动构建与发布（每日构建 + 稳定版），数据库跨版本手写迁移

改造原则：**最小侵入 + 模块化**，尽量只新增文件、不动上游核心，以便持续同步上游。

> 其中群聊、记忆、存储等模块整合自其他二开分支的成熟实现，ROCL 负责移植、适配与持续维护。变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## ✨ 继承自上游的能力

- 🎨 Material You Design and 🌙 Dark mode
- 📦 Workspace: a proot-based Linux agent environment
- 🔄 Multiple AI Provider Support: custom API / URL / models (all OpenAI, Google, Anthropic compatible api)
- 🖼️ Multimodal input support (Image, Text Documentation, PDF, Docx)
- 🖥️ Web access for multi-platform use
- 🛠️ MCP support
- 📝 Markdown Rendering (with code highlighting, Latex formulas, tables, Mermaid)
- 🪾 Message Branching
- 🔍 Search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, time, etc.)
- 🤳 QR code export and import for providers
- 🤖 Agent customization
- 🧠 ChatGPT-like memory feature
- 📝 AI Translation
- 🌐 Custom HTTP request headers and request bodies
- 💌 Silly Tavern character card import

## 🚀 下载

到 [Releases](https://github.com/roccla231023/ROCL/releases) 下载 APK：

| 文件 | 适用设备 |
| --- | --- |
| `app-arm64-v8a-release.apk` | 绝大多数现代手机 |
| `app-universal-release.apk` | 不确定架构时用这个（体积最大） |
| `app-x86_64-release.apk` | x86 设备 / 模拟器 |

每个版本附带 sha256 校验值。

## 🌿 分支

| 分支 | 用途 |
| --- | --- |
| `custom`（默认） | 开发分支，ROCL 的所有改动都在这里 |
| `master` | 上游基线快照 |

上游同步策略：改动尽量落在新增文件与扩展点上，保留上游合并路径，方便持续跟上游。

## ✨ 构建

使用 [Android Studio](https://developer.android.com/studio) 打开本项目。

技术栈：

- [Kotlin](https://kotlinlang.org/)（开发语言）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（UI 框架）
- [Koin](https://insert-koin.io/)（依赖注入）
- [Room](https://developer.android.com/training/data-storage/room)（数据库）
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)（偏好存储）
- [Okhttp](https://square.github.io/okhttp/)（HTTP 客户端）
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)（JSON 序列化）

> [!TIP]
> 构建需要 `app/` 目录下的 `google-services.json`。

## 📄 License

沿用上游许可：[GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）。

底座项目：[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)