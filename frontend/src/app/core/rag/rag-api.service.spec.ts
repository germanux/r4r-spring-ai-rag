import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RagApiService } from './rag-api.service';

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
        RagApiService
      ]
    });

    service = TestBed.inject(RagApiService);
    service.setBase(mockBackendUrl);
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

  it('reuses query for an unsupported SSE route and preserves the bilingual request', async () => {
    spyOn(window, 'fetch').and.resolveTo(new Response(null, {status:404}));
    const query = spyOn(service, 'query').and.callThrough();
    const answer = service.ask('A renovation question', 'en', new AbortController().signal, () => {});
    await Promise.resolve();
    await Promise.resolve();
    const request = httpMock.expectOne(`${mockBackendUrl}/api/rag/answers`);
    expect(query).toHaveBeenCalledOnceWith({question:'A renovation question',responseLanguage:'en'});
    expect(request.request.body).toEqual({question:'A renovation question',responseLanguage:'en'});
    request.flush(mockAnswerResult);
    expect(await answer).toEqual(mockAnswerResult);
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
import { provideHttpClient } from '@angular/common/http';
describe('SSE protocol', () => {
 let api: RagApiService;
 beforeEach(() => { TestBed.configureTestingModule({providers:[provideHttpClient()]});api=TestBed.inject(RagApiService); });
 function mock(chunks: string[]) {const encoder=new TextEncoder();spyOn(window,'fetch').and.resolveTo(new Response(new ReadableStream({start(controller){chunks.forEach(c=>controller.enqueue(encoder.encode(c)));controller.close();}}),{headers:{'Content-Type':'text/event-stream'}}));}
 it('handles split CRLF boundaries, heartbeats and UTF-8 answers',async()=>{
 mock([': heartbeat\r\n\r','\ndata: {"stage":"RETRIEVAL_STARTED"}\r','\n\r\ndata: {"stage":"ANSWER_READY","answer":"Reparación","abstained":false,"citations":[]}\r\n\r','\n']);
 const events:string[]=[];const r=await api.ask('Pregunta','es',new AbortController().signal,e=>events.push(e.stage));expect(r.answer).toBe('Reparación');expect(events).toEqual(['RETRIEVAL_STARTED','ANSWER_READY']);
 });
 it('rejects truncated streams without issuing a second request',async()=>{mock(['data: {"stage":"GENERATION_STARTED"}\n\n']);await expectAsync(api.ask('Pregunta','es',new AbortController().signal,()=>{})).toBeRejected();expect(window.fetch).toHaveBeenCalledTimes(1);});
 it('rejects invalid final answer contracts',async()=>{mock(['data: {"stage":"ANSWER_READY","answer":"x"}\n\n']);await expectAsync(api.ask('Pregunta','es',new AbortController().signal,()=>{})).toBeRejected();});
});
