import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { AssistantState } from '../../services/assistant-state.service';
import { QuestionFormComponent } from '../question-form/question-form.component';
import { AnswerPanelComponent } from '../answer-panel/answer-panel.component';
@Component({ selector: 'app-rag-assistant', standalone: true, imports: [QuestionFormComponent, AnswerPanelComponent], changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './rag-assistant.scss', template: `<section class="section demo-section" id="demo" [class.has-query]="s.mobileResult()"><div class="shell"><div class="mobile-query-summary" id="querySummary" tabindex="-1"><p>{{s.question()}}</p><button id="editQuestion" class="small-btn" type="button" (click)="s.editQuestion()">{{s.i.t('editQuestion')}}</button></div><div class="demo-grid"><app-question-form/><app-answer-panel/></div>
<details id="rag-privacy-controls" class="privacy-options">
  <summary>{{s.i.language()==='es'?'Opciones de privacidad':'Privacy options'}}</summary>
  <p>{{s.i.language()==='es'?'Puedes oponerte al registro de texto e IP en esta apertura sin dejar de usar el asistente. Se solicitará restringir las referencias recientes disponibles.':'You can object to text and IP recording during this opening and keep using the assistant. Restriction of available recent references will be requested.'}}</p>
  <button id="privacy-object" class="small-btn" type="button" [disabled]="s.api.privacy.objectionState()==='pending'" (click)="s.api.privacy.oppose(true,s.api.base())">{{s.i.language()==='es'?'Oponerme al registro':'Object to recording'}}</button>
  @if(s.api.privacy.objected()){<button id="privacy-resume" class="small-btn" type="button" (click)="s.api.privacy.oppose(false,s.api.base())">{{s.i.language()==='es'?'Reanudar para nuevas consultas':'Resume for new queries'}}</button>}
  @if(s.api.privacy.objectionState()==='submitted'){<p role="status">{{s.i.language()==='es'?'Oposición comunicada. Los nuevos envíos omitirán texto e IP.':'Objection sent. New submissions will omit text and IP.'}}</p>}
  @if(s.api.privacy.objectionState()==='failed'){<p role="alert">{{s.i.language()==='es'?'No se pudo comunicar la oposición sobre registros anteriores. Los nuevos envíos omitirán texto e IP. Contacta por el canal de privacidad para revisar los anteriores.':'The objection for previous records could not be sent. New submissions will omit text and IP. Use the privacy contact to review previous records.'}}</p>}
  @if(s.api.requestId()){<p class="privacy-reference">{{s.i.language()==='es'?'Referencia de consulta:':'Query reference:'}} <code>{{s.api.requestId()}}</code></p>}
  <p><a [href]="s.api.privacy.contact()">{{s.i.language()==='es'?'Solicitar supresión o revisión de otra consulta':'Request erasure or review of another query'}}</a> · <a [href]="s.api.privacy.policy()" target="_blank" rel="noopener">{{s.i.language()==='es'?'Política de privacidad':'Privacy Policy'}}</a></p>
</details></div></section>` })
export class RagAssistantComponent implements AfterViewInit, OnDestroy {
    readonly s = inject(AssistantState);
    private observer?: IntersectionObserver;
    private usageObserver?: IntersectionObserver;
    private engaged = false;
    private viewId = this.s.api.viewId;
    private visible = false;
    private trackView = () => {
        if (this.visible) this.s.api.http.post(this.s.api.base() + '/api/rag/usage/views', null,
            {headers:{...this.s.api.privacy.headers(),'X-R4R-Request-Id':this.viewId,'X-R4R-View-Id':this.viewId}}).subscribe({error:()=>{}});
    };
    private engage = () => { this.engaged = true; this.observer?.disconnect(); };
    ngAfterViewInit() {
        if (location.hash === '#rag-privacy-controls') {
            const controls = document.getElementById('rag-privacy-controls') as HTMLDetailsElement | null;
            if (controls) controls.open = true;
        }
        if (!new URLSearchParams(location.search).has('prerender')) {
            this.usageObserver = new IntersectionObserver(entries => {
                if (!entries.some(e => e.isIntersecting)) return;
                this.usageObserver?.disconnect();
                this.visible = true; this.trackView();
            }, {threshold:.1});
            const demo = document.getElementById('demo'); if (demo) this.usageObserver.observe(demo);
        }
        if (new URLSearchParams(location.search).has('prerender')) return;
        this.s.runtime.warmup();
        if (new URLSearchParams(location.search).get('embed') === '1') return;
        window.addEventListener('pointerdown', this.engage, { once: true }); window.addEventListener('keydown', this.engage, { once: true }); this.observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting) && !this.engaged) {
        this.observer?.disconnect();
        setTimeout(() => { if (!this.engaged)
            this.s.play(); }, 700);
    } }, { threshold: .2 }); const el = document.getElementById('demo'); if (el)
        this.observer.observe(el); }
    ngOnDestroy() { this.usageObserver?.disconnect(); this.observer?.disconnect(); this.s.clear(); window.removeEventListener('pointerdown', this.engage); window.removeEventListener('keydown', this.engage); }
}
