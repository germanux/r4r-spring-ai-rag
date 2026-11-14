import { Injectable, signal } from '@angular/core';
import recordings from './recordedExamples.json';
import { RagResponse } from './models';
@Injectable({ providedIn: 'root' })
export class DemoPlaybackService {
    readonly active = signal(false);
    private cycle = 0;
    readonly recordings = recordings as unknown as Record<string, Record<'es' | 'en', RagResponse | RagResponse[]>>;
    stop() { ++this.cycle; this.active.set(false); }
    get(key: string, lang: 'es' | 'en'): RagResponse[] { const r = this.recordings[key]?.[lang]; return Array.isArray(r) ? r : r ? [r] : []; }
    async play(lang: 'es' | 'en', question: (q: string) => void, start: () => void, progress: (e: NonNullable<RagResponse['events']>[number]) => void, result: (r: RagResponse) => void, step: (name: string) => void = () => { }) {
        this.stop();
        const cycle = this.cycle;
        this.active.set(true);
        const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
        const pause = async (ms: number) => { await new Promise(r => setTimeout(r, ms)); return cycle === this.cycle; };
        for (const r of this.get('kitchen', lang)) {
            const q = r.question || '';
            question('');
            for (let n = 1; n <= q.length; n += reduced ? q.length : 1) {
                question(q.slice(0, n));
                if (!await pause(reduced ? 1 : 28))
                    return;
            }
            question(q);
            if (!await pause(1050))
                return;
            start();
            if (!r.events?.length)
                for (const name of ['preparing', 'searching', 'generating', 'verifying']) {
                    step(name);
                    if (!await pause(reduced ? 30 : 350))
                        return;
                }
            for (const e of r.events || []) {
                progress(e);
                if (!await pause(reduced ? 40 : 260))
                    return;
            }
            result({ ...r, answer: reduced ? r.answer : ' ' });
            if (!reduced)
                for (let n = 6; n < r.answer.length; n += 6) {
                    result({ ...r, answer: r.answer.slice(0, n) });
                    if (!await pause(26))
                        return;
                }
            result(r);
            if (!await pause(reduced ? 200 : 3000))
                return;
        }
        if (cycle === this.cycle)
            this.active.set(false);
    }
}
