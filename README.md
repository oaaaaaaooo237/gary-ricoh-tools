# Gary趣味应用

[English](README.en.md)

安卓理光相机工具：关机画面编辑与替换、原厂图片恢复、XPan 宽幅与构图辅助线。

- Wi-Fi 换图：GR IV／GR IV HDF 1.20。
- SD 换图：GR IIIx 普通／Urban／HDF 1.70。
- 拍摄增强：基于 [GRKit 1.3.5](https://github.com/ahonn/GRKit/tree/v1.3.5)。

## 构建

需要 Python 3、JDK 21、Android SDK Platform 35 和 Build Tools 35.0.0，设置 `JAVA_HOME`、`ANDROID_HOME`。

```sh
python scripts/fetch_dependencies.py
python scripts/build.py
```

原厂图片放入不参与 Git 的 `private-assets/`：`GB_HDF.jpg`、`GB_GR4.jpg`、`gr3x-goodbye.jpg`、`gr3x-urban.jpg`。构建输出为 `build/Gary-Fun-Apps.apk`。

## 许可

[MIT](LICENSE)。GRKit 的版权及许可见 [LICENSES](LICENSES/)。SD 关机图流程研究参考 [radium-wang 的项目](https://github.com/radium-wang/ricoh-gr4-firmware-analysis-and-feature-expansion)。图片及固件资源不在本仓库 MIT 许可范围内。
