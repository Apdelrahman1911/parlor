#!/usr/bin/env python3
"""Original KMP workload then combined Xcode observations, never qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(aggregate_context=True))
