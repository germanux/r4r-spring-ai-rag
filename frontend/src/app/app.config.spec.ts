import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { appConfig } from './app.config';

describe('App configuration', () => {
  it('provides HttpClient for the active services', () => {
    TestBed.configureTestingModule({ providers: [...appConfig.providers] });
    expect(TestBed.inject(HttpClient)).toBeTruthy();
  });
});
