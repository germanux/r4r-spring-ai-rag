#!/usr/bin/env python3
"""Copy the canonical consent manager into the two static source repositories. No build or deploy."""
import argparse
from pathlib import Path
import shutil
parser=argparse.ArgumentParser()
parser.add_argument('--athagon',type=Path,required=True)
parser.add_argument('--commercial',type=Path,required=True)
args=parser.parse_args()
source=Path(__file__).resolve().parents[1]/'frontend/public/privacy-consent.js'
for directory in [args.athagon/'html-static',args.commercial/'dist']:
    if not (directory/'index.html').is_file():
        raise SystemExit('Not a known static site root: '+str(directory))
    shutil.copy2(source,directory/'privacy-consent.js')
    print(directory/'privacy-consent.js')
