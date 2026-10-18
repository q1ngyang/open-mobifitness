#!/usr/bin/env python3
"""Additional v0.3.4 regression sequences. Expected values never come from OpenMOBI.

Wire counters, explicit zero, sentinels, sparse updates, and recovery after malformed
metadata are replayed verbatim into each original APK and the current implementation.
Controller timing/Room/GATT/UI are tested separately and are not simulated here.
"""
import argparse, collections, json, random
from pathlib import Path
from generate import packet, be, init, agg, pulse, v1


def main():
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);root=p.parse_args().output
    cases=[]
    def add(group,protocol,kind,sub,steps,**kw):
        cases.append(dict(id=f'v034-{group}-{len(cases):04}',group=group,protocol=protocol,kind=kind,subtype=sub,
            name=kw.pop('name',{9:'MOBI-R',10:'MOBI-B',11:'MOBI-E',12:'MOBI-T',16:'MOBI-S'}[kind]),steps=steps,**kw))
    for name in ['AT-R66146','Other bike']:
        for counts in [[0,1,2,2,3],[100,101,100,0,1],[65533,65534,65535,0,1,2],[65534,0,1],[0,65535,0]]:
            steps=[packet('ffe4',v1(10,1,1000,count)[:13]) for count in counts]
            steps += [packet('ffe4',v1(10,1,0,counts[-1])[:13])]*6
            add('revolutions','V1',10,1,steps,name=name)
    for kind,sub in [(9,0),(9,1),(9,3),(9,7),(9,8),(9,9),(9,10),(10,18),(11,17),(12,18),(16,1),(16,2)]:
        for mode in [2,3]:
            for counts in [[65534,65535,0,1],[1000,0,1],[0,100,50,101]]:
                steps=init(kind,sub,mode)
                for i,count in enumerate(counts):
                    b=agg(kind,sub)['data'];b[3:5]=be(100+i);b[5:7]=be(count)
                    if kind==9:b[11:13]=be(count)
                    if kind==16 and sub==1:b[11:13]=be(count)
                    steps.append(packet('8813',b))
                add('counter-sequence','V2',kind,sub,steps)
    for kind,sub in [(10,18),(11,17)]:
        for minimum in [0,1]:
            for maximum in [1,24,32,128,255]:
                steps=init(kind,sub)+[packet('8806',[5,minimum,maximum]),pulse(1000)]
                for level in [minimum,maximum,0,1,127,128,254,255,minimum]:
                    steps += [packet('8812',[level]),{'tick':1},pulse(1000)]
                steps += [packet('8805',[0]),pulse(1000),packet('8805',[1]),pulse(1000)]
                add('zero-recovery','V2',kind,sub,steps,motion=True)
    frames=[('2ad2',10,[4,0,16,14,120,0],[5,0,0,0],[5,0,255,255]),
            ('2ace',11,[8,0,0,16,14,120,0,0,0],[9,0,0,0,0,0,0],[9,0,0,255,255,0,0]),
            ('2ad1',9,[8,0,60,12,0,100,0],[0,0,0,13,0],[8,0,60,12,0,1,0]),
            ('2acd',12,[32,0,16,14,120],[33,0,0],[0,0,255,255])]
    for char,kind,full,partial,sentinel in frames:
        for sequence in [[full]+[partial]*8+[sentinel,full],[sentinel,partial,full],[full,full[:-1],partial,[],full]]:
            for declared in [[0,0,240,0,5,0],[1,0,245,0,5,0],[0,0,240,0,0,0],[0,0,240,0],[0,0,240,0,5]]:
                add('sparse-fields','FTMS',kind,0,[packet('2ad6',declared)]+[packet(char,data) for data in sequence],motion=True,
                    **({'initialSportState':'STOP'} if kind==12 else {}))
    rng=random.Random(340018)
    for sub in range(11):
        for mode in [1,3]:
            steps=init(9,sub,mode)
            steps += [pulse(rng.choice([0,1,10,100,150,300,500,800,1500,5000,65535]),i) for i in range(96)]
            steps += [agg(9,sub),agg(9,sub,True),pulse(300),agg(9,sub)]
            add('rower-pulse-recovery','V2',9,sub,steps)
    root.mkdir(parents=True,exist_ok=True)
    (root/'cases.jsonl').write_text(''.join(json.dumps(c,separators=(',',':'))+'\n' for c in cases))
    report=dict(seed=340018,scenarios=len(cases),steps=sum(len(c['steps']) for c in cases),groups=dict(collections.Counter(c['group'] for c in cases)))
    (root/'coverage.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))

if __name__=='__main__':main()
