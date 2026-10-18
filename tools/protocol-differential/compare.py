#!/usr/bin/env python3
"""Compare traces without turning callback defaults into unquestioned truth.

All differences and unmapped fields survive in artifacts. A mismatch is a review
candidate, NOT an assertion that OpenMOBI should copy the vendor behavior.
"""
import argparse, collections, hashlib, json, math
from pathlib import Path

def equal(a,b):
    if isinstance(a,(int,float)) and not isinstance(a,bool) and isinstance(b,(int,float)) and not isinstance(b,bool):
        return math.isclose(a,b,rel_tol=1e-6,abs_tol=1e-6)
    return a==b
def stable(v):
    # Only timestamps and version-specific stack/source locations are excluded.
    if isinstance(v,dict): return {k:stable(x) for k,x in v.items() if k not in ('Start_time','ConnectTime','stack')}
    if isinstance(v,list): return [stable(x) for x in v]
    return v
def load(path):
    rows=[json.loads(l) for l in path.read_text().splitlines()]
    assert len({r['id'] for r in rows})==len(rows),f'duplicate IDs: {path}'
    return {r['id']:r for r in rows}
def convert(v,m):
    return v*m if isinstance(v,(int,float)) else v
def projected(events,kind):
    result={}; unmapped=[]
    for e in events:
        n=e['method']; a=e.get('args',[]); v=a[0] if a else None
        if n=='updateBikeOrEllipticalRPM':
            if not a[1]: result['Cadence']=v
        elif n=='updateResistance': result['Resistance']=v
        elif n=='upDumbbellWeight': result['LoadKg']=v
        elif n in ('upGeneralSportData','updateTreadmillData','upDumbbellData','upSkippingRopeData','updateInstantData'):
            if n in ('upGeneralSportData','updateTreadmillData'):
                treadmill=n=='updateTreadmillData'
                fields={'Speed':('SpeedMps',1/3.6 if treadmill else 1),'Distance':('DistanceM',1),
                        'Calories':('CaloriesKcal',1),'Incline':('InclinePercent',1),'Duration':('DeviceDurationSec',1),
                        'Frequency':('StepRate' if treadmill else 'Cadence',1),'Power':('PowerW',1),'HeartRate':('HeartBpm',1),
                        'Resistance':('Resistance',1),'StepDistance':('StrideM',.01),'StepCount':('StepCount',1)}
                if kind==9: fields['Strokes']=('Strokes',1)
            elif n=='upDumbbellData': fields={s:(t,1) for s,t in [('TotalNumber','Repetitions'),('Duration','DeviceDurationSec'),('FewActions','DumbbellFewActions'),('ActionNumber','DumbbellActionNumber')]}
            elif n=='upSkippingRopeData': fields={s:(t,1) for s,t in [('TotalNumber','JumpCount'),('Duration','DeviceDurationSec'),('Frequency','Cadence'),('Calories','CaloriesKcal'),('ContinuousJump','ContinuousJumps'),('Interrupts','JumpInterruptions')]}
            else:
                # InstantData is a calculation record, not final MotionData output.
                fields={'AvgF':('ForceN',1)}
                if not a[1]: fields.update(Speed=('SpeedMps',1),Spm=('Cadence',1),Power=('PowerW',1))
            for source,x in v.items():
                if source in fields:
                    target,mult=fields[source];result[target]=convert(x,mult)
                else: unmapped.append((n,source,x))
        elif n=='rxBus' and e.get('type')=='HeartRateData':
            pass  # Compared at the accessory boundary, not as an equipment delta.
        elif n=='bus':
            for f,x in e.get('value',{}).items(): unmapped.append(('bus.'+e.get('type','unknown'),f,x))
        elif n not in ('updateDeviceType','updateMachineStatus','upTreadmillStatus','bus'):
            unmapped.append((n,'*',e))
    return result,unmapped

def main():
    p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('--reference',choices=['international','chinese'],default='international');a=p.parse_args();root=a.directory
    suffix='' if a.reference=='international' else '-chinese'
    cases=load(root/'cases.jsonl'); refs={v:load(root/(v+'.jsonl')) for v in ('international','chinese','openmobi')}
    for v,rows in refs.items():
        assert rows.keys()==cases.keys(),f'{v}: missing/extra cases'
        for id,r in rows.items():
            assert 'error' in r or len(r['trace'])==len(cases[id]['steps']),f'{v}/{id}: incomplete trace'
    diffs=[]; counts=collections.Counter(); errors=collections.Counter(); unmaps=collections.Counter(); vendor_diffs=[]; checks=0
    def compare(q,i,layer,field,expected,actual):
        nonlocal checks
        checks+=1
        if not equal(expected,actual):
            key=(q['group'],q['protocol'],q['kind'],q['steps'][i].get('chr','tick'),layer,field)
            counts[key]+=1
            diffs.append(dict(id=q['id'],index=i,layer=layer,field=field,official=expected,openmobi=actual))
    for id,q in cases.items():
        vendor=refs[a.reference][id];cn=refs['chinese' if a.reference=='international' else 'international'][id];ours=refs['openmobi'][id]
        if stable(vendor)!=stable(cn): vendor_diffs.append(id)
        for variant in refs:
            if 'error' in refs[variant][id]: errors[(variant,'setup',refs[variant][id]['error']['type'])]+=1
        if 'error' in vendor or 'error' in ours: continue
        accumulated={}
        for i,(step,v,o) in enumerate(zip(q['steps'],vendor['trace'],ours['trace'])):
            for variant in refs:
                r=refs[variant][id]
                if 'error' in r: continue
                row=r['trace'][i]
                if 'error' in row: errors[(variant,step.get('chr','tick'),row['error']['type'])]+=1
                for e in row.get('events',[]):
                    if 'downstreamError' in e: errors[(variant,'downstream',e['downstreamError']['type'])]+=1
            expected,unmapped=projected(v.get('events',[]),q['kind']); actual=o.get('metrics') or {}
            for n,f,x in unmapped: unmaps[(q['protocol'],n,f)]+=1
            for f,x in expected.items(): compare(q,i,'callback',f,x,actual.get(f))
            accumulated.update(expected)
            for f,x in accumulated.items(): compare(q,i,'accumulated-callback',f,x,(o.get('merged') or {}).get(f))
            # Extra public metrics also appear in the diff, including estimated power.
            for f,x in actual.items():
                if x is not None and f not in expected and f not in ('PowerEstimated','DistanceEstimated','CumulativeMotionMs'): compare(q,i,'extra-metric',f,None,x)
            chr=step.get('chr'); vs=v.get('state',{}); os=o.get('state',{}); details=os.get('Details') or {}
            if chr=='2a37' and q.get('captureRx'):
                hearts=[e['value']['HeartRate'] for e in v.get('events',[]) if e['method']=='rxBus' and e.get('type')=='HeartRateData']
                compare(q,i,'accessory','HeartBpm',hearts[-1] if hearts else None,o.get('heart'))
            rc=vs.get('ResistanceConstant',{}); tc=vs.get('TreadmillConstant',{})
            if chr in ('8802','8806'):
                compare(q,i,'capability','ResistanceMode',rc.get('ResistanceMode'),os.get('ResistanceMode'))
                for f,s in [('ResistanceMin','Min'),('ResistanceMax','Max')]: compare(q,i,'capability',f,rc.get(f),(os.get('Range') or {}).get(s))
            if chr=='8804': compare(q,i,'metadata','SignalAcquisitionScale',rc.get('SignalAcquisitionScale'),details.get('SignalAcquisitionScale'))
            if chr=='8805': compare(q,i,'metadata','Magnets',vs.get('magnets'),os.get('Magnets'))
            if chr in ('8807','8808','2ad4','2ad5','2acc'):
                for axis,fields in [('Speed',('SpeedAdjustmentMethod','LowSpeed','HighSpeed')),('Incline',('InclineAdjustmentMethod','InclineMin','InclineMax'))]:
                    for f,s in zip(fields,('Mode','Minimum','Maximum')): compare(q,i,'metadata',axis+'.'+s,tc.get(f),(details.get(axis) or {}).get(s))
            if chr=='2a19': compare(q,i,'metadata','Battery',vs.get('battery'),details.get('Battery'))
            if chr in ('880e','2ada'): compare(q,i,'status','MachineStatus',vs.get('machineStatus'),os.get('MachineStatus'))
            if chr in ('8901','8902'):
                compare(q,i,'metadata','FoldedState',tc.get('FoldedState'),details.get('FoldedState'))
                compare(q,i,'metadata','SupportFold',tc.get('SupportFold'),details.get('SupportFold'))
            for event in v.get('events',[]):
                if event['method']=='bus' and event.get('type') in ('SmallEquipmentControl','GamePadEvent','TmallCommandsEvent'):
                    val=event['value']
                    if event['type']=='SmallEquipmentControl':
                        compare(q,i,'interaction','Command',val['InstructionType'],(details.get('Interaction') or {}).get('Command'))
                        compare(q,i,'interaction','Value',val['Instruction'],(details.get('Interaction') or {}).get('Value'))
                    else:
                        f,source=('GamePad','Data') if event['type']=='GamePadEvent' else ('VoiceCommand','Code')
                        compare(q,i,'interaction',f,val[source],details.get(f))
            if 'motion' in v and chr=='8811' and q['kind'] in (10,11) and any(e['method']=='updateBikeOrEllipticalRPM' and not e['args'][1] for e in v.get('events',[])):
                compare(q,i,'MotionData','PowerW',v['motion']['Power'],(o.get('merged') or {}).get('PowerW'))
                compare(q,i,'MotionData','Cadence',v['motion']['Rpm'],(o.get('merged') or {}).get('Cadence'))
            if 'motion' in v and chr in ('8813','2ad2','2ace','2acd','2ad1') and q['kind'] in (9,10,11,12):
                mapping={'Speed':'SpeedMps','Power':'PowerW','Distance':'DistanceM','Duration':'DeviceDurationSec',
                    'Frequency' if q['kind']==12 else 'Spm' if q['kind']==9 else 'Rpm':'StepRate' if q['kind']==12 else 'Cadence'}
                for src,target in mapping.items(): compare(q,i,'MotionData',target,v['motion'][src],(o.get('merged') or {}).get(target))
    summary=dict(reference=a.reference,scenarios=len(cases),steps_per_apk=sum(len(q['steps']) for q in cases.values()),comparisons=checks,
        differences=len(diffs),vendor_difference_cases=vendor_diffs,
        errors=[dict(variant=k[0],boundary=k[1],type=k[2],count=v) for k,v in sorted(errors.items())],
        groups=[dict(group=k[0],protocol=k[1],kind=k[2],characteristic=k[3],layer=k[4],field=k[5],count=v) for k,v in counts.most_common()],
        unmapped_fields=[dict(protocol=k[0],event=k[1],field=k[2],count=v) for k,v in unmaps.most_common()])
    (root/('differences'+suffix+'.jsonl')).write_text(''.join(json.dumps(r,separators=(',',':'))+'\n' for r in diffs))
    (root/('comparison'+suffix+'.json')).write_text(json.dumps(summary,indent=2)+'\n')
    hashes={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in [root/'cases.jsonl',*(root/(v+'.jsonl') for v in refs)]}
    (root/'trace-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('groups','unmapped_fields','vendor_difference_cases')},indent=2))
    print('Vendor difference cases:',len(vendor_diffs))
if __name__=='__main__':main()
