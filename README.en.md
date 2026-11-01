# Gary Fun Apps

[中文](README.md)

An Android utility for Ricoh cameras: shutdown-image editing and replacement, stock-image restoration, XPan framing and composition guides.

- Wi-Fi image replacement: GR IV / GR IV HDF 1.20.
- SD image replacement: GR IIIx standard / Urban / HDF 1.70.
- Shooting enhancements: powered by [GRKit 1.3.5](https://github.com/ahonn/GRKit/tree/v1.3.5).

## Build

Requires Python 3, JDK 21, Android SDK Platform 35 and Build Tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`.

```sh
python scripts/fetch_dependencies.py
python scripts/build.py
```

Place stock images in the Git-ignored `private-assets/` directory: `GB_HDF.jpg`, `GB_GR4.jpg`, `gr3x-goodbye.jpg`, `gr3x-urban.jpg`. Output: `build/Gary-Fun-Apps.apk`.

## License

[MIT](LICENSE). GRKit copyright and notices are preserved in [LICENSES](LICENSES/). SD shutdown-image research references [radium-wang's project](https://github.com/radium-wang/ricoh-gr4-firmware-analysis-and-feature-expansion). Images and firmware resources are outside this repository's MIT license.
