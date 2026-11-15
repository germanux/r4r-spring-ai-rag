import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { AssistantState } from '../../services/assistant-state.service';
import { QuestionFormComponent } from '../question-form/question-form.component';
import { AnswerPanelComponent } from '../answer-panel/answer-panel.component';
@Component({ selector: 'app-rag-assistant', standalone: true, imports: [QuestionFormComponent, AnswerPanelComponent], changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './rag-assistant.scss', template: `<section class="section demo-section" id="demo" [class.has-query]="s.mobileResult()"><div class="shell"><div class="mobile-query-summary" id="querySummary" tabindex="-1"><p>{{s.question()}}</p><button id="editQuestion" class="small-btn" type="button" (click)="s.editQuestion()">{{s.i.t('editQuestion')}}</button></div><div class="demo-grid"><app-question-form/><app-answer-panel/></div></div></section>` })
export class RagAssistantComponent implements AfterViewInit, OnDestroy {
    readonly s = inject(AssistantState);
    private observer?: IntersectionObserver;
    private usageObserver?: IntersectionObserver;
    private engaged = false;
    private viewId = crypto.randomUUID();
    private visible = false;
    private trackView = () => {
        if (this.visible) this.s.api.http.post(this.s.api.base() + '/api/rag/usage/views', null,
            {headers:{...this.s.api.privacy.headers(),'X-R4R-Request-Id':this.viewId}}).subscribe({error:()=>{}});
    };
    private engage = () => { this.engaged = true; this.observer?.disconnect(); };
    ngAfterViewInit() {
        if (!new URLSearchParams(location.search).has('prerender')) {
            this.usageObserver = new IntersectionObserver(entries => {
                if (!entries.some(e => e.isIntersecting)) return;
                this.usageObserver?.disconnect();
                this.visible = true; this.trackView();
            }, {threshold:.1});
            window.addEventListener('r4r:privacy-change', this.trackView);
            try { if (parent !== window && parent.location.origin === location.origin) parent.addEventListener('r4r:privacy-change', this.trackView); } catch {}
            const demo = document.getElementById('demo'); if (demo) this.usageObserver.observe(demo);
        }
        if (new URLSearchParams(location.search).has('prerender') || new URLSearchParams(location.search).get('embed') === '1')
        return; this.s.runtime.warmup(); window.addEventListener('pointerdown', this.engage, { once: true }); window.addEventListener('keydown', this.engage, { once: true }); this.observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting) && !this.engaged) {
        this.observer?.disconnect();
        setTimeout(() => { if (!this.engaged)
            this.s.play(); }, 700);
    } }, { threshold: .2 }); const el = document.getElementById('demo'); if (el)
        this.observer.observe(el); }
    ngOnDestroy() { window.removeEventListener('r4r:privacy-change', this.trackView); try { if (parent !== window) parent.removeEventListener('r4r:privacy-change', this.trackView); } catch {} this.usageObserver?.disconnect(); this.observer?.disconnect(); this.s.clear(); window.removeEventListener('pointerdown', this.engage); window.removeEventListener('keydown', this.engage); }
}
