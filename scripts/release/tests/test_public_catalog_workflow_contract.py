"""Fail-closed controls for the optional, read-only PR300 artifact decoder."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as contract


class PublicCatalogWorkflowContractTest(unittest.TestCase):
    def test_exact_reviewed_workflow_and_companions_are_accepted(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_CATALOG_REVIEW).read_text()
        contract.verify_public_catalog_review(text)

    def test_privilege_runner_trigger_upload_or_decoder_changes_are_rejected(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_CATALOG_REVIEW).read_text()
        mutations = (
            ('contents: read', 'contents: write'),
            ('actions: read', 'actions: write'),
            ('macos-15', 'macos-15-large'),
            ('timeout-minutes: 10', 'timeout-minutes: 30'),
            ('  push:', '  workflow_dispatch:'),
            ('cancel-in-progress: false', 'cancel-in-progress: true'),
            ('persist-credentials: false', 'persist-credentials: true'),
            ('/receipt.json', '/**'),
            ('review_public_catalog_commit.py', 'review_public_back_commit.py'),
        )
        for old, new in mutations:
            self.assertIn(old, text)
            with self.subTest(old=old), self.assertRaises(RuntimeError):
                contract.verify_public_catalog_review(text.replace(old, new))

    def test_missing_changed_or_symlinked_companion_fails_closed(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_CATALOG_REVIEW).read_text()
        originals = contract.ROOT / 'scripts/verification/ios-input-boundaries'
        for name in contract.PUBLIC_CATALOG_REVIEW_COMPANIONS:
            for mutation in ('missing', 'changed', 'symlink'):
                with self.subTest(name=name, mutation=mutation), tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    destination = root / 'scripts/verification/ios-input-boundaries'
                    destination.mkdir(parents=True)
                    for companion in contract.PUBLIC_CATALOG_REVIEW_COMPANIONS:
                        (destination / companion).write_bytes((originals / companion).read_bytes())
                    target = destination / name
                    target.unlink()
                    if mutation == 'changed':
                        target.write_bytes((originals / name).read_bytes() + b'\n# changed\n')
                    elif mutation == 'symlink':
                        target.symlink_to(originals / name)
                    with patch.object(contract, 'ROOT', root), self.assertRaises(RuntimeError):
                        contract.verify_public_catalog_review(text)
