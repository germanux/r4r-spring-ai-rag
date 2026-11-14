export type RagStatus = 'ANSWERED' | 'PARTIAL' | 'ABSTAINED';
export interface Citation {
    label: string;
    source?: string;
    headingPath?: string[];
    ordinal?: number;
    document?: string;
    section?: string;
    link?: string;
}
export interface ProviderInfo {
    name?: string;
    model?: string;
    inferenceHost?: {
        type?: string;
    };
    state?: string;
    refreshing?: boolean;
}
export interface ProgressEvent {
    stage: string;
    elapsedMs?: number;
    durationMs?: number;
    documentsFound?: number;
    provider?: string | ProviderInfo;
    model?: string;
    message?: string;
}
export interface AuditResult {
    conclusive: boolean;
    originalStatus: RagStatus;
    recommendedStatus: RagStatus;
    effectiveStatus: RagStatus;
    note: string;
}
export interface RagResponse {
    answer: string;
    abstained: boolean;
    status?: RagStatus;
    citations: Citation[];
    auditId?: string;
    provider?: ProviderInfo;
    providerName?: string;
    model?: string;
    question?: string;
    durationMs?: number;
    recordedAt?: string;
    events?: ProgressEvent[];
    capture?: {
        auditor?: string;
        provider?: string;
        duration?: string;
        display?: string;
    };
}
export const statusOf = (r: RagResponse): RagStatus => r.abstained ? 'ABSTAINED' : r.status === 'PARTIAL' ? 'PARTIAL' : 'ANSWERED';
export function validResponse(r: unknown): r is RagResponse { const v = r as RagResponse; return !!v && typeof v.answer === 'string' && typeof v.abstained === 'boolean' && Array.isArray(v.citations); }
