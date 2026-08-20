# NewLandPlugins

新大陆 / HC PDA 硬件 Android 插件的统一开源仓库。按 Firebase / HMS / AndroidX 的方式拆成**可选模块**：业务按需依赖，JitPack 一次发布，后续增删插件不必改消费方工程结构。

旧仓库 [NewLandHCUHF](https://github.com/violet000/NewLandHCUHF)、[NewlandN1](https://github.com/violet000/NewlandN1) 的对外 API 包名保持不变，迁移时只改 Maven 坐标。

## 架构

```
NewLandPlugins
├── newland-bom          # 版本对齐（BOM）
├── newland-core         # 插件 SPI + 清单发现
├── newland-all          # 聚合 HC / Newland 公开插件
├── zijin-all            # 聚合紫金 / Chainway 公开插件
└── plugins/             # 自动发现：增删目录即可
    ├── uhf / n1-scanner / hc-power / nlsdk / serial-port   # Newland HC
    ├── usb-camera                                          # USB 摄像头人脸
    ├── zijin-uhf / zijin-scan / zijin-fingerprint          # 紫金设备
    └── cw-*                                                # 内部厂商 AAR/JAR
```

`settings.gradle` 会扫描 `plugins/` 下除 `_` 开头以外、且含 `build.gradle` 的目录。  
`newland-all` / `zijin-all` 分别聚合 `newland.bundle=newland`（默认）和 `newland.bundle=zijin` 的公开模块。 Cordova 原工程在 `cordova-plugins/`，仅作对照，不参与发布。

## 接入

仓库：`https://github.com/violet000/NewLandPlugins`（以你实际推送的地址为准）

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 按需引入（推荐）

```gradle
dependencies {
    implementation platform('com.github.violet000.NewLandPlugins:newland-bom:1.1.6')
    implementation 'com.github.violet000.NewLandPlugins:uhf'
    implementation 'com.github.violet000.NewLandPlugins:n1-scanner'
}
```

### 一次引入全部公开插件

```gradle
dependencies {
    implementation 'com.github.violet000.NewLandPlugins:newland-all:1.1.6'
    // 紫金 / Chainway（原 Cordova 插件）：
    // implementation 'com.github.violet000.NewLandPlugins:zijin-all:1.1.6'
}
```

只使用某一个插件时不要依赖 `*-all`，避免把用不到的 so / 厂商库打进 APK。

紫金能力按需引入：

```gradle
implementation 'com.github.violet000.NewLandPlugins:zijin-uhf:1.1.6'
implementation 'com.github.violet000.NewLandPlugins:zijin-scan:1.1.6'
implementation 'com.github.violet000.NewLandPlugins:zijin-fingerprint:1.1.6'
implementation 'com.github.violet000.NewLandPlugins:usb-camera:1.1.6'
```

## 用法

建议在 `Application.onCreate()` 里发现已安装插件（可选，不影响直接调各插件 API）：

```java
NewLand.init(this);
// 或 PluginRegistry.discover(this);
```

### UHF

```java
UHFReader reader = UHFReader.getInstance();
UHFReaderResult<Boolean> connect = reader.connect();
if (connect.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
    reader.setOnInventoryDataListener(tags -> { /* 盘点 */ });
    reader.startInventory();
}
```

停止盘点：`reader.stopInventory()`；退出：`reader.disConnect()`。

### N1 红外扫码

机型前提：HC N1（或兼容），默认 `/dev/ttyS1` @ `115200`，电源控制走 `/dev/iodev`。当前 HC native 以 **arm64-v8a** 为主。

```java
N1Scanner scanner = new N1Scanner();
scanner.setListener((data, len) -> {
    String barcode = new String(data, 0, len);
});
scanner.open();       // 或 open("/dev/ttyS1", 115200)
scanner.startScan();
// onPause
scanner.close();
```

### 紫金 UHF / 扫码 / 指纹（原 Cordova）

已去掉 Cordova 依赖，原生 Android 直接调。页面可见性变化时转发 `onResume` / `onPause`，退出时 `destroy()`。

```java
ResultCallback callback = new ResultCallback() {
    @Override public void onSuccess(String data) { }
    @Override public void onError(String message) { }
};

ZijinUhf uhf = new ZijinUhf(this);
uhf.open(callback);
uhf.startInventory(callback);

ZijinScan scan = new ZijinScan(this);
scan.scan(callback);

ZijinFingerprint fp = new ZijinFingerprint(this);
fp.open(callback);
fp.scan(callback);
```

### USB 摄像头人脸

人脸 Activity 来自 [violet000/USBCamera](https://github.com/violet000/USBCamera)（JitPack `1.0.5`），底层 UVC 为其传递依赖 [violet000/AndroidUVCCamera](https://github.com/violet000/AndroidUVCCamera)。本机摄像头默认**前置**、**横屏**；关闭页面时会停预览并 `release` 系统相机。USB 外接走 `startFaceVerifyByUsbCamera`。

```java
UsbCamera camera = new UsbCamera();
// USB 外接
camera.startFaceVerifyByUsbCamera(this, size, baseUrl);
// 手机前置摄像头
camera.startFaceVerifyByCamera(this, baseUrl);
// 需要后置时：camera.startFaceVerifyByCamera(this, baseUrl, Camera.CameraInfo.CAMERA_FACING_BACK);

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    camera.onActivityResult(this, requestCode, resultCode, data, callback);
}
```

## 添加插件

1. 复制 `plugins/_template/` 为 `plugins/your-id/`（目录名不要以下划线开头）。
2. 改 `gradle.properties`：`newland.artifact`、`newland.public`、`newland.bundle`（`newland` 或 `zijin`）、说明文案。
3. 实现 `HardwarePlugin`，并在 `AndroidManifest.xml` 声明：

```xml
<application>
    <meta-data
        android:name="com.newland.plugin.your-id"
        android:value="com.newland.plugins.yourid.YourPlugin" />
</application>
```

4. 对外 API 放在独立 package，不要改已有插件的包名。
5. 本地检查：`./gradlew listPlugins`，再 `./gradlew :plugins:your-id:assembleRelease`。
6. 打 tag 后 JitPack 会发布 `com.github.violet000.NewLandPlugins:your-id:<tag>`。

**不必**修改 `settings.gradle`。公开插件会按 `newland.bundle` 自动进入 `newland-all` 或 `zijin-all`，并写入 BOM。

## 移除 / 下线插件

- 删除或移走 `plugins/xxx/` 即可，聚合模块与 BOM 会跟着变。
- 已发布的旧版本不受影响；新 tag 不再包含该 artifact。
- 内部模块（`newland.public=false`）不会被 `newland-all` 直接暴露，但仍会随公开插件传递依赖发布，供 JitPack 解析。

## 本地构建与发布

```bash
./gradlew listPlugins
./gradlew publishToMavenLocal -x test
```

JitPack：把本目录作为 **新仓库** 推到 GitHub（建议仓库名 `NewLandPlugins`），打 tag（如 `1.0.0`），打开 [jitpack.io](https://jitpack.io/#violet000/NewLandPlugins) 选择模块。`jitpack.yml` 已指定 JDK 17。

JitPack 多模块坐标为 `com.github.<user>.<repo>:<artifact>:<tag>`。根目录 `GROUP=com.github.violet000` 用于本地 `mavenLocal()`。

原先独立的 `NewLandHCUHF/`、`NewlandN1/`、`cordova-plugins/` 目录仅作迁移对照，已被 `.gitignore` 排除。

## 版本

根目录 `gradle.properties` 中的 `VERSION_NAME` 用于本地 Maven；JitPack 对外版本以 **Git tag** 为准。

## License

封装与示例代码按本仓库约定使用。Newland / HC 的 jar、so 请遵守厂商许可，开源分发前请确认是否允许再分发二进制。
