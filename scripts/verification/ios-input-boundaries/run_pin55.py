#!/usr/bin/env python3
"""Adapt frozen runner externally; never broaden its old profiles or success labels."""
import json
from pathlib import Path
import sys

import pin47_failure_runloop as prior
import pin55_first_responder as text
import run_pin03


def main():
    prior.require_capability_binding()
    text.require_capability()
    text.require('--profile' in sys.argv and sys.argv[sys.argv.index('--profile')+1] == text.PROFILE,
                 'Only explicit original-first-responder boundary')
    profiles, select = prior.PROFILES, prior.select_profile
    prior.PROFILES = (*profiles,text.PROFILE)
    prior.select_profile = lambda profile: text if profile == text.PROFILE else select(profile)
    try:
        result = run_pin03.main(failure_runloop=True)
        # Frozen runner rejects unknown labels. Admit ONLY our independently checked
        # new diagnostic result; never map a rejected/incomplete profile into success.
        out = Path(sys.argv[sys.argv.index('--output')+1])
        state = json.loads((out/'state.json').read_text())
        text.require(result == 1 and state['result'] == text.RESULT, 'Exact new completed result, no qualification mapping')
        text.require(text.accept_native_export(out,state) == text.RESULT, 'New source-specific native admission')
        return 0
    finally:
        prior.PROFILES, prior.select_profile = profiles, select


if __name__ == '__main__':
    sys.exit(main())
