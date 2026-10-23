"""主 APK 规则生成与已发布公共资源复用；不重建资源包。"""
import hashlib
import json
import zipfile
from pathlib import PurePosixPath


def digest_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def valid_rule(name):
    path = PurePosixPath(name)
    return (name == 'data_manifest.toml' or name.startswith('csv_logic/')) and not path.is_absolute() and '..' not in path.parts and '\\' not in name and str(path) == name


def index_rules(source, target, registry):
    versions = {}
    with zipfile.ZipFile(source) as original:
        for entry in original.infolist():
            version, name = entry.filename.split('/', 1)
            if entry.is_dir() or version not in registry or not valid_rule(name):
                raise ValueError('规则路径未登记：' + entry.filename)
            files = versions.setdefault(version, {})
            if name in files or entry.file_size > 1024 * 1024:
                raise ValueError('规则重复或过大：' + entry.filename)
            data = original.read(entry)
            files[name] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        if set(versions) != set(registry):
            raise ValueError('规则版本不完整')
        for version, files in versions.items():
            digest = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            if len(files) != registry[version]['files'] or digest != registry[version]['inventorySha256']:
                raise ValueError('规则快照与登记不符：' + version)
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as output:
            for name, data in [('index.json', json.dumps(versions, sort_keys=True, separators=(',', ':')).encode()), *[(e.filename, original.read(e)) for e in original.infolist()]]:
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                output.writestr(info, data)
    return versions


def reuse_resources(source, target, registration, engine):
    if registration['engine'] != engine or source.stat().st_size != registration['bytes'] or digest_file(source) != registration['sha256']:
        raise ValueError('公共资源与已发布登记不符')
    with zipfile.ZipFile(source) as archive:
        inventory = json.loads(archive.read('ready.json'))
    if inventory['resourceSetId'] != engine:
        raise ValueError('公共资源身份不符')
    if source.resolve() != target.resolve():
        import platform
        import shutil
        import subprocess
        if platform.system() == 'Darwin':
            # APFS 写时复制：候选复用相同字节，避免再占用 1.44 GB。
            subprocess.run(['cp', '-c', source, target], check=True)
        else:
            shutil.copyfile(source, target)
    return inventory
