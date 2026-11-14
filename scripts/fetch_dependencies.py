"""Fetch the pinned upstream packages without changing their contents."""
import hashlib
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.6.0"
PACKAGES = {
    "gr3": "81a411a81c143cc19adffa9d67d54b8a558eaf68763ee5bb4192db05f2cf364c",
    "gr3x": "c9dc61d4312a30a25307e99936d19aedadec73ba144209d36dd67fa6f32d8b4e",
    "gr4": "ef3443559839f4f2996202938ceb680fa775bab6ed0bd71247975ba03413676f",
}

LEGACY = {'gr3': 'b97e9aa3817c2e9a1cc00d538c8f8a0df188c055674fe2a432f544a4a26071ae', 'gr3x': '73efffa80db1078c09f727155a459724e4b6e92b8daa8fbca461bf5d1d5ffaf3', 'gr4': '99401ae517bb4cf702a8a97852fedc0e13e22b899e96791ab84a47f00fb605a9'}

def fetch():
    destination = ROOT / "dependencies"
    destination.mkdir(exist_ok=True)
    for version, packages in ((VERSION, PACKAGES), ("1.3.5", LEGACY)):
        fetch_version(destination, version, packages)


def fetch_version(destination, version, packages):
    for family, expected in packages.items():
        name = f"GRKit-{version}-{family}.zip"
        target = destination / name
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == expected:
            print(name + " verified")
            continue
        request = urllib.request.Request(
            f"https://github.com/ahonn/GRKit/releases/download/v{version}/" + name,
            headers={"User-Agent": "Gary-Fun-Apps"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read(8 * 1024 * 1024 + 1)
        if hashlib.sha256(data).hexdigest() != expected:
            raise RuntimeError(name + " SHA-256 mismatch")
        temporary = target.with_suffix(".tmp")
        temporary.write_bytes(data)
        temporary.replace(target)
        print(name + " verified")

if __name__ == "__main__":
    fetch()
