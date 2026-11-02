"""Portable source safeguards, not actual Xcode availability or runtime evidence."""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import review_profiler_tool as tool


class ProfilerToolReviewTests(unittest.TestCase):
    def test_exact_installed_interfaces_never_record_attach_launch_or_query(self):
        self.assertEqual([c for _,c in tool.COMMANDS],[
            ['/usr/bin/sw_vers'],['/usr/bin/xcrun','--find','xctrace'],
            ['/usr/bin/xcrun','xctrace','help'],['/usr/bin/xcrun','xctrace','help','record'],
            ['/usr/bin/xcrun','xctrace','help','export'],['/usr/bin/xcrun','xctrace','list','templates'],
        ])

    def test_non_apple_host_cannot_attest_native_availability(self):
        with patch.object(tool.sys,'platform','linux'),self.assertRaisesRegex(RuntimeError,'actual macOS'):
            tool.review(Path('/unavailable'))

    def test_existing_or_redirected_output_rejected_before_commands(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(tool.sys,'platform','darwin'),patch.object(tool.subprocess,'run') as run:
            root=Path(tmp).resolve()
            with self.assertRaises(ValueError):tool.review(root)
            link=root/'link';link.symlink_to(root,target_is_directory=True)
            with self.assertRaises(ValueError):tool.review(link/'out')
            run.assert_not_called()

    def test_exact_exits_hashes_developer_and_clean_environment_retained(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(tool.sys,'platform','darwin'),patch.dict(os.environ,{'GH_TOKEN':'not-forwarded','DYLD_INSERT_LIBRARIES':'not-forwarded'}),patch('sys.stdout',new=io.StringIO()):
            target=Path(tmp).resolve()/'out'
            def execute(command,**kwargs):
                self.assertEqual(kwargs['timeout'],8)
                self.assertEqual(set(kwargs['env'])-{'HOME','TMPDIR'},{'PATH','LC_ALL','DEVELOPER_DIR'})
                self.assertEqual(kwargs['env']['DEVELOPER_DIR'],tool.DEVELOPER)
                return subprocess.CompletedProcess(command,255,b'actual usage output\n')
            with patch.object(tool.subprocess,'run',side_effect=execute) as run:tool.review(target)
            self.assertEqual(run.call_count,6)
            receipt=json.loads((target/'receipt.json').read_text())
            self.assertEqual([r['exit'] for r in receipt['commands']],[255]*6)
            self.assertEqual(receipt['apps_launched'],0);self.assertEqual(receipt['recordings_started'],0)
            self.assertFalse(receipt['shipping_qualified'])

    def test_empty_oversized_or_late_tool_evidence_cannot_be_admitted(self):
        for raw in (b'',b'x'*(tool.MAX_OUTPUT+1)):
            with tempfile.TemporaryDirectory() as tmp,patch.object(tool.sys,'platform','darwin'),patch.object(tool.subprocess,'run',return_value=subprocess.CompletedProcess([],0,raw)),self.assertRaises(ValueError):
                tool.review(Path(tmp).resolve()/'out')
        with tempfile.TemporaryDirectory() as tmp,patch.object(tool.sys,'platform','darwin'),patch.object(tool.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'usage')),patch.object(tool.time,'monotonic',side_effect=[0,8.1]),self.assertRaisesRegex(ValueError,'Late observation'):
            tool.review(Path(tmp).resolve()/'out')
