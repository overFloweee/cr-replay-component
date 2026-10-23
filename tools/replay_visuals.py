# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""从已发布基础资源构建 38.4.11 局部动画覆盖，不使用未认证的 CDN 整库。"""
import hashlib
import io
import json
import sqlite3
import tempfile
import zipfile
from pathlib import Path

VERSION = '38.4.11'
ASSET = 'sc3d/characters/hero/hero_electrowizard/animation/asm_hero_electrowizard_battle'
SOURCE_SHA256 = 'addcc26eac62564cc3eef922ee886bc71d9c827a7f411faded8fc377b102a838'
BASE_SHA1 = '9a417392d6976addcace034e129f2bbbfbe55309'


def build_visuals(root, pack, output):
    source = Path(__import__('os').environ['REPLAY_VISUAL_SOURCE']).read_bytes()
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA256:
        raise ValueError('英雄电法官方动画来源摘要不符')
    with zipfile.ZipFile(pack) as outer:
        nested = io.BytesIO(outer.read('assets/replay-resources.zip')) if 'assets/replay-resources.zip' in outer.namelist() else pack
    with zipfile.ZipFile(nested) as inner:
        database = inner.read('assets.scdb')
        if hashlib.sha1(database).hexdigest() != BASE_SHA1:
            raise ValueError('动画覆盖的基础资源库不符')
        fingerprint = json.loads(inner.read('fingerprint.json'))
        hashes = {row['file']: row['sha'] for row in fingerprint['files']}
        for clip in ('hero_electrowizard_skill_attack.glb', 'hero_electrowizard_skill_attack_01.glb'):
            name = ASSET.rsplit('/', 1)[0] + '/' + clip
            if hashlib.sha1(inner.read(name)).hexdigest() != hashes[name]:
                raise ValueError('动画依赖缺失或摘要不符')
    with tempfile.TemporaryDirectory(dir=output.parent) as folder:
        path = Path(folder) / 'assets.scdb'
        path.write_bytes(database)
        baseline = Path(folder) / 'baseline.scdb'
        baseline.write_bytes(database)
        with sqlite3.connect(path) as changed, sqlite3.connect(baseline) as original:
            cursor = changed.execute('UPDATE asset SET data=? WHERE path=?', (source, ASSET))
            if cursor.rowcount != 1:
                raise ValueError('英雄电法动画记录缺失或重复')
            changed.commit()
            if changed.execute('PRAGMA quick_check').fetchone() != ('ok',):
                raise ValueError('动画资源库完整性检查失败')
            for table in ('meta', 'asset', 'sqlite_stat1', 'sqlite_master'):
                old = original.execute('SELECT * FROM ' + table + ' ORDER BY rowid').fetchall()
                new = changed.execute('SELECT * FROM ' + table + ' ORDER BY rowid').fetchall()
                differences = [(a, b) for a, b in zip(old, new) if a != b]
                if len(old) != len(new) or (table != 'asset' and differences):
                    raise ValueError('动画覆盖改变了其它资源或结构')
                if table == 'asset' and (len(differences) != 1 or differences[0][0][0] != ASSET
                        or differences[0][1] != (ASSET, source, *differences[0][0][2:])):
                    raise ValueError('动画覆盖超出目标一行')
        files = {'assets.scdb': path.read_bytes(), ASSET + '.json': source}
    inventory = {name: dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest()) for name, data in files.items()}
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in [('index.json', json.dumps({VERSION: inventory}, sort_keys=True).encode()),
                           *[(VERSION + '/' + name, data) for name, data in files.items()]]:
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    return inventory
