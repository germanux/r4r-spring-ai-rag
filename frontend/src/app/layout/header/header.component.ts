import { Component, ChangeDetectionStrategy, HostListener, ViewChild, ElementRef, inject, signal, effect } from '@angular/core';
import { AssistantState } from '../../features/rag/services/assistant-state.service';
import { I18nService } from '../../core/i18n/i18n.service';
@Component({ selector: 'app-header', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, templateUrl: './header.html', styleUrl: './header.scss' })
export class HeaderComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    readonly menuOpen = signal(false);
    constructor() { effect(() => { if (this.s.mobileResult()) this.closeMenu(); }, {allowSignalWrites:true}); }
    @ViewChild('menuButton') menuButton?: ElementRef<HTMLButtonElement>;
    closeMenu() { this.menuOpen.set(false); }
    @HostListener('document:keydown.escape') escape() {
        if (this.menuOpen()) { this.closeMenu(); this.menuButton?.nativeElement.focus(); }
    }
}
