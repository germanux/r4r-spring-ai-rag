import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AssistantState } from './assistant-state.service';
import { I18nService } from './i18n.service';
import { SourceCatalogService } from './source-catalog.service';
@Component({ selector: 'app-citations', standalone: true, imports: [CommonModule], changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './citations.scss', template: `
<details class="citations-block" id="sourcesDetails" [open]="!!s.response()?.citations?.length"><summary class="citations-head"><strong>{{i.t('page.sources')}}</strong><span id="citationCount">{{i.t('sources',s.response()?.citations?.length || 0)}}</span></summary>
<div id="citations" class="citations">
@for(c of s.response()?.citations;track $index){<article class="citation"><div class="citation-top"><span class="citation-label">{{c.label}}</span>@if(catalog.resolve(c,i.language());as doc){<a class="citation-source" [href]="catalog.url(c,i.language())" target="_blank" rel="noopener">{{c.document || doc.title.replace('Riansares 4R — ','')}} · {{i.t('openDocument')}} (PDF · {{doc.source.startsWith('en/')?'EN':'ES'}})</a>}@else{<span class="citation-source">{{i.t('unknownSource')}}</span>}</div><div class="citation-path">{{c.section || c.headingPath?.join(' › ') || i.t('noHeading')}}</div>@if(c.source){<details><summary>{{i.t('originalSource')}}</summary><p>{{c.source}}</p><a [href]="catalog.url(c,i.language(),true)" target="_blank" rel="noopener">HTML</a></details>}@if(s.developer){<div class="citation-ordinal">{{i.t('ordinal')}}: {{c.ordinal ?? '—'}}</div>}</article>}
@empty{<div class="citation">{{i.t(s.response()?.abstained?'noCitationsAbstained':'noCitations')}}</div>}
</div></details>` })
export class CitationsComponent {
    readonly s = inject(AssistantState);
    readonly i = inject(I18nService);
    readonly catalog = inject(SourceCatalogService);
}
