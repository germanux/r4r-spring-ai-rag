#!/usr/bin/env python3
"""Compatibility launcher for the Java importer. No chunking, metadata or embedding logic."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'lambda-titan/target/r4r-titan-retrieval-1.0.0.jar'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'knowledge/struct-data/r4r-s3vectors-import.json')
    parser.add_argument('--report', type=Path, default=ROOT / 'lambda-titan/target/import-report.json')
    parser.add_argument('--ingest', action='store_true', help='Enable actual Titan charges and writes to the Titan index only')
    args = parser.parse_args(argv)
    if not JAR.is_file():
        parser.error('Run mvn -f lambda-titan/pom.xml clean verify first')
    command = ['java', '-cp', str(JAR), 'com.riansares.r4r.titan.CorpusImporter',
               '--source', str(args.source.resolve()), '--report', str(args.report.resolve())]
    if args.ingest:
        command.append('--ingest')
    return subprocess.run(command, cwd=ROOT, check=False).returncode


if __name__ == '__main__':
    raise SystemExit(main())
