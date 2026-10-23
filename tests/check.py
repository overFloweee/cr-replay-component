#!/usr/bin/env python3
"""Host checks with synthetic fixtures only; downloads a pinned test-only JSON jar."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/local/tests'
sys.path.insert(0, str(ROOT / 'tools'))
from replay_rules import index_rules
import replay_content

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def write(name, entries):
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', UserWarning)
        with zipfile.ZipFile(OUT/name, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path, data in entries:
                archive.writestr(path, data)

def fixtures():
    entries = []
    index = {}
    for version in json.loads((ROOT/'metadata/rules_registry.json').read_text()):
        files = {'data_manifest.toml': ('version="'+version+'"').encode(), 'csv_logic/test.csv': b'fixture,value\n1,2\n'}
        index[version] = {name: {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name,data in files.items()}
        entries += [(version+'/'+name,data) for name,data in files.items()]
    registry = {v: {'files': len(files), 'inventorySha256': hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()} for v,files in index.items()}
    write('source.zip', entries)
    index_rules(OUT/'source.zip', OUT/'good.zip', registry)
    with zipfile.ZipFile(OUT/'good.zip') as archive:
        entries = [(e.filename,archive.read(e)) for e in archive.infolist()]
    write('bad-missing.zip', entries[:-1]); write('bad-duplicate.zip',entries+[entries[1]])
    write('bad-damaged.zip',[entries[0],(entries[1][0],b'broken'),*entries[2:]])
    write('bad-overflow.zip',[entries[0],(entries[1][0],b'X'*1048577),*entries[2:]])
    write('bad-path.zip',entries+[('38.4.3/csv_logic/../../escape',b'x')])
    write('bad-unknown.zip',entries+[('999.0.0/csv_logic/x',b'x')])
    write('bad-index.zip',entries[1:]); write('bad-empty.zip',[('index.json',b'{}')])
    (OUT/'bad-truncated.zip').write_bytes((OUT/'good.zip').read_bytes()[:100])
    for name in ('size','digest','index-path','budget'):
        bad=json.loads(entries[0][1]); first=next(iter(bad['38.4.3']))
        if name=='size': bad['38.4.3'][first]['bytes']+=1
        if name=='digest': bad['38.4.3'][first]['sha256']='0'*64
        if name=='index-path': bad['38.4.3']['csv_logic/../bad']=bad['38.4.3'].pop(first)
        if name=='budget':
            for files in bad.values():
                for spec in files.values(): spec['bytes']=1048576
            # A larger synthetic index exercises the aggregate limit, with individually valid entries.
            bad['38.4.3'].update({'csv_logic/extra%d.csv'%i:{'bytes':1048576,'sha256':'0'*64} for i in range(20)})
        write('bad-'+name+'.zip',[('index.json',json.dumps(bad).encode()),*entries[1:]])
    for bad in (entries[1:-1], entries[1:]+[entries[1]], [(entries[1][0],b'broken'),*entries[2:]]):
        write('invalid-source.zip',bad)
        try: index_rules(OUT/'invalid-source.zip',OUT/'invalid-output.zip',registry)
        except ValueError: pass
        else: raise AssertionError('invalid source accepted')
    visual={'assets.scdb':b'fixture database', 'sc3d/characters/hero/hero_electrowizard/animation/asm_hero_electrowizard_battle.json':b'{"synthetic":true}'}
    index={'38.4.11':{n:{'bytes':len(d),'sha256':hashlib.sha256(d).hexdigest()} for n,d in visual.items()}}
    entries=[('index.json',json.dumps(index).encode())]+[('38.4.11/'+n,d) for n,d in visual.items()]
    write('visuals-good.zip',entries)
    write('visuals-bad-damaged.zip',[entries[0],(entries[1][0],b'broken'),*entries[2:]])
    write('visuals-bad-path.zip',entries+[('38.4.11/../escape',b'x')])
    write('visuals-bad-duplicate.zip',entries+[entries[1]])
    write('visuals-bad-missing.zip',entries[:-1])

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert replay_content.render_java(ROOT)==(ROOT/'android/cn/crhistory/player/ReplayBootstrap.java').read_text()
    jar=OUT/'json-20180813.jar'
    if not jar.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20180813/json-20180813.jar', timeout=60) as response:
            jar.write_bytes(response.read(1000000))
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='518080049ba83181914419d11a25d9bc9833a2d729b6a6e7469fa52851356da8'
    fixtures()
    android=Path(os.environ.get('ANDROID_HOME','/opt/homebrew/share/android-commandlinetools'))/'platforms/android-36/android.jar'
    classes=OUT/'classes'; classes.mkdir(exist_ok=True)
    run('javac','-encoding','UTF-8','-cp',str(jar)+os.pathsep+str(android),'-d',classes,*sorted((ROOT/'android').rglob('*.java')),*sorted((ROOT/'tests').glob('*.java')))
    cp=os.pathsep.join(map(str,(classes,jar,android)))
    for name,args in [('CompatibilityCheck',[]),('DynamicCheck',[]),('cn.crhistory.player.RulesCheck',[OUT]),('cn.crhistory.player.PackBoundaryCheck',[OUT])]:
        run('java','-cp',cp,name,*args)
    run(sys.executable,ROOT/'tests/check_result.py')
    run(sys.executable,ROOT/'tests/check_archive.py')
    for flag in ('-O','-OO'):
        result=subprocess.run([sys.executable,flag,str(ROOT/'tools/official_core.py')],capture_output=True,text=True)
        assert result.returncode!=0 and 'Python optimization disables required integrity assertions' in result.stderr
    print('PASS: synthetic host checks; Android playback is recorded separately',flush=True)

if __name__=='__main__': main()
