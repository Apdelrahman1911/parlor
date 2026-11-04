"""No actual preinstalled-tool deletion in these resource-safety controls."""
from collections import namedtuple
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.ci import hosted_linux_capacity as c

Usage=namedtuple('Usage', 'total used free')
ENV=dict(GITHUB_ACTIONS='true',RUNNER_ENVIRONMENT='github-hosted',RUNNER_OS='Linux',
         RUNNER_ARCH='X64',ImageOS='ubuntu24',GITHUB_REPOSITORY='Apdelrahman1911/parlor',
         GITHUB_JOB='desktop-android',GITHUB_RUN_ID='41580234749',GITHUB_RUN_ATTEMPT='1')


class HostedCapacityTests(unittest.TestCase):
    def test_only_fixed_unused_tools_no_android_java_caches_or_evidence(self):
        self.assertEqual(tuple(map(str,c.TOOL_ROOTS)),('/usr/share/dotnet','/usr/local/.ghcup','/usr/local/swift','/opt/hostedtoolcache/CodeQL',
                                                     '/opt/hostedtoolcache/go','/opt/hostedtoolcache/Ruby',
                                                     '/opt/google/chrome','/opt/microsoft/msedge',
                                                     '/usr/local/share/chromium','/usr/local/lib/node_modules',
                                                     '/usr/local/aws-cli','/usr/local/aws-sam-cli',
                                                     '/usr/lib/google-cloud-sdk','/opt/az'))
        self.assertEqual(c.MIN_FREE_BYTES,26*1024**3)
        self.assertEqual(c.runner_task(ENV),{k:ENV[k] for k in c.TASK_KEYS})

    def test_previous_reserve_is_rejected_and_new_boundary_is_inclusive(self):
        for free, sufficient in ((23934611456, False), (c.MIN_FREE_BYTES - 1, False),
                                 (c.MIN_FREE_BYTES, True), (c.MIN_FREE_BYTES + 1, True)):
            with self.subTest(free=free), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp).resolve()/'unused';root.mkdir()
                result=c.reclaim((root,),lambda _:None,lambda _:Usage(100*1024**3,0,free))
                self.assertEqual(result['capacity_sufficient'],sufficient)
                self.assertEqual(result['minimum_free_bytes'],c.MIN_FREE_BYTES)

    def test_fixed_tools_never_include_required_runtime_or_dependency_roots(self):
        protected=('/usr/local/lib/android','/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk',
                   '/usr/bin','/home/runner','/root','/home/runner/work')
        for root in c.TOOL_ROOTS:
            self.assertFalse(any(root==Path(p) or root.is_relative_to(p) for p in protected))
        # GitHub actions run using the runner's own bundled Node, not these npm tools.
        self.assertNotIn(Path('/opt/hostedtoolcache/node'),c.TOOL_ROOTS)

    def test_cloud_cleanup_is_install_root_only_not_shared_runtime_or_command_directories(self):
        clouds=tuple(map(Path,('/usr/local/aws-cli','/usr/local/aws-sam-cli',
                               '/usr/lib/google-cloud-sdk','/opt/az')))
        self.assertEqual(c.TOOL_ROOTS[-4:],clouds)
        # Never remove shared parent trees, system Python, command symlinks,
        # cloud credentials, or the runner's bundled Node/actions.
        protected=('/usr/lib','/usr/local','/opt','/usr/bin','/usr/local/bin',
                   '/usr/lib/python3','/opt/hostedtoolcache/Python',
                   '/home/runner/.aws','/home/runner/.config/gcloud',
                   '/home/runner/.azure','/home/runner/runners')
        for root in clouds:
            self.assertFalse(any(root==Path(p) or Path(p).is_relative_to(root)
                                 for p in protected))

    def test_nonhosted_other_platform_repository_or_task_never_admitted(self):
        for key in ('GITHUB_ACTIONS','RUNNER_ENVIRONMENT','RUNNER_OS','RUNNER_ARCH','ImageOS','GITHUB_REPOSITORY','GITHUB_JOB'):
            for value in ('','unexpected'):
                with self.subTest(key=key,value=value),self.assertRaises(RuntimeError):
                    c.runner_task(ENV | {key:value})
        for key in c.TASK_KEYS[:2]:
            for value in ('', '1;rm', '١', '1'*21):
                with self.subTest(key=key,value=value),self.assertRaises(RuntimeError):c.runner_task(ENV|{key:value})

    def test_all_roots_validate_before_any_deletion_and_absent_tools_are_explicit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();valid=root/'valid';valid.mkdir();target=root/'target';target.mkdir();link=root/'link';link.symlink_to(target,target_is_directory=True)
            removed=[]
            with self.assertRaises(RuntimeError):c.reclaim((valid,link),removed.append)
            self.assertEqual(removed,[])
            calls=iter((Usage(100,80,20),Usage(100,70,30)))
            result=c.reclaim((valid,root/'absent'),removed.append,lambda _:next(calls))
            self.assertEqual(removed,[valid]);self.assertEqual([e['result'] for e in result['tools']],['REMOVED','NOT_INSTALLED'])
            self.assertEqual(result['reclaimed_bytes'],10)
            self.assertFalse(result['capacity_sufficient'])

    def test_regular_file_mounted_or_foreign_root_rejects(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();file=root/'file';file.write_text('keep')
            with self.assertRaises(RuntimeError):c.checked_tools((file,))
            with patch.object(Path,'is_mount',return_value=True),self.assertRaises(RuntimeError):c.checked_tools((root,))
            entry=root.stat();foreign=list(entry);foreign[4]=os.getuid()+100000
            with patch.object(Path,'lstat',return_value=os.stat_result(foreign)),self.assertRaises(RuntimeError):c.checked_tools((root,))

    def test_delete_argv_has_no_shell_follows_no_link_and_never_retries(self):
        with tempfile.TemporaryDirectory() as tmp:
            absent=Path(tmp).resolve()/'removed'
            with patch.object(c.subprocess,'run') as run:
                run.return_value.returncode=0;c.delete_tool(absent)
                self.assertEqual(run.call_count,1)
                self.assertEqual(run.call_args.args[0],['sudo','-n','rm','-rf','--one-file-system','--',str(absent)])
                self.assertEqual(run.call_args.kwargs['timeout'],120)
                self.assertNotIn('shell',run.call_args.kwargs)
                run.return_value.returncode=1
                with self.assertRaises(RuntimeError):c.delete_tool(absent)
                self.assertEqual(run.call_count,2)

    def test_canonical_exclusive_receipt_rejects_redirected_temp_and_existing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();env=ENV|{'RUNNER_TEMP':str(root)};task=c.runner_task(env)
            path=c.receipt_path(env,task);self.assertEqual(path.parent,root)
            path.write_text('custody')
            with self.assertRaises(RuntimeError):c.receipt_path(env,task)
            path.unlink();link=root/'link';link.symlink_to(root,target_is_directory=True)
            with self.assertRaises(RuntimeError):c.receipt_path(env|{'RUNNER_TEMP':str(link)},task)

    def test_capacity_success_failure_and_cleanup_errors_leave_private_receipts(self):
        for sufficient,raises in ((True,False),(False,False),(False,True)):
            with self.subTest(sufficient=sufficient,raises=raises),tempfile.TemporaryDirectory() as tmp:
                env=ENV|{'RUNNER_TEMP':str(Path(tmp).resolve())}
                result={'capacity_sufficient':sufficient,'after':{'free':c.MIN_FREE_BYTES if sufficient else 0},'reclaimed_bytes':0}
                with patch.dict(os.environ,env,clear=True),patch.object(c,'reclaim',return_value=result,side_effect=RuntimeError('safe cleanup failure') if raises else None):
                    self.assertEqual(c.main(),0 if sufficient else 1)
                path=next(Path(tmp).glob('*.json'));self.assertEqual(path.stat().st_mode&0o777,0o600)
                with patch.dict(os.environ,env,clear=True),self.assertRaises(RuntimeError):c.main()


class HostedCapacityWorkflowTests(unittest.TestCase):
    def test_closed_full_only_step_precedes_dependencies_and_retains_measurements(self):
        from scripts.release import workflow_contract as w
        source=(w.WORKFLOWS/'production-verification.yml').read_text()
        w.verify_hosted_linux_capacity(w.validation_job(source,'desktop-android'))
        self.assertEqual(source.count('- name: Reclaim unused standard Linux runner toolchains'),1)

    def test_unknown_cleanup_program_scope_or_missing_evidence_rejects(self):
        from scripts.release import workflow_contract as w
        job=w.validation_job((w.WORKFLOWS/'production-verification.yml').read_text(),'desktop-android')
        for old,new in (
            ('run: python3 -B scripts/ci/hosted_linux_capacity.py','run: sudo rm -rf /usr/local/lib/android'),
            ("        if: "+w.FULL_VERIFICATION_SCOPE+"\n        run: python3 -B scripts/ci/hosted_linux_capacity.py","        if: always()\n        run: python3 -B scripts/ci/hosted_linux_capacity.py"),
            ('${{ github.job }}-hosted-disk.json','${{ github.job }}-not-retained.json'),
        ):
            with self.subTest(new=new),self.assertRaises(RuntimeError):w.verify_hosted_linux_capacity(job.replace(old,new))

    def test_mutated_cleanup_source_cannot_silence_safeguards(self):
        from scripts.release import workflow_contract as w
        original=Path.read_bytes
        def changed(path):return b'changed' if path.name=='hosted_linux_capacity.py' else original(path)
        job=w.validation_job((w.WORKFLOWS/'production-verification.yml').read_text(),'desktop-android')
        with patch.object(Path,'read_bytes',changed),self.assertRaises(RuntimeError):w.verify_hosted_linux_capacity(job)

    def test_capacity_receipt_only_added_to_full_common_artifact(self):
        from scripts.release import workflow_contract as w
        for job in w.VERIFICATION_JOBS:
            if job in {'desktop-android', 'desktop-linux-arm64', 'desktop-macos-x64',
                       'desktop-windows-x64', 'ios', 'ios-release-simulator',
                       'ios-release-device', 'ios-release-x64'}:
                paths = w.current_verification_artifact_paths(job)
                receipts = [p for p in paths if p.endswith('-hosted-disk.json')]
                self.assertEqual(len(receipts), 1 if job == 'desktop-android' else 0)
                if receipts:
                    self.assertTrue(paths[paths.index(receipts[0]) - 1].endswith('-ownership.json'))
                    self.assertTrue(paths[paths.index(receipts[0]) + 1].endswith('-stop-*.json'))


if __name__ == '__main__':
    unittest.main()
