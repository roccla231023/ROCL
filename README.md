<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="App Icon" width="100" />
  <h1>ROCL</h1>

Android LLM 聊天客户端 · 基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 的二开版

An Android LLM chat client, forked from [RikkaHub](https://github.com/rikkahub/rikkahub).
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

> [!WARNING]
> ROCL 是 RikkaHub 的个人二开版本，与上游项目无关。
> 上游相关问题请反馈至 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)。使用第三方分支请自行评估隐私与权限风险。

## 🚀 下载

从 [Releases](https://github.com/roccla231023/ROCL/releases) 下载 APK：

| 文件 | 适用设备 |
| --- | --- |
| `app-arm64-v8a-release.apk` | 绝大多数现代手机 |
| `app-universal-release.apk` | 不确定架构时使用（体积最大） |
| `app-x86_64-release.apk` | x86 设备 / 模拟器 |

每个 Release 附带 sha256 校验值。每日构建见 [Actions](https://github.com/roccla231023/ROCL/actions)。

## 🧩 与上游的差异

| 模块 | 说明 |
| --- | --- |
| 多智能体群聊 | 按席位（角色）单独配置模型与工具作用域；跨席位的工具执行结果可透传，便于席位之间互相审查 |
| 会话记忆 | 记忆的抽取、组装与检索接入生成链路；本地向量检索，缺少嵌入模型或超时时自动降级为关键词检索 |
| 高级外观 | 全局背景图与透明度 / 虚化，聊天页可单独开关，玻璃主题下弹层与过渡保持不透明 |
| 存储管理 | 集中查看与清理会话记录、文件与图片 |
| 构建与发布 | GitHub Actions 每日构建 + 稳定版发布；数据库跨版本手写迁移 |

其余功能与使用方式与上游一致，详见[上游 README](https://github.com/rikkahub/rikkahub)。

## 🌿 分支

| 分支 | 说明 |
| --- | --- |
| `custom`（默认） | 开发分支 |
| `master` | 上游基线快照 |

## 🛠 构建

使用 [Android Studio](https://developer.android.com/studio) 打开本项目。

- [Kotlin](https://kotlinlang.org/) · [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Koin](https://insert-koin.io/) · [Room](https://developer.android.com/training/data-storage/room) · [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [OkHttp](https://square.github.io/okhttp/) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

> [!TIP]
> 构建需要 `app/` 目录下的 `google-services.json`。

## 🙏 致谢

- [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) —— 本项目底座
- 部分功能实现来自 RikkaHub 社区的其他二开分支

## 📄 License

[GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）