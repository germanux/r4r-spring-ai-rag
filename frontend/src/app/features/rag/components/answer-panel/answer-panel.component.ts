import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { AssistantState } from '../../services/assistant-state.service';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { CitationsComponent } from '../citations/citations.component';
import { AuditStatusComponent } from '../audit-status/audit-status.component';
import { RagProgressComponent } from '../rag-progress/rag-progress.component';
import { MarkdownPipe } from '../../pipes/markdown.pipe';
@Component({
    selector: 'app-answer-panel', standalone: true, imports: [CitationsComponent, AuditStatusComponent, RagProgressComponent, MarkdownPipe], changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './answer-panel.scss', template: `
        <article
          class="response-panel panel"
          id="responsePanel"
          [class.has-audit]="!!s.response()?.auditId && !s.recorded()"
          tabindex="-1"
          [attr.aria-busy]="s.busy()"
        >
          <div class="panel-head">
            <div class="response-heading">
              <div class="rag-status-slot">
                <span class="response-status" id="resultMode" role="status" aria-live="polite" [class.is-verified]="!!s.response() && s.status()==='ANSWERED'" [class.is-partial]="!!s.response() && s.status()==='PARTIAL'">{{s.heading()}}</span>
                <span class="response-status state-reserve" aria-hidden="true">{{i.t('abstention')}}</span>
              </div>
              <div class="audit-slot">
                <app-audit-status />
                <span class="response-status audit-note state-reserve" aria-hidden="true">{{i.t('auditDowngraded')}}</span>
              </div>
            </div>
            <span id="elapsed" class="elapsed">{{s.response()?.capture?.duration || (s.duration()?(s.duration()/1000).toFixed(2)+' s':'')}}</span>
          </div>
          <div class="empty-state" id="emptyState" [hidden]="s.loading() || !!s.response() || !!s.error()">
            <div class="empty-icon">RAG</div>
            <h2>{{i.t('page.your_answer_will_appear_here')}}</h2>
          </div>
          <div
            id="loadingState"
            class="loading-state"
            role="status"
            aria-live="polite"
            [hidden]="!s.loading()"
          >
            <div class="loading-icon" aria-hidden="true">⌕</div>
            <h3>{{i.t('page.preparing_an_answer_with_sources')}}</h3>
          </div>
          <div id="resultContent" class="result-content" [hidden]="!s.response()">
            <div class="decision-row" id="decisionRow" [hidden]="s.status()==='ANSWERED'">
              <span id="decisionPill" class="decision-pill" [class.abstain]="s.status()==='ABSTAINED'" [class.partial]="s.status()==='PARTIAL'">{{i.t(s.status()==='ABSTAINED'?'abstention':'partialAnswer')}}</span>
              <span id="decisionText">{{i.t(s.status()==='ABSTAINED'?'noEvidence':'partialExplanation')}}</span>
              <span class="classification-reserve" aria-hidden="true">{{i.t('partialExplanation')}}</span>
              <span class="classification-reserve" aria-hidden="true">{{i.t('noEvidence')}}</span>
            </div>
            <div class="answer-box">
              <div class="block-head">
                <button id="copyAnswerButton" type="button" class="text-btn" (click)="s.copy()">{{i.t(s.copied()?'copied':'copy')}}</button>
              </div>
              <p id="answerProvider" class="translation-note">{{s.provider()}}</p>
              @if(s.embeddingProvider()){
                <p id="embeddingProvider" class="translation-note">Embeddings: {{s.embeddingProvider()}}</p>
              }
              <div id="answerText" class="answer-text" aria-live="off" [innerHTML]="(s.response()?.answer || (s.response()?.abstained?i.t('abstained'):''))|markdown"></div>
            </div>
            <details class="audit-details" id="auditDetails" [hidden]="!auditDetail()">
              <summary>{{i.t('auditDetails')}}</summary>
              <p id="auditDetailText">{{auditDetail()}}</p>
            </details>
            <app-citations />
          </div>
          <app-rag-progress />
          <div id="errorBox" class="error-box" role="alert" [hidden]="!s.error()">{{s.error()}}</div>
        </article>
    `
})
export class AnswerPanelComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    auditDetail() { return this.s.audit()?.note || this.s.response()?.capture?.auditor || ''; }
}
