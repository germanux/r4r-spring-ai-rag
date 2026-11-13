#!/usr/bin/env python3
"""Compatibility entry point: publish branded bilingual PDFs, never HTML replacements."""
from export_sources import export_all

if __name__ == '__main__':
    catalogue = export_all()
    print(f'{sum(map(len, catalogue.values()))} PDFs, {len(catalogue)} stable IDs')
