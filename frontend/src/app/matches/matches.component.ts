import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchResponse, Tier, Structure, Availability } from '../models';

export interface DraftIntroRequest {
  providerId: string;
  skillId: string;
  providerName: string;
}

@Component({
  selector: 'app-matches',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="state-panel">
      <div class="back-row">
        <button class="btn-back" type="button" (click)="back.emit()">← Back</button>
      </div>

      <h2>Matches near {{ city }}</h2>

      <div *ngIf="!matchResponse || matchResponse.groups.length === 0" class="no-matches">
        No matches found for the selected skills and radius.
      </div>

      <div *ngFor="let group of matchResponse?.groups" class="skill-group">
        <div class="group-header">
          <h3>{{ group.label }}</h3>
          <span *ngIf="group.regulatedHint" class="regulated-badge" title="May require a state license">
            ⚑ Regulated trade
          </span>
        </div>

        <div *ngIf="group.regulatedHint" class="regulated-notice">
          This type of work may require a state license.
          <a href="https://www.michigan.gov/en/lara/bureau-list/cscl/online-services" target="_blank" rel="noopener">
            Check LARA lookup
          </a>
        </div>

        <div *ngIf="group.matches.length === 0" class="no-matches-skill">
          No providers found for this skill within the requested area.
        </div>

        <div *ngFor="let m of group.matches" class="match-card">
          <div class="card-top">
            <div class="card-name-row">
              <span class="provider-name">{{ m.name }}</span>
              <span class="structure-chip">{{ structureLabel(m.structure) }}</span>
              <span class="avail-chip" [class]="availClass(m.availability)">
                {{ availLabel(m.availability) }}
              </span>
            </div>
            <div class="card-location">
              {{ m.city }} · {{ m.distanceMiles | number:'1.1-1' }} mi
            </div>
          </div>

          <!-- Trust badges (green = licensed/registered, gray = self_declared, red = not_in_good_standing) -->
          <div class="badges-row" *ngIf="m.badges.length > 0">
            <span
              *ngFor="let badge of m.badges"
              class="badge"
              [class]="badgeClass(m.tier)"
            >{{ badge }}</span>
          </div>

          <!-- Warnings -->
          <div
            *ngFor="let w of m.warnings"
            class="warning-callout"
            role="alert"
          >
            <span class="warn-icon" aria-hidden="true">⚠</span>
            <span>{{ w }}</span>
            <a
              *ngIf="w.includes('state license')"
              href="https://www.michigan.gov/en/lara/bureau-list/cscl/online-services"
              target="_blank"
              rel="noopener"
              class="warn-link"
            >Check license</a>
          </div>

          <div class="explanation">
            <span class="explanation-text">{{ m.explanation }}</span>
            <span class="source-label">{{ sourceLabel(m.explanationSource) }}</span>
          </div>

          <div class="card-summary" *ngIf="m.summary">{{ m.summary }}</div>

          <div class="card-actions">
            <button
              class="btn-draft"
              type="button"
              (click)="draftIntro.emit({ providerId: m.providerId, skillId: group.skillId, providerName: m.name })"
            >
              Draft intro…
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .state-panel { max-width: 760px; margin: 0 auto; }
    .back-row { margin-bottom: 12px; }
    .btn-back { background: none; border: none; color: #3b82d4; cursor: pointer; font-size: 0.9rem; padding: 0; }
    .btn-back:hover { text-decoration: underline; }
    h2 { margin: 0 0 20px; font-size: 1.3rem; }
    .skill-group { margin-bottom: 32px; }
    .group-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
    h3 { margin: 0; font-size: 1.1rem; }
    .regulated-badge {
      background: #fef3c7; color: #92400e; border: 1px solid #fde68a;
      border-radius: 12px; padding: 2px 10px; font-size: 0.75rem; font-weight: 600;
    }
    .regulated-notice {
      background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px;
      padding: 8px 12px; font-size: 0.82rem; color: #78350f; margin-bottom: 10px;
    }
    .regulated-notice a { color: #92400e; }
    .no-matches, .no-matches-skill { color: #6b7280; font-style: italic; font-size: 0.9rem; margin: 8px 0; }
    .match-card {
      border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px 16px;
      margin-bottom: 12px; background: #fff;
    }
    .card-top { margin-bottom: 8px; }
    .card-name-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 2px; }
    .provider-name { font-weight: 700; font-size: 1rem; }
    .structure-chip {
      background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb;
      border-radius: 12px; padding: 1px 9px; font-size: 0.75rem;
    }
    .avail-chip { border-radius: 12px; padding: 1px 9px; font-size: 0.75rem; font-weight: 600; }
    .avail-this-month { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
    .avail-next-month { background: #e0f2fe; color: #075985; border: 1px solid #7dd3fc; }
    .avail-flexible { background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb; }
    .card-location { font-size: 0.82rem; color: #6b7280; }
    .badges-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
    .badge { border-radius: 12px; padding: 2px 10px; font-size: 0.78rem; font-weight: 600; }
    /* Color is NOT the only signal — badges also carry text */
    .badge-green { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
    .badge-gray  { background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb; }
    .badge-red   { background: #fee2e2; color: #991b1b; border: 1px solid #fca5a5; }
    .warning-callout {
      background: #fff7ed; border: 1px solid #fed7aa; border-radius: 6px;
      padding: 6px 10px; font-size: 0.82rem; color: #9a3412;
      display: flex; align-items: flex-start; gap: 6px; margin-bottom: 6px;
    }
    .warn-icon { flex-shrink: 0; }
    .warn-link { margin-left: 6px; color: #9a3412; font-weight: 600; }
    .explanation { margin: 8px 0 4px; font-size: 0.88rem; color: #374151; display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap; }
    .explanation-text { flex: 1; }
    .source-label {
      font-size: 0.72rem; color: #6b7280; white-space: nowrap;
      background: #f3f4f6; border-radius: 10px; padding: 1px 7px; align-self: center;
    }
    .card-summary { font-size: 0.82rem; color: #6b7280; margin-bottom: 8px; }
    .card-actions { display: flex; gap: 8px; }
    .btn-draft {
      background: #f7f8fa; border: 1px solid #d1d5db; border-radius: 6px;
      padding: 6px 14px; font-size: 0.85rem; cursor: pointer;
    }
    .btn-draft:hover { background: #e9ecef; }
  `],
})
export class MatchesComponent {
  @Input() city = '';
  @Input() matchResponse: MatchResponse | null = null;
  @Output() back = new EventEmitter<void>();
  @Output() draftIntro = new EventEmitter<DraftIntroRequest>();

  structureLabel(s: Structure): string {
    const map: Record<Structure, string> = {
      sole_prop: 'Sole prop',
      partnership: 'Partnership',
      llc: 'LLC',
      corp: 'Corp',
    };
    return map[s] ?? s;
  }

  availLabel(a: Availability): string {
    const map: Record<Availability, string> = {
      this_month: 'Available this month',
      next_month: 'Available next month',
      flexible: 'Flexible availability',
    };
    return map[a] ?? a;
  }

  availClass(a: Availability): string {
    return `avail-chip avail-${a.replace('_', '-')}`;
  }

  badgeClass(tier: Tier): string {
    if (tier === 'licensed_verified' || tier === 'registered') return 'badge badge-green';
    if (tier === 'not_in_good_standing') return 'badge badge-red';
    return 'badge badge-gray';
  }

  sourceLabel(src: 'granite' | 'template'): string {
    return src === 'granite' ? 'AI (Granite)' : 'Template';
  }
}
