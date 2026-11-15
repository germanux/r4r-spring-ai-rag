import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { ProgressEvent, RAGAnswerResult, RAGQuestionRequest, validResponse } from './rag.models';
@Injectable({ providedIn: 'root' })
export class RagApiService {
    readonly http = inject(HttpClient);
    readonly base = signal('https://r4r-api.athagon.tech');
    constructor() { if (new URLSearchParams(location.search).get('embed') === '1') return; try {
        this.setBase(localStorage.getItem('r4r.apiBaseUrl') || this.base());
    }
    catch { } }
    setBase(value: string): boolean {
        let url: URL;
        try { url = new URL(value.trim()); } catch { return false; }
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) return false;
        this.base.set(value.trim().replace(/\/+$/, ''));
        try { localStorage.setItem('r4r.apiBaseUrl', this.base()); } catch {}
        return true;
    }
    query(request: RAGQuestionRequest): Observable<RAGAnswerResult> {
        return this.http.post<RAGAnswerResult>(this.base() + '/api/rag/answers', request, {headers:{Accept:'application/json'}});
    }
    async ask(question: string, language: 'es' | 'en', signal: AbortSignal, progress: (e: ProgressEvent) => void): Promise<RAGAnswerResult> {
        const url = this.base() + '/api/rag/answers';
        const body = { question, responseLanguage: language };
        let response = await fetch(url + '/stream', { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, body: JSON.stringify(body), signal });
        if ([404, 405, 406, 501].includes(response.status)) {
            await response.body?.cancel();
            return new Promise((resolve, reject) => { const sub = this.query(body).pipe(timeout(180000)).subscribe({ next: r => validResponse(r) ? resolve(r) : reject(new Error('invalidJson')), error: reject }); const abort = () => { sub.unsubscribe(); reject(new DOMException('Cancelled', 'AbortError')); }; signal.addEventListener('abort', abort, { once: true }); sub.add(() => signal.removeEventListener('abort', abort)); if (signal.aborted)
                abort(); });
        }
        if (!response.ok)
            throw new HttpErrorResponse({ status: response.status, headers: new HttpHeaders({ 'Retry-After': response.headers.get('Retry-After') || '' }) });
        if (!response.headers.get('Content-Type')?.includes('text/event-stream') || !response.body)
            throw new Error('invalidJson');
        const reader = response.body.getReader(), decoder = new TextDecoder();
        let buffer = '';
        try {
            while (true) {
                const { value, done } = await reader.read();
                buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
                let match: RegExpExecArray | null;
                while ((match = /\r?\n\r?\n/.exec(buffer))) {
                    const frame = buffer.slice(0, match.index).replace(/\r\n/g, '\n');
                    buffer = buffer.slice(match.index + match[0].length);
                    if (frame.length > 131072)
                        throw new Error('invalidJson');
                    const data = frame.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5).trimStart()).join('\n');
                    if (!data)
                        continue;
                    const e = JSON.parse(data) as ProgressEvent;
                    progress(e);
                    if (e.stage === 'ERROR')
                        throw new Error(e.message === 'timeout' ? 'timeout' : 'httpError');
                    if (e.stage === 'ANSWER_READY') {
                        if (!validResponse(e))
                            throw new Error('invalidJson');
                        return e;
                    }
                }
                if (buffer.length > 131072)
                    throw new Error('invalidJson');
                if (done)
                    throw new Error('invalidJson');
            }
        }
        finally {
            await reader.cancel().catch(() => { });
            reader.releaseLock();
        }
    }
}
