#!/usr/bin/env python3
"""Focused synthetic rule boundaries; no case proves a real model supports a feature.

Keep this batch separate from the earlier 2,772-case audit and its retained hashes.
The shared runner executes each APK's original receive/calculation code, not the UI.
"""
import argparse
import collections
import json
from pathlib import Path


def packet(chr, data, **kw):
    return dict(chr=chr, data=list(data), **kw)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    root = parser.parse_args().output
    cases = []

    def add(id, protocol, kind, sub, steps, **kw):
        cases.append(dict(id=id, group=id.split('-')[0], protocol=protocol, kind=kind,
                          subtype=sub, name={9:'MOBI-R',10:'MOBI-B',11:'MOBI-E',12:'MOBI-T'}[kind],
                          steps=steps, **kw))

    profiles = [(9,7,24),(10,1,24),(10,18,24),(10,21,32),(10,24,32),(10,33,8),
                (11,17,24),(11,18,8),(11,22,40)]
    for kind, sub, maximum in profiles:
        for minimum in (0,1):
            for raw_mode in (3,4,5):
                for before in (False,True):
                    cap = packet('8806',[raw_mode,minimum,maximum])
                    identity = packet('8802',[kind,sub])
                    steps = [packet('8801',[1]),*( [cap,identity] if before else [identity,cap]),packet('8805',[1])]
                    if kind != 9:
                        steps += [packet('8811',[3,232])]
                    for level in (min(12,maximum),0,1,maximum,maximum+1):
                        steps += [packet('8812',[level])]
                        if kind != 9:
                            steps += [dict(tick=1) for _ in range(6)]
                    add(f'zero-v2-{kind}-{sub}-{minimum}-{raw_mode}-{int(before)}','V2',kind,sub,steps,motion=kind!=9)

    for kind, chr, full in [(10,'2ad2',[36,0,16,14,120,0,12,0]),
                            (11,'2ace',[136,0,0,16,14,120,0,0,0,120,0]),
                            (9,'2ad1',[128,0,60,12,0,12,0])]:
        for minimum in (0,1):
            for increment in (5,10):
                steps=[packet('2acc',[255,0,0,0,7,0,0,0]),packet('2ad6',[minimum*10,0,240,0,increment,0])]
                for level in (12,0,1):
                    frame=full.copy()
                    frame[-2]=level*(10 if kind==11 else 1)
                    steps += [packet(chr,frame)]
                    if kind!=9:
                        steps += [dict(tick=1) for _ in range(12)]
                add(f'zero-ftms-{kind}-{minimum}-{increment}','FTMS',kind,0,steps,motion=kind!=9)

    for kind, sub in [(12,18),(12,19),(9,1),(9,7),(9,10)]:
        for before in (False,True):
            identity=packet('8802',[kind,sub])
            capability=packet('8901',[4])
            steps=([capability,identity] if before else [identity,capability])
            steps += [packet('8902',[3,1,state]) for state in (0,1,2,255)]
            steps += [packet('8901',[0]),packet('8902',[3,1,1]),packet('8901',[4])]
            add(f'fold-v2-{kind}-{sub}-{int(before)}','V2',kind,sub,steps)
    for kind, sub in [(12,18),(12,19),(9,1),(9,7)]:
        frame=[171,4,0,kind,sub,1,0,72,0,0,120] if kind==12 else [171,4,1,kind,sub,87,1,0,0,3,232,0,1]
        steps=[packet('ffe4',frame)]+[packet('ffe4',[174,1,0,state]) for state in (0,1,2,255)]
        add(f'fold-v1-{kind}-{sub}','V1',kind,sub,steps)

    # Byte signedness is an exploratory boundary, not a claim of 255 physical gears.
    for kind, sub in [(10,18),(11,17)]:
        steps=[packet('8802',[kind,sub]),packet('8806',[5,0,255])]
        steps += [packet('8812',[v],mode=mode) for mode in ('read','notify') for v in (0,1,127,128,255)]
        add(f'boundary-v2-{kind}-{sub}','V2',kind,sub,steps)

    root.mkdir(parents=True,exist_ok=True)
    (root/'cases.jsonl').write_text(''.join(json.dumps(q)+'\n' for q in cases))
    summary=dict(cases=len(cases),steps_per_apk=sum(len(q['steps']) for q in cases),
                 groups=dict(collections.Counter(q['group'] for q in cases)))
    (root/'coverage.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(summary))


if __name__=='__main__':
    main()
