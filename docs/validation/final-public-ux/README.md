# Public UX validation — 2026-11-13

The working tree started clean at `f1240cc`. Changes concern only the static frontend,
its tests, recorded fixtures and documentation. No backend, model, prompt, threshold,
contract, secret, deployment or provider-routing change was made.

## Bilingual retrieval

Eight successful live requests to the NAS API used only `question` and
`responseLanguage`; no browser-generated retrieval question. Exact answers,
citations, available SSE stages, per-stage timings, embedding models and per-request
generation models are in [bilingual-evidence.json](bilingual-evidence.json).

| Pair | Result | ES / EN latency | Evidence |
|---|---|---|---|
| A: services | PASS, with wording caveat | 2879 / 4051 ms | Both cite identity/services, mentioning homes and small offices. EN conservatively notes that separate house/office service lists are not explicit. |
| B: initial assessment | PASS | 3093 / 3093 ms | Both cite FAQ and request location, photographs, measurements, use, problem, expected result and timeframe; neither substitutes for inspection. |
| C: solera / solado | PASS | 2911 / 4716 ms | Both distinguish structural base from final flooring. ES cites the patio case; EN adds the matching FAQ. |
| D: photosynthesis | PASS | 1775 / 2434 ms | Both abstain, return no unsupported answer and no citations. |

The documents were checked against `knowledge/R4R_00_*.md`,
`knowledge/R4R_10_*.md` and `knowledge/faqs/R4R_40_ALL_FAQ.md`.
Generation reported `PC principal / qwen3:8b` where generation occurred;
embedding reported `nomic-embed-text:latest`. English emits server-side translation
stages. The effective Spanish search string and per-chunk retrieval scores are not
exposed by these SSE events, so those specific internals remain **INCONCLUSIVE**.
This is a small regression sample, not a general English-accuracy guarantee.

The previous frontend sent English text to MyMemory and supplied the resulting
`retrievalQuestion`, bypassing the backend's own translation path. In current Java,
`CitedRagService.answer` invokes `LocalRetrievalQueryTranslator` when that field is
absent for English. Removing the browser call exercises this existing contract,
avoids third-party disclosure from the browser and removes its translation timeout
and cancellation race. The backend's configured fallback may still use remote
providers; this is not a claim of complete local processing.

Initial public requests from Python received 403; the NAS was used for the pairs.
Some captures received 429 and were subsequently repeated after waiting. Limits
were neither changed nor bypassed. A later English request from Chromium using
local frontend assets under the production origin succeeded against the **public**
API: it returned the initial-assessment answer, FAQ citation and
`PC principal · qwen3:8b`. This did not publish the local assets.

## Browser and network

Chromium matrix: 1440×900, 1280×900, 390×844 and 360×800.
The reproducible script is `front-athagon-r4r-static/js/browser.test.cjs`.
It checks visibility-triggered autoplay, visible button press, no external/backend
requests during preview, source URL, language switching, interruption, session
replay guard, reduced motion, one manual SSE request, result positioning, 429,
developer controls and horizontal overflow. Mocked transport checks are separate
from the live retrieval evidence above. The PDF assets themselves remain the
original branded exports, with filenames preserved. Recorded fixtures contain
real captured answers and events, not a translation invented for the preview.

Lighthouse Accessibility: **100/100 mobile and 100/100 desktop**, after fixing heading order and the brand accessible name. Results are in [accessibility.json](accessibility.json).
Audits use the locally served page and do not certify arbitrary live output.
A Chromium keyboard check confirmed a focusable brand link with a visible outline. All eight PDFs returned HTTP 200 with a `%PDF-` signature; the favicon and social PNG also returned HTTP 200 locally. [First mobile viewport](first-viewport-mobile.png) complements the full-page captures. Three external users'
ten-second comprehension test remains **manual acceptance pending**.

## Limitations

- NAS, residential connection and tunnel are still dependencies; AWS retrieval
  backup is not a standalone backend or high availability.
- This run does not force provider failure, IAM rejection or cloud retrieval
  fallback in production. Those paths are described from implementation, not
  claimed newly verified by this frontend release.
- Social PNG and favicon resolve locally. The new production social URL cannot
  be verified until the user publishes this release. `noindex,nofollow` is retained.
- No push or deployment is performed.

## Final automated gates

- Baseline: 83 Node tests passed; `mvn clean verify` passed.
- Final: 71 Node tests passed; obsolete browser-MyMemory tests were replaced by
  server-delegation and recorded-fixture/source tests. This is a changed suite, not
  a claim that the counts are directly comparable.
- Final `mvn clean verify`: BUILD SUCCESS (21.659 s). Optional live cloud/LLM
  test gates retain their existing configuration; this does not assert all optional
  production integrations ran.
- Chromium: all four viewport checks plus reduced motion, cancellation, live SSE
  transport mock, citations, result visibility, 429/Retry-After and developer mode
  passed. A separate real public-API browser query also passed.
- PDF filenames map to existing `%PDF-` files; all eight pairs of saved ES/EN
  recordings are checked against the embedded data.
- Lighthouse dependencies were installed only in `/tmp`; project dependencies
  were not changed. Git whitespace checks passed.
