#!/usr/bin/env python3
"""Execution completeness, vendor deltas and repeatability; not product correctness."""
import collections, hashlib, json, sys
from pathlib import Path
from compare import load, stable

def walk(a,b,path=''):
    if isinstance(a,dict) and isinstance(b,dict):
        for k in a.keys()|b.keys(): yield from walk(a.get(k),b.get(k),path+'/'+k)
    elif isinstance(a,list) and isinstance(b,list):
        for x,y in zip(a,b): yield from walk(x,y,path+'/*')
        if len(a)!=len(b): yield path+'/length',len(a),len(b)
    elif a!=b: yield path,a,b

def main():
    root=Path(sys.argv[1]);report={'batches':{},'order_check':{},'hashes':{}}
    for batch in ('','supplement','extra-rowers','special-rules','regression'):
        folder=root/batch;cases=load(folder/'cases.jsonl');rows={v:load(folder/(v+'.jsonl')) for v in ('international','chinese','openmobi')}
        setup=[];downstream=[];step_errors=[];paths=collections.Counter();examples={}
        for variant,rs in rows.items():
            assert rs.keys()==cases.keys()
            for id,r in rs.items():
                if r.get('error'):setup.append([variant,id,r['error']]);continue
                assert len(r['trace'])==len(cases[id]['steps'])
                for t in r['trace']:
                    if t.get('error'):step_errors.append([variant,id,t['index'],t['error']])
                    for e in t.get('events',[]):
                        if 'downstreamError' in e:downstream.append([variant,id,t['index'],e['downstreamError']])
        for id in cases:
            for path,a,b in walk(stable(rows['international'][id]),stable(rows['chinese'][id])):
                paths[path]+=1;examples.setdefault(path,dict(id=id,international=a,chinese=b))
        report['batches'][batch or 'main']=dict(scenarios=len(cases),steps_per_apk=sum(len(q['steps']) for q in cases.values()),
            setup_errors=setup,downstream_errors=downstream,step_errors=step_errors,vendor_difference_fields=[dict(path=k,count=v,example=examples[k]) for k,v in paths.most_common()])
        assert not setup and not downstream, 'Resolve execution-environment failures before claiming coverage'
        assert not any(e[0]=='openmobi' for e in step_errors), 'Unexpected product callback exception'
        for f in [folder/'cases.jsonl',*(folder/(v+'.jsonl') for v in rows)]:report['hashes'][str(f.relative_to(root))]=hashlib.sha256(f.read_bytes()).hexdigest()
    # These are constructor-time cached upload labels, not the active calculation
    # state. Raw differences remain documented rather than silently discarded.
    order_exclusions={'/trace/*/motion/LastUpload/Device_subtype','/trace/*/motion/LastUpload/Box_name'}
    for variant in ('international','chinese','openmobi'):
        original=load(root/(variant+'.jsonl'));repeat=load(root/'order-check'/(variant+'.jsonl'))
        assert repeat.keys()==load(root/'order-check/cases.jsonl').keys()
        all_diffs=collections.Counter();failures=[]
        for id,r in repeat.items():
            for path,a,b in walk(stable(original[id]),stable(r)):
                all_diffs[path]+=1
                if path not in order_exclusions:failures.append(dict(id=id,path=path,first=a,repeat=b))
        report['order_check'][variant]=dict(scenarios=len(repeat),raw_differences=dict(all_diffs),calculation_or_active_state_differences=failures)
        assert not failures, 'State leaked across synthetic cases'
    report['unique_scenarios_per_apk']=sum(b['scenarios'] for b in report['batches'].values())
    report['unique_steps_per_apk']=sum(b['steps_per_apk'] for b in report['batches'].values())
    (root/'execution-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k.startswith('unique_')},indent=2))
if __name__=='__main__':main()
