import { Component, ChangeDetectionStrategy, AfterViewInit, OnDestroy, inject } from '@angular/core';
import { I18nService } from './core/i18n/i18n.service';
import { HeaderComponent } from './layout/header/header.component';
import { HeroComponent } from './features/home/components/hero/hero.component';
import { RagAssistantComponent } from './features/rag/components/rag-assistant/rag-assistant.component';
import { InfoSectionsComponent } from './features/home/components/info-sections/info-sections.component';
import { FooterComponent } from './layout/footer/footer.component';
@Component({selector:'app-root',standalone:true,imports:[HeaderComponent,HeroComponent,RagAssistantComponent,InfoSectionsComponent,FooterComponent],changeDetection:ChangeDetectionStrategy.OnPush,template:`@if (!embedded) {<app-header/>}<main>@if (!embedded) {<app-hero/>}<app-rag-assistant/>@if (!embedded) {<app-info-sections/>}</main>@if (!embedded) {<app-footer/>}`})
export class AppComponent implements AfterViewInit, OnDestroy {
    readonly embedded = new URLSearchParams(location.search).get('embed') === '1';
    private resize?: ResizeObserver;
    constructor() { if (this.embedded) { inject(I18nService).set('es'); document.documentElement.classList.add('embedded-assistant'); } }
    ngAfterViewInit() {
        if (!this.embedded || parent === window) return;
        this.resize = new ResizeObserver(() => parent.postMessage({type:'r4r:height', height:document.body.offsetHeight}, location.origin));
        this.resize.observe(document.body);
    }
    ngOnDestroy() { this.resize?.disconnect(); document.documentElement.classList.remove('embedded-assistant'); }
}
