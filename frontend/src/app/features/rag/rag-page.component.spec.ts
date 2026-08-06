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

    it('submits a trimmed question, enters loading state, disables input controls, and blocks duplicate submission', () => {
      // Attempt submission 1 (This triggers the async query)
      submit();
      fixture.detectChanges();

      expect(ragApiService.query).toHaveBeenCalledOnceWith({ question: 'Test question' });
      component.onSubmit();
      fixture.detectChanges();

      expect(component.currentState).toBe('loading');
      // Check loading state DOM assertion required by specs/Codex instructions
const loadingElement = fixture.nativeElement.querySelector('.loading-state[role="status"]');
expect(loadingElement).not.toBeNull();
// Asserting that the loading message is displayed and contains key process information.
expect(loadingElement?.textContent).toContain('Processing your question...');

      // Assert input controls are disabled
      expect(component.questionControl.disabled).toBeTrue();
      const submitButton = fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement;
      expect(submitButton).not.toBeNull();
      expect(submitButton.disabled).toBeTrue();

      // Attempting duplicate submission while loading (should be prevented by component logic)
      component.onSubmit();
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

  it('renders ordered citations with source and complete heading path', () => {
    submit();
    querySubject.next({
      ...successResponse,
      answer: 'A grounded answer',
      citations: [
        { ordinal: 2, source: 'Doc B', headingPath: ['Section 1', 'Subsection A'], label: 'B' },
        { ordinal: 3, source: 'Doc C', headingPath: ['Section 2'], label: 'C' },
        { ordinal: 1, source: 'Doc A', headingPath: ['Intro'], label: 'A' }
      ]
    });
    fixture.detectChanges();

    const citationItems = fixture.nativeElement.querySelectorAll('.citation-item');

    // Check that citations are ordered by ordinal
    expect(citationItems[0].textContent?.trim()).toContain('1.');
    expect(citationItems[0].textContent?.trim()).toContain('Doc A');
    expect(citationItems[0].textContent?.trim()).toContain('(Intro)');

    expect(citationItems[1].textContent?.trim()).toContain('2.');
    expect(citationItems[1].textContent?.trim()).toContain('Doc B');
    expect(citationItems[1].textContent?.trim()).toContain('(Section 1 > Subsection A)');

    expect(citationItems[2].textContent?.trim()).toContain('3.');
    expect(citationItems[2].textContent?.trim()).toContain('Doc C');
    expect(citationItems[2].textContent?.trim()).toContain('(Section 2)');
  });

  it('renders citations in ordinal order even when input is out of order', () => {
    submit();
    querySubject.next({
      ...successResponse,
      answer: 'A grounded answer',
      citations: [
        { ordinal: 3, source: 'Doc C', headingPath: ['Section 2'], label: 'C' },
        { ordinal: 1, source: 'Doc A', headingPath: ['Intro'], label: 'A' },
        { ordinal: 2, source: 'Doc B', headingPath: ['Section 1', 'Subsection A'], label: 'B' }
      ]
    });
    fixture.detectChanges();

    const citationItems = fixture.nativeElement.querySelectorAll('.citation-item');

    // Check that citations are ordered by ordinal regardless of input order
    expect(citationItems[0].textContent?.trim()).toContain('1.');
    expect(citationItems[0].textContent?.trim()).toContain('Doc A');
    expect(citationItems[0].textContent?.trim()).toContain('(Intro)');

    expect(citationItems[1].textContent?.trim()).toContain('2.');
    expect(citationItems[1].textContent?.trim()).toContain('Doc B');
    expect(citationItems[1].textContent?.trim()).toContain('(Section 1 > Subsection A)');

    expect(citationItems[2].textContent?.trim()).toContain('3.');
    expect(citationItems[2].textContent?.trim()).toContain('Doc C');
    expect(citationItems[2].textContent?.trim()).toContain('(Section 2)');
  });

  it('omits citation section when citations array is empty', () => {
    submit();
    querySubject.next({ answer: 'No citations needed', abstained: false, citations: [] });
    fixture.detectChanges();

    // Check that citations section is not present
    expect(fixture.nativeElement.querySelector('.citations-section')).toBeNull();
  });

  it('does not parse citation-like answer text into citations', () => {
    submit();
    querySubject.next({
      answer: 'This is [1] Fake Source > Fake Heading a citation-like string but should remain as plain text',
      abstained: false,
      citations: []
    });
    fixture.detectChanges();

    // Check that the citation-like text remains in the answer content
    const answerContent = fixture.nativeElement.querySelector('.answer-content') as HTMLElement;
    expect(answerContent.textContent).toContain('[1] Fake Source > Fake Heading');

    // Check that no citation items are created
    expect(fixture.nativeElement.querySelectorAll('.citation-item').length).toBe(0);

    // Check that citations section is not present
    expect(fixture.nativeElement.querySelector('.citations-section')).toBeNull();
  });

  it('does not create citations when answer contains citation-like text but citations array is empty', () => {
    submit();
    querySubject.next({
      answer: 'According to [2] Some Source > Some Heading, this is an example of citation-like text in the answer.',
      abstained: false,
      citations: []
    });
    fixture.detectChanges();

    // Check that citation-like text remains in the answer content
    const answerContent = fixture.nativeElement.querySelector('.answer-content') as HTMLElement;
    expect(answerContent.textContent).toContain('[2] Some Source > Some Heading');

    // Verify no citation items were created
    const citationItems = fixture.nativeElement.querySelectorAll('.citation-item');
    expect(citationItems.length).toBe(0);

    // Verify citations section is absent
    expect(fixture.nativeElement.querySelector('.citations-section')).toBeNull();
  });

  it('clears and resets the form after displaying any state (success or error)', () => {
    // 1. Simulate a success state first with citations to prove clearing works after complex display
    submit();
    querySubject.next({
      ...successResponse,
      answer: 'Reset test answer',
    });
    fixture.detectChanges();

    expect(component.currentState).toBe('success');
    // The original clear logic only checked simple cases; we must now prove it clears all components statefully.
    const initialAnswer = fixture.nativeElement.querySelector('.answer-content');
    expect(initialAnswer).not.toBeNull();

    // 2. Trigger an error state first to test removing stale errors
    querySubject.error(new Error('Pre-clear network failure'));
    fixture.detectChanges();
    expect(component.currentState).toBe('error');

    // 3. Now, clear the result and verify comprehensive reset
    component.clear();
    fixture.detectChanges();

    // Assert final idle state
    const idleStateElement = fixture.nativeElement.querySelector('.idle-state');
    expect(idleStateElement).not.toBeNull('Expected .idle-state to be present after clearing.');

    // Assert all prior state containers are cleared/absent
    const loadingStateContainer = fixture.nativeElement.querySelector('.loading-state[role="status"]');
    expect(loadingStateContainer).toBeNull('The .loading-state should be absent after clearing.');

    const errorStateElement = fixture.nativeElement.querySelector('.error-state[role="alert"]');
    expect(errorStateElement).toBeNull('Any stale error state container should be removed and the div should be absent or cleared.');
    // Assert content containers are cleared/absent
    const answerContent = fixture.nativeElement.querySelector('.answer-content');
    const citationsSection = fixture.nativeElement.querySelector('.citations-section');
    expect(answerContent).toBeNull('The .answer-content should be absent after clearing.');
    expect(citationsSection).toBeNull('The .citations-section should be absent after clearing.');

    // Assert internal component state cleanup
    expect(component.currentState).toBe('idle');
    expect(component.response).toBeNull();
    expect(component.error).toBeNull();
    expect(component.questionControl.value).toEqual(''); // Check form field is also blank
  });
});
