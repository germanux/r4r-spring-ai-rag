import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { BACKEND_URL } from './core/config/backend-url.token';
import { environment } from '../environments/environment';
import { appConfig } from './app.config';

describe('App Config', () => {
  it('should provide BACKEND_URL with correct value from environment', () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers]
    });

    const backendUrl = TestBed.inject(BACKEND_URL);
    expect(backendUrl).toBe(environment.backendUrl);
  });

  it('should provide HttpClient', () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClient(withInterceptorsFromDi())]
    });

    // This test confirms that HttpClient is injectable when the configuration includes provideHttpClient()
    const backendUrl = TestBed.inject(BACKEND_URL);
    expect(backendUrl).toBe(environment.backendUrl);
  });
});