#!/usr/bin/env python3
"""Independent, offline replay of pinned InputDispatcher display-space hit semantics.
No Android/app execution, screenshot synthesis, source editing, or Gradle.
"""
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import re

ROOT=Path('/workspace/D.A.-Caller')
RAW=Path('/workspace/work/native-013-ci-44447145702/ui/ui-modern-snapshots/DA-CALLER-next-snapshots/modern-drag-input-routing.json')
data=json.loads(RAW.read_text())
gesture=data['gestures'][0]
raw=gesture['last_raw_input']
dispatcher=raw.split('Input Dispatcher State:',1)[1].split('Input Dispatcher State at time of last ANR:',1)[0]
display=dispatcher.split('\n  Display: 0\n',1)[1].split('\n    Windows:',1)[0]
lines=display.strip().splitlines()
assert lines[0].strip()=='logicalSize=480x854'
assert lines[1].strip()=='transform (ROT_0) (SCALE TRANSLATE)'
matrix=[[float(v) for v in line.split()] for line in lines[2:5]]
assert matrix[0][1]==matrix[1][0]==matrix[2][0]==matrix[2][1]==0
assert matrix[2][2]==1
sx,sy,tx,ty=matrix[0][0],matrix[1][1],matrix[0][2],matrix[1][2]
assert sx>0 and sy>0

record=re.compile(r"^\s*(\d+): name='(.*)', id=(-?\d+), displayId=(-?\d+), inputConfig=(.*?), alpha=([0-9.]+), frame=(\[-?\d+,-?\d+\]\[-?\d+,-?\d+\]), globalScale=([0-9.]+), applicationInfo\.name=(.*?), applicationInfo\.token=(.*?), touchableRegion=(.*?), ownerPid=(-?\d+), ownerUid=(-?\d+), dispatchingTimeout=(-?\d+)ms, hasToken=(true|false), touchOcclusionMode=(\S+)\s*$")
rect=re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')
windows=[]
for line in dispatcher.splitlines():
    m=record.match(line)
    if m is None: continue
    assert int(m[4])==0
    windows.append(dict(index=int(m[1]),name=m[2],id=int(m[3]),flags=set(m[5].split(' | ')),frame=list(map(int,rect.fullmatch(m[7]).groups())),regions=[list(map(int,r)) for r in rect.findall(m[11])],uid=int(m[13]),token=m[15]=='true'))
assert [w['index'] for w in windows]==list(range(12))

def contains(r,x,y):
    return r[0]<=x<r[2] and r[1]<=y<r[3]

def first_target(ws,x,y):
    for w in ws:
        if w['flags']&{'NOT_VISIBLE','NOT_TOUCHABLE','SPY'}:continue
        if any(contains(r,x,y) for r in w['regions']):return w
    return None

def owns(w):
    return bool(w and w['uid']==10174 and w['token'] and w['name'].endswith('DA CALLER call island') and not w['flags']&{'NO_INPUT_CHANNEL','PAUSE_DISPATCHING','DROP_INPUT','DROP_INPUT_IF_OBSCURED'})

def inverse(x,y):
    return (x-tx)/sx,(y-ty)/sy

def hit(ws,x,y):
    a,b=inverse(x,y)
    return first_target(ws,int(a),int(b))

def consensus(ws,x,y):
    a,b=map(int,inverse(x,y));center=first_target(ws,a,b)
    return owns(center) and all(first_target(ws,a+dx,b+dy)==center for dx,dy in itertools.product((-1,0,1),repeat=2))

results=[]
def check(name,predicate,evidence=None):
    assert predicate,name
    results.append(dict(name=name,status='PASS',evidence=evidence))

x,y=gesture['point_x'],gesture['point_y']
server=inverse(x,y)
center=hit(windows,x,y)
check('actual_center_owned_island',owns(center),dict(logical=[x,y],inverse=list(server),truncated=list(map(int,server)),target=center['name']))
check('actual_center_all_nine_neighbor_points_same_owned_window',consensus(windows,x,y))
status=next(w for w in windows if w['name'].endswith(' StatusBar'))
island=next(w for w in windows if w['name'].endswith('DA CALLER call island'))
def forward_rect(r):return [sx*r[0]+tx,sy*r[1]+ty,sx*r[2]+tx,sy*r[3]+ty]
status_logical=forward_rect(status['frame'])
island_logical=forward_rect(island['frame'])
check('status_is_full_width_top_after_display_transform',abs(status_logical[0])<.01 and abs(status_logical[1])<.01 and abs(status_logical[2]-480)<.01,dict(server=status['frame'],logical=status_logical,conservative_bottom=math.ceil(status_logical[3])))
check('status_outward_ceil_is_37',math.ceil(status_logical[3])==37)
check('island_forward_rect_matches_actual_capture',island_logical[0]==45 and island_logical[2]==435 and 48<island_logical[1]<49 and 132<island_logical[3]<133,dict(server=island['frame'],logical=island_logical))
check('exclusive_island_left_edge_45_owned',owns(hit(windows,45,90)))
check('one_left_of_island_44_not_owned',not owns(hit(windows,44,90)))
check('exclusive_island_right_edge_435_not_owned',not owns(hit(windows,435,90)))
check('last_full_right_pixel_434_owned',owns(hit(windows,434,90)))
check('pixel_y48_not_owned',not owns(hit(windows,240,48)))
check('pixel_y49_owned',owns(hit(windows,240,49)))
check('pixel_y132_server_edge_rounding_owned',owns(hit(windows,240,132)))
check('pixel_y133_not_owned',not owns(hit(windows,240,133)))
check('status_y36_target_status',hit(windows,240,36)['name'].endswith(' StatusBar'))
check('status_y37_no_status',not hit(windows,240,37)['name'].endswith(' StatusBar'))
check('one_pixel_edge_neighborhood_rejected',not consensus(windows,45,90))

for flag,expected in [('0x0',False),('NOT_VISIBLE',True),('NOT_TOUCHABLE',True),('SPY',True)]:
    blocker=copy.deepcopy(status);blocker.update(id=9876,name='synthetic front window',regions=[[150,85,170,105]],flags={flag})
    check('front_blocker_'+flag,owns(hit([blocker]+windows,x,y))==expected)
wrong_uid=copy.deepcopy(windows);next(w for w in wrong_uid if w['id']==island['id'])['uid']=10175
check('same_title_wrong_uid_rejected',not owns(hit(wrong_uid,x,y)))
for flag in ['NO_INPUT_CHANNEL','PAUSE_DISPATCHING','DROP_INPUT','DROP_INPUT_IF_OBSCURED']:
    altered=copy.deepcopy(windows);next(w for w in altered if w['id']==island['id'])['flags'].add(flag)
    check('owned_window_'+flag+'_fails_closed',not owns(hit(altered,x,y)))

# A four-decimal dump gives coefficient intervals of +/-0.00005. Enumerate
# corners independently for each positive axis to bound the exact real quotient.
e=.00005
intervals=[]
for logical,scale,trans in [(x,sx,tx),(y,sy,ty)]:
    corners=[(logical-t)/s for t,s in itertools.product((trans-e,trans+e),(scale-e,scale+e))]
    intervals.append([min(corners),max(corners)])
check('rounded_matrix_uncertainty_small_at_actual_point',all(hi-lo<.02 for lo,hi in intervals),dict(inverse_intervals=intervals,native_casts=[sorted({int(v) for v in bound}) for bound in intervals]))

# Negative fractional casts intentionally differ from floor, matching C++ int32_t.
check('negative_fractional_cast_toward_zero',int(-.5)==0 and math.floor(-.5)==-1)
check('wrong_forward_direction_loses_island',not owns(first_target(windows,int(sx*x+tx),int(sy*y+ty))))
check('wrong_local_window_inverse_loses_island',not owns(first_target(windows,int((x+45)/sx),int((y+100.5307)/sy))))

out=dict(status='INDEPENDENT_OFFLINE_REPLAY_PASS_NOT_NATIVE_EXECUTION',ci_run=44447145702,source='71dd0f409552628b2fe255285e6c7b3c7b497413',raw_json=str(RAW),raw_sha256=hashlib.sha256(RAW.read_bytes()).hexdigest(),pinned_reference_commit='e424b61ebd9ecc1adb2a23d2ee4f18c6a8edd624',reference_sha256=hashlib.sha256((ROOT/'work/nov19-input-coordinate/reference/InputDispatcher.cpp').read_bytes()).hexdigest(),cases=results,case_count=len(results),no_gradle=True,no_android_execution=True,no_app_source_edits=True,publication_allowed=False)
(ROOT/'work/input-transform-audit-nov19/replay-results.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(status=out['status'],case_count=len(results),server_point=server,status_logical=status_logical,island_logical=island_logical,uncertainty_intervals=intervals),indent=2))
