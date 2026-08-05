import { Component } from '@angular/core';
import { RagPageComponent } from './features/rag/rag-page.component';

@Component({
  selector: 'app-root',
  template: `
    <app-rag-page></app-rag-page>
  `,
  styles: [],
  standalone: true,
  imports: [RagPageComponent]
})
export class AppComponent {
}