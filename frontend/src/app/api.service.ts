import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BACKEND_URL } from './core/config/backend-url.token';
import { inject } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly backendUrl = inject(BACKEND_URL);

  constructor(private http: HttpClient) { }

  getTestData() {
    return this.http.get(`${this.backendUrl}/test`);
  }
}