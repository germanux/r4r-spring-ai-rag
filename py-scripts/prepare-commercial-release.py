#!/usr/bin/env python3
"""Assemble a reviewable static release; never publish or alter source repositories."""
import argparse
import pathlib
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--commercial', type=pathlib.Path, required=True)
parser.add_argument('--output', type=pathlib.Path, required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
out = args.output.resolve()
if out.exists():
    raise SystemExit('Output must be a new directory (no destructive replacement).')
bundle = root / 'frontend/dist/r4r-frontend/browser'
if not (bundle / 'index.html').is_file():
    raise SystemExit('First run: npm --prefix frontend run build -- --base-href ./')
files = subprocess.check_output(['git', '-C', str(args.commercial), 'ls-files', '-z', 'dist/']).decode().split('\0')
for name in filter(None, files):
    relative = pathlib.Path(name).relative_to('dist')
    if '.gitignore' in relative.parts or relative.name.startswith('webpack.'):
        continue
    destination = out / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.commercial / name, destination)
# Explicit new source files, not yet tracked by the controller.
for name in ['js/assistant-embed.js', '_headers', 'privacy-consent.js']:
    shutil.copy2(args.commercial / 'dist' / name, out / name)
shutil.copytree(bundle, out / 'asistente', ignore=shutil.ignore_patterns('fixtures'))
shutil.copytree(root / 'front-athagon-r4r-static/docs', out / 'asistente/docs')
print(out)
