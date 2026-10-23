# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""规则登记与显式临时映射；Java 表由同一输入生成。"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = 'metadata/rules_registry.json'
ALIASES = 'metadata/rules_aliases.json'
BEGIN = '    // BEGIN GENERATED REPLAY RULES'
END = '    // END GENERATED REPLAY RULES'


def catalog(root=ROOT, registry=None):
    registry = json.loads((root / REGISTRY).read_bytes()) if registry is None else registry
    aliases = json.loads((root / ALIASES).read_bytes())
    if not registry or any(not re.fullmatch(r'38\.4\.[0-9]+', v) for v in registry):
        raise ValueError('规则登记超出当前引擎系列')
    contents = {v: 4 if v == '38.4.3' else int(v.split('.')[-1]) for v in registry}
    if len(set(contents.values())) != len(contents) or not {4, 5} <= set(contents.values()):
        raise ValueError('规则内容版本冲突或缺少旧版基线')
    seen = set()
    for row in aliases:
        if (not re.fullmatch(r'38\.4\.[0-9]+', row['source']) or row['source'] in registry
                or type(row['content']) is not int or row['content'] <= 0
                or row['target'] not in registry or not row.get('evidence')
                or (row['source'], row['content']) in seen):
            raise ValueError('临时映射与精确规则冲突或缺少证据')
        seen.add((row['source'], row['content']))
    return registry, contents, aliases


def select_rules(source, content, root=ROOT):
    registry, contents, aliases = catalog(root)
    # 真实 38.4.9 回放可携带格式 10；只允许其精确来源使用已归档规则。
    if source == '38.4.9' and content == 10 and source in registry:
        return source
    supported = set(contents.values()) | {r['content'] for r in aliases}
    if content is not None and (type(content) is not int or content not in supported):
        raise ValueError('未支持的内容版本')
    content = 5 if content is None else content
    for row in aliases:
        if source == row['source'] and content == row['content']:
            return row['target']
    if any(source == row['source'] or (content == row['content'] and content not in contents.values()) for row in aliases):
        raise ValueError('临时映射必须明确匹配来源和内容版本')
    if source:
        if source not in registry:
            raise ValueError('未支持的来源版本')
        return source
    return next(v for v, c in contents.items() if c == content)


def render_java(root=ROOT, registry=None):
    registry, contents, aliases = catalog(root, registry)
    path = root / 'android/cn/crhistory/player/ReplayBootstrap.java'
    text = path.read_text(encoding='utf-8')
    if text.count(BEGIN) != 1 or text.count(END) != 1:
        raise ValueError('播放器生成区域改变，停止自动适配')
    versions = sorted(registry)
    strings = lambda xs: ','.join(json.dumps(x) for x in xs)
    block = BEGIN + '\n'
    block += '    private static final String[] RULE_SOURCES={' + strings(versions) + '};\n'
    block += '    private static final int[] RULE_CONTENTS={' + ','.join(str(contents[v]) for v in versions) + '};\n'
    block += '    private static final String[][] RULE_ALIASES={' + ','.join('{' + strings([r['source'], str(r['content']), r['target']]) + '}' for r in aliases) + '};\n'
    block += END
    start, stop = text.index(BEGIN), text.index(END) + len(END)
    updated = text[:start] + block + text[stop:]
    return updated


def update_java(root=ROOT):
    updated = render_java(root)
    path = root / 'android/cn/crhistory/player/ReplayBootstrap.java'
    if path.read_text(encoding='utf-8') != updated:
        path.write_text(updated, encoding='utf-8')
    _, contents, aliases = catalog(root)
    return sorted(set(contents.values()) | {r['content'] for r in aliases})
