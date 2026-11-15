import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from '../../services/assistant-state.service';
import { I18nService } from '../../../../core/i18n/i18n.service';
@Component({ selector: 'app-question-form', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, templateUrl: './question-form.html', styleUrl: './question-form.scss' })
export class QuestionFormComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    endpoint(input: HTMLInputElement) { input.setCustomValidity(this.s.api.setBase(input.value) ? '' : this.i.t('invalidUrl')); if (!input.reportValidity()) return; this.s.runtime.warmup(); void this.s.runtime.load(); }

}
