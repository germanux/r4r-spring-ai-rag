import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { I18nService } from '../../../core/i18n/i18n.service';
import { RagApiService } from '../../../core/rag/rag-api.service';
import { RagAuditService } from '../../../core/rag/rag-audit.service';
import { DemoPlaybackService } from './demo-playback.service';
import { RuntimeService } from '../../../core/rag/runtime.service';
import { AuditResult, ProgressEvent, RAGAnswerResult, statusOf } from '../../../core/rag/rag.models';
@Injectable({ providedIn: 'root' })
export class AssistantState {
    readonly i = inject(I18nService);
    readonly api = inject(RagApiService);
    readonly audits = inject(RagAuditService);
    readonly demo = inject(DemoPlaybackService);
    readonly runtime = inject(RuntimeService);
    readonly question = signal('');
    readonly mobileResult = signal(false);
    readonly auditState = signal<'idle'|'pending'|'reviewed'|'inconclusive'|'unavailable'>('idle');
    readonly playbackStage = signal('');
    readonly response = signal<RAGAnswerResult | null>(null);
    readonly loading = signal(false);
    readonly error = signal('');
    readonly events = signal<ProgressEvent[]>([]);
    readonly audit = signal<AuditResult | null>(null);
    readonly auditing = signal(false);
    readonly recorded = signal(false);
    readonly duration = signal(0);
    readonly copied = signal(false);
    readonly developer = new URLSearchParams(location.search).get('dev') === '1';
    private controller?: AbortController;
    private cycle = 0;
    readonly status = computed(() => this.audit()?.effectiveStatus || (this.response() ? statusOf(this.response()!) : 'ANSWERED'));
    readonly provider = computed(() => { const r = this.response(); return r?.capture?.provider || [r?.provider?.name || r?.providerName, r?.provider?.model || r?.model, this.recorded() ? r?.recordedAt : null].filter(Boolean).join(' · '); });
    readonly heading = computed(() => this.loading() ? this.i.t('inProgress') : this.error() && !this.response() ? this.i.t('failed') : this.response() ? (this.response()?.capture?.display || this.i.t(this.status() === 'ANSWERED' ? 'verified' : this.status() === 'PARTIAL' ? 'partialAnswer' : 'abstention')) : this.i.t('waiting'));
    stopDemo() { if (this.demo.active()) {
        this.demo.stop();
        this.loading.set(false);
    } }
    edit(q: string) { this.stopDemo(); this.question.set(q.slice(0, 700)); }
    reset() { this.controller?.abort(); ++this.cycle; this.loading.set(false); this.response.set(null); this.error.set(''); this.events.set([]); this.audit.set(null); this.auditing.set(false); this.auditState.set('idle'); this.duration.set(0); this.recorded.set(false); this.playbackStage.set(''); }
    clear() { this.mobileResult.set(false); this.stopDemo(); this.reset(); this.question.set(''); try {
        localStorage.removeItem('r4r.lastQuestion');
    }
    catch { } }
    language(lang: string) { this.stopDemo(); if (this.loading()) { this.mobileResult.set(false); this.reset(); } this.i.set(lang); }
    play() { this.reset(); void this.demo.play(this.i.language(), q => this.question.set(q), () => { this.response.set(null); this.loading.set(true); this.events.set([]); this.recorded.set(true); }, e => this.receive(e), r => { this.response.set(r); this.loading.set(false); this.recorded.set(true); this.duration.set(r.durationMs || 0); }, stage => this.playbackStage.set(stage)); }
    editQuestion() {
        this.stopDemo();
        if (this.loading()) this.reset();
        this.mobileResult.set(false);
        requestAnimationFrame(() => { const input = document.getElementById('question'); input?.focus({preventScroll:true}); input?.scrollIntoView({behavior:'instant',block:'center'}); });
    }
    private revealResult() {
        this.mobileResult.set(true);
        if (!matchMedia('(max-width:760px)').matches) return;
        (document.activeElement as HTMLElement | null)?.blur();
        requestAnimationFrame(() => {
            if (!this.mobileResult()) return;
            const summary = document.getElementById('querySummary');
            if (!summary) return;
            const header = document.querySelector('.site-header')?.getBoundingClientRect().height || 64;
            summary.style.scrollMarginTop = `${header + 8}px`;
            summary.focus({preventScroll:true});
            // One positioning operation per submission. SSE and review never scroll.
            summary.scrollIntoView({behavior:'instant',block:'start'});
        });
    }
    receive(e: ProgressEvent) { if (typeof e?.stage !== 'string')
        return; this.events.update(rows => [...rows, e]); }
    async example(key: string) { if (this.loading() && !this.demo.active())
        return; this.stopDemo(); const r = this.demo.get(key, this.i.language())[0]; if (!r)
        return; this.question.set(r.question || ''); await this.send(true); }
    async send(allowSaved = false) {
        this.stopDemo();
        if (this.loading())
            return;
        const q = this.question().trim();
        try {
            localStorage.setItem('r4r.lastQuestion', q);
        }
        catch { }
        if (!q) {
            document.getElementById('question')?.focus();
            return;
        }
        this.reset();
        const cycle = this.cycle, lang = this.i.language(), started = performance.now();
        const controller = new AbortController();
        this.controller = controller;
        this.loading.set(true);
        this.revealResult();
        const timer = setTimeout(() => controller.abort(), 180000);
        try {
            const r = await this.api.ask(q, lang, controller.signal, e => { if (cycle === this.cycle)
                this.receive(e); });
            if (cycle !== this.cycle)
                return;
            this.response.set(r);
            if (r.auditId) {
                this.auditing.set(true);
                this.auditState.set('pending');
                void this.audits.review(r.auditId, statusOf(r)).then(a => { if (cycle === this.cycle) {
                    this.audit.set(a.result);
                    this.auditState.set(a.state);
                    this.auditing.set(false);
                } });
            }
        }
        catch (e) {
            if (cycle !== this.cycle)
                return;
            const status = e instanceof HttpErrorResponse ? e.status : 0;
            const key = status === 429 ? 'busyPublic' : status === 503 ? 'backendUnavailable' : status === 403 ? 'forbidden' : e instanceof DOMException ? 'timeout' : e instanceof Error && ['invalidJson', 'timeout'].includes(e.message) ? e.message : 'networkPublic';
            const retry = e instanceof HttpErrorResponse ? e.headers.get('Retry-After') : null;
            this.error.set(this.i.t(key) + (retry && /^\d+$/.test(retry) ? ' (' + retry + ' s)' : ''));
            if (allowSaved && (!status || status >= 500)) {
                const saved = Object.keys(this.demo.recordings).flatMap(k => this.demo.get(k, lang)).find(r => r.question === q);
                if (saved) {
                    this.response.set(saved);
                    this.recorded.set(true);
                }
            }
        }
        finally {
            clearTimeout(timer);
            if (cycle === this.cycle) {
                this.loading.set(false);
                this.duration.set(performance.now() - started);
                this.controller = undefined;
            }
        }
    }
    async copy() { try {
        await navigator.clipboard.writeText(this.response()?.answer || '');
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
    }
    catch {
        this.copied.set(false);
    } }
}
