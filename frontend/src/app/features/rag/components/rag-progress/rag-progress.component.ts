import { isCommercialAssistant } from '../../../../core/presentation';
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from '../../services/assistant-state.service';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { ProgressEvent } from '../../../../core/rag/rag.models';
@Component({
    selector: 'app-rag-progress', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './rag-progress.scss', template: `
        <ol
          id="simpleProgressSteps"
          class="simple-progress-steps"
          [class.is-complete]="!!s.response()"
          [hidden]="!s.events().length && !s.playbackStage()"
          [attr.aria-label]="i.t('page.query_steps')"
        >
          @for(stage of stages();track stage;let idx=$index){
            <li [attr.data-state]="s.response() || idx < stages().length-1?'complete':'current'">
              <span class="step-number">{{idx+1}}</span>
              <span class="step-label">{{i.t(stage)}}</span>
              <span class="step-state">{{s.response() || idx < stages().length-1?'✓':'…'}}</span>
            </li>
          }
        </ol>
        @if (!commercial) {
        <details
          id="progressDetails"
          class="query-telemetry"
          [hidden]="!s.events().length && !s.playbackStage()"
        >
          <summary>{{i.t('page.technical_details')}}</summary>
          @if(s.recorded() && !s.events().length){
            <p>{{i.t('noRecordedTimeline')}}</p>
          }
          <ol id="ragProgress" class="rag-progress">
            @for(e of s.events();track $index){
              <li>
                {{e.stage}}
                ·
                {{e.elapsedMs}}
                ms —
                {{message(e)}}
                <details>
                  <summary>{{i.t('showJson')}}</summary>
                  <pre>{{e|json}}</pre>
                </details>
              </li>
            }
          </ol>
        </details>
        }
    `
})
export class RagProgressComponent {
    readonly commercial = isCommercialAssistant();
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    stages() {
        if (this.s.playbackStage()) {
            const steps = ['preparing', 'searching', 'generating', 'verifying'];
            return steps.slice(0, steps.indexOf(this.s.playbackStage()) + 1);
        }
        return [...new Set(this.s.events().map(e => e.stage === 'ANSWER_READY' ? 'done' : e.stage.startsWith('VALIDATION') ? 'verifying' : /^(GENERATION|PROVIDER_FAILED|FAILOVER)/.test(e.stage) ? 'generating' : /^(RETRIEVAL|EMBEDDING)/.test(e.stage) ? 'searching' : 'preparing'))];
    }
    message(e: ProgressEvent) { return (this.i.stages()[e.stage] || e.stage).replace('{model}', e.model || 'IA').replace('{count}', String(e.documentsFound || 0)).replace('{provider}', typeof e.provider === 'string' ? e.provider : e.provider?.name || ''); }
}
