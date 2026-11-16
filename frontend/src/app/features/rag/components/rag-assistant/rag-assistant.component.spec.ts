import { TestBed } from '@angular/core/testing';
import { AssistantState } from '../../services/assistant-state.service';
import { RagAssistantComponent } from './rag-assistant.component';

describe('RAG opening warmup', () => {
  let originalUrl: string;
  beforeEach(() => { originalUrl = location.href; });
  afterEach(() => { history.replaceState(null, '', originalUrl); });

  for (const [query, expected] of [['', 1], ['?embed=1', 1], ['?prerender', 0]] as const) {
    it(`requests warmup ${expected} time(s) for ${query || 'standalone mode'}`, () => {
      history.replaceState(null, '', location.pathname + query);
      const warmup = jasmine.createSpy('warmup');
      const state = { api: { viewId: 'test-opening' }, runtime: { warmup },
        clear: jasmine.createSpy('clear'), play: jasmine.createSpy('play') };
      TestBed.configureTestingModule({ providers: [{ provide: AssistantState, useValue: state }] });
      const component = TestBed.runInInjectionContext(() => new RagAssistantComponent());
      try {
        component.ngAfterViewInit();
        expect(warmup).toHaveBeenCalledTimes(expected);
      } finally { component.ngOnDestroy(); }
    });
  }
});
