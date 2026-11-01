"""Private-free main-run-loop software controls; not app/dispatch/AX causal proof."""
import hashlib
import json
import math
from pathlib import Path

import frozen_sources
import pin45_original_runtime_context as context

HERE=Path(__file__).resolve().parent
TEMPLATE='PIN46MainRunLoop.swift.in'
CONTROL='PIN46MainRunLoopControl.swift.in'
TEMPLATE_SHA256='6dec78a0716ad3c8841f884f7e076294e95b00c567124d9f0ab0bdc3a1e82aad'
CONTROL_SHA256='bc41471caf2d33d8bf72549f935afdecdb9da6615e51a47e8d2bda599b12e619'
SCOPE='main-run-loop-phase-intervals-not-dispatch-or-AX-roundtrip'
MAX_BYTES=8192
CONTEXT_RUN=41335643678
CONTEXT_HARNESS='15445c82a5c28fb2dc16225e7cf7c38ebbe39212'
CONTEXT_RECEIPT_SHA256='fb910a482b4a095eac63696441692bfc0459f64bc2892a5c842529ffa2cb5ff2'
require=context.require


def require_context_binding():
    context.require_context_binding()
    require(CONTEXT_RUN==41335643678 and CONTEXT_HARNESS=='15445c82a5c28fb2dc16225e7cf7c38ebbe39212'
            and CONTEXT_RECEIPT_SHA256=='fb910a482b4a095eac63696441692bfc0459f64bc2892a5c842529ffa2cb5ff2',
            'Exact accepted full original-runtime context')


def source(name):
    require(name in (TEMPLATE,CONTROL),'One owned run-loop source')
    raw=frozen_sources._read(HERE/name,65536)
    require(hashlib.sha256(raw).hexdigest()==(TEMPLATE_SHA256 if name==TEMPLATE else CONTROL_SHA256),
            'Exact reviewed run-loop source')
    return raw


def decode(raw):
    require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded literal run-loop report')
    v=json.loads(raw,object_pairs_hook=context.aggregate.pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite run-loop report')))
    require(type(v) is dict and set(v)=={'schema','installed','capturedMs','phaseCounts','completedActiveIntervals',
        'completedWaitingIntervals','largestActiveMs','largestWaitingMs','partialActiveMs','invalid','boundary',
        'timerOrPolling','additionalThread','allIntervalsRetained','longestActiveIntervalsMs','longestWaitingIntervalsMs','observerRemoved'},
        'Exact private-free run-loop schema')
    require(type(v['schema']) is int and v['schema']==1 and v['installed'] is True and v['observerRemoved'] is True and v['boundary']==SCOPE
        and v['timerOrPolling'] is v['additionalThread'] is v['allIntervalsRetained'] is False,
        'Actual phase scope, not timers/threads/all-call ledger or dispatch/AX attestation')
    require(type(v['invalid']) is int and v['invalid']==0,'All recorded counters/intervals valid')
    require(type(v['capturedMs']) in (float,int) and math.isfinite(v['capturedMs']) and v['capturedMs']>=0,
        'Finite monotonic capture clock')
    counts=v['phaseCounts']
    require(type(counts) is list and len(counts)==5 and all(type(n) is int and 0<=n<=2_147_483_647 for n in counts),
        'Bounded numeric phase counters')
    for key in ('completedActiveIntervals','completedWaitingIntervals'):
        require(type(v[key]) is int and 0<=v[key]<=sum(counts),'Finite counts consistent with observed phases')
    require(v['completedActiveIntervals']<=counts[2]+counts[4] and v['completedWaitingIntervals']<=counts[3],
        'Completed intervals have actual terminating phase callbacks')
    def interval(row,optional=False):
        require(type(row) is list and (len(row)==3 or optional and not row),'Finite interval shape')
        if not row:return
        require(all(type(n) in (int,float) and math.isfinite(n) and 0<=n<=v['capturedMs'] for n in row)
            and row[1]>=row[0] and math.isclose(row[2],row[1]-row[0],abs_tol=0.001,rel_tol=1e-9),
            'Exact finite interval timestamps/duration')
    for kind,count in (('Active','completedActiveIntervals'),('Waiting','completedWaitingIntervals')):
        longest=v['longest'+kind+'IntervalsMs'];largest=v['largest'+kind+'Ms']
        require(type(longest) is list and len(longest)==min(8,v[count]),'Eight longest bounded intervals, not full history')
        for row in longest:interval(row)
        interval(largest,optional=True)
        require(largest==(longest[0] if longest else []) and
            all(longest[i][2]>=longest[i+1][2] for i in range(len(longest)-1)),'Sorted longest intervals/largest agree')
    interval(v['partialActiveMs'],optional=True)
    if v['partialActiveMs']:require(v['partialActiveMs'][1]==v['capturedMs'],'Partial interval ends at capture, not completed')
    return v
