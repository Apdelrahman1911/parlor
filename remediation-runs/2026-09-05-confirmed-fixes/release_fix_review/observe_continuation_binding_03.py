#!/usr/bin/env python3
"""Independent read-only source/evidence/path observation; not a test/build runner.

No imports of author controls; no application writes or process signals.
Output is compact JSON on stdout for exclusive capture by this reviewer.
"""
import collections
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
RUN = ROOT / 'remediation-runs/2026-09-05-confirmed-fixes'
FINAL = RUN / 'final'
FROZEN = 'e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7'


def need(value, message):
    if not value:
        raise RuntimeError(message)


def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha(path):
    need(path.is_absolute() and '..' not in path.parts, 'Unsafe path')
    path.relative_to(ROOT)
    need(not any(p.is_symlink() for p in (path, *path.parents)), 'Symlink ancestry')
    need(path.is_file(), 'Missing regular file: ' + str(path))
    with path.open('rb') as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(65536), b''):
            digest.update(chunk)
        return digest.hexdigest()


def load(path):
    sha(path)
    return json.loads(path.read_text())


def git(*args):
    result = subprocess.run(['git', *args], cwd=ROOT, capture_output=True)
    need(result.returncode == 0 and not result.stderr, 'Read-only Git query failed')
    return result.stdout


def reference(path):
    return {'path': str(path.relative_to(ROOT)), 'sha256': sha(path)}


references = []


def walk_refs(value):
    if isinstance(value, dict):
        name = value.get('path') or (value.get('ref') if value.get('kind') == 'path' else None)
        expected = value.get('sha256')
        if isinstance(name, str) and isinstance(expected, str):
            rel = Path(name)
            need(not rel.is_absolute() and '..' not in rel.parts, 'Unsafe evidence ref')
            need(not any(x in name.lower() for x in ('.keystore', '.p12', '.p8', '.mobileprovision', 'credentials.json', 'service-account', 'local.properties')), 'Protected ref')
            need(sha(ROOT / rel) == expected, 'Reference mismatch: ' + name)
            references.append(name)
        for child in value.values():
            walk_refs(child)
    elif isinstance(value, list):
        for child in value:
            walk_refs(child)


def reconstruct_copies(cycle):
    directory = RUN / 'evidence' / cycle
    manifest = load(directory / 'copied-wrapper-manifest.json')
    rows = {x['path']: x for x in manifest}
    need(len(rows) == len(manifest) == 18, 'Copy manifest duplicates or count drift')
    for name, row in rows.items():
        need(sha(ROOT / name) == row['original_sha256'], 'Copied original drift')
    lines = (directory / 'copied-wrapper.diff').read_text().splitlines(keepends=True)
    i = 0
    reconstructed = {}
    while i < len(lines):
        need(lines[i].startswith('--- original/'), 'Unexpected copy diff header')
        name = lines[i][len('--- original/'):].rstrip('\n')
        need(lines[i+1] == '+++ audit-copy/' + name + '\n', 'Mismatched copy path')
        i += 2
        old = (ROOT / name).read_text().splitlines(keepends=True)
        output, cursor = [], 0
        while i < len(lines) and not lines[i].startswith('--- original/'):
            match = re.fullmatch(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@[^\n]*\n', lines[i])
            need(match is not None, 'Invalid copy hunk')
            start, old_count, new_start, new_count = (int(match[1]), int(match[2] or 1), int(match[3]), int(match[4] or 1))
            need(start - 1 >= cursor, 'Overlapping copy hunk')
            output.extend(old[cursor:start-1]); cursor = start-1
            need(len(output) == new_start-1, 'New hunk position mismatch')
            consumed = emitted = 0
            i += 1
            while i < len(lines) and not lines[i].startswith(('@@ ', '--- original/')):
                line = lines[i]
                need(line and line[0] in ' +-','Unsupported hunk metadata')
                if line[0] in ' -':
                    need(cursor < len(old) and old[cursor] == line[1:], 'Original context/removal mismatch')
                    cursor += 1; consumed += 1
                if line[0] in ' +':
                    output.append(line[1:]); emitted += 1
                i += 1
            need((consumed, emitted) == (old_count, new_count), 'Hunk count mismatch')
        output.extend(old[cursor:])
        digest = sha_bytes(''.join(output).encode())
        need(name in rows and digest == rows[name]['copied_sha256'], 'Reconstructed copy hash mismatch')
        reconstructed[name] = digest
    changed = {name for name, row in rows.items() if row['original_sha256'] != row['copied_sha256']}
    need(set(reconstructed) == changed and len(changed) == 4, 'Changed copied file mismatch')
    return {'manifest': reference(directory/'copied-wrapper-manifest.json'), 'diff': reference(directory/'copied-wrapper.diff'), 'original_count':18, 'unchanged_copies':14, 'reconstructed_copies': reconstructed}


def main():
    frozen = load(FINAL / 'source-identity.json')
    baseline = load(RUN / 'baseline.json')
    names = sorted(set(git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')))
    source = []
    for name in names:
        if not name or name.startswith(('audit-runs/', 'remediation-runs/', 'project-code-audit/', 'design/')):
            continue
        if any(x in name.lower() for x in ('.keystore', '.p12', '.p8', '.mobileprovision', 'credentials.json', 'service-account', 'local.properties')):
            continue
        source.append([name, sha(ROOT / name)])
    need(source == frozen['source_manifest'] and len(source) == 658, 'Current source names/bytes drift')
    need(sha_bytes(json.dumps(source, separators=(',', ':')).encode()) == FROZEN, 'Source digest mismatch')
    for key,args in {'commit':('rev-parse','HEAD'), 'tree':('rev-parse','HEAD^{tree}'), 'branch':('branch','--show-current'), 'tracked_status':('status','--porcelain=v1','--untracked-files=no')}.items():
        need(git(*args).decode().strip() == frozen[key], 'Checkout mismatch: ' + key)
    diff = git('diff','--binary','HEAD','--')
    need(sha_bytes(diff) == frozen['diff_sha256'], 'Tracked diff mismatch')
    need(git('show-ref').decode().strip() == baseline['refs'].strip(), 'Ref mutation')
    need(git('stash','list').decode().strip() == baseline['stashes'].strip(), 'Stash mutation')
    need(not git('diff','--cached','--name-only').strip(), 'Index mutation')
    modified, unchanged, untracked_changed = [],0,[]
    for item in baseline['files']:
        if sha(ROOT/item['path']) == item['sha256']:
            unchanged += 1
        else:
            modified.append(item['path'])
            if item['kind'] != 'tracked': untracked_changed.append(item['path'])
    need(len(modified) == 34 and unchanged == 1584 and not untracked_changed, 'Baseline preservation drift')
    new_sources = sorted(set(x[0] for x in source) - {x['path'] for x in baseline['files']})
    need(len(new_sources) == 28, 'Added source inventory mismatch')
    patch = diff
    for name in new_sources:
        result = subprocess.run(['git','diff','--no-index','--binary','--','/dev/null',name], cwd=ROOT, capture_output=True)
        need(result.returncode == 1 and not result.stderr, 'New-source diff capture failed')
        patch += result.stdout
    need(patch == (FINAL/'remediation.patch').read_bytes(), 'Complete patch bytes mismatch')

    issues = load(FINAL/'issues-continued.json'); gates = load(FINAL/'gates-continued.json'); cont = load(FINAL/'continuation-evidence.json')
    for value in (issues,gates,cont): walk_refs(value)
    prior_issues = {x['id']:x for x in load(FINAL/'issues.json')['issues']}
    new_issues = {x['id']:x for x in issues['issues']}
    need(len(new_issues) == len(issues['issues']) == 16 and set(new_issues)==set(prior_issues), 'Issue scope drift')
    need([k for k in new_issues if new_issues[k]!=prior_issues[k]] == ['DS-C01'], 'Unexpected changed issue object')
    need(all(new_issues[k]['status']==prior_issues[k]['status'] for k in new_issues), 'Issue status drift')
    counts = dict(collections.Counter(x['status'] for x in new_issues.values()))
    need(counts == {'FIXED AND VERIFIED':14,'PARTIALLY VERIFIED':1,'BLOCKED':1}, 'Issue counts incorrect')
    gate_counts = dict(collections.Counter(x['status'] for x in gates['gates']))
    need(len(gates['gates']) == len({x['id'] for x in gates['gates']}) == 38 and gate_counts=={'PASS':23,'BLOCKED':15}, 'Gate scope/count drift')

    cycles = []
    for record in cont['new_cycle_records']:
        p = ROOT / (record.get('receipt',{}).get('path') or record['final_receipt_locator'])
        receipt = load(p)
        if 'recorded_fields' in record:
            need(all(receipt.get(k)==v for k,v in record['recorded_fields'].items()), 'Cycle projection drift')
        cycles.append({'cycle':record['cycle'],'status_now':receipt.get('status'), 'receipt':reference(p), 'materialization_was_running':record.get('status_at_materialization')=='RUNNING'})
    need(len(cycles) == 11, 'Continuation cycle count changed')
    apphost = []
    for cycle in ('dsc01-apphost-01','dsc01-apphost-02'):
        d = RUN/'evidence'/cycle
        receipt = load(d/'receipt.json'); inputs = load(d/'input-manifest.json')
        identity = {k:v for k,v in frozen.items() if k!='recorded_at'}
        need(receipt['source_before'] == receipt['source_after'] == inputs['source'] == identity, 'Apphost source binding mismatch')
        for row in inputs['files']: need(sha(ROOT/row['path'])==row['sha256'],'Apphost control drift')
        need(sha_bytes(json.dumps(inputs['files'],separators=(',',':')).encode())==inputs['control_sha256']==receipt['approved_control_sha256']==receipt['controls_after_sha256'], 'Apphost control digest drift')
        copy_check = reconstruct_copies(cycle)
        observed = receipt['ownership_observed']; pids = sorted({x['pid'] for x in observed})
        ps = subprocess.run(['ps','-o','pid=,lstart=','-p',','.join(map(str,pids))],capture_output=True,text=True)
        need(ps.returncode in (0,1) and not ps.stderr, 'Targeted process observation failure')
        live = {}
        for line in ps.stdout.splitlines():
            pid,start = line.strip().split(None,1); live[int(pid)]=' '.join(start.split())
        matching = sorted({x['pid'] for x in observed if live.get(x['pid']) == ' '.join(x['start'].split())})
        exact_paths = [receipt['owned_temporary_directory'], *receipt['secondary_cleanup']['removed']]
        remaining = [name for name in exact_paths if Path(name).exists() or Path(name).is_symlink()]
        need(not matching and not remaining, 'Task-owned worker/path still present')
        apphost.append({'cycle':cycle,'copy_binding':copy_check,'control_sha256':inputs['control_sha256'],'receipt':reference(d/'receipt.json'),'owned_pid_count':len(pids),'targeted_ps_exit':ps.returncode,'same_pid_new_start_not_owned':sorted(live),'matching_task_processes':matching,'owned_paths_checked':exact_paths,'remaining_owned_paths':remaining})
    result = {'reviewer':'/root/release_fix_review','observed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Read-only evidence/checkout/path observations; no new build or runtime test, no cleanup or process signaling',
              'observer_sha256':sha(Path(__file__).absolute()),'source_manifest_sha256':FROZEN,'source_count':658,'tracked_diff_sha256':sha_bytes(diff),'complete_patch_sha256':sha_bytes(patch),'baseline_files':1618,'unchanged_original_files':unchanged,'modified_original_files':modified,'preserved_preexisting_untracked_count':sum(x['kind']!='tracked' for x in baseline['files']),'preexisting_untracked_changed':untracked_changed,'new_source_count':28,'refs_stash_index_preserved':True,'reference_occurrences':len(references),'distinct_reference_count':len(set(references)),'old_milestone_count':len(cont['previous_deliverables_unchanged']),'issue_counts':counts,'gate_counts':gate_counts,'continuation_cycles':cycles,'apphost':apphost,'new_final_deliverables':[reference(FINAL/n) for n in ('REPORT-CONTINUATION.md','issues-continued.json','gates-continued.json','continuation-evidence.json')], 'pending_final_observation_exists':(FINAL/'final-observation.json').exists()}
    print(json.dumps(result,indent=2))


if __name__ == '__main__':
    main()
