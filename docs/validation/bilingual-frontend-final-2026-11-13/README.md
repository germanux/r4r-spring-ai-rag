# Bilingual public frontend completion — 2026-11-13

## Baseline and scope

Initial working tree: clean, `6f67395`. The NAS HTML at
`http://192.168.1.203/r4r-rag/` matched the source SHA-256
`a86b80efbb0dd4c6f9fd39badcfdf3bd970aa22c835b284c32ca634b7d2888d4`.
The Athagon HTML differed (`c2efc0391b759ef1ea34af7f6137df462abff7592d0a239a905ef19544dbadf0`)
and still contained an older technical-first description. Baseline Node: 71 passing.

Backend, ingestion, corpus Markdown, embeddings, database, routing, API contracts
and private configuration were not modified. No push or deployment occurred.

## Reused and new functionality

Reused: static HTML/CSS/JS, bilingual dictionaries, IntersectionObserver,
cancellable typewriter/answer playback, real SSE reader, live request controller,
existing branded PDFs and logo, disclosure styling, endpoint presets and runtime.

Added or completed:

- Six kitchen recordings supplied by the user, preserving exact questions, answers,
  citations, source identifiers, date and durations: ES 3013/4413/4852 ms; EN
  6427/5727/4722 ms. **These six API calls were not repeated.** No model/provider
  or original SSE timeline was supplied. The fixtures deliberately contain no
  invented model or measured events; playback labels its presentation-only stages.
- Three sequential cumulative questions, progressive typing and response reveal,
  visible pressed state, short pauses, interruption and reduced-motion support.
  One automatic run per page load; no session-storage suppression on a later load.
- Real API-first CTA (four Rs) and four original quick-example questions. Ten
  corresponding ES/EN results were newly captured from the NAS for exact-match
  fallback, with actual metadata where present. These are distinct from the six
  supplied kitchen results. All ten completed without capture errors.
- Network/timeout/5xx fallback is restricted to an explicitly clicked CTA/example
  with an exact stored question in the requested language. Free typed queries,
  even if their text matches an example, do not automatically use recordings.
  Valid abstentions and 429 are never replaced. The saved response is labelled,
  retains historical metadata and keeps the actual operational error visible.
- SSE history keeps started/completed stages, elapsed times, measured durations,
  retrieval counts and provider/model from the current request. Missing embedding
  provider metadata is not inferred from global runtime. Original raw source
  trace is retained in optional details; no private hostname is copied to the
  playback metadata. The simple progress labels follow actual received phases. The SSE disclosure is outside the transient loading block and remains inspectable after completion; both started and completed events are retained.
- Safe Markdown builds DOM nodes for emphasis, paragraphs, lists and HTTP(S)
  links. Model HTML is text; unsafe links are not activated. Citation markers stay.
- Enter sends; Shift+Enter inserts a line; IME composition does not send. There
  is one active query. Mobile autoplay does not focus the input; explicit example
  selection and clearing focus it with preventScroll. Existing single conservative
  response positioning is retained without per-chunk page scrolling.
- Fixed an inherited `.config-details summary` flex rule that compressed the nested architecture cards: outer disclosure styles now target direct children, while architecture summaries use an explicit grid. Layout was checked in ES/EN on mobile and desktop.
- Engineering content and SVG architecture are inside a collapsed Engineering &
  Architecture subsection immediately before Configuration, Hardware & Metrics.
  The standalone engineering heading and duplicate technology list are removed.
  How it works stays as a shorter separate accordion.
- Canonical URL added; noindex, social preview, favicon and professional links
  retained. Current hero text and keyboard help are localized.

## Source publishing

`py-scripts/export_public_documents.py` publishes 82 branded readable HTML views
(41 stable R4R IDs × ES/EN) and a catalogue with original source paths, titles,
content hashes and available numeric section anchors. Each ID has a unique
counterpart per language; the existing reviewed corpus translations are used,
not generated translations. An English UI can open its English counterpart while
retaining the original Spanish citation/path/section in the source trace.
Numeric section links are added only when the anchor exists in that counterpart;
otherwise the whole document opens without claiming an exact section mapping.
Unknown/unpublished sources remain unlinked rather than generating a false URL.

The eight existing PDFs and the source corpus remain unchanged. The new and
translated documents use readable views rather than generating dozens of large
PDF copies. The optional existing PDF exporter now retains language/subfolder
paths so same-basename translations cannot overwrite each other.

All 82 generated views returned HTTP 200 and contained a document H1 from the
local server. Browser tests also open cited views in a tab. New English R4R_80
views currently return **404 on both NAS and Athagon**: publishing the generated
`docs/` tree is required. They are not falsely reported as already deployed.

## Validation results

- Final Node: **78 passed**. New coverage includes exact fallback isolation,
  abstention/429, safe Markdown, bilingual source trace, six historical timings,
  Enter/Shift+Enter/IME and catalogue/assets.
- `mvn clean verify`: **BUILD SUCCESS**, 21.978 s. Existing optional integration
  switches were preserved; this does not assert every optional cloud gate ran.
- Chromium: **ES and EN × 390, 768, 1280, 1920 px passed**. Tests exercise three
  cumulative questions, progressive values/answers, button press, zero backend
  requests during autoplay, live CTA plus four live buttons, free text, sources,
  keyboard, 429, 503 fallback, runtime disclosure, overflow and console errors.
  Transport is mocked here; real API captures are independently stored in fixtures.
  Animation timers are accelerated in the browser suite, never presented as
  measured backend durations.
- Additional Chromium interaction suite: reduced motion, no automatic mobile
  focus, interruption, language cancellation, double click, timeout fallback and
  free-text isolation **passed**.
- Lighthouse Accessibility **100/100 mobile and desktop**, version 13.6.0,
  measured on the local static server. Compact results: [accessibility.json](accessibility.json).
- Git whitespace checks passed. No project dependency/framework was introduced.

Screenshots: [ES mobile](es-390.png), [EN mobile](en-390.png),
[ES desktop](es-1280.png), [EN desktop](en-1280.png).
Expanded details: [ES mobile](technical-es-390.png),
[EN mobile](technical-en-390.png), [ES desktop](technical-es-1280.png),
[EN desktop](technical-en-1280.png).

## Real browser request

A separate Chromium check served the new local assets under the permitted
production origin (without deployment), clicked the English CTA and observed
exactly **one POST** to the real public `/api/rag/answers/stream`, HTTP **200**,
mode **Live**, and `PC principal · qwen3:8b`. Request fields and returned answer:
[public-cta-evidence.json](public-cta-evidence.json). It did not query any of the
six kitchen prompts. This verifies the frontend request path and CORS for that
specific permitted-origin case, not every origin/deployment combination.
The expanded-layout screenshots use the local origin; runtime may be unavailable
there and its handled message is not a diagnosis of production backend health.
Opening source details during a replay pause was also checked to preserve the
completed recorded answer rather than clearing it.

## Remaining limits

This is not a deployment. Fresh document URLs need the complete static tree to be
published on both hosts. Production CORS and these new assets cannot be certified
as deployed from local/mocked-browser results. Historical supplied kitchen results
lack model/provider and SSE timings; none are fabricated. No cloud/provider
failure was forced, and no high-availability or standalone AWS backend claim is
made. NAS/network/tunnel dependencies remain. External-user comprehension
acceptance is still a manual task.
