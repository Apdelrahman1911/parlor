#!/usr/bin/env python3
"""Execute only the existing failed touch sentinel with the new public-scroll boundary."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(public_scroll_export=True))
