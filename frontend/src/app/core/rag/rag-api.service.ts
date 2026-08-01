import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BACKEND_URL } from '../config/backend-url.token';
import { RAGQuestionRequest, RAGAnswerResult } from './rag.models';
import { inject } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class RagApiService {
  private readonly backendUrl = inject(BACKEND_URL);

  constructor(private http: HttpClient) {}

  query(request: RAGQuestionRequest): Observable<RAGAnswerResult> {
    const url = `${this.backendUrl}/api/rag/answers`;
    return this.http.post<RAGAnswerResult>(url, request);
  }
}