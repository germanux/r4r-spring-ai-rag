import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { AssistantState } from '../../services/assistant-state.service';
import { I18nService } from '../../../../core/i18n/i18n.service';
@Component({
    selector: 'app-audit-status', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './audit-status.scss', template: `
        <div
          id="auditNote"
          class="response-status audit-note"
          role="status"
          aria-live="polite"
          [hidden]="s.auditState()==='idle' && !s.response()?.capture?.auditor"
          [class.is-loading]="state()==='pending'"
          [attr.aria-busy]="state()==='pending'"
          [attr.data-level]="s.auditState()==='unavailable' || s.auditState()==='inconclusive' || s.audit()?.originalStatus!==s.audit()?.effectiveStatus?'warning':'neutral'"
          [attr.data-state]="state()"
        >
          @if(state()==='pending'){
            <span id="auditIcon" class="spinner" aria-hidden="true">
            </span>
          }
          @else{
            <span id="auditIcon" aria-hidden="true">
              {{state()==='reviewed' && s.audit()?.originalStatus===s.audit()?.effectiveStatus?'✓':'⚠'}}
            </span>
          }
          <span id="auditText">
            {{text()}}
          </span>
        </div>
    `
})
export class AuditStatusComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    state() { const captured = this.s.response()?.capture?.auditor; return captured ? /(?:\.\.\.|…)$/.test(captured) ? 'pending' : 'reviewed' : this.s.auditState(); }
    text() {
        if (this.state() === 'pending')
            return this.i.t('auditChecking');
        if (this.state() === 'inconclusive')
            return this.i.t('auditInconclusive');
        if (this.state() === 'unavailable')
            return this.i.t('auditUnavailable');
        const a = this.s.audit();
        return this.i.t(a && a.originalStatus !== a.effectiveStatus ? 'auditDowngraded' : 'auditReviewed');
    }
}
