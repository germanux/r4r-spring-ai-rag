import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { RagApiService } from '../../core/rag/rag-api.service';
import { RAGAnswerResult } from '../../core/rag/rag.models';

export type RAGState = 'idle' | 'loading' | 'success' | 'abstention' | 'error';

@Component({
  selector: 'app-rag-page',
  templateUrl: './rag-page.component.html',
  styleUrls: ['./rag-page.component.scss'],
  standalone: true,
  imports: [ReactiveFormsModule]
})
export class RagPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly ragApiService = inject(RagApiService);

  readonly ragForm = this.formBuilder.nonNullable.group({
    question: ['', [Validators.required, Validators.pattern(/\S/)]]
  });

  currentState: RAGState = 'idle';
  response: RAGAnswerResult | null = null;
  error: string | null = null;

  get questionControl() {
    return this.ragForm.controls.question;
  }

  get sortedCitations(): RAGAnswerResult['citations'] {
    return [...(this.response?.citations ?? [])].sort((first, second) => first.ordinal - second.ordinal);
  }

  get hasCitations(): boolean {
    return this.sortedCitations.length > 0;
  }

  onSubmit(): void {
    if (this.currentState === 'loading') {
      return;
    }

    if (this.ragForm.invalid) {
      this.ragForm.markAllAsTouched();
      return;
    }

    const question = this.questionControl.value.trim();
    this.questionControl.disable();
    this.currentState = 'loading';
    this.response = null;
    this.error = null;

    this.ragApiService.query({ question }).subscribe({
      next: (response) => {
        this.response = response;
        this.currentState = response.abstained ? 'abstention' : 'success';
        this.questionControl.enable();
      },
      error: () => {
        this.error = 'Transport error occurred';
        this.currentState = 'error';
        this.questionControl.enable();
      }
    });
  }

  clear(): void {
    this.ragForm.reset();
    this.questionControl.enable();
    this.currentState = 'idle';
    this.response = null;
    this.error = null;
  }
}
