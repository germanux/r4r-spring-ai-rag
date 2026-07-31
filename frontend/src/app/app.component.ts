import { Component, OnInit } from '@angular/core';
import { ApiService } from './api.service';
import { JsonPipe } from '@angular/common';
import { BACKEND_URL } from './core/config/backend-url.token';
import { inject } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <div class="container">
      <h1>Angular 17 Standalone App</h1>
      <p>Backend URL: {{ backendUrl }}</p>
      <button (click)="testApiCall()">Test API Call</button>
      @if (apiResult) {
        <div>API Result: {{ apiResult | json }}</div>
      }
    </div>
  `,
  styles: [],
  standalone: true,
  imports: [JsonPipe]
})
export class AppComponent implements OnInit {
  backendUrl = '';
  apiResult: any = null;
  private readonly backendUrlToken = inject(BACKEND_URL);

  constructor(private apiService: ApiService) {
    this.backendUrl = this.backendUrlToken;
  }

  ngOnInit(): void {
  }

  testApiCall() {
    // This is a test method - in real implementation it would make actual API calls
    console.log('API call test initiated');
    this.apiResult = { message: 'Test successful', backend: this.backendUrl };
  }
}