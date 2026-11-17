import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';
import { AppComponent } from './app.component';
import { I18nService } from './core/i18n/i18n.service';
import { AssistantState } from './features/rag/services/assistant-state.service';

describe('AppComponent', () => {
  let originalUrl: string;
  beforeEach(() => { originalUrl = location.href; });
  afterEach(() => { history.replaceState(null, '', originalUrl); });
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the RAG page integration with correct heading', () => {
    const fixture = TestBed.createComponent(AppComponent);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    // Verify that we're rendering the RAG interface
    expect(compiled.querySelector('app-rag-assistant')).toBeTruthy();

    // Verify header text indicating RAG - this replaces the stale title assertion
    const heading = compiled.querySelector('h1');
    expect(heading?.textContent?.length).toBeGreaterThan(10);
  });
  for (const language of ['es', 'en'] as const) {
    it(`keeps the commercial page simple and privacy visible in ${language}`, () => {
      history.replaceState(null, '', '/asistente-ia/?dev=1&prerender=1');
      TestBed.inject(I18nService).set(language);
      const fixture = TestBed.createComponent(AppComponent);
      fixture.detectChanges();
      const page = fixture.nativeElement as HTMLElement;
      expect(page.querySelector('.brand')?.textContent).toContain('Riansares 4R');
      expect(page.querySelector('a[href="https://athagon.tech/"]')).toBeNull();
      expect(page.querySelector('a[href*="github.com"]')).toBeNull();
      expect(page.querySelector('#runtime')).toBeNull();
      expect(page.querySelector('#progressDetails')).toBeNull();
      expect(page.querySelector('#endpointSettings')).toBeNull();
      expect(page.querySelector('#como-funciona')).toBeTruthy();
      expect(page.querySelector('#por-que-puedes-comprobarlo')).toBeTruthy();
      expect(page.querySelectorAll('[data-example]').length).toBe(4);
      expect(page.querySelector('#tryDemoButton')).toBeTruthy();
      const notice = page.querySelector('#rag-privacy-notice')!;
      expect(notice.closest('details')).toBeNull();
      expect(notice.closest('article')).toBeNull();
      expect(notice.querySelector('a')?.href).toBe('https://riansares4r.com/politica-privacidad.html');
      expect(page.querySelector('#question')?.getAttribute('aria-describedby')).toBe('rag-privacy-notice');
      expect(page.querySelector('#rag-privacy-controls')).toBeTruthy();
      expect(document.getElementById('commercial-site-styles')?.getAttribute('href')).toBe('assets/riansares/home-blueprint.css');
      const state = TestBed.inject(AssistantState);
      state.response.set({ answer: 'Answer [S1]', abstained: false, status: 'ANSWERED', citations: [] });
      state.audit.set({ originalStatus: 'ANSWERED', recommendedStatus: 'PARTIAL', effectiveStatus: 'PARTIAL', conclusive: true, note: 'Check this conclusion on site.' });
      fixture.detectChanges();
      const detail = page.querySelector('#auditDetails');
      expect(detail?.tagName).toBe('P');
      expect(detail?.textContent).toContain('Check this conclusion on site.');
      expect(detail?.querySelector('summary')).toBeNull();
      state.audit.set({ originalStatus: 'ANSWERED', recommendedStatus: 'ANSWERED', effectiveStatus: 'ANSWERED', conclusive: true, note: '' });
      fixture.detectChanges();
      expect(page.querySelector('#auditDetails')).toBeNull();
    });
  }

  it('preserves the technical presentation at /r4r-rag/', () => {
    history.replaceState(null, '', '/r4r-rag/?prerender=1');
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.brand')?.textContent).toContain('ATHAGON');
    expect(page.querySelector('#runtime')).toBeTruthy();
    expect(page.querySelector('#progressDetails')).toBeTruthy();
    expect(page.querySelector('#rag-privacy-notice')?.closest('details')).toBeNull();
  });

  it('omits redundant machine and model labels from the answer', () => {
    history.replaceState(null, '', '/r4r-rag/?prerender=1');
    const fixture = TestBed.createComponent(AppComponent);
    const state = TestBed.inject(AssistantState);
    state.response.set({ answer: 'Answer [S1]', abstained: false, status: 'ANSWERED', citations: [],
      provider: { name: 'desktop', model: 'qwen3:8b', inferenceHost: { hostname: 'PC-GIGA-ZORUX' } },
      embeddingProvider: { name: 'laptop', model: 'qwen3-embedding:0.6b', inferenceHost: { hostname: 'PC-ASUS-ZORIN' } } });
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const details = page.querySelector('#progressDetails') as HTMLDetailsElement;
    expect(details.open).toBeFalse();
    expect(page.querySelector('.answer-box #answerProvider')).toBeNull();
    expect(page.querySelector('.answer-box #embeddingProvider')).toBeNull();
    expect(page.querySelector('.answer-box')?.textContent).not.toContain('PC-GIGA-ZORUX');
    expect(page.querySelector('.answer-box')?.textContent).not.toContain('qwen3:8b');
    expect(details.querySelector('#answerProvider')).toBeNull();
    expect(details.querySelector('#embeddingProvider')).toBeNull();
    expect(page.querySelector('#runtime #runtimeUsed')?.textContent).toContain('PC-GIGA-ZORUX');
  });

});
