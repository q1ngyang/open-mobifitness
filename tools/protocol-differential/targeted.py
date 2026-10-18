#!/usr/bin/env python3
"""Focused follow-up inputs discovered by the broad subtype/sequence sweep."""
import argparse, json
from pathlib import Path
from generate import packet, init, pulse, agg, v1

def main():
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();root=a.output
    cases=[]
    def add(id,protocol,kind,sub,steps,**kw):
        cases.append(dict(id=id,group=id.split('-')[0],protocol=protocol,kind=kind,subtype=sub,
            name='MOBI-E' if kind==11 else 'MOBI-B',steps=steps,**kw))
    for protocol in ['V2','FTMS']:
        for kind in [9,10,11,12,16]:
            steps=init(kind,1,3) if protocol=='V2' else []
            steps += [packet('2a37',d) for d in [[],[120],[0,120],[1,44,1],[1,1,44],[0,0],[0,29],[0,255],[1,255,255],[1,0]]]
            steps += [packet('2a19',[v],mode=m) for m in ['read','notify'] for v in [0,1,87,100,101,128,255]]
            add(f'accessories-{protocol}-{kind}',protocol,kind,1,steps,captureRx=True)
    for kind,sub in [(9,3),(10,18),(11,17),(12,19),(16,1),(16,2)]:
        steps=init(kind,sub,3)
        if kind==12: steps += [packet('880e',[1,0])]
        add(f'motion-aggregate-{kind}-{sub}','V2',kind,sub,steps+[pulse(1000),agg(kind,sub),agg(kind,sub,True),agg(kind,sub)],motion=True)
    for char,kind,full,partial in [('2ad2',10,[4,0,16,14,120,0],[5,0,0,0]),('2ace',11,[8,0,16,14,120,0,0,0],[9,0,0,0,0,0]),('2ad1',9,[8,0,60,12,0,100,0],[1,0]),('2acd',12,[32,0,16,14,120],[33,0,0])]:
        steps=[packet('2acc',[255,0,0,0,7,0,0,0])]
        if kind==12: steps += [packet('2ada',[4])]
        add(f'motion-ftms-{kind}','FTMS',kind,0,steps+[packet(char,full),packet(char,partial),packet(char,full[:-1]),packet(char,full)],motion=True)
    supplement=root/'supplement';supplement.mkdir(parents=True,exist_ok=True)
    (supplement/'cases.jsonl').write_text(''.join(json.dumps(q)+'\n' for q in cases))
    cases=[]
    for sub in range(7,11):
        for mode in (1,2,3):
            steps=init(9,sub,mode)+[pulse(v,i) for i,v in enumerate((1000,600,300,200,150,200,500,800,1200,0))]+[agg(9,sub),agg(9,sub,True),agg(9,sub)]
            add(f'newrower-{sub}-{mode}','V2',9,sub,steps)
        add(f'newrower-v1-{sub}','V1',9,sub,[packet('ffe4',v1(9,sub,v,i)) for i,v in enumerate((1000,600,300,150,250,600,1000,0))])
    extra=root/'extra-rowers';extra.mkdir(parents=True,exist_ok=True)
    (extra/'cases.jsonl').write_text(''.join(json.dumps(q)+'\n' for q in cases))
if __name__=='__main__':main()
