#!/usr/bin/env python3
"""Independent, read-only evidence reconciliation; writes only one new review JSON.
No repository helper imports, Gradle, network, native APIs, private stores or tests.
"""
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT / 'remediation-runs/2026-09-07-local-readiness'
OUT = BASE / 'reviews/dependency-current-input-static-checks-01.json'
GRAPH_DIR = BASE / 'evidence/dependency-graph-export-02/raw-graphs'
DECL_DIR = BASE / 'evidence/dependency-license-research-03/report'
CI = BASE / 'reviews/ci-run-34152138368-evidence/retained'
CI_COMMIT = '89dbe8aeaf4e2c83f491521629982be09706cf67'
PREFIXES = ('composeApp/', 'shared/', 'game-modes/', 'build-logic/', 'gradle/', 'config/', 'iosApp/')
START = dt.datetime.now(dt.timezone.utc).isoformat()
assert not OUT.exists() and sys.flags.optimize == 0

def raw(path):
    p = Path(path)
    if not p.is_absolute():
        p = ROOT / p
    assert p.is_file() and not p.is_symlink(), str(p)
    return p.read_bytes()

def sha(data):
    return hashlib.sha256(data).hexdigest()

def read(path):
    return json.loads(raw(path))

def evidence(path):
    p = Path(path)
    if not p.is_absolute():
        p = ROOT / p
    data = raw(p)
    return dict(path=str(p.relative_to(ROOT)), sha256=sha(data), bytes=len(data))

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def identity():
    return dict(head=git('rev-parse', 'HEAD').decode().strip(),
                tree=git('rev-parse', 'HEAD^{tree}').decode().strip(),
                branch=git('branch', '--show-current').decode().strip(),
                tracked_status=git('status', '--short', '--untracked-files=no').decode(),
                diff_sha256=sha(git('diff', '--binary', 'HEAD')))

before = identity()
old_receipt = read(BASE / 'evidence/dependency-graph-export-02/receipt.json')
old = old_receipt['source_before']
assert old == old_receipt['source_after']
assert sha(json.dumps(old['source_manifest'], separators=(',', ':')).encode()) == old['source_manifest_sha256']
assert len(dict(old['source_manifest'])) == len(old['source_manifest']) == 742
assert old_receipt['status'] == 'PASS' and old_receipt['exit_code'] == old_receipt['stop_exit_code'] == 0
assert not old_receipt['cleanup_errors'] and not old_receipt['remaining_outputs']
assert not old_receipt['workers']['remaining_owned_workers']
assert '--dependency-verification=strict' in old_receipt['command']
assert '\n> Task :writeResolvedDependencyInventory\n' in raw(BASE / 'evidence/dependency-graph-export-02/gradle.log').decode()
assert '5 actionable tasks: 4 executed, 1 from cache' in raw(BASE / 'evidence/dependency-graph-export-02/gradle.log').decode()
old_map = dict(old['source_manifest'])
ci_inventory_path = CI / 'ios-verification/parlor/parlor/build/ci-evidence/ios-release-artifact-inventory.json'
ci_inventory = read(ci_inventory_path)
ci_source = ci_inventory['source']
assert ci_source['head'] == CI_COMMIT
ci_map = {r['path']: r['sha256'] for r in ci_source['files']}
assert len(ci_map) == len(ci_source['files']) == 796
tracked = git('ls-files', '-z').decode().split('\0')[:-1]
fixed_inputs = {
    'gradle/libs.versions.toml', 'gradle.properties', 'gradle/verification-metadata.xml',
    'gradle/wrapper/gradle-wrapper.properties', 'gradle/wrapper/gradle-wrapper.jar',
    'gradlew', 'gradlew.bat', 'config/parlor-version.xcconfig',
    'iosApp/Configuration/Config.xcconfig', 'config/detekt/detekt.yml',
}
inputs = sorted(p for p in tracked if p.endswith(('build.gradle.kts', 'settings.gradle.kts'))
                or p.startswith('build-logic/convention/src/') or p in fixed_inputs)
assert len(inputs) == 30
exporter = 'scripts/verification/resolved_dependencies.init.gradle'
input_rows = []
for path in [*inputs, exporter]:
    current = raw(path)
    digest = sha(current)
    assert digest == old_map[path] == ci_map[path]
    assert sha(git('show', f'{CI_COMMIT}:{path}')) == digest
    input_rows.append(dict(path=path, bytes=len(current), sha256=digest,
        old_actual_manifest_matches=True, ci_actual_manifest_and_git_blob_match=True))
untracked = git('ls-files', '--others', '--exclude-standard', '--', *[p[:-1] for p in PREFIXES]).decode()
assert untracked == ''
shipping_rows = [r for r in ci_source['files'] if r['path'].startswith(PREFIXES)]
assert all(sha(raw(r['path'])) == r['sha256'] for r in shipping_rows)
assert {p for p in tracked if p.startswith(PREFIXES)} == {r['path'] for r in shipping_rows}
research_receipt = read(BASE / 'evidence/dependency-license-research-03/receipt.json')
assert research_receipt['status'] == 'PASS' and research_receipt['exit_code'] == 0
assert research_receipt['control_before'] == research_receipt['control_after']
for p, digest in research_receipt['control_before'].items():
    assert sha(raw(p)) == digest == ci_map[p]

completion = read(GRAPH_DIR / 'resolution-complete.json')
assert completion['status'] == 'COMPLETE'
expected_source = {k: old[k] for k in ('commit', 'tree', 'diff_sha256')}
assert completion['source'] == expected_source
license_report = read(DECL_DIR / 'license-evidence.json')
assert license_report['source'] == expected_source
assert license_report['graph_sha256'] == completion['graph_sha256']
assert license_report['unresolved'] == []
ns = '{https://schema.gradle.org/dependency-verification}'
verification = ET.fromstring(raw('gradle/verification-metadata.xml'))
allowed = {}
for component in verification.findall(ns+'components/'+ns+'component'):
    component_id = ':'.join(component.attrib[k] for k in ('group', 'name', 'version'))
    for artifact in component.findall(ns+'artifact'):
        allowed[(component_id, artifact.attrib['name'])] = {n.attrib['value'] for n in artifact.findall(ns+'sha256')}
all_maven, all_artifacts, graph_rows = set(), set(), []
graphs = {}
for name, digest in completion['graph_sha256'].items():
    path = GRAPH_DIR / (name+'.json')
    assert sha(raw(path)) == digest
    graph = read(path)
    assert graph['source'] == expected_source and graph['graph'] == name and graph['strict_dependency_verification'] is True
    expected_config = 'releaseRuntimeClasspath' if name == 'androidRelease' else name+'CompileKlibraries'
    assert graph['configuration'] == expected_config
    ids = {r['id'] for r in graph['components']}
    assert len(ids) == len(graph['components']) and graph['root_component'] == 'project::composeApp'
    assert not {'project::shared:engine-testing', 'project::shared:networking-testing'} & ids
    for row in graph['components']:
        assert row['kind'] in ('maven', 'project')
        if row['kind'] == 'maven': all_maven.add(row['id'])
        for edge in row['dependencies']:
            assert edge['selected'] in ids and type(edge['constraint']) is bool
    artifact_keys = set()
    for row in graph['artifacts']:
        key = row['component'], row['name']
        assert key not in artifact_keys and row['component'] in ids
        artifact_keys.add(key)
        assert row['sha256'] in allowed[key] and type(row['bytes']) is int and row['bytes'] > 0
        all_artifacts.add((row['component'], row['name'], row['sha256'], row['bytes']))
    graphs[name] = graph
    graph_rows.append(dict(graph=name, raw_graph=evidence(path), configuration=graph['configuration'],
        components=len(ids), maven_components=sum(c['kind']=='maven' for c in graph['components']),
        artifacts=len(graph['artifacts']), strict_artifact_sha_matches=len(graph['artifacts']),
        edges_including_constraints=sum(len(c['dependencies']) for c in graph['components']),
        deduplicated_nonconstraint_edges=sum(len({d['selected'] for d in c['dependencies'] if not d['constraint']}) for c in graph['components']),
        project_components=sorted(x for x in ids if x.startswith('project:'))))
assert len(all_maven) == 456 and len(all_artifacts) == 372

# Independently parse entire retained POM bytes; do not call the renderer.
declarations = license_report['declarations']
prior_pom_ledger = read(BASE / 'reviews/dependency-research03-pom-evidence-ledger-01.json')
prior_poms = {r['component']: r for r in prior_pom_ledger['files']}
assert len(declarations) == len(prior_poms) == 459
assert len(list((DECL_DIR/'metadata').glob('*'))) == 459
parsed_poms, pom_bytes = {}, 0
for identifier, row in declarations.items():
    assert row['status'] == 'PUBLISHER_DECLARED' and row['component'] == identifier
    assert re.fullmatch('[a-f0-9]{64}\\.pom', row['file'])
    data = raw(DECL_DIR/'metadata'/row['file'])
    assert sha(data) == row['sha256'] == row['file'][:-4] == prior_poms[identifier]['sha256']
    assert len(data) == prior_poms[identifier]['bytes']
    text = data.decode('utf-8-sig')
    assert '<!DOCTYPE' not in text.upper() and '<!ENTITY' not in text.upper() and '\0' not in text
    root = ET.fromstring(text)
    assert root.tag in ('project', '{http://maven.apache.org/POM/4.0.0}project')
    prefix = root.tag[:-7]
    def value(path):
        node = root.find('/'.join(prefix+p for p in path.split('/')))
        return '' if node is None or node.text is None else node.text.strip()
    actual_id = ':'.join((value('groupId') or value('parent/groupId'), value('artifactId'), value('version') or value('parent/version')))
    assert actual_id == identifier
    direct = [dict(name=(item.findtext(prefix+'name') or '').strip(), url=(item.findtext(prefix+'url') or '').strip()) for item in root.findall(prefix+'licenses/'+prefix+'license')]
    parent = ':'.join(value('parent/'+k) for k in ('groupId', 'artifactId', 'version')) if value('parent/artifactId') else None
    parsed_poms[identifier] = dict(licenses=direct, parent=parent)
    if direct:
        assert direct == row['licenses'] and 'inherited_from' not in row
    else:
        assert parent == row['inherited_from'] and declarations[parent]['licenses'] == row['licenses']
    group, name, version = identifier.split(':')
    suffix = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
    assert row['url'] in [base+suffix for base in ('https://repo.maven.apache.org/maven2/', 'https://dl.google.com/dl/android/maven2/')]
    assert dt.datetime.fromisoformat(research_receipt['started_at']) <= dt.datetime.fromisoformat(row['accessed_at']) <= dt.datetime.fromisoformat(research_receipt['finished_at'])
    pom_bytes += len(data)
for identifier in declarations:
    chain, current = [], identifier
    while not parsed_poms[current]['licenses']:
        assert current not in chain and len(chain) < 5
        chain.append(current)
        current = parsed_poms[current]['parent']
    assert declarations[identifier]['licenses'] == parsed_poms[current]['licenses']
extra_parents = sorted(set(declarations)-all_maven)
assert extra_parents == ['com.google.guava:guava-parent:26.0-android', 'org.slf4j:slf4j-bom:2.0.16', 'org.slf4j:slf4j-parent:2.0.16']

schema_receipt = read(BASE/'evidence/dependency-sbom-schema-02/schema-validation.json')
assert schema_receipt['result'] == 'PASS' and len(schema_receipt['results']) == 4
schema_cycle = read(BASE/'evidence/dependency-sbom-schema-cycle-02/receipt.json')
assert schema_cycle['exit_code'] == schema_cycle['stop_exit_code'] == 0 and not schema_cycle['cleanup_errors']
for row in schema_receipt['schemas']:
    path = ROOT/row['file'] if 'file' in row else BASE/'evidence/dependency-sbom-schema-02'/row['url'].rsplit('/',1)[-1]
    assert sha(raw(path)) == row['sha256']
    json.loads(raw(path))
sbom_rows = []
for schema_row in schema_receipt['results']:
    assert schema_row['errors'] == [] and sha(raw(schema_row['file'])) == schema_row['sha256']
    bom = read(schema_row['file'])
    props = {r['name']: r['value'] for r in bom['metadata']['properties']}
    name = props['parlor:graph']; graph = graphs[name]
    assert {k: props['parlor:'+k] for k in expected_source} == expected_source
    assert props['parlor:configuration'] == graph['configuration']
    assert bom['bomFormat'] == 'CycloneDX' and bom['specVersion'] == '1.6'
    assert bom['metadata']['component']['version'] == expected_source['commit']
    refs = {}
    for c in graph['components']:
        if c['kind'] == 'maven':
            group, artifact, version = c['id'].split(':')
            refs[c['id']] = f'pkg:maven/{urllib.parse.quote(group,safe="")}/{urllib.parse.quote(artifact,safe="")}@{urllib.parse.quote(version,safe="")}'
        else: refs[c['id']] = c['id']
    bom_components = {c['bom-ref']: c for c in bom['components']}
    assert len(bom_components) == len(bom['components']) == len(refs)
    for c in graph['components']:
        b = bom_components[refs[c['id']]]
        assert b['type'] == 'library'
        if c['kind'] == 'maven':
            group, artifact, version = c['id'].split(':')
            assert (b['group'], b['name'], b['version'], b['purl']) == (group,artifact,version,refs[c['id']])
            assert b['licenses'] == [{'license':{k:v for k,v in item.items() if v}} for item in declarations[c['id']]['licenses']]
            expected = sorted((a['name'],a['sha256'],str(a['bytes'])) for a in graph['artifacts'] if a['component']==c['id'])
            actual = sorted((a['name'],a['hashes'][0]['content'],a['properties'][0]['value']) for a in b['components'])
            assert actual == expected and all(a['hashes'][0]['alg']=='SHA-256' for a in b['components'])
        else:
            assert b['properties'] == [{'name':'parlor:license-status','value':'OWNER_DECISION_REQUIRED'}]
    actual_edges = {r['ref']:r['dependsOn'] for r in bom['dependencies']}
    expected_edges = {refs[c['id']]:sorted({refs[d['selected']] for d in c['dependencies'] if not d['constraint']}) for c in graph['components']}
    assert len(actual_edges) == len(bom['dependencies']) and actual_edges == expected_edges
    sbom_rows.append(dict(graph=name, evidence=evidence(schema_row['file']), all_components_artifacts_licenses_edges_match=True, historical_official_schema_receipt_still_binds_same_bytes=True))

notice_manifest = read('config/third-party-notices.json')
manifest_hash = sha(raw('config/third-party-notices.json'))
catalog = raw('gradle/libs.versions.toml')
assert sha(catalog.replace(b'\r\n',b'\n')) == notice_manifest['catalog']['sha256']
notice_dir = ROOT/notice_manifest['resource_directory']
assert {p.name for p in notice_dir.iterdir()} == {r['name'] for r in notice_manifest['files']}
notice_rows = []
for r in notice_manifest['files']:
    path = notice_dir/r['name']; data = raw(path)
    assert len(data) == r['bytes'] and sha(data) == r['sha256'] == ci_map[str(path.relative_to(ROOT))]
    notice_rows.append({k:r[k] for k in ('name','bytes','sha256')})
assert len(notice_rows) == notice_manifest['resource_count'] == 26
notice_total = sum(r['bytes'] for r in notice_rows)
assert notice_total == 99515
package_rows = []
for label, job, prefix in [('android','desktop-android-verification','base/assets/'),('ios','ios-verification','compose-resources/')]:
    base = CI/job/'parlor/parlor/build/ci-evidence'
    notices = read(base/(label+'-release-notices.json'))
    inventory = read(base/(label+'-release-artifact-inventory.json'))
    assert notices['status'] == notices['source']['status'] == notices['package']['status'] == inventory['status'] == 'PASS'
    assert notices['source']['manifest_sha256'] == manifest_hash
    assert notices['source']['catalog_sha256'] == notice_manifest['catalog']['sha256']
    assert notices['source']['files'] == notice_rows
    actual = notices['package']['files']
    assert [{k:r[k] for k in ('name','bytes','sha256')} for r in actual] == notice_rows
    assert all(r['path'] == prefix+'composeResources/com.parlor.app.resources/files/legal/'+r['name'] for r in actual)
    assert notices['package']['resource_count'] == 26 and notices['package']['total_bytes'] == notice_total
    entries = {r['path']:r for r in inventory['entries' if label=='android' else 'files']}
    for r in actual:
        assert entries[r['path']]['sha256'] == r['sha256'] and entries[r['path']]['bytes'] == r['bytes']
    package_rows.append(dict(platform=label, notice_receipt=evidence(base/(label+'-release-notices.json')),
        inventory=evidence(base/(label+'-release-artifact-inventory.json')), exact_source_manifest_and_26_entries_match=True,
        artifact_sha256=inventory.get('artifact_sha256'), total_notice_bytes=notice_total,
        limitation='Android raw AAB independently rehashed/inspected in previous C01 report; iOS raw app not uploaded, source-bound producer inventory only.'))

# Notice bindings are newer than graph export; never back-date delivery evidence.
for p in ('config/third-party-notices.json','.gitattributes','scripts/verification/third_party_notices.py','scripts/verification/ios_release_artifacts.py'):
    assert p not in old_map and sha(raw(p)) == ci_map[p] == sha(git('show',f'{CI_COMMIT}:{p}'))

# Every read above completed without modification; recheck source inputs once more.
for row in input_rows:
    assert sha(raw(row['path'])) == row['sha256']
after = identity()
assert before == after
result = dict(schema_version=1, reviewer='/root/release_fix_review', started_at=START,
    finished_at=dt.datetime.now(dt.timezone.utc).isoformat(), status='PASS_SCOPED_READ_ONLY_INPUT_RECONCILIATION',
    baseline=before, source_stable_during_parse=True, script=evidence(Path(__file__)),
    historical_export_source=expected_source, historical_source_manifest_sha256=old['source_manifest_sha256'],
    historical_receipt=evidence(BASE/'evidence/dependency-graph-export-02/receipt.json'),
    ci_source_commit=CI_COMMIT, ci_source_tree=ci_source['tree'], ci_manifest_sha256=ci_source['source_manifest_sha256'],
    input_scope='30 tracked config/build files including Detekt config, plus the exporter; source hashes are comparison evidence, not a new resolution run.',
    input_files=input_rows, compiled_source_directory_files_matching_ci=len(shipping_rows), untracked_configured_inputs=[],
    renderer_controls=research_receipt['control_before'], graphs=graph_rows,
    unique_maven_coordinates=len(all_maven), unique_artifact_content_identities=len(all_artifacts),
    strict_artifact_pin_misses=0, graph_test_fixture_projects_absent=True,
    poms=dict(files=len(declarations), bytes=pom_bytes, all_entire_xml_documents_reparsed=True,
        hashes_urls_coordinates_access_times_direct_and_inherited_licenses_match=True,
        inherited_license_records=sum(not p['licenses'] for p in parsed_poms.values()), extra_parent_coordinates=extra_parents,
        original_independent_ledger=evidence(BASE/'reviews/dependency-research03-pom-evidence-ledger-01.json')),
    sboms=sbom_rows, historical_schema_receipt=evidence(BASE/'evidence/dependency-sbom-schema-02/schema-validation.json'),
    schema_validation_not_reexecuted=True, notice_manifest=evidence('config/third-party-notices.json'),
    notice_resources=notice_rows, package_binding=package_rows,
    current_delta_from_ci=git('diff','--name-status',CI_COMMIT,'HEAD').decode(),
    hygiene=dict(no_gradle_xcode_app_test_native_network_or_background_workers_started=True,
        no_global_cache_or_user_work_touched=True, no_generated_build_outputs_created=True,
        output_is_required_compact_evidence=True, only_synchronous_git_and_python_readers_used=True))
with OUT.open('x') as stream:
    json.dump(result,stream,indent=2)
    stream.write('\n')
print(json.dumps(dict(status=result['status'],output=str(OUT.relative_to(ROOT)),sha256=sha(raw(OUT)),
    inputs=len(input_rows),shipping_files=len(shipping_rows),maven=len(all_maven),artifacts=len(all_artifacts),
    poms=len(declarations),sboms=len(sbom_rows),notices=len(notice_rows))))
