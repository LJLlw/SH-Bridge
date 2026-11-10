## SH Bridge

让国行「健康服务」（`com.samsung.android.swsportplugin`）在国际版三星健康 7.x 上恢复正常使用。

- 微信运动：步数、爬楼、活动汇总、卡路里、运动数据同步
- 餐食扫描：识别后「添加至三星健康」可写入饮食记录
- 其它走 Health SDK 的读写（欧姆龙血压计、支付宝 / Spax 分享等）

### 安装

1. 安装 APK
2. LSPosed 中启用模块，作用域勾选「三星健康」
3. 强行停止三星健康或重启手机

需要 root + LSPosed，不是普通 App。仅改运行时行为，不写系统分区、不改 APK。
