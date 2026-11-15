import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { validAudit, RagAuditService } from './rag-audit.service';
describe('Audit contract',()=>{
    it('accepts a downgrade and rejects an auditor upgrade', () => {
        const r = { conclusive: true, originalStatus: 'ANSWERED' as const, recommendedStatus: 'PARTIAL' as const, effectiveStatus: 'PARTIAL' as const, note: 'Limits' };
        expect(validAudit(r, 'ANSWERED')).toBeTrue();
        expect(validAudit({ ...r, originalStatus: 'PARTIAL', recommendedStatus: 'ANSWERED', effectiveStatus: 'ANSWERED' }, 'PARTIAL')).toBeFalse();
        expect(validAudit({ ...r, conclusive: false }, 'ANSWERED')).toBeFalse();
    });
});
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

describe('Asynchronous review outcomes', () => {
    let service: RagAuditService;
    let http: HttpTestingController;
    const id = '12345678-1234-1234-1234-123456789abc';
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(RagAuditService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());
    it('keeps inconclusive separate from a successful review', async () => {
        const pending = service.review(id, 'ANSWERED');
        http.expectOne(r => r.url.endsWith(id)).flush({originalStatus:'ANSWERED',recommendedStatus:'ANSWERED',effectiveStatus:'ANSWERED',conclusive:false,note:''});
        expect(await pending).toEqual({state:'inconclusive',result:null});
    });
    it('keeps transport failure separate from a successful review', async () => {
        const pending = service.review(id, 'ANSWERED');
        http.expectOne(r => r.url.endsWith(id)).flush({}, {status:503,statusText:'Unavailable'});
        expect(await pending).toEqual({state:'unavailable',result:null});
    });
    it('returns only a validated downgrade', async () => {
        const result = {originalStatus:'ANSWERED',recommendedStatus:'PARTIAL',effectiveStatus:'PARTIAL',conclusive:true,note:'Limits'};
        const pending = service.review(id, 'ANSWERED');
        const request = http.expectOne(r => r.url.endsWith(id));
        expect(request.request.method).toBe('POST');request.flush(result);
        expect(await pending).toEqual({state:'reviewed',result:result as any});
    });
});
