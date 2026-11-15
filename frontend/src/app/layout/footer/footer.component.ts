import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from '../../features/rag/services/assistant-state.service';
import { I18nService } from '../../core/i18n/i18n.service';
@Component({ selector: 'app-footer', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, templateUrl: './footer.html', styleUrl: './footer.scss' })
export class FooterComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
}
