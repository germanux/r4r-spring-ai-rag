import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AnswerPanelComponent } from './answer-panel.component';
import { AssistantState } from '../../services/assistant-state.service';
import { I18nService } from '../../../../core/i18n/i18n.service';

describe('User-facing audit detail', () => {
  for (const language of ['es', 'en'] as const) {
    it(`omits the duplicate badge but retains a useful note in ${language}`, () => {
      const audit = signal<any>(null);
      const response = signal<any>({ capture: { auditor: language === 'es' ? 'Referencias revisadas.' : 'Sources reviewed.' } });
      TestBed.configureTestingModule({ providers: [{ provide: AssistantState, useValue: { audit, response } }] });
      TestBed.inject(I18nService).set(language);
      const panel = TestBed.runInInjectionContext(() => new AnswerPanelComponent());
      expect(panel.auditDetail()).toBe('');
      audit.set({ note: 'A site inspection is needed.' });
      expect(panel.auditDetail()).toBe('A site inspection is needed.');
      audit.set(null);
      response.set({ capture: { auditor: 'A cited source does not confirm this conclusion.' } });
      expect(panel.auditDetail()).toBe('A cited source does not confirm this conclusion.');
    });
  }
});
