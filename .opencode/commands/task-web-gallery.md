# Task — Rebuild the before/after gallery section

Reference: `https://riansares4r.com/galeria-antes-despues`.
Target hint: `/html/body/main/section[2]`. Confirm it is the section headed
`Trabajos realizados`; XPath position alone is not authoritative.

1. Open the reference page once with Playwright. Inspect only the target section,
   its parent layout, computed styles, responsive behavior, console and same-page
   CSS/JS requests directly needed to explain the section.
2. Locate the local source for route `/galeria-antes-despues` by route or heading.
   If absent, stop: do not invent a second site inside an unrelated repository.
3. Preserve the hero/first section and every section after the gallery. Rebuild only
   the target gallery in the local framework and conventions.
4. Reuse local design tokens and components. Do not paste whole remote stylesheets,
   minified bundles, analytics, trackers or unrelated scripts. Copy no credentials,
   cookies or user data.
5. Keep semantic headings, image alt text, keyboard behavior and responsive layout.
   Avoid fragile absolute XPath selectors in production code.
6. Run the existing formatter/build/tests. Run existing Playwright/e2e checks when
   available; otherwise report that browser acceptance remains unproven.
7. Stop after one coherent implementation and validation pass. After two identical
   tool failures, stop and report the exact blocker.

Remote site is read-only. Never submit forms, contact links, upload, deploy, push or
change DNS/hosting. Final report: changed paths, checks, visual equivalence and gaps.
