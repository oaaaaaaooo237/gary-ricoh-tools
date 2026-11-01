"""Build the native Android app using the installed JDK and Android SDK."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).parent))
from fetch_dependencies import PACKAGES

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

def build():
    sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_value:
        raise RuntimeError("Set ANDROID_HOME to your Android SDK directory")
    sdk = Path(sdk_value)
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
    tools = sdk / "build-tools" / "35.0.0"
    android = sdk / "platforms" / "android-35" / "android.jar"
    suffix = ".exe" if os.name == "nt" else ""
    def jdk(name):
        return java / (name + suffix) if java else name
    output = ROOT / "build"
    for folder in ("classes", "dex", "assets"):
        path = output / folder
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True)
    resources = {
        "GB_HDF.jpg": "d4b8554a7b71094bd6f63d87d5113b8cfe9f4db5cd5ec5317b48244a3b0cbf96",
        "GB_GR4.jpg": "dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79",
        "gr3x-goodbye.jpg": "dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79",
        "gr3x-urban.jpg": "7a2154a6d24ed5e460d2a918783d593171e399562923da582801186098ffc89f",
    }
    for name, expected in resources.items():
        source = ROOT / "private-assets" / name
        if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise RuntimeError("Missing or invalid stock image: private-assets/" + name)
        shutil.copyfile(source, output / "assets" / name)
    for family, expected in PACKAGES.items():
        source = ROOT / "dependencies" / f"GRKit-1.3.5-{family}.zip"
        if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise RuntimeError("Run scripts/fetch_dependencies.py first")
        shutil.copyfile(source, output / "assets" / f"grkit-{family}.zip")
    sources = sorted((ROOT / "app" / "src").rglob("*.java"))
    run(jdk("javac"), "-encoding", "UTF-8", "-source", "8", "-target", "8",
        "-bootclasspath", str(tools / "core-lambda-stubs.jar") + os.pathsep + str(android),
        "-d", output / "classes", *sources)
    run(jdk("jar"), "cf", output / "classes.jar", "-C", output / "classes", ".")
    run(jdk("java"), "-cp", tools / "lib" / "d8.jar", "com.android.tools.r8.D8",
        "--lib", android, "--min-api", "26", "--output", output / "dex", output / "classes.jar")
    run(tools / ("aapt2" + suffix), "link", "-I", android, "--manifest",
        ROOT / "app" / "AndroidManifest.xml", "-A", output / "assets", "-o", output / "unsigned.apk")
    run(jdk("jar"), "uf", output / "unsigned.apk", "-C", output / "dex", "classes.dex")
    run(tools / ("zipalign" + suffix), "-f", "4", output / "unsigned.apk", output / "aligned.apk")
    key = output / "debug.jks"
    if not key.exists():
        run(jdk("keytool"), "-genkeypair", "-keystore", key, "-alias", "debug",
            "-storepass", "android", "-keypass", "android", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "10000", "-dname", "CN=Gary Fun Apps")
    apk = output / "Gary-Fun-Apps.apk"
    run(jdk("java"), "-jar", tools / "lib" / "apksigner.jar", "sign", "--ks", key,
        "--ks-key-alias", "debug", "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--out", apk, output / "aligned.apk")
    run(jdk("java"), "-jar", tools / "lib" / "apksigner.jar", "verify", apk)
    print("build/Gary-Fun-Apps.apk " + hashlib.sha256(apk.read_bytes()).hexdigest())

if __name__ == "__main__":
    build()
