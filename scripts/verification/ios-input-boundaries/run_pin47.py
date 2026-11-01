#!/usr/bin/env python3
"""Copied passive failure-only run-loop boundary on the original failed iOS runtime."""
import sys
import run_pin03
import pin47_failure_runloop
if __name__=='__main__':
    pin47_failure_runloop.require_capability_binding()
    sys.exit(run_pin03.main(failure_runloop=True))
