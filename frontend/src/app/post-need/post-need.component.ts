import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GoldenNeed, City } from '../models';

export interface PostNeedOutput {
  text: string;
  city: string;
}

@Component({
  selector: 'app-post-need',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="state-panel">
      <h2>Describe your need</h2>
      <p class="hint">Tell us what trades or services you're looking for. Be as specific as you like.</p>

      <div class="form-group">
        <label for="city-select">City</label>
        <select id="city-select" [(ngModel)]="selectedCity">
          <option *ngFor="let c of cities" [value]="c.name">{{ c.name }}</option>
        </select>
      </div>

      <div class="form-group">
        <label for="need-text">Your need</label>
        <textarea
          id="need-text"
          [(ngModel)]="needText"
          rows="5"
          placeholder="e.g. I'm a general contractor and need a licensed electrician for a deck lighting project in Royal Oak…"
        ></textarea>
      </div>

      <div class="sample-needs">
        <span class="sample-label">Try a sample:</span>
        <div class="sample-buttons">
          <button
            *ngFor="let n of sampleNeeds"
            class="btn-sample"
            type="button"
            [title]="n.text"
            (click)="loadSample(n)"
          >{{ n.persona }}</button>
        </div>
      </div>

      <div class="actions">
        <button
          class="btn-primary"
          type="button"
          [disabled]="!needText.trim() || !selectedCity || loading"
          (click)="submit()"
        >
          <span *ngIf="loading">Analyzing…</span>
          <span *ngIf="!loading">Find help →</span>
        </button>
      </div>

      <div *ngIf="error" class="error-msg" role="alert">{{ error }}</div>
    </div>
  `,
  styles: [`
    .state-panel { max-width: 680px; margin: 0 auto; }
    h2 { margin: 0 0 4px; font-size: 1.3rem; }
    .hint { margin: 0 0 20px; color: #57606a; font-size: 0.9rem; }
    .form-group { margin-bottom: 14px; display: flex; flex-direction: column; gap: 4px; }
    label { font-weight: 600; font-size: 0.85rem; }
    select, textarea {
      border: 1px solid #d1d5db; border-radius: 6px; padding: 8px 10px;
      font-size: 0.95rem; font-family: inherit; background: #fff;
    }
    select { width: 220px; }
    textarea { resize: vertical; width: 100%; box-sizing: border-box; }
    .sample-needs { margin-bottom: 20px; }
    .sample-label { font-size: 0.8rem; color: #57606a; display: block; margin-bottom: 6px; }
    .sample-buttons { display: flex; flex-wrap: wrap; gap: 6px; }
    .btn-sample {
      border: 1px solid #d1d5db; background: #f7f8fa; border-radius: 20px;
      padding: 4px 12px; font-size: 0.8rem; cursor: pointer; color: #1f2328;
    }
    .btn-sample:hover { background: #e9ecef; border-color: #adb5bd; }
    .actions { display: flex; align-items: center; gap: 12px; }
    .btn-primary {
      background: #3b82d4; color: #fff; border: none; border-radius: 6px;
      padding: 10px 22px; font-size: 0.95rem; font-weight: 600; cursor: pointer;
    }
    .btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
    .btn-primary:not(:disabled):hover { background: #2563eb; }
    .error-msg { color: #b91c1c; font-size: 0.85rem; margin-top: 8px; }
  `],
})
export class PostNeedComponent {
  @Input() cities: City[] = [];
  @Input() loading = false;
  @Input() error: string | null = null;
  @Output() submitted = new EventEmitter<PostNeedOutput>();

  readonly sampleNeeds: GoldenNeed[] = [
    {
      id: 'need-1',
      persona: 'GC – deck build (Royal Oak)',
      city: 'Royal Oak',
      text: "I'm a general contractor building a 400 sq ft deck with a retaining wall for a homeowner in Royal Oak. I need someone to pour concrete footings, someone to frame the deck, and an electrician for deck lighting. Work starts in about three weeks.",
    },
    {
      id: 'need-2',
      persona: 'Landscaper – back-office (Ann Arbor)',
      city: 'Ann Arbor',
      text: "I run a landscaping company with six crew members in Ann Arbor. I'm buried in weekly invoicing, chasing late payments, scheduling crews and doing payroll. I'd rather spend my time on the work itself.",
    },
    {
      id: 'need-3',
      persona: 'Cafe – permits & web (Detroit)',
      city: 'Detroit',
      text: "I'm opening a cafe in Corktown in about two months. I need help with the permit and licensing paperwork, the paperwork for hiring my first employees, and a simple website.",
    },
    {
      id: 'need-4',
      persona: 'Roofer sub needed (Livonia)',
      city: 'Livonia',
      text: 'My crew is booked solid and I need a roofing subcontractor to re-roof a detached garage in Livonia within the next few weeks.',
    },
    {
      id: 'need-5',
      persona: 'Electrician for garage (Ann Arbor)',
      city: 'Ann Arbor',
      text: "I'm finishing a garage conversion in Ann Arbor and need an electrician to run new circuits and install exterior lighting this month.",
    },
  ];

  needText = '';
  selectedCity = 'Royal Oak';

  loadSample(n: GoldenNeed): void {
    this.needText = n.text;
    this.selectedCity = n.city;
  }

  submit(): void {
    if (!this.needText.trim() || !this.selectedCity) return;
    this.submitted.emit({ text: this.needText.trim(), city: this.selectedCity });
  }
}
