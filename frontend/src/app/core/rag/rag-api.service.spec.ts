import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RagApiService } from './rag-api.service';
import { BACKEND_URL } from '../config/backend-url.token';

describe('RagApiService', () => {
  let service: RagApiService;
  let httpMock: HttpTestingController;

  const mockBackendUrl = 'http://localhost:8080';
  const mockQuestionRequest = { question: 'What is the capital of France?' };
  const mockAnswerResult = {
    answer: 'The capital of France is Paris.',
    abstained: false,
    citations: [
      {
        label: 'cite1',
        source: 'document1.pdf',
        headingPath: ['Introduction', 'Geography'],
        ordinal: 1
      },
      {
        label: 'cite2',
        source: 'document2.pdf',
        headingPath: ['History'],
        ordinal: 2
      }
    ]
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        RagApiService,
        { provide: BACKEND_URL, useValue: mockBackendUrl }
      ]
    });

    service = TestBed.inject(RagApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify(); // Verify that no unmatched requests are outstanding
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should query RAG API and return typed result with citation order preserved', () => {
    service.query(mockQuestionRequest).subscribe(result => {
      expect(result.answer).toBe(mockAnswerResult.answer);
      expect(result.abstained).toBe(mockAnswerResult.abstained);
      expect(result.citations).toEqual(mockAnswerResult.citations);
      expect(result.citations.length).toBe(2);
      expect(result.citations[0].ordinal).toBe(1);
      expect(result.citations[1].ordinal).toBe(2);
    });

    const req = httpMock.expectOne(`${mockBackendUrl}/api/rag/answers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockQuestionRequest);

    req.flush(mockAnswerResult);
  });

  it('should propagate transport errors without inventing an answer', () => {
    service.query(mockQuestionRequest).subscribe({
      next: result => fail('Should not emit a result on error'),
      error: error => {
        expect(error.status).toBe(500);
      }
    });

    const req = httpMock.expectOne(`${mockBackendUrl}/api/rag/answers`);
    expect(req.request.method).toBe('POST');
    req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });
  });
});