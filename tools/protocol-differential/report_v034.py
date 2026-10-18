#!/usr/bin/env python3
"""Publish a compact v0.3.4 evidence manifest; raw APKs/traces/screens stay local."""
import argparse, collections, hashlib, json, os, re
from pathlib import Path
import xml.etree.ElementTree as ET

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def tree_digest(paths):
    h=hashlib.sha256()
    for p in sorted(paths):
        h.update(p.as_posix().encode()+b'\0');h.update(p.read_bytes());h.update(b'\0')
    return h.hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('evidence',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    root=args.evidence; traces=root/'differential'
    execution=json.loads((traces/'execution-validation.json').read_text())
    for name,expected in execution['hashes'].items(): assert digest(traces/name)==expected, f'Stale validation: {name}'
    batches={}
    for name,item in execution['batches'].items():
        assert not item['setup_errors'] and not item['downstream_errors']
        assert not any(e[0]=='openmobi' for e in item['step_errors'])
        folder=traces/('' if name=='main' else name)
        comparisons={}
        for reference,suffix in [('international',''),('chinese','-chinese')]:
            value=json.loads((folder/('comparison'+suffix+'.json')).read_text())
            comparisons[reference]={k:value[k] for k in ['comparisons','differences','unmapped_fields']}
        batches[name]={'sequences':item['scenarios'],'steps_per_apk':item['steps_per_apk'],
            'setup_errors':0,'downstream_errors':0,'step_exceptions':dict(collections.Counter(e[0]+': '+e[3]['type'] for e in item['step_errors'])),
            'comparisons':comparisons,'vendor_difference_paths':{x['path']:x['count'] for x in item['vendor_difference_fields']}}
    for item in execution['order_check'].values(): assert not item['calculation_or_active_state_differences']
    unit={};build=Path(os.environ['DEV_TEMP_BASE'])/'build/projects/open-mobifitness/outputs'
    for name,folder in [('core',build/'core/test-results/test'),('debug',build/'app/test-results/testDebugUnitTest'),('release',build/'app/test-results/testReleaseUnitTest')]:
        suites=[ET.parse(p).getroot() for p in folder.glob('TEST-*.xml')]
        unit[name]={k:sum(int(s.attrib.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
        assert unit[name]['tests']>0 and not any(unit[name][k] for k in ['failures','errors','skipped'])
    reviewed=set((root/'visual-review.txt').read_text().splitlines());ui={}
    for name in ['narrow','landscape','large','phone','dark','tablet']:
        assert name in reviewed and 'OK (1 test)' in (root/('ui-'+name+'.log')).read_text()
        folder=root/'screens'/name;files=sorted(folder.glob('*.png'));assert len(files)==10
        ui[name]={'environment':(folder/'environment.txt').read_text().strip(),'screens':{p.name:digest(p) for p in files},'visual_review':True}
    artifacts=json.loads((root/'artifact-validation.json').read_text())
    for variant,name in [('regular','OpenMOBI-0.3.4.apk'),('debug','OpenMOBI-0.3.4-debug.apk')]:assert artifacts[variant]['sha256']==digest(root/'release'/name)
    sources=[p for base in ['app/src','core/src','app/schemas'] for p in Path(base).rglob('*') if p.is_file()]
    report={'version':'0.3.4','base_commit':'59cf4fdb576f11296ed8c1871013fbf371f45771',
        'scope':'Original DEX callbacks and selected MotionData methods; independent product invariants; native UI, no hardware control',
        'references':{p.name:digest(p) for p in [Path('apk/莫比健身 2.1.14.apk'),Path('apk/莫比健身 4.5.16.1.apk')]},
        'sequences_per_apk':execution['unique_scenarios_per_apk'],'steps_per_apk':execution['unique_steps_per_apk'],
        'batches':batches,'order_check':execution['order_check'],'trace_hashes':execution['hashes'],
        'unit_tests':unit,'native_ui':ui,'artifacts':artifacts,'upgrade':json.loads((root/'upgrade-validation.json').read_text()),
        'source_tree_sha256':tree_digest(sources),
        'tool_sources':{p.name:digest(p) for p in sorted(Path('tools/protocol-differential').iterdir()) if p.suffix in ['.java','.py','.sh']},
        'review_notes':[
            'Main replay changes versus v0.3.3 are metadata, explicit invalidation, zero/unsigned feedback, supported increments and documented guards.',
            'Rower stress callbacks: speed and cadence match; 687 force differences clear old force at zero speed, 13 reject out-of-bound force. Power differences are truncation, zero-speed handling or validity guards.',
            'Raw defaults, missing fields, derived metrics, state layers and unmapped fields are not counted as matched product features.',
            'Each official APK has 106 recorded malformed-input step exceptions, including 48 Firebase-context limitations. These are not passing matches or proof of full-app crashes.',
            'Fifteen initial treadmill settlement failures were preserved and rerun with explicit STOP initialization. Existing active-state fixtures retain START.',
            'Only cached LastUpload labels and timestamps are excluded from order-repeat calculation equivalence; exclusions remain in raw evidence.'
        ],
        'limits':['No exhaustive model/firmware or radio/mechanical validation.','No original full-app recording, settlement or cloud parity.','No calibrated power/energy claim.','Additional stroke-detail product metrics, unconfirmed 3209 selection and offline histories remain outside support.']}
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'version':report['version'],'sequences_per_apk':report['sequences_per_apk'],'steps_per_apk':report['steps_per_apk'],'unit_tests':unit,'ui_cases':len(ui),'ui_screens':sum(len(x['screens']) for x in ui.values())}))
if __name__=='__main__':main()
