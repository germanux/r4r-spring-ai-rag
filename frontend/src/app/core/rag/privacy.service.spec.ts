import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting,HttpTestingController} from '@angular/common/http/testing';
import {PrivacyService} from './privacy.service';
describe('Technical history objection',()=>{
 let service:PrivacyService,http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});service=TestBed.inject(PrivacyService);http=TestBed.inject(HttpTestingController);});
 afterEach(()=>http.verify());
 it('has no persistent identifiers or consent dependency',()=>{expect(service.headers()).toEqual({});expect(service.objected()).toBeFalse();});
 it('restricts recent references and omits future content even if sending the objection fails',()=>{
  service.noteView('00000000-0000-4000-8000-000000000002');
  service.noteQuery('00000000-0000-4000-8000-000000000001');service.oppose(true,'https://api.test');
  expect(service.headers()).toEqual({'X-R4R-Usage-Opt-Out':'1'});
  const request=http.expectOne('https://api.test/api/rag/usage/objections');
  expect(request.request.body).toEqual({references:['00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000002']});
  request.flush(null,{status:503,statusText:'Unavailable'});expect(service.objectionState()).toBe('failed');expect(service.objected()).toBeTrue();
 });
});
