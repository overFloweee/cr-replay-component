#!/usr/bin/env python3
"""Build the standalone local host from explicitly supplied, hash-registered materials."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import zipfile
from official_engine import prepare_engine
from host_patch import patch_host
from replay_visuals import build_visuals
from resource_archive import build_archive
from replay_rules import digest_file
import replay_content

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/local'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-root', type=Path, required=True)
    parser.add_argument('--visual-source', type=Path, required=True)
    parser.add_argument('--apktool', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/opt/homebrew/share/android-commandlinetools')))
    args = parser.parse_args()
    if replay_content.render_java(ROOT) != (ROOT/'android/cn/crhistory/player/ReplayBootstrap.java').read_text():
        raise ValueError('Generated Java rules table differs from registration')
    os.environ['REPLAY_INPUT_ROOT'] = str(args.input_root.resolve())
    os.environ['REPLAY_VISUAL_SOURCE'] = str(args.visual_source.resolve())
    OUT.mkdir(parents=True, exist_ok=True)
    decoded = OUT/'decoded'
    if decoded.exists():
        raise ValueError('build/local/decoded already exists; retain evidence and remove only rebuildable intermediates before rebuilding')
    reference, engine = prepare_engine(ROOT, OUT, decoded, run, args.apktool)
    patch_host(decoded)
    # The private pack is rebuilt from the registered APK and update snapshot.
    pack = OUT/'resources.pack'
    inventory = build_archive(decoded/'assets', reference, pack, engine['engineId'])
    registration = dict(id=engine['engineId'], engine=engine['engineId'], bytes=pack.stat().st_size,
                        sha256=digest_file(pack))
    with zipfile.ZipFile(pack) as archive, zipfile.ZipFile(__import__('io').BytesIO(archive.read('assets/replay-resources.zip'))) as update:
        registration['runtimeBytes'] = sum(item.file_size for item in update.infolist())
    (OUT/'resource-registration.json').write_text(json.dumps(registration, indent=2)+'\n')
    build_visuals(ROOT, pack, OUT/'replay-visuals.zip')
    # Keep game materials only in ignored outputs. The APK needs its own generated rule bundles.
    shutil.rmtree(decoded/'assets')
    assets = decoded/'assets'; (assets/'crhistory').mkdir(parents=True); (assets/'component').mkdir()
    for name in ('replay-rules.zip', 'replay-visuals.zip'):
        shutil.copy2(OUT/name, assets/'crhistory'/name)
    shutil.copy2(OUT/'resource-registration.json', assets/'component/resource-registration.json')
    shutil.copy2(ROOT/'example/AndroidManifest.xml', decoded/'AndroidManifest.xml')
    sdk = args.sdk; bt = sdk/'build-tools/36.0.0'; android = sdk/'platforms/android-36/android.jar'
    host = {'Darwin':'darwin-x86_64','Linux':'linux-x86_64'}[platform.system()]
    clang = sdk/('ndk/27.1.12297006/toolchains/llvm/prebuilt/'+host+'/bin/aarch64-linux-android24-clang++')
    run(clang, '-std=c++17', '-O2', '-fPIC', '-fvisibility=hidden', '-shared', '-static-libstdc++',
        '-Wl,-z,max-page-size=16384', '-Wall', '-Wextra', '-Werror', '-o', decoded/'lib/arm64-v8a/libcrprobe.so', ROOT/'native/replay_bridge.cpp', '-llog', '-ldl')
    classes = OUT/'classes'; classes.mkdir(exist_ok=True)
    run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', android, '-d', classes, *sorted((ROOT/'android').rglob('*.java')))
    dex = OUT/'dex'; dex.mkdir(exist_ok=True)
    run(bt/'d8', '--lib', android, '--min-api', '30', '--output', dex, *sorted(classes.rglob('*.class')))
    run('java', '-Xmx2g', '-jar', args.apktool, 'b', '-f', '-j', '2', '-o', OUT/'unsigned.apk', decoded)
    with zipfile.ZipFile(OUT/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        index=2
        for source in sorted(dex.glob('classes*.dex')):
            while f'classes{index}.dex' in apk.namelist(): index+=1
            apk.write(source, f'classes{index}.dex'); index+=1
    run(bt/'zipalign', '-f', '-P', '16', '4', OUT/'unsigned.apk', OUT/'aligned.apk')
    # Local-only development signing material; never imported from the source application.
    private = ROOT/'.local'; private.mkdir(exist_ok=True); private.chmod(0o700)
    key = private/'development.p12'
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12', '-storepass', 'local-development', '-alias', 'local', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Local Replay Component')
        key.chmod(0o600)
    run(bt/'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:local-development', '--ks-key-alias', 'local', '--out', OUT/'ReplayComponent.apk', OUT/'aligned.apk')
    run(bt/'apksigner', 'verify', OUT/'ReplayComponent.apk')
    with zipfile.ZipFile(OUT/'ReplayComponent.apk') as apk:
        for name in ('replay-rules.zip','replay-visuals.zip'):
            if apk.read('assets/crhistory/'+name) != (OUT/name).read_bytes(): raise ValueError('Final bundle mismatch')
    sources = sorted([*ROOT.glob('tools/*.py'), *ROOT.glob('android/**/*.java'), *ROOT.glob('native/*.cpp'), *ROOT.glob('metadata/*.json'), ROOT/'example/AndroidManifest.xml'])
    proof = dict(sourceInputs={str(path.relative_to(ROOT)): digest_file(path) for path in sources},
                 apkSha256=digest_file(OUT/'ReplayComponent.apk'), apkBytes=(OUT/'ReplayComponent.apk').stat().st_size,
                 coreSha256=engine['coreSha256'], resourceRegistration=registration, resourceFiles=len(inventory['files']),
                 publicReleaseApproved=False, devicePlaybackVerified=False)
    (OUT/'build-proof.json').write_text(json.dumps(proof, indent=2)+'\n'); print(json.dumps(proof), flush=True)

if __name__=='__main__': main()
