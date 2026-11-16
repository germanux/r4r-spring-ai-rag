import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
interface CommercialAnalytics { open():void; withdraw():void; read():{analytics:boolean}; }
declare global { interface Window { r4rPrivacy?:CommercialAnalytics; } }
@Injectable({providedIn:'root'})
export class PrivacyService {
    private http=inject(HttpClient);
    readonly objected=signal(false);
    readonly objectionState=signal<'idle'|'pending'|'submitted'|'failed'>('idle');
    private references:string[]=[];
    private viewReference?:string;
    noteView(id:string){this.viewReference=id;}
    headers():Record<string,string>{return this.objected()?{'X-R4R-Usage-Opt-Out':'1'}:{};}
    noteQuery(id:string){this.references.push(id);if(this.references.length>100)this.references.shift();}
    oppose(value:boolean,apiBase:string){
        this.objected.set(value);
        if(!value){this.objectionState.set('idle');return;}
        this.objectionState.set('pending');
        this.http.post(apiBase+'/api/rag/usage/objections',{references:[...this.references.slice(-99),...(this.viewReference?[this.viewReference]:[])]}).subscribe({
            next:()=>this.objectionState.set('submitted'),error:()=>this.objectionState.set('failed')});
    }
    contact(){return this.policy()==='/politica-privacidad.html'?'mailto:riansares4r@gmail.com':'mailto:german.caballero.rguez@gmail.com';}
    policy(){return location.hostname.includes('riansares')||new URLSearchParams(location.search).get('embed')==='1'?'/politica-privacidad.html':'/privacy.html';}
}
