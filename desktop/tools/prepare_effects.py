"""Extract only the pinned GRKit converter and effect-preview data, not the web app."""
import hashlib, json, pathlib, sys, tarfile

archive, output = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
if hashlib.sha256(archive.read_bytes()).hexdigest() != "160be4ddc00645e90e6b10e61f4a3b9164b02d01c5ab589ad14f7ff71dc39f13":
    raise ValueError("GRKit site release hash mismatch")
with tarfile.open(archive) as tar:
    def copy(source, target):
        member = tar.getmember("./" + source)
        if not member.isfile() or member.size > 16 * 1024 * 1024:
            raise ValueError("Unexpected release member")
        path = output / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(tar.extractfile(member).read())
    for name in ("grkit_wasm-Bv1y84tD.js", "grkit_wasm_bg-Ddg15dz0.wasm"):
        copy("assets/" + name, name)
    copy("THIRD_PARTY_LICENSES.txt", "GRKit-THIRD_PARTY_LICENSES.txt")
    for target in ("gr3-2.10", "gr4-1.11", "gr4-1.20", "gr4-1.20-0528"):
        prefix = "effects/" + target + "/"
        catalog = json.loads(tar.extractfile("./" + prefix + "catalog.json").read())
        copy(prefix + "catalog.json", "catalog/" + target + "/catalog.json")
        for entry in catalog["effects"]:
            preview = entry["preview"]
            if not preview.startswith("preview/") or ".." in preview or "\\" in preview:
                raise ValueError("Unexpected preview path")
            copy(prefix + preview, "catalog/" + target + "/" + preview)
(output / "package.json").write_text('{"type":"module"}\n', encoding="utf-8")
