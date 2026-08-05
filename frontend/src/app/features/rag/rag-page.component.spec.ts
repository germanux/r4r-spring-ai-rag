import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RagPageComponent } from './rag-page.component';
import { RagApiService } from '../../core/rag/rag-api.service';
import { of, throwError, Subject, BehaviorSubject } from 'rxjs';
import { RAGAnswerResult } from '../../core/rag/rag.models';
import { NO_ERRORS_SCHEMA } from '@angular/core';

describe('RagPageComponent', () => {
  let component: RagPageComponent;
  let fixture: ComponentFixture<RagPageComponent>;
  let mockRagApiService: jasmine.SpyObj<RagApiService>;
  let querySubject: Subject<RAGAnswerResult>;

  beforeEach(async () => {
    querySubject = new Subject<RAGAnswerResult>();
    const spy = jasmine.createSpyObj<RagApiService>('RagApiService', ['query']);
    spy.query.and.returnValue(querySubject);

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, RagPageComponent],
      providers: [
        { provide: RagApiService, useValue: spy }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(RagPageComponent);
    component = fixture.componentInstance;
    mockRagApiService = TestBed.inject(RagApiService) as jasmine.SpyObj<RagApiService>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have a valid form on init', () => {
    expect(component.ragForm.valid).toBeFalsy();
  });

  it('should reject blank questions', () => {
    component.ragForm.controls['question'].setValue('');
    expect(component.ragForm.valid).toBeFalsy();
  });

  it('should accept valid questions', () => {
    component.ragForm.controls['question'].setValue('Test question');
    expect(component.ragForm.valid).toBeTruthy();
  });

  describe('onSubmit', () => {
    const mockResponse: RAGAnswerResult = {
      answer: "This is the answer to: Test question",
      abstained: false,
      citations: [
        { ordinal: 1, source: "Doc A", headingPath: ["Introduction"], label: "Doc A - Introduction" },
        { ordinal: 2, source: "Doc B", headingPath: [], label: "Doc B" }
      ]
    };

    it('should submit a valid question', () => {
      component.ragForm.controls['question'].setValue('Test question');
      // Mock already set up in beforeEach to return querySubject

      component.onSubmit();

      expect(mockRagApiService.query).toHaveBeenCalledWith({ question: 'Test question' });
      expect(component.currentState).toBe('loading');
    });

    it('should handle loading state and disabled controls', () => {
      component.ragForm.controls['question'].setValue('Test question');
      // Mock already set up in beforeEach to return querySubject

      component.onSubmit();

      expect(component.ragForm.get('question')?.disabled).toBeTruthy();
      expect(component.currentState).toBe('loading');
    });

    it('should handle successful response', (done) => {
      component.ragForm.controls['question'].setValue('Test question');
      // Mock already set up in beforeEach to return querySubject

      component.onSubmit();

      // Check state before emission
      expect(component.ragForm.get('question')?.disabled).toBeTruthy();
      expect(component.currentState).toBe('loading');

      // Emit the mock response through the subject
      querySubject.next(mockResponse);

      setTimeout(() => {
        // After emission
        expect(component.response).toEqual(mockResponse);
        expect(component.currentState).toBe('success');
        expect(component.ragForm.get('question')?.disabled).toBeFalsy();
        done();
      }, 0);
    });

    it('should handle error state', (done) => {
      component.ragForm.controls['question'].setValue('Test question');
      // Mock already set up in beforeEach to return querySubject

      component.onSubmit();

      // Check state before emission
      expect(component.ragForm.get('question')?.disabled).toBeTruthy();
      expect(component.currentState).toBe('loading');

      // Emit error through the subject
      querySubject.error({ message: 'API Error' });

      setTimeout(() => {
        // After emission
        expect(component.error).toBe('Transport error occurred');
        expect(component.currentState).toBe('error');
        expect(component.ragForm.get('question')?.disabled).toBeFalsy();
        done();
      }, 0);
    });

    it('should handle abstention', (done) => {
      const abstentionResponse: RAGAnswerResult = {
        answer: "Cannot answer this question",
        abstained: true,
        citations: []
      };
      component.ragForm.controls['question'].setValue('Test question');
      // Mock already set up in beforeEach to return querySubject

      component.onSubmit();

      // Check state before emission
      expect(component.ragForm.get('question')?.disabled).toBeTruthy();
      expect(component.currentState).toBe('loading');

      // Emit the abstention response through the subject
      querySubject.next(abstentionResponse);

      setTimeout(() => {
        // After emission
        expect(component.response).toEqual(abstentionResponse);
        expect(component.currentState).toBe('abstention');
        expect(component.ragForm.get('question')?.disabled).toBeFalsy();
        done();
      }, 0);
    });
  });

  describe('clear', () => {
    it('should reset form and state', () => {
      component.ragForm.controls['question'].setValue('Test question');
      component.currentState = 'success';

      component.clear();

      expect(component.ragForm.get('question')?.disabled).toBeFalsy();
      expect(component.currentState).toBe('idle');
    });
  });

  describe('sortedCitations', () => {
    it('should sort citations by ordinal', () => {
      const mockResponse: RAGAnswerResult = {
        answer: "Test",
        abstained: false,
        citations: [
          { ordinal: 3, source: "Doc C", headingPath: [], label: "Doc C" },
          { ordinal: 1, source: "Doc A", headingPath: [], label: "Doc A" },
          { ordinal: 2, source: "Doc B", headingPath: [], label: "Doc B" }
        ]
      };
      component.response = mockResponse;

      const sorted = component.sortedCitations;

      expect(sorted[0].ordinal).toBe(1);
      expect(sorted[1].ordinal).toBe(2);
      expect(sorted[2].ordinal).toBe(3);
    });
  });

  describe('hasCitations', () => {
    it('should return true when citations exist', () => {
      const mockResponse: RAGAnswerResult = {
        answer: "Test",
        abstained: false,
        citations: [
          { ordinal: 1, source: "Doc A", headingPath: [], label: "Doc A" }
        ]
      };
      component.response = mockResponse;

      expect(component.hasCitations).toBe(true);
    });

    it('should return false when no citations exist', () => {
      const mockResponse: RAGAnswerResult = {
        answer: "Test",
        abstained: false,
        citations: []
      };
      component.response = mockResponse;

      expect(component.hasCitations).toBe(false);
    });
  });

  it('should render escaped text (not parse citation-like text as citations)', () => {
    const mockResponse: RAGAnswerResult = {
      answer: "<p>This is a <b>bold</b> statement.</p>",
      abstained: false,
      citations: []
    };
    component.response = mockResponse;

    // The content should be shown as escaped HTML text, not rendered as HTML
    expect(component.response.answer).toBe("<p>This is a <b>bold</b> statement.</p>");
  });
});
