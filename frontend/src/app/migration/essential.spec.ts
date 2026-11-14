import { validAudit } from './rag-audit.service';
import { markdown } from './markdown.pipe';
import { validResponse } from './models';
describe('RAG browser contract', () => {
    it('accepts a downgrade and rejects an auditor upgrade', () => {
        const r = { conclusive: true, originalStatus: 'ANSWERED' as const, recommendedStatus: 'PARTIAL' as const, effectiveStatus: 'PARTIAL' as const, note: 'Limits' };
        expect(validAudit(r, 'ANSWERED')).toBeTrue();
        expect(validAudit({ ...r, originalStatus: 'PARTIAL', recommendedStatus: 'ANSWERED', effectiveStatus: 'ANSWERED' }, 'PARTIAL')).toBeFalse();
        expect(validAudit({ ...r, conclusive: false }, 'ANSWERED')).toBeFalse();
    });
    it('rejects incomplete stream answers', () => { expect(validResponse({ answer: 'x', abstained: false })).toBeFalse(); expect(validResponse({ answer: 'x', abstained: false, citations: [] })).toBeTrue(); });
    it('escapes HTML and only renders safe Markdown links', () => { const html = markdown('<img src=x onerror=alert(1)> **safe** [bad](javascript:alert) [ok](https://example.com)'); expect(html).not.toContain('<img'); expect(html).not.toContain('href="javascript:'); expect(html).toContain('<strong>safe</strong>'); expect(html).toContain('href="https://example.com"'); });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { RagApiService } from './rag-api.service';
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
