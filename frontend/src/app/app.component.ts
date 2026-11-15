import { Component, ChangeDetectionStrategy } from '@angular/core';
import { HeaderComponent } from './layout/header/header.component';
import { HeroComponent } from './features/home/components/hero/hero.component';
import { RagAssistantComponent } from './features/rag/components/rag-assistant/rag-assistant.component';
import { InfoSectionsComponent } from './features/home/components/info-sections/info-sections.component';
import { FooterComponent } from './layout/footer/footer.component';
@Component({selector:'app-root',standalone:true,imports:[HeaderComponent,HeroComponent,RagAssistantComponent,InfoSectionsComponent,FooterComponent],changeDetection:ChangeDetectionStrategy.OnPush,template:`<app-header/><main><app-hero/><app-rag-assistant/><app-info-sections/></main><app-footer/>`})
export class AppComponent {}
