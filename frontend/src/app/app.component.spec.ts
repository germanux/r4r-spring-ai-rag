import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the RAG page integration with correct heading', () => {
    const fixture = TestBed.createComponent(AppComponent);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    // Verify that we're rendering the RAG interface
    expect(compiled.querySelector('app-rag-page')).toBeTruthy();

    // Verify header text indicating RAG - this replaces the stale title assertion
    const heading = compiled.querySelector('h1');
    expect(heading?.textContent).toContain('RAG Question Answering');
  });
});
