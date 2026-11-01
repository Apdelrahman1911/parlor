#!/usr/bin/env python3
"""One isolated native-only export capability, never production qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(native_only_export=True))
