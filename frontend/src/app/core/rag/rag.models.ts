export interface RAGQuestionRequest {
    question: string;
    responseLanguage?: 'es' | 'en';
}

export type RagStatus = 'ANSWERED' | 'PARTIAL' | 'ABSTAINED';
export interface RAGCitation {
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
        hostname?: string;
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
export interface RAGAnswerResult {
    answer: string;
    abstained: boolean;
    status?: RagStatus;
    citations: RAGCitation[];
    auditId?: string;
    provider?: ProviderInfo;
    embeddingProvider?: ProviderInfo;
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
export const statusOf = (r: RAGAnswerResult): RagStatus => r.abstained ? 'ABSTAINED' : r.status === 'PARTIAL' ? 'PARTIAL' : 'ANSWERED';
export const describeProvider = (p?: ProviderInfo): string => [p?.inferenceHost?.hostname || p?.name, p?.model].filter(Boolean).join(' · ');
export function validResponse(r: unknown): r is RAGAnswerResult { const v = r as RAGAnswerResult; return !!v && typeof v.answer === 'string' && typeof v.abstained === 'boolean' && Array.isArray(v.citations); }
