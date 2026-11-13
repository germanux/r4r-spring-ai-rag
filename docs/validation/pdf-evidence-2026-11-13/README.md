# PDF remains the primary evidence viewer

## Scope and content

- Generated **41 ES + 41 EN PDFs** from the existing Markdown corpus using the
  established ReportLab template, original logo, fonts, A4 margins, table styling
  and pagination. English headers/footers are localized. Headings are kept with
  their following content to avoid orphan headings.
- Markdown, database, embeddings and backend were not modified. Concurrent
  changes in evaluation scripts/tests were left outside this task and its commits.
- Eight original root-level PDFs remain byte-for-byte unchanged, checked against
  `front-athagon-r4r-static/fixtures/legacy-pdfs.json`.
- PDF paths preserve source IDs, names, languages and subdirectories:
  `es/guides/R4R_80_*.md` maps to `docs/es/guides/R4R_80_*.pdf`.
  The 41 reviewed ES/EN counterpart pairs use stable R4R IDs. Original retrieved
  identifiers, citation labels and headings remain available in evidence trace.
  Missing counterparts fall back to the actual source language, not an invented URL.
- The UI uses readable titles with an explicit PDF/language indicator. Numeric
  section links use **actual generated PDF page numbers**, excluding ambiguous
  duplicate section IDs. No HTML fragment is appended to a PDF.
- `export_public_documents.py` is now a compatibility entry point for the PDF
  exporter; it cannot generate replacement HTML viewers. Previous HTML assets
  are retained for old bookmarks, but are bypassed by the current PDF-only mapping.
- The catalogue switches only after all 82 files have valid PDF signatures and
  pass `pdfinfo`. The hosting copy contains the full coherent frontend and PDFs;
  the older demo directory and existing hosting PDFs were not modified.

## Verification

- **302 pages rendered** from all 82 PDFs; contact-sheet review plus high-resolution
  review of the kitchen guide and the wide price table. A bounding-box check found
  zero terminal orphan headings after fixing keep-with-next layout.
- **79 existing/frontend Node tests pass**, including ES/EN source mapping,
  original trace and actual PDF section pages.
- **4 PDF-specific tests pass**: 41 bilingual IDs, exact source-to-PDF paths,
  source/content hashes, PDF parser validity, real page bounds, embedded catalogue
  consistency, legacy preservation, and rejection of missing/HTML/truncated files.
- HTTP mode adds a fifth test. **90/90** URL responses have correct status,
  `application/pdf`, full PDF bytes and expected checksum on both the local source
  server and the exact prepared hosting copy. See [local](local-urls.json) and
  [hosting copy](publish-copy-urls.json) results.
- Chromium ES/EN at **390, 768, 1280 and 1920 px** checks actual PDF response bytes
  and MIME for cited links in addition to existing frontend interactions. No other
  frontend behavior is intentionally changed.

## Publication limitation — do not call this deployed

The normal GitLab authentication check failed: no credentials are available to
this session. No credentials or private files were read. The ready-to-publish
package is in `/home/german/Desarrollo/athagon-web.git/html-static/r4r-rag/`;
no commit, push or deployment was performed in that hosting repository.

All 90 URLs were also tested against both actual deployed hosts:

| Host | Valid exact PDFs | Remaining results |
|---|---:|---|
| athagon.tech | 8/90 | 82 new paths return 404; files are not published yet. |
| NAS | 5/90 | 82 new paths return 404; 3 legacy checks timed out. |

The three legacy NAS checks were retried separately with longer timeouts;
results remained timeout/unreachable, so their remote availability is
**INCONCLUSIVE**, not falsely classified as changed or broken by this release.
They pass the unchanged-byte and local HTTP tests. Full results:
[public URLs](public-urls.json), [NAS URLs](nas-urls.json), [NAS retries](nas-retry.json).

After publication, run:

```bash
R4R_PDF_BASE_URL=https://athagon.tech/r4r-rag/ node front-athagon-r4r-static/js/pdf-links.test.cjs
R4R_PDF_BASE_URL=http://192.168.1.203/r4r-rag/ node front-athagon-r4r-static/js/pdf-links.test.cjs
```

Until those remote checks pass, public availability of the new PDFs remains
pending. Local and prepared-copy success is not presented as production success.
