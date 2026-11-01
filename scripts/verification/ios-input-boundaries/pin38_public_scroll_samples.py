"""Observe the exact previously failing finite sample context with public scrolls.

PIN31's two real failures lacked original-touch/overscroll/public-scroll facts.
PIN37's two original methods passed but did not exercise that sampled context.
No new sampling count, retry, sentinel or successful full-cohort replay exists.
"""
import json
import re

import frozen_sources
import pin02_capture
import pin33_original_touch_delivery as native
import pin36_public_scroll_boundary as scroll

SOURCE, TREE, ARMS = native.SOURCE, native.TREE, native.ARMS
PROFILE = 'fixed-public-scroll-failure-boundaries'
CASES, TESTS, TEST_TIMEOUT_SECONDS = native.CASES, native.TESTS, native.TEST_TIMEOUT_SECONDS
RESULT = 'FIXED_PUBLIC_SCROLL_SAMPLES_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Exact existing five EN/AR pairs from failed PIN31, original actions/assertions/deadlines; '
         'observe missing actual public ScrollState/getter and original native touch boundaries; '
         'no adaptive sampling, retry, sentinel/full23 replay, causality or qualification')
previous, decoder = native.previous, native.decoder
pair = scroll.pair


def renderer_for_profile(profile, source, runtime, device):
    decoder.require((profile,source,runtime,device) == (PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
        'Exact fixed public-scroll source/profile/runtime/device')
    base = native.renderer_for_profile(native.PROFILE, source, runtime, device)
    return lambda sources, digests, *, arm: scroll.extend(base(sources,digests,arm=arm))


test_command = native.test_command
verify_results = native.verify_results
verify_commands = native.verify_commands
failed_method_exports = native.failed_method_exports
retain_companion = native.retain_companion
retain_root_companion = native.retain_root_companion
retain_motion_companion = native.retain_motion_companion


def accept_native_export(out, state):
    req = decoder.require
    pair = previous.previous.observed.original.motion.pair
    observed = previous.previous.observed
    req(state['original_inputs'].get(scroll.PERMISSION) == scroll.PERMISSION_SHA256, 'Exact original Permission source')
    req(state['original_inputs'].get(observed.original.text.NAME) == observed.original.text.NAME_SHA256,
        'Exact original public Name source required')
    # Same generic original-source/product/outcome/commands/cleanup gate, with
    # this exact new renderer; do not relabel state as an older accepted profile.
    observed.original.motion.boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
        result_verifier=verify_results, command_verifier=verify_commands)
    observed.original.verify_companion_files(out, state)
    observed.verify_failed_queries(out, state)
    dest = out/'compose'
    original_getters, original_touches, public_scrolls = [], [], []
    for path in sorted(dest.glob('capture-*.json')):
        legacy = json.loads(frozen_sources._read(path, pin02_capture.MAX_BYTES))
        req(not any(row[1] in (1,2) for row in legacy['snapshot']['rows']), 'No invented added-pointer observations')
        args = dict(token=legacy['token'], simulator=state['arms'][0]['simulator'], bundle=state['debug_bundle'], legacy=legacy)
        original_getters.append(previous.decode_overscroll(frozen_sources._read(
            dest/('catalog-overscroll-'+legacy['token']+'.json'), previous.MAX_BYTES), **args))
        touch = decoder.decode(frozen_sources._read(dest/('native-touch-'+legacy['token']+'.json'), decoder.MAX_BYTES), **args)
        req(touch['complete'], 'Actual unique original recognizer and exact ABI installation, zero overflow required')
        original_touches.append(touch)
        raw = frozen_sources._read(dest/('public-scroll-'+legacy['token']+'.json'), scroll.MAX_BYTES)
        public = scroll.decode(raw, **args)
        rows = json.loads(raw)['scroll']['rows']
        mounted = [any(r[2:] == [kind,2,1,0] for r in rows) for kind in range(3)]
        req(all(n == 0 or mounted[k] for k,n in enumerate(public['created'])),
            'Actual public scopes must mount; unentered scopes never fabricated')
        public_scrolls.append(dict(scroll=public, mounted=mounted))
    req(len(list(dest.glob('catalog-overscroll-*.json'))) == len(original_getters) and
        len(list(dest.glob('native-touch-*.json'))) == len(original_touches) and
        len(list(dest.glob('public-scroll-*.json'))) == len(public_scrolls), 'One original companion set per failure only')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(dest/'test.log',4*1024*1024))
    req(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'All ten fixed original cases once in order')
    receipt = dict(methods=state['arms'][0]['methods'], original_getters=original_getters,
        original_touches=original_touches, public_scrolls=public_scrolls, added_recognizer=False, compose_pointer_observers=False,
        application_root_cause=False, shipping_qualified=False, native_timing_neutrality=False, physical_verified=False)
    target = out/'public-scroll-sample-observations.json'
    if target.exists(): req(json.loads(target.read_bytes()) == receipt, 'Exact original saved native-touch receipt')
    else:
        with target.open('x') as f: json.dump(receipt, f, indent=2)
    return RESULT
