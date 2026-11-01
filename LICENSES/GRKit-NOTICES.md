# Licenses

This file gives the licenses of the files in this package.

## GRKit

The files in `SD_ROOT/` are GRKit software, except the effect files. GRKit is licensed under the MIT License. The license text is in "GRKit license" below.

Each `CORE.BIN` file (`CORE1.BIN` and on, in a package for several firmware builds) also contains some bytes of the official RICOH firmware. GRKit uses them to identify the firmware before it changes the camera memory. These bytes are not GRKit software, and the MIT License does not apply to them.

The text in the menu icons and in the live view uses the [Roboto Condensed](https://github.com/googlefonts/roboto-classic) typeface, which is licensed under the SIL Open Font License 1.1. GRKit uses its Light, Bold, and Black weights, with only the ASCII characters.

The installation guide uses the [Noto Sans CJK](https://github.com/notofonts/noto-cjk) typefaces, which are licensed under the SIL Open Font License 1.1. Its code text uses DejaVu Sans Mono, which is licensed under the DejaVu Fonts License.

The screenshot in the installation guide shows the photo [Snow in Akihabara](https://www.flickr.com/photos/cafetricot/51803682755/) by nagi usano, which is licensed under [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/). The photo is cropped and scaled, and the screenshot shows the GRKit menu and the Portra 400 effect over it.

## Effects

Each file in `SD_ROOT/GRKIT/EFFECTS/` is one Film Look effect. Each effect of the GRKit catalog is a fit of one film simulation LUT. GRKit contributors made the fits. The files contain the fitted parameters, not the LUTs. "Changes to the LUTs" below describes the changes.

If an effect is not in the GRKit catalog, its author gives its license.

### spektrafilm

Portra 400, Gold 200, Ektar 100, Pro 400H, Superia 400, Vision3 250D, Vision3 500T, Portra 800 +2, Kodachrome 64, Velvia 100, and Ektachrome E100 derive from LUTs of [spektrafilm](https://github.com/andreavolpato/spektrafilm) 0.3.4 by Andrea Volpato.

These effects are licensed under the [Creative Commons Attribution-ShareAlike 4.0 International License](https://creativecommons.org/licenses/by-sa/4.0/) (CC BY-SA 4.0). The upstream notice is at <https://github.com/andreavolpato/spektrafilm/blob/main/SPEKTRAFILM_LICENSE.txt>. The effects are provided as is, without warranty. Section 5 of the license gives the disclaimer.

### Spectral Film LUT

Tri-X 400, Tri-X 400 Red, Aerocolor IV, Eterna 500T, Instax, and Double-X 5222 derive from LUTs of [Spectral Film LUT](https://github.com/JanLohse/spectral_film_lut) by Jan Lohse.

These effects are licensed under the MIT License. The copyright and permission notice is in "Spectral Film LUT license" below.

### t3mujinpack

HP5 Plus 400 derives from a HaldCLUT of [t3mujinpack](https://github.com/t3mujin/t3mujinpack) by João Almeida, a set of darktable film styles.

This effect is licensed under the MIT License. The copyright and permission notice is in "t3mujinpack license" below.

### Names

Effect names and film, paper, and filter names identify the simulated materials. Some of these names are trademarks of their owners. GRKit is not affiliated with or endorsed by RICOH IMAGING COMPANY, LTD., Eastman Kodak Company, Kodak Alaris, Fujifilm, or HARMAN technology Limited (Ilford Photo). The simulations are models of published data or hand-made styles, not measured equivalents of the products.

## Changes to the LUTs

```text
Changes to third-party LUTs (as asked by SPEKTRAFILM_LICENSE.txt)

No LUT, profile, or rendered LUT output is included in this repository. Each effect encodes the
look of one LUT as effect parameters of the GR cameras: a 3 x 3 look matrix, optional hue
sectors, three tone curves, and an optional 3 x 3 post-curve matrix. `grkit effects import` fits
them to the LUT output on a gray ramp, a color chart, and natural colors, and keeps colors
brightening with exposure as the LUT does.

spektrafilm LUTs (Andrea Volpato, https://github.com/andreavolpato/spektrafilm, spektrafilm 0.3.4,
git 3bb2c2d2801ff68b92019cf1dbcbb133d60832bc, CC BY-SA 4.0):
- Built with spektrafilm-lut: input Panasonic V-Log / V-Gamut, output sRGB, 33^3, default settings.
- Slide films (Kodachrome 64, Velvia 100, Ektachrome E100) are scanned directly
  (`io.scan_film`) instead of printed, because the LUT builder always prints and the print
  stocks are negative-working.
- Vision3 500T (on Supra Endura paper) is built with the scanner black and white correction on,
  at the default levels, as a lab scan of the print sets them.
  spektrafilm-lut turns the correction off for every LUT. Its references are fixed (the film base
  and the film's maximum density), so the LUT holds it.
- The "# Created:" header line is removed before the fit.
- Fitted as above by GRKit contributors.

Spectral Film LUT tables (Jan Lohse, https://github.com/JanLohse/spectral_film_lut, 0.18.13,
git bcbcfc6861f49cc706dc5d00ea048e4853060aab, MIT):
- Built with the library defaults: input Panasonic V-Log / V-Gamut, output sRGB, 33^3.
- The red filter effect multiplies the Tri-X 400 sensitivity by the Kodak Wratten 25
  transmission from the library's own data.
- Double-X 5222 uses the library's 12-minute development of Kodak 5222 on grade 3 paper.
- Fitted as above by GRKit contributors. The monochrome fits have identical matrix rows and
  curves; GRKit marks them as monochrome.

t3mujinpack HaldCLUT (João Almeida, https://github.com/t3mujin/t3mujinpack, git
0b421f3e25209ed78253d1724a29cc6255c5e7fe, MIT):
- HP5 Plus 400 is fitted with Standard input and +0.2 EV, so mid gray matches Standard.

bake_spektrafilm.py and bake_spectral_film_lut.py reproduce the LUTs. manifest.json records the
SHA-256 of each LUT that an effect was fitted from.
```

## GRKit license

```text
MIT License

Copyright (c) 2026 ahonn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Spectral Film LUT license

```text
MIT License

Copyright (c) 2026 Jan Lohse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## t3mujinpack license

```text
MIT License

Copyright (c) 2017 João Almeida

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
