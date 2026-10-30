"""Exact optional read-only decoder contract; no wider workflow authority."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts.release import workflow_contract as contract


class BackReviewContractTest(unittest.TestCase):
    def test_exact_optional_workflow_rejects_paid_write_dispatch_execution_and_upload_changes(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_BACK_REVIEW).read_text()
        contract.verify_public_back_review(text)
        for old, new in (("macos-15", "macos-15-large"),
                         ("contents: read", "contents: write"),
                         ("actions: read", "actions: write"),
                         ("  push:", "  workflow_dispatch:"),
                         ("retention-days: 14", "retention-days: 90"),
                         ("receipt.json", "**/*"),
                         ("review_public_back_commit.py", "unreviewed.py")):
            self.assertIn(old, text)
            with self.subTest(change=new), self.assertRaises(RuntimeError):
                contract.verify_public_back_review(text.replace(old, new))

    def test_companion_mutation_and_unlisted_workflow_fail_closed(self):
        text = (contract.WORKFLOWS / contract.PUBLIC_BACK_REVIEW).read_text()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            companions = root / "scripts/verification/ios-input-boundaries"
            companions.mkdir(parents=True)
            for name in contract.PUBLIC_BACK_REVIEW_COMPANIONS:
                (companions / name).write_bytes((contract.ROOT / "scripts/verification/ios-input-boundaries" / name).read_bytes())
            with patch.object(contract, "ROOT", root):
                contract.verify_public_back_review(text)
                for name in contract.PUBLIC_BACK_REVIEW_COMPANIONS:
                    p = companions / name; original = p.read_bytes()
                    p.write_bytes(original + b"\n# changed intake/export\n")
                    with self.subTest(companion=name), self.assertRaises(RuntimeError):
                        contract.verify_public_back_review(text)
                    p.write_bytes(original)
            workflows = root / "workflows"; workflows.mkdir()
            for path in contract.WORKFLOWS.glob("*.yml"):
                (workflows / path.name).write_bytes(path.read_bytes())
            with patch.object(contract, "WORKFLOWS", workflows):
                contract.load_files()
                (workflows / "unlisted.yml").write_text("name: unreviewed\n")
                with self.assertRaises(RuntimeError):
                    contract.load_files()
