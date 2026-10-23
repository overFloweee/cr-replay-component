# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""按登记的原始引擎和完整内容快照构建离线宿主。"""
import hashlib
import json
import os
import shutil
import tarfile
import zipfile
from pathlib import Path
from official_core import restore_core
from replay_rules import index_rules


def prepare_engine(root, out, decoded, run, apktool=None):
    metadata = json.loads((root / 'metadata/supported_engine.json').read_text())
    registry = json.loads((root / 'metadata/rules_registry.json').read_text())
    workspace = Path(os.environ['REPLAY_INPUT_ROOT'])
    apk = workspace / metadata['sourceApk']
    inputs = workspace / metadata['inputDirectory']
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == metadata['sourceApkSha256'], '原始 APK 摘要不符'
    for name, digest in metadata['inputs'].items():
        assert hashlib.sha256((inputs / name).read_bytes()).hexdigest() == digest, name + ' 摘要不符'
    run('java', '-Xmx2g', '-jar', apktool or root.parent / 'cr-replay-player/.deps/apktool.jar', 'd', '-f', '-o', decoded, apk)
    library = decoded / 'lib/arm64-v8a/libg.so'
    library.write_bytes(restore_core(library.read_bytes(), inputs, metadata))
    for abi in (decoded / 'lib').iterdir():
        if abi.name != 'arm64-v8a':
            shutil.rmtree(abi)
    assets = decoded / 'assets'
    # 原始规则按内容版本保存；每场进入原生加载器前选择对应快照。
    with tarfile.open(inputs / 'historical-content-38.4.3.tar') as historical, tarfile.open(inputs / 'content-update.tar') as current, zipfile.ZipFile(assets / 'replay-rules.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as rules:
        current_files = {member.name: member for member in current if member.isfile()}
        for member in sorted(historical.getmembers(), key=lambda item: item.name):
            path = Path(member.name)
            assert member.isfile() and path.parts[0] == 'update' and '..' not in path.parts and not path.is_absolute(), member.name
            name = Path(*path.parts[1:]).as_posix()
            assert name.startswith('csv_logic/') or name == 'data_manifest.toml', name
            with historical.extractfile(member) as source:
                old = source.read()
            if member.name in current_files:
                with current.extractfile(current_files[member.name]) as source:
                    new = source.read()
            else:
                new = (assets / name).read_bytes()
            for version, data in [('38.4.3', old), ('38.4.5', new)]:
                info = zipfile.ZipInfo(version + '/' + name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                rules.writestr(info, data)
        for version in sorted(set(registry) - {'38.4.3', '38.4.5'}):
            snapshot = 'content-rules-' + version + '.tar'
            assert snapshot in metadata['inputs'], '附加规则未登记摘要：' + version
            with tarfile.open(inputs / snapshot) as latest:
                for member in sorted(latest.getmembers(), key=lambda item: item.name):
                    path = Path(member.name)
                    assert member.isfile() and path.parts[0] == 'update' and '..' not in path.parts and not path.is_absolute(), member.name
                    name = Path(*path.parts[1:]).as_posix()
                    assert name.startswith('csv_logic/') or name == 'data_manifest.toml', name
                    info = zipfile.ZipInfo(version + '/' + name, (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    with latest.extractfile(member) as source:
                        rules.writestr(info, source.read())
    index_rules(assets / 'replay-rules.zip', out / 'replay-rules.zip', registry)
    # 更新包只含本地游戏素材；使用稳定 ZIP 作为现有资源准备链路的输入。
    with tarfile.open(inputs / 'content-update.tar') as source, zipfile.ZipFile(assets / 'replay-resources.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as output:
        for member in sorted(source.getmembers(), key=lambda item: item.name):
            if not member.isfile():
                continue
            path = Path(member.name)
            assert path.parts[0] == 'update' and '..' not in path.parts and not path.is_absolute(), member.name
            info = zipfile.ZipInfo(Path(*path.parts[1:]).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            with source.extractfile(member) as data, output.open(info, 'w') as destination:
                shutil.copyfileobj(data, destination, 1024 * 1024)
    # 主进程只使用本地示例能力；仅 :replay 初始化原生游戏和公开资源。
    application = decoded / 'smali/com/supercell/titan/TitanApplication.smali'
    text = application.read_text().replace('Lcom/ace/gshell/AceApplication;', 'Landroid/app/Application;')
    marker = '.method public onCreate()V\n    .locals 1'
    assert marker in text
    text = text.replace(marker, marker + '''
    invoke-static {p0}, Lcn/crhistory/player/ReplayBootstrap;->isReplayProcess(Landroid/content/Context;)Z
    move-result v0
    if-nez v0, :cr_replay_application
    invoke-super {p0}, Landroid/app/Application;->onCreate()V
    return-void
    :cr_replay_application
    invoke-static {p0}, Lcn/crhistory/player/ReplayBootstrap;->watchStartup(Landroid/content/Context;)V
    invoke-static {p0}, Lcn/crhistory/player/ExternalAssets;->attach(Landroid/content/Context;)V
''', 1)
    assert text.count("invoke-static {}, Lcom/supercell/titan/TitanApplication;->nOnApplicationCreate()V") == 1
    text = text.replace("    invoke-static {}, Lcom/supercell/titan/TitanApplication;->nOnApplicationCreate()V", "    invoke-static {}, Lcom/supercell/titan/TitanApplication;->nOnApplicationCreate()V\n    const-string v0, \"crprobe\"\n    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V")
    application.write_text(text)
    game = decoded / 'smali/com/supercell/clashroyale/GameApp.smali'
    game.parent.mkdir(parents=True, exist_ok=True)
    game.write_text('''.class public Lcom/supercell/clashroyale/GameApp;
.super Lcom/supercell/titan/GameApp;
.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Lcom/supercell/titan/GameApp;-><init>()V
    return-void
.end method
.method protected onCreate(Landroid/os/Bundle;)V
    .locals 0
    invoke-super {p0, p1}, Lcom/supercell/titan/GameApp;->onCreate(Landroid/os/Bundle;)V
    invoke-static {p0}, Lcn/crhistory/player/ReplayBootstrap;->begin(Landroid/app/Activity;)V
    return-void
.end method
.method public onBackPressed()V
    .locals 0
    invoke-static {p0}, Lcn/crhistory/player/ReplayBootstrap;->userExit(Landroid/app/Activity;)V
    return-void
.end method
.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 1
    invoke-static {p0, p1}, Lcn/crhistory/player/ReplayBootstrap;->onResultTouch(Landroid/app/Activity;Landroid/view/MotionEvent;)Z
    move-result v0
    if-nez v0, :cr_handled
    invoke-super {p0, p1}, Lcom/supercell/titan/GameApp;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v0
    return v0
    :cr_handled
    const/4 v0, 0x1
    return v0
.end method
''')
    (out / 'engine-build.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    return apk, metadata
