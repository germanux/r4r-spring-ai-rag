import { Injectable } from '@angular/core';
interface PrivacyPreferences {
    read(): {analytics:boolean;history:boolean};
    headers(): Record<string,string>;
    open(): void;
    withdraw(): void;
    noteQuery(id:string): void;
}
declare global { interface Window { r4rPrivacy?: PrivacyPreferences; } }
@Injectable({providedIn:'root'})
export class PrivacyService {
    headers(): Record<string,string> { return window.r4rPrivacy?.headers() || {}; }
    open() { window.r4rPrivacy?.open(); }
    policy() { return location.hostname.includes('riansares') || new URLSearchParams(location.search).get('embed') === '1' ? '/politica-privacidad.html' : '/privacy.html'; }
}
