"""稳定打包回放素材，保留文件内容和既有压缩方式。"""
import hashlib
import json
import shutil
import tempfile
import zipfile
from contextlib import ExitStack
from pathlib import Path


def entry(name, compression):
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.compress_type = compression
    info._compresslevel = 1
    return info


def normalize_update(source, target):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, "w") as output:
        for item in sorted(original.infolist(), key=lambda item: item.filename):
            if item.is_dir():
                continue
            with original.open(item) as data, output.open(entry(item.filename, item.compress_type), "w") as destination:
                shutil.copyfileobj(data, destination, 1024 * 1024)
    target.seek(0)


def build_archive(assets, reference, target, resource_id="firstlight-15.535.86"):
    with zipfile.ZipFile(reference) as apk:
        compression = {item.filename: item.compress_type for item in apk.infolist()}
    files = []
    with zipfile.ZipFile(target, "w") as output:
        for source in sorted(Path(assets).rglob("*")):
            if not source.is_file() or source.name.startswith("libwbsafeedit") or source.suffix.lower() in (".dex", ".jar", ".so", ".js", ".lua", ".py", ".apk"):
                continue
            name = "assets/" + source.relative_to(assets).as_posix()
            with ExitStack() as stack:
                if name == "assets/replay-resources.zip":
                    data = stack.enter_context(tempfile.TemporaryFile())
                    normalize_update(source, data)
                else:
                    data = stack.enter_context(source.open("rb"))
                digest = hashlib.sha256()
                size = 0
                with output.open(entry(name, compression.get(name, zipfile.ZIP_DEFLATED)), "w") as destination:
                    for block in iter(lambda: data.read(1024 * 1024), b""):
                        digest.update(block)
                        size += len(block)
                        destination.write(block)
                files.append({"path": name, "bytes": size, "sha256": digest.hexdigest()})
        inventory = {"resourceSetId": resource_id, "files": files, "unpackedBytes": sum(file["bytes"] for file in files)}
        output.writestr(entry("ready.json", zipfile.ZIP_DEFLATED), json.dumps(inventory, sort_keys=True, separators=(",", ":")))
    return inventory
