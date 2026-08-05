import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { RagApiService } from '../../core/rag/rag-api.service';
import { RAGAnswerResult } from '../../core/rag/rag.models';
import { RagPageComponent } from './rag-page.component';

describe('RagPageComponent', () => {
  let component: RagPageComponent;
  let fixture: ComponentFixture<RagPageComponent>;
  let querySubject: Subject<RAGAnswerResult>;
  let ragApiService: jasmine.SpyObj<RagApiService>;

  const successResponse: RAGAnswerResult = {
    answer: 'A grounded answer',
    abstained: false,
    citations: [
      { ordinal: 2, source: 'Doc B', headingPath: [], label: 'B' },
      { ordinal: 1, source: 'Doc A', headingPath: ['Intro'], label: 'A' }
    ]
  };

  beforeEach(async () => {
    querySubject = new Subject<RAGAnswerResult>();
    ragApiService = jasmine.createSpyObj<RagApiService>('RagApiService', ['query']);
    ragApiService.query.and.returnValue(querySubject);

    await TestBed.configureTestingModule({
      imports: [RagPageComponent],
      providers: [{ provide: RagApiService, useValue: ragApiService }]
    }).compileComponents();

    fixture = TestBed.createComponent(RagPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function submit(question = '  Test question  '): void {
    component.questionControl.setValue(question);
    component.onSubmit();
    fixture.detectChanges();
  }

  it('creates and starts in the idle state', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.idle-state')).not.toBeNull();
  });

  it('rejects an empty or whitespace-only question', () => {
    component.questionControl.setValue('   ');
    component.onSubmit();
    fixture.detectChanges();

    expect(ragApiService.query).not.toHaveBeenCalled();
    expect(component.questionControl.touched).toBeTrue();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('cannot be blank');
  });

  it('submits a trimmed question, disables input, and blocks duplicate submission', () => {
    submit();
    component.onSubmit();

    expect(ragApiService.query).toHaveBeenCalledOnceWith({ question: 'Test question' });
    expect(component.currentState).toBe('loading');
    expect(component.questionControl.disabled).toBeTrue();
    expect((fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('.loading-state')).not.toBeNull();
  });

  it('renders an answer as escaped text and citations in ordinal order', () => {
    submit();
    querySubject.next({
      ...successResponse,
      answer: '<strong>Untrusted</strong>\nSecond line'
    });
    fixture.detectChanges();

    const answer = fixture.nativeElement.querySelector('.answer-content') as HTMLElement;
    const citations = Array.from(
      fixture.nativeElement.querySelectorAll('.citation-item'),
      (element: Element) => element.textContent?.trim()
    );

    expect(component.currentState).toBe('success');
    expect(answer.textContent?.trim()).toBe('<strong>Untrusted</strong>\nSecond line');
    expect(answer.querySelector('strong')).toBeNull();
    expect(citations[0]).toContain('1.');
    expect(citations[0]).toContain('Doc A');
    expect(citations[1]).toContain('Doc B');
    expect(component.questionControl.enabled).toBeTrue();
  });

  it('renders an explicit abstention without citations', () => {
    submit();
    querySubject.next({ answer: 'Not enough evidence', abstained: true, citations: [] });
    fixture.detectChanges();

    expect(component.currentState).toBe('abstention');
    expect(fixture.nativeElement.querySelector('.abstention-state')?.textContent).toContain('Not enough evidence');
    expect(fixture.nativeElement.querySelector('.citations-section')).toBeNull();
  });

  it('renders a deterministic transport error and re-enables the input', () => {
    submit();
    querySubject.error(new Error('network details must not leak'));
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.error-state[role="alert"]') as HTMLElement;
    expect(component.currentState).toBe('error');
    expect(alert.textContent).toContain('Transport error occurred');
    expect(component.questionControl.enabled).toBeTrue();
  });

  it('clears the result and resets the form', () => {
    submit();
    querySubject.next(successResponse);
    component.clear();
    fixture.detectChanges();

    expect(component.currentState).toBe('idle');
    expect(component.response).toBeNull();
    expect(component.error).toBeNull();
    expect(component.questionControl.value).toBe('');
    expect(fixture.nativeElement.querySelector('.idle-state')).not.toBeNull();
  });
});
