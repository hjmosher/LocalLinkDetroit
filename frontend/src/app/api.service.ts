import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MetaResponse,
  DecomposeRequest,
  DecomposeResponse,
  MatchRequest,
  MatchResponse,
  IntroRequest,
  IntroResponse,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  getMeta(): Observable<MetaResponse> {
    return this.http.get<MetaResponse>('/api/meta');
  }

  decompose(req: DecomposeRequest): Observable<DecomposeResponse> {
    return this.http.post<DecomposeResponse>('/api/needs/decompose', req);
  }

  getMatches(req: MatchRequest): Observable<MatchResponse> {
    return this.http.post<MatchResponse>('/api/matches', req);
  }

  getHealth(): Observable<{ status: string; graniteMode: string }> {
    return this.http.get<{ status: string; graniteMode: string }>('/api/health');
  }

  draftIntro(req: IntroRequest): Observable<IntroResponse> {
    return this.http.post<IntroResponse>('/api/intros/draft', req);
  }
}
