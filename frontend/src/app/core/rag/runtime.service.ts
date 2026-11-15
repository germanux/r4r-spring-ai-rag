import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { RagApiService } from './rag-api.service';
import { ProviderInfo } from './rag.models';
interface RuntimeInfo {
    models?: ProviderInfo[];
    preferredProvider?: ProviderInfo;
    modelStatus?: string;
    embeddingModel?: string;
    embeddingDimensions?: number;
    topK?: number;
    minScore?: number;
    javaVersion?: string;
    springBootVersion?: string;
    springAiVersion?: string;
    remoteFallback?: {
        model?: string;
        enabled?: boolean;
    };
    loadedModels?: {
        name: string;
        sizeBytes?: number;
        vramBytes?: number;
        parameterSize?: string;
    }[];
    [key: string]: unknown;
}
@Injectable({ providedIn: 'root' })
export class RuntimeService {
    private api = inject(RagApiService);
    readonly info = signal<RuntimeInfo | null>(null);
    readonly error = signal('');
    private warmed = new Map<string, number>();
    private timer?: ReturnType<typeof setTimeout>;
    readiness() { return (this.info()?.models || []).map(m => [m.name, m.model, m.state].filter(Boolean).join(' · ')).join(' / '); }
    async load() { clearTimeout(this.timer); try {
        this.info.set(await firstValueFrom(this.api.http.get<RuntimeInfo>(this.api.base() + '/api/rag/runtime').pipe(timeout(10000))));
        this.error.set('');
        if (this.info()?.models?.some(m => m.refreshing || ['loading', 'unknown'].includes(m.state || '')))
            this.timer = setTimeout(() => void this.load(), 5000);
    }
    catch {
        this.info.set(null);
        this.error.set('Servicio no disponible / Service unavailable');
    } }
    warmup() { const base = this.api.base(), now = Date.now(); if (now - (this.warmed.get(base) || 0) < 60000)
        return; this.warmed.set(base, now); this.api.http.post(base + '/api/rag/warmup', null).pipe(timeout(15000)).subscribe({ error: () => { } }); }
}
