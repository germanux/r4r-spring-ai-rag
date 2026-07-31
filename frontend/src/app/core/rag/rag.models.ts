export interface RAGQuestionRequest {
  question: string;
}

export interface RAGCitation {
  label: string;
  source: string;
  headingPath: string[];
  ordinal: number;
}

export interface RAGAnswerResult {
  answer: string;
  abstained: boolean;
  citations: RAGCitation[];
}