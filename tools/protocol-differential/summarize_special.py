#!/usr/bin/env python3
"""Validate execution and retain compact, layer-specific special-rule observations.

Successful execution is not an assertion that a device rule is correct or fixed.
Run compare.py separately to retain the full projected differences as well.
"""
import argparse
import collections
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

from compare import load, stable
from validate import walk


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('directory',type=Path)
    root=parser.parse_args().directory
    cases=load(root/'cases.jsonl')
    refs={v:load(root/(v+'.jsonl')) for v in ('international','chinese','openmobi')}
    failures=[]
    for variant,rows in refs.items():
        assert rows.keys()==cases.keys(),variant
        for id,row in rows.items():
            if 'error' in row:
                failures.append(dict(variant=variant,id=id,layer='setup',error=row['error']))
                continue
            assert len(row['trace'])==len(cases[id]['steps']),(variant,id)
            for index,step in enumerate(row['trace']):
                assert step['index']==index,(variant,id,index)
                if 'error' in step:
                    failures.append(dict(variant=variant,id=id,index=index,layer='receive',error=step['error']))
                for event in step.get('events',[]):
                    if 'downstreamError' in event:
                        failures.append(dict(variant=variant,id=id,index=index,layer='motion',error=event['downstreamError']))
    vendor_diffs=collections.Counter()
    examples={}
    for id in cases:
        for path,a,b in walk(stable(refs['international'][id]),stable(refs['chinese'][id])):
            vendor_diffs[path]+=1
            examples.setdefault(path,dict(id=id,international=a,chinese=b))

    observations=[]
    for id in ('zero-v2-10-18-0-5-0','zero-v2-9-7-0-5-0',
               'zero-ftms-10-0-5','zero-ftms-11-0-5',
               'fold-v2-9-7-0','fold-v1-12-18','fold-v1-9-7','boundary-v2-10-18'):
        for index,step in enumerate(cases[id]['steps']):
            if 'tick' in step:
                continue
            values={}
            for variant,rows in refs.items():
                trace=rows[id]['trace'][index]
                state=trace.get('state',{})
                if variant=='openmobi':
                    details=state.get('Details',{})
                    values[variant]=dict(resistance=(trace.get('metrics') or {}).get('Resistance'),
                        merged_resistance=(trace.get('merged') or {}).get('Resistance'),
                        range=state.get('Range'),mode=state.get('ResistanceMode'),
                        energy_power=state.get('EnergyPower'),fold=details.get('FoldedState'),support_fold=details.get('SupportFold'))
                else:
                    tc=state.get('TreadmillConstant',{})
                    motion=trace.get('motion',{})
                    values[variant]=dict(feedback=[e['args'][0] for e in trace.get('events',[]) if e['method']=='updateResistance'],
                        resistance=motion.get('Resistance'),power=motion.get('Power'),
                        capability=state.get('ResistanceConstant'),fold=tc.get('FoldedState'),support_fold=tc.get('SupportFold'))
            observations.append(dict(id=id,index=index,input=step,values=values))

    def test_counts(paths):
        suites=[ET.parse(p).getroot() for p in paths]
        return {key:sum(int(s.get(key,'0')) for s in suites) for key in ('tests','failures','errors','skipped')}
    files=[root/'cases.jsonl',*(root/(v+'.jsonl') for v in refs),root/'app-audit-results.xml']
    report=dict(baseline='59cf4fd',scenarios=len(cases),steps_per_apk=sum(len(q['steps']) for q in cases.values()),
                failures=failures,
                vendor_differences=[dict(path=p,count=n,example=examples[p]) for p,n in vendor_diffs.most_common()],
                audit_tests=test_counts([root/'app-audit-results.xml']),
                standard_tests=test_counts(sorted((root/'standard-test-results').glob('TEST-*.xml'))),
                hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
                observations=observations,
                limitations=['Synthetic receive/calculation injection; no physical BLE or motor action.',
                             'Zero minimum is a test declaration, not evidence that AT-R starts at zero.',
                             'A13/A14 pass when the existing gap is reproduced; production code is unchanged.',
                             'No complete UI, official persistence, firmware matrix or mechanical validation.'])
    (root/'special-rules-summary.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ('hashes','observations','vendor_differences')},indent=2))
    assert not failures,'Resolve execution failures before claiming coverage'


if __name__=='__main__':
    main()
