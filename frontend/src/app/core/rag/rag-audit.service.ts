import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { RagApiService } from './rag-api.service';
import { AuditResult, RagStatus } from './rag.models';
export function validAudit(r: AuditResult, original: RagStatus): boolean { const rank: Record<RagStatus, number> = { ABSTAINED: 0, PARTIAL: 1, ANSWERED: 2 }; return !!r && r.conclusive === true && r.originalStatus === original && Object.hasOwn(rank, r.recommendedStatus) && Object.hasOwn(rank, r.effectiveStatus) && r.effectiveStatus === (rank[r.recommendedStatus] < rank[original] ? r.recommendedStatus : original) && typeof r.note === 'string' && r.note.length <= 400; }
export interface AuditReview { state: 'reviewed' | 'inconclusive' | 'unavailable'; result: AuditResult | null; }
@Injectable({ providedIn: 'root' })
export class RagAuditService {
    private api = inject(RagApiService);
    async review(id: string, original: RagStatus): Promise<AuditReview> {
        if (!/^[a-f0-9-]{36}$/i.test(id)) return {state:'unavailable',result:null};
        try {
            const result = await firstValueFrom(this.api.http.post<AuditResult>(this.api.base() + '/api/rag/audits/' + encodeURIComponent(id), null).pipe(timeout(90000)));
            return validAudit(result, original) ? {state:'reviewed',result} : {state:'inconclusive',result:null};
        } catch { return {state:'unavailable',result:null}; }
    }
}
