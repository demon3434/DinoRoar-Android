<p align="center">
  <img src="icon.png" width="256" height="256" alt="DinoRoar Icon">
</p>

# 🦕 DinoRoar Android - 恐龙手账移动客户端

DinoRoar Android 端是基于 Jetpack Compose 构建的现代手账与心情日记客户端。提供极具沉浸感的手账绘图排版、语音输入转文字、离线存储同步及局域网服务器自动发现功能。

---

## ✨ 核心功能

- **🎨 手账贴纸编辑器**：支持拖拽、缩放、旋转手账贴纸，单篇日记严格遵循 **`6`** 张贴纸上限约束。
- **🎙️ 语音智能录入**：按住说话实时录音，一键透传至云端/局域网 SenseVoice STT 引擎转换为日记文本。
- **📡 mDNS 局域网自发现**：自动侦测内网 DinoRoar 后端服务器，免去繁琐的 IP 端口手动配置。
- **💾 离线优先架构**：基于 Room 本地数据库，支持无网状态下日记编辑与贴纸排版，联网后自动增量同步。
- **🔒 伪装锁屏防御**：内置九宫格恐龙按键与游戏伪装锁屏，严格执行安全防返回死锁逻辑。

---

## 🛠️ 技术栈

- **开发语言**：Kotlin 1.9+
- **UI 框架**：Jetpack Compose / Material Design 3
- **本地存储**：Room Database / Preferences DataStore
- **网络通信**：Retrofit 2 / OkHttp 4 / Kotlin Coroutines & Flow
- **服务发现**：Android NsdManager (mDNS / Zeroconf)
- **最低兼容**：Android 8.0 (API Level 26) +

---

## 🚀 编译与构建

### 环境要求

- **Android Studio**：Hedgehog (2023.1.1) 或更高版本
- **JDK 版本**：Java 17 (推荐 Amazon Corretto 或 Zulu 17)
- **Gradle**：8.2+ (使用 Gradle Kotlin DSL)

### 编译步骤

1. 克隆本项目并导入 Android Studio：
   ```bash
   git clone <repository-url> DinoRoar-Android
   ```
2. 等待 Gradle Sync 完成。
3. 执行 Debug 构建或直接部署至手机/模拟器：
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📁 目录结构

```text
DinoRoar-Android/
├── app/
│   ├── src/main/java/com/dinoroar/app/
│   │   ├── data/             # Room 实体、Dao 及 Retrofit API 客户端
│   │   ├── ui/               # Compose 界面、组件、手账编辑器与 ViewModel
│   │   ├── service/          # mDNS 服务发现与后台同步 Task
│   │   └── util/             # 工具函数与常量设置
│   └── build.gradle.kts
└── build.gradle.kts
```
