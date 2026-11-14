import { Component, ChangeDetectionStrategy } from '@angular/core';
import { HeaderComponent } from './migration/header.component';
import { HeroComponent } from './migration/hero.component';
import { RagAssistantComponent } from './migration/rag-assistant.component';
import { InfoSectionsComponent } from './migration/info-sections.component';
import { FooterComponent } from './migration/footer.component';
@Component({selector:'app-root',standalone:true,imports:[HeaderComponent,HeroComponent,RagAssistantComponent,InfoSectionsComponent,FooterComponent],changeDetection:ChangeDetectionStrategy.OnPush,template:`<app-header/><main><app-hero/><app-rag-assistant/><app-info-sections/></main><app-footer/>`})
export class AppComponent {}
