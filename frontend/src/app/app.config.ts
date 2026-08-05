import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig } from '@angular/core';

import { environment } from '../environments/environment';
import { BACKEND_URL } from './core/config/backend-url.token';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(),
    { provide: BACKEND_URL, useValue: environment.backendUrl }
  ]
};
