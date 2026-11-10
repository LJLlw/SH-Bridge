# SH Bridge

让国行「健康服务」（`com.samsung.android.swsportplugin`）在国际版三星健康 7.x 上恢复正常使用。

三星健康 7.x 会按地区校验合作方名单，国际版不认识这个只在国内发行的 App，于是微信运动同步不了、餐食扫描也写不进三星健康。这个 LSPosed 模块在三星健康进程内放行这一层校验；三星健康的 APK 不动、签名不变，健康服务对官方签名的检查照常通过。

- 发布者：LJLlw
- 包名：`com.sumhealth.shealthbridge`
- 版本：2.0.0（versionCode 20）
- 成品：[release/SH-Bridge-2.0.0.apk](release/SH-Bridge-2.0.0.apk)

## 能做什么

- **微信运动**：步数、爬楼、活动汇总、卡路里、运动数据读取与同步
- **餐食扫描**：拍照或选图识别后，「添加至三星健康」可以写入饮食记录
- 走同一套 Health SDK 的其它功能（欧姆龙血压计写入、支付宝 / Spax 数据分享等）

## 需要什么

- 已 root 并安装 LSPosed（Xposed 模块，按普通 App 安装不会生效）
- 三星健康国际版 7.x（实测 7.2.0.041）
- 国行「健康服务」已安装

## 安装

1. 安装 `release/SH-Bridge-2.0.0.apk`
2. LSPosed 管理器 → 模块 → 启用「健康服务桥接 (SH Bridge)」
3. 作用域勾选「三星健康」（模块已声明默认作用域）
4. 强行停止三星健康，或重启手机
5. 打开健康服务，进微信运动页或试一次餐食扫描

第 4 步不能跳：模块在三星健康启动时加载。

## 验证

```bash
adb logcat -s LSPosed | grep SHBridge
```

启动后应有六行 `hooked ... 1/1`，使用时出现 `allow ...` 记录。

## 已知取舍

- hook 未按包名收敛，三星健康内所有走该 SDK 的第三方应用会一并放行
- 同意层被跳过，健康服务不再弹授权确认
- 只改运行时行为，不写系统分区、不改 APK，关闭模块即恢复
- 升级请沿用同一签名密钥，换密钥需卸载重装并在 LSPosed 中重新启用

## 构建

需要 JDK 8+、Android build-tools 30.0.3、Android platform 30 的 `android.jar`：

```bash
export ANDROID_BUILD_TOOLS=$HOME/Android/Sdk/build-tools/30.0.3
export ANDROID_JAR=$HOME/Android/Sdk/platforms/android-30/android.jar
bash module/build.sh
```

首次构建会在 `module/build/shbridge.jks` 生成签名密钥。使用自己的密钥：

```bash
export KEYSTORE=/path/to/your.jks KEYSTORE_ALIAS=your_alias
export KEYSTORE_PASS=your_pass KEY_PASS=your_pass
bash module/build.sh
```

自行构建的 APK 与发布版签名不同，不能覆盖安装。

## 目录

```
module/    模块源码、Xposed API 桩、清单资源、构建脚本
release/   已签名成品 APK
.github/   打标签自动发布 Release 的工作流
```

## 免责声明

仅用于在个人设备上恢复已有应用的功能，与三星无任何关联。修改运行时行为可能违反相关服务条款，风险自负。MIT 协议开源。
