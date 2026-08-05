import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { RagApiService } from '../../core/rag/rag-api.service';
import { RAGAnswerResult } from '../../core/rag/rag.models';
import { CommonModule } from '@angular/common';

export type RAGState = 'idle' | 'loading' | 'success' | 'abstention' | 'error';

@Component({
  selector: 'app-rag-page',
  templateUrl: './rag-page.component.html',
  styleUrls: ['./rag-page.component.scss'],
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule]
})
export class RagPageComponent implements OnInit {
  ragForm: FormGroup;
  currentState: RAGState = 'idle';
  response: RAGAnswerResult | null = null;
  error: string | null = null;

  constructor(
    private formBuilder: FormBuilder,
    private ragApiService: RagApiService
  ) {
    this.ragForm = this.formBuilder.group({
      question: ['', [Validators.required, Validators.minLength(1)]]
    });
  }

  ngOnInit(): void {}

  onSubmit(): void {
    if (this.ragForm.invalid) {
      return;
    }

    const question = this.ragForm.value.question.trim();

    if (!question) {
      return;
    }

    // Disable the form control to prevent multiple submissions
    this.ragForm.get('question')?.disable();
    this.currentState = 'loading';
    this.response = null;
    this.error = null;

    this.ragApiService.query({ question }).subscribe({
      next: (response) => {
        if (response.abstained) {
          this.currentState = 'abstention';
        } else {
          this.currentState = 'success';
        }
        this.response = response;
        this.ragForm.get('question')?.enable();
      },
      error: (error) => {
        this.error = 'Transport error occurred'; // Deterministic message
        this.currentState = 'error';
        this.ragForm.get('question')?.enable();
      }
    });
  }

  clear(): void {
    this.ragForm.reset();
    this.ragForm.get('question')?.enable();
    this.currentState = 'idle';
    this.response = null;
    this.error = null;
  }

  get sortedCitations(): RAGAnswerResult['citations'] {
    return (this.response?.citations || []).slice().sort((a, b) => a.ordinal - b.ordinal);
  }

  get hasCitations(): boolean {
    return (this.response?.citations || []).length > 0;
  }
}
