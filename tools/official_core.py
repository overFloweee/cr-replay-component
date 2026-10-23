# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""恢复同一国服引擎的加载登记；不修改游戏指令、规则或胜负计算。"""
import sys
if sys.flags.optimize:
    raise RuntimeError("Python optimization disables required integrity assertions")
import hashlib
import json
import struct
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def restore_core(original, inputs, metadata):
    if sha(original) != metadata['sourceLibSha256']:
        raise ValueError('国服引擎来源摘要不符')
    files = {}
    for name, expected in metadata['inputs'].items():
        data = (Path(inputs) / name).read_bytes()
        if sha(data) != expected:
            raise ValueError('国服引擎输入摘要不符：' + name)
        files[name] = data
    raw = bytearray(original)
    rx, symbols = files['runtime-rx.bin'], files['runtime-dynsym.bin']
    assert len(rx) == 0x1b3cd60 and len(symbols) == 0x2b50
    assert rx[:4] == b'\x7fELF' and struct.unpack_from('<H', rx, 18)[0] == 183
    raw[:len(rx)] = rx
    raw[0x1d217b0:0x1d217b0 + len(symbols)] = symbols
    constructors = []
    for off in range(0x3428, 0x3428 + 0x33dc50, 24):
        address, info, target = struct.unpack_from('<QQq', raw, off)
        if 0x1c6d680 <= address < 0x1c6d680 + 871 * 8:
            assert info == 1027 and 0 < target < 0x1db0000
            constructors.append(address)
    assert len(constructors) == len(set(constructors)) == 871
    assert struct.unpack_from('<qQ', raw, 0x1d244c0) == (12, 0x1dad33c)
    struct.pack_into('<qQ', raw, 0x1d244c0, 25, 0x1c6d680)
    assert struct.unpack_from('<qQ', raw, 0x1d244f0) == (0, 0)
    struct.pack_into('<qQ', raw, 0x1d244f0, 27, 871 * 8)
    struct.pack_into('<qQ', raw, 0x1d24500, 0, 0)
    assert struct.unpack_from('<qQ', raw, 0x1d243b0) == (1, 0x2fb0)
    struct.pack_into('<qQ', raw, 0x1d243b0, 21, 0)
    for i in (454, 459):
        struct.pack_into('<H', raw, 0x1d217b0 + i * 24 + 6, 0xfff1)
        struct.pack_into('<Q', raw, 0x1d217b0 + i * 24 + 8, 0)

    def elf_hash(name):
        value = 0
        for char in name:
            value = (value << 4) + char
            high = value & 0xf0000000
            if high:
                value ^= high >> 24
            value &= ~high
        return value

    buckets, chains, names = [0] * 97, [0] * 462, {}
    for i in range(1, 462):
        name = struct.unpack_from('<I', raw, 0x1d217b0 + i * 24)[0]
        assert name < 0x2fff
        at = 0x341078 + name
        label = bytes(raw[at:raw.index(0, at)])
        names[i] = label
        if label:
            bucket = elf_hash(label) % 97
            chains[i], buckets[bucket] = buckets[bucket], i
    binary = struct.pack('<561I', 97, 462, *buckets, *chains)
    raw[0x1d20400:0x1d20400 + len(binary)] = binary
    assert struct.unpack_from('<qQ', raw, 0x1d244b0) == (0x6ffffef5, 0x3138)
    struct.pack_into('<qQ', raw, 0x1d244b0, 4, 0x1db4400)
    exports = json.loads(files['runtime-exports.json'])
    checked = 0
    for entry in exports:
        if entry['type'] != 'function':
            continue
        label = entry['name'].encode()
        i = buckets[elf_hash(label) % 97]
        while i and names[i] != label:
            i = chains[i]
        assert i and struct.unpack_from('<Q', raw, 0x1d217b0 + i * 24 + 8)[0] == int(entry['offset'], 16)
        checked += 1
    assert checked == 96
    for offset, address, symbol, label, wrapper in (
        (0x340df0, 0x1c72958, 8, b'fwrite', 0x1dad7d8),
        (0x340e08, 0x1c72968, 10, b'fclose', 0x1dad8b4),
        (0x340e20, 0x1c729f0, 27, b'fread', 0x1dad6fc),
    ):
        assert names[symbol] == label
        assert struct.unpack_from('<QQq', raw, offset) == (address, 1027, wrapper)
        assert (offset - 0x3428) // 24 >= 0x228e0
        struct.pack_into('<QQq', raw, offset, address, (symbol << 32) | 1026, 0)
    assert sha(raw) == metadata['coreSha256'] and len(raw) == metadata['coreBytes']
    return raw
