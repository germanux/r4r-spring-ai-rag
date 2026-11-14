import { Component, ChangeDetectionStrategy, inject, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from './assistant-state.service';
import { I18nService } from './i18n.service';
@Component({ selector: 'app-info-sections', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, templateUrl: './info-sections.html', styleUrl: './info-sections.scss' })
export class InfoSectionsComponent implements AfterViewInit, OnDestroy {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    opened: Record<string, boolean> = {};
    toggle(key: string) { this.opened = { ...this.opened, [key]: !this.opened[key] }; if (key === 'runtime' && this.opened[key])
        void this.s.runtime.load(); }
    runtimeField(key: string) { const r = this.s.runtime.info(); if (!r)
        return this.i.t('notQueried'); const missing = this.i.t('informationUnavailable'); switch (key) {
        case 'runtimeEmbeddingModel': return r.embeddingModel || missing;
        case 'runtimeEmbeddings': return r.embeddingDimensions ? String(r.embeddingDimensions) + ' ' + this.i.t('dimensions') : missing;
        case 'runtimeRetrieval': return `Top-K ${r.topK ?? missing} · ${this.i.t('minimumScore')} ${r.minScore ?? missing}`;
        case 'runtimeVersions': return `Java ${r.javaVersion || missing} · Spring Boot ${r.springBootVersion || missing} · Spring AI ${r.springAiVersion || missing}`;
        case 'runtimeRemote': return r.remoteFallback ? `${r.remoteFallback.model} · ${this.i.t(r.remoteFallback.enabled ? 'remoteEnabled' : 'remoteDisabled')}` : missing;
        case 'runtimeOllama': return r.loadedModels?.map(m => `${m.name} (${m.parameterSize || missing}; ${Math.round((m.sizeBytes || 0) / 1048576)} MB; ${Math.round((m.vramBytes || 0) / 1048576)} MB VRAM)`).join(' · ') || this.i.t('noModels');
        default: return missing;
    } }
    private hash = () => { const key = ({ 'como-funciona': 'concept', 'arquitectura': 'architecture', 'runtime': 'runtime' } as Record<string, string>)[location.hash.slice(1)]; if (key) {
        this.opened = { ...this.opened, [key]: true };
        if (key === 'runtime')
            void this.s.runtime.load();
    } };
    ngAfterViewInit() { window.addEventListener('hashchange', this.hash); setTimeout(this.hash, 0); }
    ngOnDestroy() { window.removeEventListener('hashchange', this.hash); }
}
