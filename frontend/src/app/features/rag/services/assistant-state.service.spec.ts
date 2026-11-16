import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AssistantState } from './assistant-state.service';
import { RagApiService } from '../../../core/rag/rag-api.service';
import { RagAuditService, AuditReview } from '../../../core/rag/rag-audit.service';
import { RuntimeService } from '../../../core/rag/runtime.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { DemoPlaybackService } from './demo-playback.service';
import { QuestionFormComponent } from '../components/question-form/question-form.component';
import { AuditStatusComponent } from '../components/audit-status/audit-status.component';

describe('Answer and source review lifecycle', () => {
  let state: AssistantState;
  let api: { ask: jasmine.Spy; base: () => string };
  let finishReview: (value: AuditReview) => void;

  beforeEach(() => {
    api = { ask: jasmine.createSpy('ask').and.resolveTo({ answer: 'Answer [S1]', abstained: false,
      status: 'ANSWERED', citations: [], auditId: 'test-audit',
      provider: { name: 'opaque-id', model: 'qwen3:8b', inferenceHost: { hostname: 'PC-GIGA-ZORUX' } },
      embeddingProvider: { name: 'another-id', model: 'qwen3-embedding:0.6b', inferenceHost: { hostname: 'PC-ASUS-ZORIN' } } }),
      base: () => 'http://localhost/test' };
    const pending = new Promise<AuditReview>(resolve => { finishReview = resolve; });
    TestBed.configureTestingModule({ imports: [QuestionFormComponent, AuditStatusComponent], providers: [
      provideHttpClient(), AssistantState,
      { provide: RagApiService, useValue: api },
      { provide: RagAuditService, useValue: { review: () => pending } },
      { provide: RuntimeService, useValue: {} },
      { provide: DemoPlaybackService, useValue: { active: () => false, stop: () => {} } },
    ] });
    state = TestBed.inject(AssistantState);
    state.question.set('A question');
  });

  for (const language of ['es', 'en']) {
    for (const outcome of ['reviewed', 'inconclusive', 'unavailable'] as const) {
      it(`keeps both indicators active until ${outcome} in ${language}`, fakeAsync(() => {
        TestBed.inject(I18nService).set(language);
        const form = TestBed.createComponent(QuestionFormComponent);
        const audit = TestBed.createComponent(AuditStatusComponent);
        void state.send();
        flushMicrotasks();
        form.detectChanges(); audit.detectChanges();
        expect(state.loading()).toBeFalse();
        expect(state.response()?.answer).toBe('Answer [S1]');
        expect(state.busy()).toBeTrue();
        expect(state.provider()).toContain('PC-GIGA-ZORUX');
        expect(state.embeddingProvider()).toContain('PC-ASUS-ZORIN');
        const button = form.nativeElement.querySelector('#sendButton') as HTMLButtonElement;
        expect(button.disabled).toBeTrue();
        expect(button.classList.contains('is-loading')).toBeTrue();
        const badge = audit.nativeElement.querySelector('#auditNote') as HTMLElement;
        expect(badge.getAttribute('aria-busy')).toBe('true');
        expect(badge.textContent).toContain(language === 'es' ? 'Revisando referencias' : 'Checking sources');
        const spinner = badge.querySelector('#auditIcon') as HTMLElement;
        expect(spinner.classList.contains('spinner')).toBeTrue();
        expect(getComputedStyle(spinner).animationName).toBe('spin');
        void state.send(); flushMicrotasks();
        expect(api.ask).toHaveBeenCalledTimes(1);

        finishReview({ state: outcome, result: outcome === 'reviewed' ? {
          conclusive: true, originalStatus: 'ANSWERED', recommendedStatus: 'PARTIAL',
          effectiveStatus: 'PARTIAL', note: 'Limited support' } : null });
        flushMicrotasks(); form.detectChanges(); audit.detectChanges();
        expect(state.busy()).toBeFalse();
        expect(state.auditState()).toBe(outcome);
        expect(button.disabled).toBeFalse();
        expect(button.classList.contains('is-loading')).toBeFalse();
        expect(badge.querySelector('.spinner')).toBeNull();
        if (outcome === 'reviewed') expect(state.status()).toBe('PARTIAL');
      }));
    }
  }

  it('ignores a completed review after the visitor clears the query', fakeAsync(() => {
    void state.send(); flushMicrotasks();
    state.clear();
    finishReview({ state: 'unavailable', result: null });
    flushMicrotasks();
    expect(state.response()).toBeNull();
    expect(state.busy()).toBeFalse();
    expect(state.auditState()).toBe('idle');
  }));
});
