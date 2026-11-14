"""Extract only the pinned GRKit converter and effect-preview data, not the web app."""
import hashlib, json, pathlib, sys, tarfile

archive, output = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
if hashlib.sha256(archive.read_bytes()).hexdigest() != "4050fbdf6419c3c95c8d0049ba59aea39037b6a53477f05dc8dc524b01f619ba":
    raise ValueError("GRKit site release hash mismatch")
with tarfile.open(archive) as tar:
    def copy(source, target):
        member = tar.getmember("./" + source)
        if not member.isfile() or member.size > 16 * 1024 * 1024:
            raise ValueError("Unexpected release member")
        path = output / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(tar.extractfile(member).read())
    for name in ("grkit_wasm-De6gzWck.js", "grkit_wasm_bg-C5t2Tqmz.wasm"):
        copy("assets/" + name, name)
    copy("THIRD_PARTY_LICENSES.txt", "GRKit-THIRD_PARTY_LICENSES.txt")
    for target in ("gr3-2.10", "gr4-1.11", "gr4-1.20", "gr4-1.20-0528", "gr4-1.21", "gr3-2.21"):
        prefix = "effects/" + target + "/"
        catalog = json.loads(tar.extractfile("./" + prefix + "catalog.json").read())
        copy(prefix + "catalog.json", "catalog/" + target + "/catalog.json")
        for entry in catalog["effects"]:
            preview = entry["preview"]
            if not preview.startswith("preview/") or ".." in preview or "\\" in preview:
                raise ValueError("Unexpected preview path")
            copy(prefix + preview, "catalog/" + target + "/" + preview)
(output / "package.json").write_text('{"type":"module"}\n', encoding="utf-8")
