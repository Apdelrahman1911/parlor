#!/usr/bin/env python3
"""Run the unchanged original suite/native adapter on the original failed iOS18.5."""
import json
from pathlib import Path
import sys

import pin47_failure_runloop as profiles
import pin61_original_185 as cohort
import run_pin03


def main():
    profiles.require_capability_binding()
    cohort.require_capability()
    cohort.require('--profile' in sys.argv and
        sys.argv[sys.argv.index('--profile') + 1] == cohort.PROFILE, 'Explicit original suite profile')
    previous, select = profiles.PROFILES, profiles.select_profile
    profiles.PROFILES = (*previous, cohort.PROFILE)
    profiles.select_profile = lambda profile: cohort if profile == cohort.PROFILE else select(profile)
    try:
        result = run_pin03.main(failure_runloop=True)
        out = Path(sys.argv[sys.argv.index('--output') + 1])
        state = json.loads((out / 'state.json').read_text())
        cohort.require(result == 1 and state['result'] == cohort.RESULT, 'Only completed diagnostic collection')
        cohort.require(cohort.accept_native_export(out, state) == cohort.RESULT, 'Exact new source-specific admission')
        return 0
    finally:
        profiles.PROFILES, profiles.select_profile = previous, select


if __name__ == '__main__':
    sys.exit(main())
