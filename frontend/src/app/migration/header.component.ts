import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from './assistant-state.service';
import { I18nService } from './i18n.service';
@Component({ selector: 'app-header', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, templateUrl: './header.html', styleUrl: './header.scss' })
export class HeaderComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
}
