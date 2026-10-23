# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""验证真实打包入口的稳定性、内容保留及发布幂等性，不访问 COS。"""
import hashlib
import importlib.util
import io
import json
import os
import sys
import tempfile
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("resource_archive", root / "tools/resource_archive.py")
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)


def update(path, reverse, date):
    entries = [("fingerprint.json", b'{"version":"fixture"}'), ("sc/battle.sc", b"battle" * 100)]
    with zipfile.ZipFile(path, "w") as output:
        for name, data in reversed(entries) if reverse else entries:
            info = zipfile.ZipInfo(name, date)
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, data)


def inspect(path):
    with zipfile.ZipFile(path) as output:
        inventory = json.loads(output.read("ready.json"))
        for item in inventory["files"]:
            data = output.read(item["path"])
            assert len(data) == item["bytes"]
            assert hashlib.sha256(data).hexdigest() == item["sha256"]
        assert inventory["unpackedBytes"] == sum(item["bytes"] for item in inventory["files"])
        assert "assets/code.js" not in output.namelist()
        with zipfile.ZipFile(io.BytesIO(output.read("assets/replay-resources.zip"))) as nested:
            assert nested.read("sc/battle.sc") == b"battle" * 100
        return inventory


with tempfile.TemporaryDirectory() as directory:
    folder = Path(directory)
    assets = folder / "assets"
    assets.mkdir()
    (assets / "battle.txt").write_bytes(b"unchanged")
    (assets / "code.js").write_bytes(b"excluded")
    update(assets / "replay-resources.zip", False, (2025, 1, 1, 0, 0, 0))
    reference = folder / "reference.apk"
    with zipfile.ZipFile(reference, "w") as apk:
        apk.write(assets / "battle.txt", "assets/battle.txt", compress_type=zipfile.ZIP_STORED)
    first, second, changed = [folder / name for name in ("first.pack", "second.pack", "changed.pack")]
    archive.build_archive(assets, reference, first)
    update(assets / "replay-resources.zip", True, (2026, 10, 9, 12, 0, 0))
    for source in assets.iterdir():
        os.utime(source, (1700000000, 1700000000))
        source.chmod(0o600)
    archive.build_archive(assets, reference, second)
    assert first.read_bytes() == second.read_bytes(), "时间戳、条目顺序或权限导致资源版本变化"
    assert inspect(first) == inspect(second)
    with zipfile.ZipFile(first) as output:
        assert output.getinfo("assets/battle.txt").compress_type == zipfile.ZIP_STORED
    (assets / "battle.txt").write_bytes(b"changed")
    archive.build_archive(assets, reference, changed)
    assert hashlib.sha256(first.read_bytes()).digest() != hashlib.sha256(changed.read_bytes()).digest()
    inspect(changed)
    with zipfile.ZipFile(assets / "replay-resources.zip", "w") as nested:
        nested.writestr("fingerprint.json", b'{"version":"changed"}')
        nested.writestr("sc/battle.sc", b"battle" * 100)
    archive.build_archive(assets, reference, second)
    assert hashlib.sha256(second.read_bytes()).digest() != hashlib.sha256(changed.read_bytes()).digest(), "内嵌素材变化未改变摘要"
print("PASS: stable archives across timestamps/order/permissions, content hashes, compression, code exclusion, changed content")
