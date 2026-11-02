# Gary趣味应用 · Windows

Windows x64 独立程序，与安卓共享机型、固件门槛及 SD 核验核心。GR IV HDF 1.20 的 SD 拍摄增强已完成用户实机验证，宽幅和网格线均生效；其他机型的实机效果仍待验证。

## 使用

1. 解压完整目录，运行 `GaryRicohTools.exe`。首页选择「调整关机画面」或「拍摄增强」。
2. 电脑连接相机 Wi-Fi，程序自动读取型号和固件。顶部灰灯为未连接，绿灯为当前功能适用，红灯为不适用。多网卡可在设置中选择相机网络；检测在后台进行，无倒计时。
3. 关机画面通过「选择图片」导入新图或已处理成品；支持裁切、拖动选框、缩放、拉伸、保留原尺寸及背景填充。预览显示最终 JPEG。图片库和 `.grend` 工程可再次打开。
4. GR4／GR4 HDF 1.20：确认图片后点击「确认并替换」，程序复核连接、上传、检查接口结果；最后实际关机查看并记录显示结果。原厂恢复使用同一流程。
5. GR3X 普通／Urban／HDF 1.70：自动切换 SD 流程。通过读卡器选择实际卡根目录，依次暂存、读取相机完整结果、安装、读回、核对显示和清理。每阶段显示当前操作说明。系统无法直接识别卡片时可导出 ZIP，手动复制后读回。
6. 拍摄增强：按识别结果选择 XPan 宽幅、辅助线、胶片模拟、日期打印，准备 SD 文件。在相机执行后读回本轮报告，再分别核对取景与试拍效果。准备完成会弹出相机操作说明，点击确定后进入读回步骤。
7. 胶片模拟：在「选择胶片风格／导入滤镜」中统一选择内置胶片与本地自定义风格。可载入照片查看近似色彩效果，一次最多准备 40 个风格。机内 GRK / Fn 菜单切换风格。
8. 自定义滤镜：导入 `.cube` 或 Hald PNG/TIFF，选择输入色彩空间，在本机离线转换并保存。Lightroom 的 `.xmp` 需先应用到程序导出的 Hald 模板，再按原尺寸导出 sRGB PNG/TIFF 后导入。局部蒙版、修复、模糊等不能转换为色彩滤镜。
9. 日期打印：选择模块后随 SD 文件一起准备，日期样式、格式及位置在机内 GRK / Fn 菜单设置。左侧日期和构图均为示意，照片效果需试拍确认。

拍摄增强适用：GR3／GR3 HDF 2.10、2.20；GR3X／HDF／Urban 1.60、1.70；GR4／GR4 HDF 1.11、1.20。GR3 不开放关机图替换。SD 关机图 1.70 的实机执行、显示及恢复仍待验证。

中断后重新读取相机或卡片实际状态，不沿用本地“完成”标记，也不自动重传结果未知的 Wi-Fi 操作。数据存于当前 Windows 用户的 `LocalAppData/GaryRicohTools`。

窗口按屏幕可用区域打开，保留 Windows DPI 缩放；设置中可切换标准或紧凑窗口。正文13、标题17～20，较小窗口自动收紧首页间距。

插拔读卡器后展开卡片列表，会重新检测可用存储卡；已断开的选择会清空，无需重开页面。

## 开发

```powershell
./desktop/prepare-effects.ps1
./desktop/build.ps1
./desktop/diagnose.ps1
```

后者仅为合成设备与文件的底层诊断，不作为视觉操控或实机验收。架构与新增模块方法见 [ARCHITECTURE.md](ARCHITECTURE.md)。

转换器固定使用 GRKit 1.3.5 的 WASM 与 Node.js 24.14.0；准备脚本核验发布包及二进制哈希，打包附带许可。运行与转换无需联网。程序校验滤镜结构、CRC、目标版本与保存哈希，模块执行文件来自固定版本 GRKit，不导入用户程序。

## English

Extract the complete portable folder and launch `GaryRicohTools.exe`. Choose shutdown-image customization or shooting enhancements, then let the application identify the camera over its Wi-Fi network. The status lamp indicates disconnected, supported or unsupported for the active feature.

Use the single image picker to import a new photo or reopen a prepared image. Crop, pan, resize or add a background; the preview decodes the final JPEG. GR IV / IV HDF 1.20 use Wi-Fi replacement with automatic preflight and a final physical-display check. GR IIIx standard / Urban / HDF 1.70 use staged SD preparation, complete camera readbacks, installation, display confirmation and cleanup. GR III supports shooting enhancements only.

Enhancements use GRKit SD files: XPan framing, composition guides, film looks and date imprinting. The shared look library includes bundled styles and custom `.cube` / Hald PNG/TIFF imports, with offline conversion, saved entries and approximate photo previews. Lightroom `.xmp` presets require the exported Hald template workflow; direct XMP import is unavailable. Select date format and style on the camera.

Read the new execution report and check each selected module on the camera. Interrupted tasks require external evidence before continuing; uncertain Wi-Fi requests are never retried automatically. Framing and guides were confirmed by the user on GR IV HDF 1.20. New film/date effects and other models still require physical-camera verification. Windows GUI import, preview, Hald export and SD preparation were operated with synthetic camera/card fixtures. Android has not received these three new modules.

Windows fit the display's usable area while retaining native DPI scaling. Settings offer standard and compact sizes; smaller windows use tighter home-page spacing.

Open the card selector after inserting or removing a reader to refresh available cards. Disconnected selections are cleared without restarting the page.
