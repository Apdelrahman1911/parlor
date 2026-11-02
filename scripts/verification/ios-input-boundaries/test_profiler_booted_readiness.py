import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock,patch

import profiler_booted_readiness as c

SIM='12345678-1234-1234-1234-123456789ABC'


class BootedReadinessControls(unittest.TestCase):
    def test_scope_nonmac_or_existing_output_cannot_boot_or_start_profiler(self):
        with patch.object(c.sys,'platform','linux'),patch.object(c,'operation') as operation,self.assertRaises(RuntimeError):c.control(Path('/absent'))
        operation.assert_not_called()
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'),patch.object(c,'operation') as operation,self.assertRaises(ValueError):c.control(Path(d).resolve())
        operation.assert_not_called()

    def test_new_owned_boot_readiness_precedes_unchanged_profiler_then_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            root=Path(d).resolve()/'control';events=[]
            def path(directory,receipt,label,argv):
                self.assertEqual(argv,['/usr/bin/xcrun','--find','simctl']);(directory/'simctl-path.log').write_bytes(c.SIMCTL.encode()+b'\n')
            def operation(directory,receipt,label,args,timeout):
                events.append(label)
                self.assertNotIn('all',args)
                if label=='create':self.assertEqual(args[2:],[c.DEVICE,c.RUNTIME]);return SIM.encode()+b'\n'
                if label=='cleanup-ownership':return json.dumps({'devices':{c.RUNTIME:[dict(udid=SIM,name=receipt['owned_name'],state='Booted')]}}).encode()
                self.assertIn(SIM,args);self.assertEqual(timeout,120 if label=='bootstatus' else 90)
                return b''
            with patch.object(c.profiler,'command',side_effect=path),patch.object(c,'operation',side_effect=operation), \
                    patch.object(c,'prepare_profiler',return_value={}),patch.object(c,'execute_prepared_profiler',side_effect=lambda *_:events.append('profiler')):
                c.control(root)
            self.assertEqual(events,['create','boot','bootstatus','profiler','cleanup-ownership','shutdown','delete'])
            r=json.loads((root/'receipt.json').read_text());self.assertTrue(r['owned_simulator_retired']);self.assertEqual(r['apps_launched'],0)

    def test_actual_profiler_failure_is_not_waived_and_owned_device_is_still_retired(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            out=Path(d).resolve()/'control'
            def path(directory,receipt,*_):(directory/'simctl-path.log').write_bytes(c.SIMCTL.encode()+b'\n')
            def operation(directory,receipt,label,args,timeout):return SIM.encode()+b'\n' if label=='create' else b''
            with patch.object(c.profiler,'command',side_effect=path),patch.object(c,'operation',side_effect=operation), \
                    patch.object(c,'prepare_profiler',return_value={}),patch.object(c,'execute_prepared_profiler',side_effect=ValueError('actual enumeration failure')),patch.object(c,'cleanup') as cleanup,self.assertRaisesRegex(ValueError,'actual enumeration failure'):
                c.control(out)
            cleanup.assert_called_once();r=json.loads((out/'receipt.json').read_text());self.assertIn('actual enumeration failure',r['error']);self.assertNotIn('result',r)

    def test_wrong_tool_path_cannot_create_device_and_wrong_uuid_cannot_run_profiler(self):
        for wrong in ('path','uuid'):
            with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
                out=Path(d).resolve()/'control'
                def path(directory,receipt,*_):(directory/'simctl-path.log').write_bytes(b'/foreign/tool\n' if wrong=='path' else c.SIMCTL.encode()+b'\n')
                with patch.object(c.profiler,'command',side_effect=path),patch.object(c,'operation',return_value=b'foreign-uuid\n') as op, \
                        patch.object(c,'prepare_profiler',return_value={}),patch.object(c,'execute_prepared_profiler') as profiler,self.assertRaises(ValueError):c.control(out)
                profiler.assert_not_called();self.assertEqual(op.call_count,0 if wrong=='path' else 1)

    def test_foreign_or_renamed_device_never_shutdown_deleted(self):
        for devices in ([],[dict(udid=SIM,name='foreign',state='Booted')],[dict(udid='foreign',name='owned',state='Booted')]):
            with tempfile.TemporaryDirectory() as d:
                r=dict(owned_simulator=SIM,owned_name='owned',commands=[])
                with patch.object(c,'operation',return_value=json.dumps({'devices':{c.RUNTIME:devices}}).encode()) as op,self.assertRaises(ValueError):c.cleanup(Path(d).resolve(),r)
                self.assertEqual(op.call_count,1)

    def test_invalid_owned_uuid_never_executes_cleanup_command(self):
        with patch.object(c,'operation') as op,self.assertRaises(ValueError):c.cleanup(Path('/owned'),dict(owned_simulator='all'))
        op.assert_not_called()

    def test_preboot_readiness_failure_prevents_any_device_creation_or_target(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            out=Path(d).resolve()/'control'
            def path(directory,receipt,*_):(directory/'simctl-path.log').write_bytes(c.SIMCTL.encode()+b'\n')
            with patch.object(c.profiler,'command',side_effect=path),patch.object(c,'prepare_profiler',side_effect=ValueError('actual early readiness failure')), \
                    patch.object(c,'operation') as op,patch.object(c,'execute_prepared_profiler') as target,self.assertRaises(ValueError):c.control(out)
            op.assert_not_called();target.assert_not_called()

    def test_prepared_commands_are_exact_prior_observations_not_new_executions_and_dispatch_restores(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d).resolve();early=root/'early';early.mkdir();out=root/'profiler'
            prepared=dict(commands=[])
            for index,(label,argv) in enumerate(c.PREBOOT_COMMANDS.items()):
                (early/(label+'.log')).write_bytes(b'actual-ready')
                prepared['commands'].append(dict(label=label,command=argv,status='COMPLETE',exit=0,seconds=0.1,owned_command_pid=index+2,owned_child_reaped=True,started_unix=1.0,ended_unix=1.1))
            original=c.profiler.command
            def control(directory):
                directory.mkdir();r=dict(commands=[])
                for label,argv in c.PREBOOT_COMMANDS.items():c.profiler.command(directory,r,label,argv)
                self.assertEqual([x['owned_command_pid'] for x in r['commands']],[2,3,4,5])
                self.assertTrue(all(x['preboot_output_sha256'] for x in r['commands']))
                with self.assertRaises(ValueError):c.profiler.command(directory,r,'ready-path',c.PREBOOT_COMMANDS['ready-path'])
                raise ValueError('original control failure')
            with patch.object(c.profiler,'control',side_effect=control),self.assertRaisesRegex(ValueError,'original control failure'):
                c.execute_prepared_profiler(out,early,prepared)
            self.assertIs(c.profiler.command,original)

    def test_same_original_profiler_sampling_and_deadlines_no_permission_or_test_changes(self):
        self.assertEqual(c.profiler.record_command(42,Path('/owned/trace')),[c.profiler.PROFILER,'record','--template','Time Profiler','--attach','42','--time-limit','1s','--output','/owned/trace'])
        text=Path(c.__file__).read_text()
        for token in ('DevToolsSecurity -enable','-retry-tests','sudo','typeText(','.tap()','gradlew','xcodebuild -runFirstLaunch'):
            self.assertNotIn(token,text)
        self.assertIn('profiler.control(directory)',text)


if __name__=='__main__':unittest.main()
