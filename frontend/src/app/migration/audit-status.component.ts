import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { AssistantState } from './assistant-state.service';
import { I18nService } from './i18n.service';
@Component({ selector: 'app-audit-status', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './audit-status.scss', template: `
<div id="auditNote" class="response-status audit-note" role="status" aria-live="polite" [hidden]="!s.auditing() && !s.audit() && !s.response()?.capture?.auditor" [attr.data-level]="s.audit() && s.audit()?.originalStatus!==s.audit()?.effectiveStatus?'warning':'neutral'" [attr.data-state]="s.auditing()?'pending':'reviewed'"><span id="auditIcon" aria-hidden="true">{{s.response()?.capture?.auditor?'':s.auditing()?'◌':s.audit() && s.status()!=='ANSWERED'?'⚠':'✓'}}</span> <span id="auditText">{{text()}}</span></div>` })
export class AuditStatusComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    text() { if (this.s.auditing())
        return this.i.t('auditChecking'); const a = this.s.audit(); if (!a)
        return this.s.response()?.capture?.auditor || ''; const downgrade = a.originalStatus !== a.effectiveStatus; const key = downgrade ? (a.effectiveStatus === 'PARTIAL' ? 'auditPartial' : 'auditInsufficient') : a.originalStatus === 'ABSTAINED' && a.recommendedStatus !== 'ABSTAINED' ? 'auditRelated' : 'auditReviewed'; return this.i.t(key) + (a.note && !this.s.response()?.answer.includes(a.note) ? ' ' + a.note : ''); }
}
