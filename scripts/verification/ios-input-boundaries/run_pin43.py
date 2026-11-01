#!/usr/bin/env python3
"""Run one original/seven-owned-leaf controlled pair, never until green."""
import sys
import run_pin03
import pin43_owned_leaf_pair

if __name__=='__main__':
    pin43_owned_leaf_pair.require_capability_binding()
    sys.exit(run_pin03.main(paired_owned_leaf=True))
