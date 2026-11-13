import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ApiService } from './api.service';
import {
  City,
  Skill,
  DecomposeResponse,
  MatchResponse,
} from './models';

import { PostNeedComponent, PostNeedOutput } from './post-need/post-need.component';
import { ReviewSkillsComponent, ReviewSkillsOutput } from './review-skills/review-skills.component';
import { MatchesComponent, DraftIntroRequest } from './matches/matches.component';
import { IntroDialogComponent, IntroDialogData } from './intro-dialog/intro-dialog.component';

type AppState = 'post-need' | 'review-skills' | 'matches';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    PostNeedComponent,
    ReviewSkillsComponent,
    MatchesComponent,
    IntroDialogComponent,
  ],
  template: `
    <!-- Demo-data banner (always visible, every screen) -->
    <div class="demo-banner" role="banner">
      <span>⚠ Demo data: all businesses are fictional. Trust badges are simulated. For demonstration purposes only.</span>
    </div>

    <div class="app-shell">
      <header class="app-header">
        <div class="app-logo">
          <span class="logo-icon" aria-hidden="true">🔗</span>
          <span class="logo-text">LocalLink Detroit</span>
        </div>
        <div class="granite-mode" *ngIf="graniteMode">
          AI: <strong>{{ graniteMode }}</strong>
        </div>
      </header>

      <!-- Stepper indicator -->
      <nav class="stepper" aria-label="Progress">
        <span class="step" [class.active]="state === 'post-need'" [class.done]="stepDone('post-need')">
          <span class="step-num" aria-hidden="true">1</span> Post a need
        </span>
        <span class="step-sep" aria-hidden="true">›</span>
        <span class="step" [class.active]="state === 'review-skills'" [class.done]="stepDone('review-skills')">
          <span class="step-num" aria-hidden="true">2</span> Review skills
        </span>
        <span class="step-sep" aria-hidden="true">›</span>
        <span class="step" [class.active]="state === 'matches'">
          <span class="step-num" aria-hidden="true">3</span> Matches
        </span>
      </nav>

      <main class="app-main">
        <!-- State 1: Post a need -->
        <app-post-need
          *ngIf="state === 'post-need'"
          [cities]="cities"
          [loading]="loading"
          [error]="errorMsg"
          (submitted)="onNeedSubmitted($event)"
        ></app-post-need>

        <!-- State 2: Review skills -->
        <app-review-skills
          *ngIf="state === 'review-skills'"
          [summary]="decomposeResult?.summary ?? ''"
          [city]="currentCity"
          [decomposedSkills]="decomposeResult?.skills ?? []"
          [allSkills]="allSkills"
          [loading]="loading"
          [error]="errorMsg"
          (back)="state = 'post-need'"
          (submitted)="onSkillsSubmitted($event)"
        ></app-review-skills>

        <!-- State 3: Matches -->
        <app-matches
          *ngIf="state === 'matches'"
          [city]="currentCity"
          [matchResponse]="matchResponse"
          (back)="state = 'review-skills'"
          (draftIntro)="openIntroDialog($event)"
        ></app-matches>
      </main>

      <footer class="app-footer">
        LocalLink Detroit · Hackathon demo · IBM watsonx.ai Granite
      </footer>
    </div>

    <!-- Intro dialog (overlaid when open) -->
    <app-intro-dialog
      *ngIf="introDialogVisible"
      [data]="introDialogData"
      [loading]="introLoading"
      [error]="introError"
      (close)="closeIntroDialog()"
    ></app-intro-dialog>
  `,
  styles: [`
    /* Demo-data banner */
    .demo-banner {
      background: #1f2328; color: #fbbf24;
      text-align: center; padding: 7px 16px; font-size: 0.82rem; font-weight: 600;
      position: sticky; top: 0; z-index: 50;
    }

    .app-shell { display: flex; flex-direction: column; min-height: 100vh; }

    .app-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 12px 24px; border-bottom: 1px solid #e5e7eb; background: #fff;
    }
    .app-logo { display: flex; align-items: center; gap: 8px; }
    .logo-icon { font-size: 1.3rem; }
    .logo-text { font-size: 1.15rem; font-weight: 700; color: #1f2328; }
    .granite-mode { font-size: 0.8rem; color: #6b7280; }

    .stepper {
      display: flex; align-items: center; gap: 6px;
      padding: 10px 24px; background: #f7f8fa; border-bottom: 1px solid #e5e7eb;
      font-size: 0.85rem;
    }
    .step { color: #9ca3af; display: flex; align-items: center; gap: 5px; }
    .step.active { color: #1f2328; font-weight: 700; }
    .step.done { color: #3b82d4; }
    .step-num {
      display: inline-flex; align-items: center; justify-content: center;
      width: 20px; height: 20px; border-radius: 50%; border: 1.5px solid currentColor;
      font-size: 0.72rem; font-weight: 700;
    }
    .step-sep { color: #d1d5db; font-size: 1rem; }

    .app-main { flex: 1; padding: 28px 24px; }

    .app-footer {
      text-align: center; padding: 12px; font-size: 0.75rem; color: #9ca3af;
      border-top: 1px solid #e5e7eb;
    }
  `],
})
export class AppComponent implements OnInit {
  private readonly api = inject(ApiService);

  state: AppState = 'post-need';
  cities: City[] = [];
  allSkills: Skill[] = [];
  graniteMode: string | null = null;

  loading = false;
  errorMsg: string | null = null;

  currentCity = 'Royal Oak';
  currentNeedText = '';
  decomposeResult: DecomposeResponse | null = null;
  matchResponse: MatchResponse | null = null;

  // Intro dialog state
  introDialogVisible = false;
  introLoading = false;
  introError: string | null = null;
  introDialogData: IntroDialogData | null = null;
  private pendingIntroRequest: DraftIntroRequest | null = null;

  ngOnInit(): void {
    // Load meta (cities + skills) and health in parallel
    this.api.getMeta().subscribe({
      next: (meta) => {
        this.cities = meta.cities;
        this.allSkills = meta.skills;
      },
      error: () => {
        // Fallback cities so the UI is still usable in dev without backend
        this.cities = [
          { name: 'Royal Oak' },
          { name: 'Ann Arbor' },
          { name: 'Detroit' },
          { name: 'Livonia' },
          { name: 'Southfield' },
        ];
      },
    });
    this.api.getHealth().subscribe({
      next: (h) => (this.graniteMode = h.graniteMode),
      error: () => { /* health is optional */ },
    });
  }

  stepDone(step: AppState): boolean {
    const order: AppState[] = ['post-need', 'review-skills', 'matches'];
    return order.indexOf(this.state) > order.indexOf(step);
  }

  onNeedSubmitted(output: PostNeedOutput): void {
    this.loading = true;
    this.errorMsg = null;
    this.currentNeedText = output.text;
    this.currentCity = output.city;

    this.api.decompose({ text: output.text, city: output.city }).subscribe({
      next: (res) => {
        this.decomposeResult = res;
        this.loading = false;
        this.state = 'review-skills';
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err?.error?.message ?? 'Failed to analyze need. Please try again.';
      },
    });
  }

  onSkillsSubmitted(output: ReviewSkillsOutput): void {
    this.loading = true;
    this.errorMsg = null;

    this.api
      .getMatches({ city: output.city, skillIds: output.skillIds, radiusMiles: 40 })
      .subscribe({
        next: (res) => {
          this.matchResponse = res;
          this.loading = false;
          this.state = 'matches';
        },
        error: (err) => {
          this.loading = false;
          this.errorMsg = err?.error?.message ?? 'Failed to find matches. Please try again.';
        },
      });
  }

  openIntroDialog(req: DraftIntroRequest): void {
    this.pendingIntroRequest = req;
    this.introDialogVisible = true;
    this.introLoading = true;
    this.introError = null;
    this.introDialogData = null;

    this.api
      .draftIntro({
        needText: this.currentNeedText,
        city: this.currentCity,
        providerId: req.providerId,
        skillId: req.skillId,
      })
      .subscribe({
        next: (res) => {
          this.introLoading = false;
          this.introDialogData = {
            providerName: req.providerName,
            source: res.source,
            body: res.body,
          };
        },
        error: (err) => {
          this.introLoading = false;
          this.introError = err?.error?.message ?? 'Failed to draft intro.';
        },
      });
  }

  closeIntroDialog(): void {
    this.introDialogVisible = false;
    this.introDialogData = null;
    this.introError = null;
  }
}
