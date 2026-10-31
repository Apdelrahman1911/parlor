#!/usr/bin/env python3
"""Single combined Xcode context observations, not application qualification."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(combined_execution=True))
