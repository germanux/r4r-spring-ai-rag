import { Injectable, signal } from '@angular/core';
import { dictionary } from './translations';
@Injectable({ providedIn: 'root' })
export class I18nService {
    readonly language = signal<'es' | 'en'>('es');
    constructor() { try {
        this.set(localStorage.getItem('r4r.language') || (navigator.language.startsWith('es') ? 'es' : 'en'));
    }
    catch { } }
    set(value: string) { const lang = value === 'en' ? 'en' : 'es'; this.language.set(lang); document.documentElement.lang = lang; document.body.className = lang === 'es' ? 'spanish-demo' : 'english-demo'; try {
        localStorage.setItem('r4r.language', lang);
    }
    catch { } }
    t(key: string, count = 0): string { const value = dictionary[this.language()][key]; return typeof value === 'function' ? value(count) : typeof value === 'string' ? value : ''; }
    stages(): Record<string, string> { return dictionary[this.language()]['progressStages'] as Record<string, string>; }
}
