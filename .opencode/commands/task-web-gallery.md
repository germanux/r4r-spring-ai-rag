# Web task — static gallery publication

Reference URL: `https://riansares4r.com/galeria-antes-despues`.

The remote site is read-only. Use Playwright only to inspect:
- the final rendered HTML;
- the CSS rules/stylesheets affecting the gallery;
- the JavaScript file directly required by that gallery.

Do not inspect or copy the remote repository. Do not download analytics, trackers,
cookie scripts, unrelated framework bundles, credentials or forms.

Publish exactly these local files:

- `src/main/resources/static/galeria-antes-despues.html`
- `src/main/resources/static/galeria-antes-despues.css`
- `src/main/resources/static/galeria-antes-despues.js`

Requirements:

1. Confirm the target by heading `Trabajos realizados`; XPath
   `/html/body/main/section[2]` is only a hint.
2. Save the relevant rendered HTML, not a template or source component.
3. Consolidate only the CSS needed by the target into the required CSS file.
4. Download the single directly relevant gallery script into the required JS file.
   Preserve its behavior. If no single script can be isolated safely, stop and report.
5. Rewrite HTML references to `/galeria-antes-despues.css` and
   `/galeria-antes-despues.js`.
6. Do not create or write any `browser/` directory.
7. Do not write outside `src/main/resources/static/`.
8. Do not build Angular, create frontend source code, edit Java, deploy, commit or push.
9. Validate the three local files and stop after one coherent pass.
