#!/usr/bin/env python3
"""Corrected public-name failure owner, isolated export control only."""
import sys
import run_pin03

if __name__ == '__main__':
    sys.exit(run_pin03.main(text_export_control=True))
