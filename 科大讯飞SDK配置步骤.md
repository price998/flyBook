# 科大讯飞离线语音识别 SDK 配置步骤

## 当前状态
✅ 已完成：
- Msc.jar 已导入到 `app/libs/` 目录
- native 库文件已导入到 `app/libs/arm64-v8a/` 和 `app/libs/armeabi-v7a/`
- build.gradle 已配置引用 Msc.jar
- 代码已集成 XunfeiSpeechRecognizer

## 需要完成的配置步骤

### 步骤 1：移动 native 库到 jniLibs 目录

**重要**：Android Studio 需要将 `.so` 文件放在 `jniLibs` 目录，而不是 `libs` 目录。

请执行以下操作：

1. 在 `app/src/main/` 目录下创建 `jniLibs` 文件夹（如果不存在）
2. 将以下文件夹从 `app/libs/` **移动**到 `app/src/main/jniLibs/`：
   - `arm64-v8a/` （包含 libmsc.so）
   - `armeabi-v7a/` （包含 libmsc.so）

**移动后的目录结构应该是：**
```
app/
├── libs/
│   └── Msc.jar  ← 保留在这里
└── src/
    └── main/
        ├── jniLibs/  ← 新建此文件夹
        │   ├── arm64-v8a/
        │   │   └── libmsc.so
        │   └── armeabi-v7a/
        │       └── libmsc.so
        ├── java/
        ├── res/
        └── AndroidManifest.xml
```

### 步骤 2：配置离线识别资源（可选）

如果需要使用离线识别功能，还需要：

1. 在 `app/src/main/assets/` 目录下创建 `iflytek` 文件夹
2. 将科大讯飞 SDK 中的离线资源文件（如语音模型）复制到此目录

**注意**：当前配置使用的是**在线识别**模式，如果网络可用，无需离线资源也能工作。

### 步骤 3：验证配置

完成上述步骤后：

1. 在 Android Studio 中点击 **Build > Clean Project**
2. 然后点击 **Build > Rebuild Project**
3. 检查编译是否成功

### 步骤 4：测试语音识别

运行应用后：

1. 切换到语音模式（点击麦克风图标）
2. 点击"点击说话"按钮
3. 如果科大讯飞初始化成功，会使用离线/在线识别
4. 如果初始化失败，会自动回退到 Google 语音识别

## 常见问题排查

### 问题 1：提示"离线语音识别初始化失败"

**原因**：
- native 库文件路径不正确
- 缺少必要的权限
- APPID 配置错误

**解决方法**：
1. 确认 `.so` 文件在 `app/src/main/jniLibs/` 目录下
2. 检查 AndroidManifest.xml 中的权限是否完整
3. 确认 XunfeiSpeechRecognizer.kt 中的 APPID 是否正确

### 问题 2：运行时崩溃 "UnsatisfiedLinkError"

**原因**：找不到 native 库文件

**解决方法**：
1. 确认 jniLibs 目录结构正确
2. Clean 并 Rebuild 项目
3. 卸载旧版本应用，重新安装

### 问题 3：识别不准确

**原因**：
- 环境噪音过大
- 参数配置不当
- 网络问题（在线模式）

**解决方法**：
1. 在安静环境测试
2. 调整 VAD_BOS 和 VAD_EOS 参数
3. 检查网络连接

## 当前配置参数

在 `XunfeiSpeechRecognizer.kt` 中的配置：

```kotlin
// APPID（已配置）
private const val APPID = "b4d78b8e"

// 语言设置
setParameter(SpeechConstant.LANGUAGE, "zh_cn")  // 中文
setParameter(SpeechConstant.ACCENT, "mandarin") // 普通话

// 超时设置
setParameter(SpeechConstant.VAD_BOS, "4000")  // 前端点 4秒
setParameter(SpeechConstant.VAD_EOS, "1000")  // 后端点 1秒

// 标点符号
setParameter(SpeechConstant.ASR_PTT, "1")  // 开启标点
```

## 离线识别配置（高级）

如果要启用完全离线识别，需要：

1. **下载离线资源包**：
   - 登录科大讯飞控制台
   - 下载对应的离线识别资源包

2. **配置离线引擎**：
   ```kotlin
   // 在 XunfeiSpeechRecognizer.kt 的 setParams() 方法中添加
   setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL)
   setParameter(SpeechConstant.ASR_RES_PATH, "fo|res/asr/common.jet")
   ```

3. **放置资源文件**：
   - 将资源文件放在 `assets/iflytek/` 目录下

## 优势说明

### 当前实现的优势：

1. **智能回退机制**：
   - 优先使用科大讯飞（更准确，支持离线）
   - 失败时自动切换到 Google 识别（兼容性好）

2. **无缝体验**：
   - 用户无需关心使用哪种识别方式
   - 自动选择最佳方案

3. **资源管理**：
   - 自动释放资源，避免内存泄漏
   - 生命周期管理完善

## 下一步

完成上述配置后，应用将支持：
- ✅ 科大讯飞在线语音识别（需要网络）
- ✅ Google 语音识别（备用方案）
- ⏳ 科大讯飞离线语音识别（需要额外配置离线资源）

如有问题，请查看 Logcat 日志，搜索 "XunfeiSpeechRecognizer" 或 "ChatActivity" 标签。
