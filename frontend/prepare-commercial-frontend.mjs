import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

// Strip any technical prerender before Angular selects the commercial presentation.
export function commercialEntry(html) {
  return html.replace(/<app-root\b[^>]*>[\s\S]*?<\/app-root>/, '<app-root></app-root>')
    .replace(/<title>[\s\S]*?<\/title>/, '<title>Asistente de IA para reformas · Riansares 4R</title>')
    .replace(/<html\b([^>]*)>/, (_, attrs) => `<html${attrs} class="commercial-assistant">`)
    .replace(/<link\b[^>]*rel="icon"[^>]*>/, '<link rel="icon" href="/favicon.ico">')
    .replace('</head>', '<link id="commercial-site-styles" rel="stylesheet" href="assets/riansares/home-blueprint.css">\n</head>');
}
if (process.argv[1] && import.meta.url === new URL('file://' + resolve(process.argv[1])).href) {
  const file = resolve(process.argv[2], 'index.html');
  await writeFile(file, commercialEntry(await readFile(file, 'utf8')));
}
