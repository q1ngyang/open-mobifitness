#!/usr/bin/env python3
"""Convert actual original-DEX JSONL results to compact, JVM-readable golden tables.
No protocol formula is used to compute expected values; only unit conversions and
field-name mappings between the vendor records and OpenMOBI's SI-unit model.
"""
import argparse, csv, json
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('results',type=Path)
parser.add_argument('output',type=Path)
parser.add_argument('--private',action='store_true',help='Mark outputs as private hardware evidence; keep outside Git')
a=parser.parse_args()
rows=[json.loads(line) for line in a.results.read_text().splitlines()]
assert all('error' not in row for row in rows), 'Oracle errors must be resolved before extraction'
a.output.mkdir(parents=True,exist_ok=True)
tables={
 'v011-baseline':[['packet','Cadence','Resistance','HeartBpm','PowerW','maximum','energyPower','commands']],
 'power':[['kind','subtype','maximum','rpm','level','weight','power','kcalPerSecond']],
 'wire':[['byte','mode','incline','will']],
 'profiles':[['protocol','kind','subtype','mode','maximum']],
 'packets':[['scenario','protocol','kind','subtype','magnets','hardware','upload','packetIndex','packet','characteristic','field','expected']],
 'ftms':[['name','characteristic','packet','field','expected']],
 'ftms-handler':[['name','characteristic','packet','field','expected']],
 'commands':[['handler','command','value','unlock','template','service','characteristic','bytes']],
 'fold':[['handler','folded','service','characteristic','bytes']],
 'status':[['scenario','protocol','kind','subtype','upload','packetIndex','packet','characteristic','field','expected']],
 'ftms-status':[['characteristic','packet','field','expected']],
 'interactions':[['packet','field','expected']],
 'resistance':[['scenario','kind','subtype','maximum','rpm','weight','tick','actual','target','power','kcalPerSecond']],
 'alternate':[['maximum','rpm','level','power','kcalPerHour']],
 'row-math':[['speed','model','power','kcalPerSecond']],
 'rowing':[['scenario','type','model3209','boat','forceNormal','reset','mode','sample','input','extra','State','Stroke','Speed','Spm','CaloriePerSecond','GraphValue','BackTime','PullTime','Power','StrokeEnergy','StrokeDistance','PullLength','MaxF','AvgF','Score','First','ReverseMode','Boat','FreqFactor']],
}
tables['equipment']=[tables['packets'][0]]
for scenario,row in enumerate(rows):
 q=row['input']; op=q['op']
 if op=='openMobiV1':
  for v in row['values']:tables['v011-baseline'].append([','.join(v[k]) if k=='commands' else '' if v[k] is None else v[k] for k in tables['v011-baseline'][0]])
 elif op=='power':
  target='alternate' if q.get('model',0)!=0 else 'power'
  tables[target].append([q.get(k,70) if k not in row else row[k] for k in tables[target][0]])
 elif op=='rowMath': tables['row-math'].append([q[k] if k in q else row[k] for k in tables['row-math'][0]])
 elif op=='rowing':
  for index,(sample,value) in enumerate(zip(q['samples'],row['values'])):
   table=tables['rowing']; inp,extra=sample if isinstance(sample,list) else (sample,'')
   fields=[str(value[k]).lower() if isinstance(value[k],bool) else value[k] for k in table[0][10:]]
   table.append([scenario,q['type'],str(q.get('model3209',False)).lower(),q.get('boat',0),str(q.get('forceNormal',True)).lower(),str(q.get('reset',False)).lower(),q.get('mode','interval'),index,inp,extra]+fields)
 elif op=='resistanceRamp':
  actual=1
  for event,value in zip(q['events'],row['values']):
   actual=event.get('resistance',actual)
   tables['resistance'].append([scenario,q['kind'],q['subtype'],q['maximum'],q['rpm'],q.get('weight',70),str(event.get('tick',False)).lower(),actual,value['target'],value['power'],value['kcalPerSecond']])
 elif op=='commands':
  for write in row['writes'] or [{'service':'','characteristic':'','bytes':[]}]:
   if q['command']==7: tables['fold'].append([q['handler'],q['folded'],write['service'],write['characteristic'],bytes(write['bytes']).hex()])
   else: tables['commands'].append([q['handler'],q['command'],q['value'],str(q.get('unlock',False)).lower(),bytes(q.get('template',[])).hex(),write['service'],write['characteristic'],bytes(write['bytes']).hex()])
 elif op=='wireHelpers': tables['wire'] += [[v[k] for k in tables['wire'][0]] for v in row['values']]
 elif op=='ftms':
  characteristic={'CrossTrainerDataFlags':'2ace','RowerDataFlags':'2ad1','TreadmillDataFlags':'2acd','IndoorBikeDataFlags':'2ad2'}[q['class']]
  mappings={'InstantaneousSpeed':('SpeedMps',1/3.6),'TotalDistance':('DistanceM',1),'StepPerMinute':('Cadence',1),'InstantaneousCadence':('Cadence',1),'StrokeRate':('Cadence',1),'StrokeCount':('Strokes',1),'Incline':('InclinePercent',1),'Inclination':('InclinePercent',1),'InstantaneousPower':('PowerW',1),'ResistanceLevel':('Resistance',1),'HeartRate':('HeartBpm',1),'TotalEnergy':('CaloriesKcal',1),'ForceOnBelt':('ForceN',1),'PowerOutput':('PowerW',1)}
  for packet,values in zip(q['packets'],row['values']):
   for field,value in values.items():
    if field in mappings:
     target,mult=mappings[field];tables['ftms'].append([q.get('name',''),characteristic,bytes(packet).hex(),target,value*mult])
    elif field=='InstantaneousPace' and characteristic=='2ad1' and value>0: tables['ftms'].append([q.get('name',''),characteristic,bytes(packet).hex(),'SpeedMps',500/value])
 elif op=='packets':
  if q.get('captureBus'):
   for packet,events in zip(q['packets'],row['values']):
    assert events, 'Every interaction probe must emit an actual original event'
    for event in events:
     assert event['method']=='bus'
     value=event['args'][0]
     fields={'SmallEquipmentControl':{'Instruction':'value','InstructionType':'command'},'GamePadEvent':{'Data':'gamePad'},'TmallCommandsEvent':{'Code':'voiceCommand'}}[value['type']]
     for source,target in fields.items():tables['interactions'].append([bytes(packet).hex(),target,value[source]])
   continue
  if q['handler']=='FtmsHandler':
   if q.get('metadataStates'):
    char,fields={'handlerData$12$1':('2ad4',['LowSpeed','HighSpeed']), 'handlerData$13$1':('2ad5',['InclineMin','InclineMax']), 'handlerData$17$1':('2ada',['machineStatus'])}[q['callback']]
    for packet,state in zip(q['packets'],row['states']):
     values=state | state['treadmill']
     for field in fields:tables['ftms-status'].append([char,bytes(packet).hex(),field,values[field]])
    continue
   char={'handlerData$6$1':'2acd','handlerData$7$1':'2ace','handlerData$8$1':'2ad1','handlerData$9$1':'2ad2'}[q['callback']]
   for packet,events in zip(q['packets'],row['values']):
    expected={}
    for event in events:
     method=event['method'];v=event['args'][0]
     if method=='updateResistance':expected['Resistance']=v
     elif method in ['upGeneralSportData','updateTreadmillData']:
      treadmill=method=='updateTreadmillData'
      expected.update(SpeedMps=v['Speed']/(3.6 if treadmill else 1),DistanceM=v['Distance'],CaloriesKcal=v['Calories'],DeviceDurationSec=v['Duration'],HeartBpm=v['HeartRate'])
      if treadmill:
       expected.update(InclinePercent=v['Incline'],StepRate=v['Frequency'],StrideM=v['StepDistance']/100)
      else:
       expected.update(Cadence=v['Frequency'],PowerW=v['Power'])
       if char=='2ace':expected['InclinePercent']=v['Incline']
       if char=='2ad1':expected['Strokes']=v['Strokes']
    for field,value in expected.items():tables['ftms-handler'].append(['',char,bytes(packet).hex(),field,value])
   continue
  proto={'V1Handler':'V1','V2Handler':'V2','HuanTongHandler':'HUANTONG'}[q['handler']]
  if q.get('metadataStates'):
   char,fields={
    'handlerData$14$1':('8807',['SpeedAdjustmentMethod','LowSpeed','HighSpeed']),
    'handlerData$15$1':('8808',['InclineAdjustmentMethod','InclineMin','InclineMax']),
    'handlerData$5$1':('8901',['SupportFold','SkippingRopControl','DumbbellControl']),
    'handlerData$6$1':('8902',['FoldedState']),
    'handlerData$16$1':('880e',['machineStatus']),
    'bleVer0x01DeviceNotifyProcessing$1':('ffe4',['FoldedState'])}[q['callback']]
   for i,(packet,state) in enumerate(zip(q['packets'],row['states'])):
    values=state | state['treadmill'] | state['hardware']
    for field in fields:
     value=values[field]
     tables['status'].append([scenario,proto,q['kind']+9,q['subtype'],q.get('intervalMode',2),i,bytes(packet).hex(),char,field,str(value).lower() if isinstance(value,bool) else value])
   continue
  if q['callback']=='handlerData$9$1':
   r=row['resistance'];tables['profiles'].append([proto,q['kind']+9,q['subtype'],r['ResistanceMode'],r['ResistanceMax']]);continue
  characteristic='fff1' if proto=='HUANTONG' else 'ffe4' if proto=='V1' else '8811' if q['callback']=='handlerData$17$1' else '8812' if q['callback'] in ['handlerData$18$1','handlerData$19$1'] else '8813'
  for i,(packet,events) in enumerate(zip(q['packets'],row['values'])):
   expected={}
   for e in events:
    method=e['method'];v=e['args'][0]
    if method=='updateResistance': expected['Resistance']=v
    elif method=='updateBikeOrEllipticalRPM':expected['Cadence']=v
    elif method=='upDumbbellWeight':expected['LoadKg']=v
    elif method=='upDumbbellData':
     expected.update(Repetitions=v['TotalNumber'],DeviceDurationSec=v['Duration'],DumbbellFewActions=v['FewActions'],DumbbellActionNumber=v['ActionNumber'])
    elif method=='upSkippingRopeData':
     expected.update(JumpCount=v['TotalNumber'],DeviceDurationSec=v['Duration'],Cadence=v['Frequency'],CaloriesKcal=v['Calories'],ContinuousJumps=v['ContinuousJump'],JumpInterruptions=v['Interrupts'])
    elif method in ['upGeneralSportData','updateTreadmillData']:
     treadmill=method=='updateTreadmillData'
     expected.update(SpeedMps=v['Speed']/(3.6 if treadmill else 1),DistanceM=v['Distance'],CaloriesKcal=v['Calories'],InclinePercent=v['Incline'])
     if not treadmill: expected['Cadence']=v['Frequency']
     elif proto=='V2' or q['subtype']==18: expected['StepRate']=v['Frequency']
     if not treadmill and v['Power']!=0:expected['PowerW']=v['Power']
     if q['kind']==0 and len(packet)>=13:expected['Strokes']=v['Strokes']
     if treadmill and v['StepDistance']>0: expected['StrideM']=v['StepDistance']/100
   for field,value in expected.items():
    tables['equipment' if q['kind']==7 else 'packets'].append([scenario,proto,q['kind']+9,q['subtype'],q.get('magnets',1),q.get('machineType',''),q.get('intervalMode',2),i,bytes(packet).hex(),characteristic,field,value])
for name,data in tables.items():
 if len(data)==1:continue
 with (a.output/(name+'.tsv' if name=='v011-baseline' else name+'-intl-2.1.14.tsv')).open('w') as f:
  f.write('# Private hardware replay of original APK output; do not commit.\n' if a.private else '# Unmodified OpenMOBI v0.1.1 debug APK output. Synthetic inputs.\n' if name=='v011-baseline' else '# Original DEX outputs; synthetic inputs, not hardware captures. See tools/protocol-oracle.\n')
  csv.writer(f,delimiter='\t',lineterminator='\n').writerows(data)
 print(name,len(data)-1)
