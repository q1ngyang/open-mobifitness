#!/usr/bin/env python3
"""Deterministic synthetic wire sequences. No implementation supplies expected values."""
import argparse, collections, itertools, json, random
from pathlib import Path

KNOWN = {9: list(range(7)), 10: [1,*range(18,26),32,33],
         11: [1,*range(17,23)], 12: [0,1,*range(17,23)], 16:[1,2]}
cases=[]
def packet(chr, data, **kw): return dict(chr=chr,data=list(data),**kw)
def add(group, protocol, kind, sub, steps, **kw):
    cases.append(dict(id=f'{group}-{len(cases):05}', group=group, protocol=protocol,
        kind=kind,subtype=sub,name=kw.pop('name',{9:'MOBI-R',10:'MOBI-B',11:'MOBI-E',12:'MOBI-T',16:'MOBI-S'}[kind]),steps=steps,**kw))
def be(v,n=2): return list(v.to_bytes(n,'big',signed=False))
def init(kind,sub,mode=1,cap=True):
    steps=[packet('8801',[mode]),packet('8802',[kind,sub]),packet('8803',[0x16]),packet('8804',[1]),packet('8805',[2])]
    if cap: steps.append(packet('8806',[5,1,24]))
    return steps
def pulse(interval,count=1): return packet('8811',be(interval)+be(count))
def agg(kind,sub,zero=False,length=13):
    if kind==16:
        b=[2,3,60,*be(30),*be(100),*be(123),0,0,*be(4),*be(50)] if sub==1 else [2,3,0,*be(30),*be(100)]
    else: b=[30,5,60,*be(30),*be(100),*be(123),*be(200),*be(12)][:length]
    return packet('8813',[0]*len(b) if zero else b)
def v1(kind,sub,period=1000,count=1):
    if kind==12: return [171,4,0,kind,sub,1,1,72,5,120,120,*be(30),*be(10),*be(12),0]
    return [171,4,1,kind,sub,87,1,*be(period,4),*be(count),12,120]

# Every subtype byte is exploratory, not a claim of 1,024 real product models.
for kind in range(9,13):
    for sub in range(256):
        add('subtype-byte', 'V2',kind,sub,init(kind,sub,cap=False)+[pulse(1000),packet('8812',[12])])

for kind,subs in KNOWN.items():
    for sub in subs:
        for mode in (1,2,3):
            steps=init(kind,sub,mode)
            if kind==16: steps += [packet('8812',[0,75]),agg(kind,sub),agg(kind,sub,True),agg(kind,sub)]
            else:
                steps += [pulse(v,i+1) for i,v in enumerate((1000,500,400,300,200,300,600,1000,0,65535))]
                steps += [agg(kind,sub,length=n) for n in (9,11,13)] + [agg(kind,sub,True),agg(kind,sub)]
            steps += [packet('880e',[1,0]),packet('880e',[2,0]),packet('880e',[0,2]),packet('2a19',[87]),packet('2a37',[0,120])]
            add('known-sequence','V2',kind,sub,steps)
        if kind!=16:
            steps=[packet('ffe4',v1(kind,sub,v,i+100)) for i,v in enumerate((1000,500,0,1000,65535))]
            if kind==12: steps += [packet('ffe4',[173,3,232]),packet('ffe4',[173,0,0]),packet('ffe4',[174,1,0,0])]
            add('v1-known','V1',kind,sub,steps)

for kind,sub in [(10,18),(10,19),(10,33),(11,22),(12,19),(9,3),(16,1),(16,2)]:
    for raw in (0,1,3,4,5):
        for order in itertools.permutations([packet('8801',[1]),packet('8802',[kind,sub]),packet('8806',[raw,1,24])]):
            add('metadata-order','V2',kind,sub,list(order)+[pulse(1000),packet('8812',[12])])

for kind in (10,11):
    for sub in KNOWN[kind]+[0]:
        for magnets in (0,1,2,8,127,128,255):
            add('magnets','V2',kind,sub,init(kind,sub)+[packet('8805',[magnets])]+[pulse(v) for v in (1,100,300,1000,0)])
for kind,sub in [(9,3),(10,18),(11,17),(12,19),(16,1),(16,2)]:
    for char,data in [('8801',[3]),('8802',[kind,sub]),('8803',[0x18]),('8804',[255]),('8805',[2]),
        ('8806',[5,1,24]),('8807',[5,10,160]),('8808',[5,129,26]),('880e',[1,2]),
        ('8811',[3,232,0,1]),('8812',[12]),('8813',agg(kind,sub)['data']),('8814',[3,232]),
        ('8901',[0x35]),('8902',[4,3,2,1,20]),('2a19',[87]),('2a37',[1,44,1])]:
        # Every length prefix plus a trailing byte, then a valid packet to detect corruption.
        for n in range(len(data)+2):
            candidate=data[:n] if n<=len(data) else data+[255]
            add('length-boundary','V2',kind,sub,init(kind,sub,3)+[packet(char,candidate),agg(kind,sub)])
for kind,subs in KNOWN.items():
    if kind==16: continue
    for sub in subs:
        frame=v1(kind,sub)
        add('v1-length','V1',kind,sub,[packet('ffe4',frame[:n]) for n in range(len(frame)+1)]+[packet('ffe4',frame)])
for sub in (0,1,2,3,4,5,6,7):
    add('old-rower','V1',9,sub,[packet('ffe4',[171,3,0,9,0,87,sub<<5,*be(v,4),*be(i)]) for i,v in enumerate((1000,600,300,150,250,600,1000,0))])
for kind in (10,11):
    for d in ([0,96],[1,32],[0,0],[0,250],[255,255]):
        add('huantong','HUANTONG',kind,18,[packet('fff1',[0,1,*d,*([0]*8)]),packet('fff1',[0,0,*d,*([0]*8)])])

# FTMS layouts based on wire field widths. Values and flag subsets vary independently.
widths={'2ad2':[2,2,2,2,3,2,2,2,5,1,1,2,2], '2ace':[2,2,3,4,2,4,4,2,2,2,5,1,1,2,2],
        '2ad1':[3,1,3,2,2,2,2,2,5,1,1,2,2], '2acd':[2,2,3,4,4,1,1,5,1,1,2,2,4,3]}
rng=random.Random(20261018)
for char,ws in widths.items():
    kind={'2ad2':10,'2ace':11,'2ad1':9,'2acd':12}[char]
    headers=[2,3] if char=='2ace' else [2]
    flags=[0,1]+[1<<b for b in range(1,len(ws))]+[(1<<len(ws))-2,(1<<len(ws))-1]+[rng.randrange(1<<len(ws)) for _ in range(24)]
    for header in headers:
        for flags_value in flags:
            for payload in (0,1,255):
                b=list(flags_value.to_bytes(header,'little'))
                for bit,w in enumerate(ws):
                    if (bit==0 and not flags_value&1) or (bit>0 and flags_value&(1<<bit)):
                        b += [payload]*w
                add('ftms-flags','FTMS',kind,0,[packet('2acc',[255,255,0,0,7,0,0,0]),packet('2ad6',[10,0,240,0,10,0]),packet(char,b),packet(char,b[:max(header,len(b)-1)])],name='MB-E1010' if header==3 else 'MOBI-E' if char=='2ace' else 'MOBI-B')
    for chr,data in [('2ad4',[10,0,144,1,10,0]),('2ad5',[156,255,200,0,10,0]),('2ad6',[10,0,240,0,10,0]),('2acc',[8,0,0,0,7,0,0,0]),('2ada',[3]),('2a19',[87]),('2a37',[1,44,1])]:
        add('ftms-metadata','FTMS',kind,0,[packet(chr,data[:n]) for n in range(len(data)+1)]+[packet('2ada',[4]),packet('2ada',[2,2]),packet('2ada',[2,1])])

# Stateful official MotionData listener + calculation ticks, with no UI timer.
for kind in (10,11):
    for sub in KNOWN[kind]:
        steps=init(kind,sub)+[packet('8812',[1]),pulse(1000),{'tick':1},packet('8812',[24])]
        steps += list(itertools.chain.from_iterable(([{'tick':1},pulse(1000)] for _ in range(12))))
        steps += [pulse(0),pulse(500)]
        add('motion-ramp','V2',kind,sub,steps,motion=True)

def main():
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    (a.output/'cases.jsonl').write_text(''.join(json.dumps(q,separators=(',',':'))+'\n' for q in cases))
    coverage=dict(scenarios=len(cases),steps=sum(len(q['steps']) for q in cases),groups=dict(collections.Counter(q['group'] for q in cases)),known_profiles=sum(map(len,KNOWN.values())),known_by_kind=KNOWN,seed=20261018)
    (a.output/'coverage.json').write_text(json.dumps(coverage,indent=2)+'\n');print(json.dumps(coverage,indent=2))
    order=a.output/'order-check';order.mkdir(exist_ok=True)
    selected=random.Random(20261018).sample([q for q in cases if not q.get('motion')],62)+[q for q in cases if q.get('motion')]
    selected.reverse()
    (order/'cases.jsonl').write_text(''.join(json.dumps(q)+'\n' for q in selected))
if __name__=='__main__': main()
