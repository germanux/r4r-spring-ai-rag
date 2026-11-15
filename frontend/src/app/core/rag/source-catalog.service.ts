import { Injectable } from '@angular/core';
import catalog from './data/documentSources.json';
import { RAGCitation } from './rag.models';
interface DocumentSource {
    source: string;
    title: string;
    url: string;
    sectionPages?: Record<string, number>;
}
@Injectable({ providedIn: 'root' })
export class SourceCatalogService {
    private catalog = catalog as Record<string, Partial<Record<'es' | 'en', DocumentSource>>>;
    resolve(c: RAGCitation, language: 'es' | 'en'): DocumentSource | undefined { const id = /^R4R_\d+/.exec((c.source || '').replace(/\\/g, '/').split('/').pop() || '')?.[0]; const variants = id ? this.catalog[id] : undefined; return variants?.[language] || variants?.[c.source?.startsWith('en/') ? 'en' : 'es'] || Object.values(this.catalog).flatMap(v => Object.values(v)).find(d => d.title.replace(/^Riansares 4R\s*[—-]\s*/, '').toLowerCase() === c.document?.toLowerCase()); }
    url(c: RAGCitation, language: 'es' | 'en', html = false): string | null { const d = this.resolve(c, language); if (!d)
        return null; const section = (c.headingPath || []).map(h => /^(\d+(?:\.\d+)*)/.exec(h)?.[1]).filter(Boolean).reverse().map(n => 'section-' + n!.replace(/\./g, '-')).find(s => d.sectionPages?.[s]); return html ? 'docs/' + d.source.split('/')[0] + '/html/' + d.source.split('/').pop()!.replace(/\.md$/, '.html') + (section ? '#' + section : '') : d.url + (section ? '#page=' + d.sectionPages![section] : ''); }
}
