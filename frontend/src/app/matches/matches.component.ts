import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchResponse, Tier, Structure, Availability } from '../models';

export interface DraftIntroRequest {
  providerId: string;
  skillId: string;
  providerName: string;
}

/** Per-badge trust level, inferred from badge text so each badge gets its own color,
 *  not just the provider-level tier. This handles mixed-badge rows correctly and
 *  ensures color is never the only signal (all badges carry text + a shape prefix).
 *  Note: "not in good standing" is checked first; "unverified" must NOT trigger green. */
function badgeTierFromText(badge: string): 'green' | 'gray' | 'red' {
  const lower = badge.toLowerCase();
  if (lower.includes('not in good standing')) return 'red';
  // "unverified" contains "verified" as a substring — exclude it explicitly.
  const hasVerified = lower.includes('verified') && !lower.includes('unverified');
  if (lower.includes('licensed') || lower.includes('registered') || hasVerified) return 'green';
  return 'gray';
}

@Component({
  selector: 'app-matches',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="state-panel">
      <div class="back-row">
        <button class="btn-back" type="button" (click)="back.emit()">← Back to skills</button>
      </div>

      <h2>Matches near {{ city }}</h2>

      <div *ngIf="!matchResponse || matchResponse.groups.length === 0" class="no-matches">
        No matches found for the selected skills and radius.
      </div>

      <div *ngFor="let group of matchResponse?.groups" class="skill-group">
        <div class="group-header">
          <h3>{{ group.label }}</h3>
          <span
            *ngIf="group.regulatedHint"
            class="regulated-badge"
            aria-label="Regulated trade: may require a state license"
          >
            ⚑ Regulated trade
          </span>
        </div>

        <!-- Group-level regulated notice — applies to every provider in this skill -->
        <div
          *ngIf="group.regulatedHint"
          class="regulated-notice"
          role="note"
          aria-label="Regulated work notice"
        >
          <span class="notice-icon" aria-hidden="true">ℹ</span>
          <span>
            This type of work may require a state license.
            <a
              href="https://www.michigan.gov/en/lara/bureau-list/cscl/online-services"
              target="_blank"
              rel="noopener"
              aria-label="Check LARA license lookup (opens in new tab)"
            >Check LARA lookup →</a>
          </span>
        </div>

        <div *ngIf="group.matches.length === 0" class="no-matches-skill">
          No providers found for this skill within the requested area.
        </div>

        <div
          *ngFor="let m of group.matches"
          class="match-card"
        >
          <!-- ── Card header row ── -->
          <div class="card-top">
            <div class="card-name-row">
              <span class="provider-name">{{ m.name }}</span>
              <span class="structure-chip">{{ structureLabel(m.structure) }}</span>
              <span
                class="avail-chip"
                [class]="availClass(m.availability)"
                [attr.aria-label]="'Availability: ' + availLabel(m.availability)"
              >
                {{ availLabel(m.availability) }}
              </span>
            </div>
            <div class="card-location" aria-label="Location and distance">
              {{ m.city }} · {{ m.distanceMiles | number:'1.1-1' }} mi away
            </div>
          </div>

          <!-- ── Trust badge pills ──
               Color is NEVER the only signal:
               green  = licensed or registered  → ✓ prefix
               gray   = self-declared           → ◎ prefix
               red    = not in good standing    → ✗ prefix
               Badges come from code, not from the model. -->
          <div
            class="badges-row"
            *ngIf="m.badges.length > 0"
            aria-label="Trust badges"
          >
            <span
              *ngFor="let badge of m.badges"
              class="badge"
              [ngClass]="badgeNgClass(badge)"
              [attr.aria-label]="badgeAriaLabel(badge)"
            >
              <span class="badge-icon" aria-hidden="true">{{ badgeIcon(badge) }}</span>
              {{ badge }}
            </span>
          </div>

          <!-- Self-declared notice when no badges exist and tier is self_declared -->
          <div
            *ngIf="m.badges.length === 0 && m.tier === 'self_declared'"
            class="badges-row"
            aria-label="Trust status"
          >
            <span class="badge badge-gray" aria-label="Self-declared: not independently verified">
              <span class="badge-icon" aria-hidden="true">◎</span>
              Self-declared
            </span>
          </div>

          <!-- ── Warning callouts ── -->
          <div
            *ngFor="let w of m.warnings; let i = index"
            class="warning-callout"
            role="alert"
            [attr.aria-label]="'Warning: ' + w"
          >
            <span class="warn-icon" aria-hidden="true">⚠</span>
            <span class="warn-text">{{ w }}</span>
            <a
              *ngIf="w.includes('state license')"
              href="https://www.michigan.gov/en/lara/bureau-list/cscl/online-services"
              target="_blank"
              rel="noopener"
              class="warn-link"
              aria-label="Check state license lookup (opens in new tab)"
            >Check license →</a>
          </div>

          <!-- ── Explanation + source label ── -->
          <div class="explanation">
            <span class="explanation-text">{{ m.explanation }}</span>
            <span
              class="source-label"
              [ngClass]="sourceLabelClass(m.explanationSource)"
              [attr.aria-label]="'Explanation source: ' + sourceLabel(m.explanationSource)"
            >{{ sourceLabel(m.explanationSource) }}</span>
          </div>

          <!-- ── Provider summary (background info) ── -->
          <div class="card-summary" *ngIf="m.summary">{{ m.summary }}</div>

          <!-- ── Draft intro button ── -->
          <div class="card-actions">
            <button
              class="btn-draft"
              type="button"
              [attr.aria-label]="'Draft intro message to ' + m.name"
              (click)="draftIntro.emit({ providerId: m.providerId, skillId: group.skillId, providerName: m.name })"
            >
              ✉ Draft intro…
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .state-panel { max-width: 760px; margin: 0 auto; }

    /* ── Navigation ── */
    .back-row { margin-bottom: 12px; }
    .btn-back {
      background: none; border: none; color: #3b82d4; cursor: pointer;
      font-size: 0.9rem; padding: 4px 0; border-radius: 3px;
    }
    .btn-back:hover { text-decoration: underline; }
    .btn-back:focus-visible { outline: 2px solid #3b82d4; outline-offset: 2px; }

    h2 { margin: 0 0 20px; font-size: 1.3rem; }

    /* ── Skill group ── */
    .skill-group { margin-bottom: 32px; }
    .group-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
    h3 { margin: 0; font-size: 1.1rem; }

    .regulated-badge {
      background: #fef3c7; color: #92400e; border: 1px solid #fde68a;
      border-radius: 12px; padding: 2px 10px; font-size: 0.75rem; font-weight: 600;
    }

    .regulated-notice {
      background: #fffbeb; border: 1px solid #fde68a; border-left: 4px solid #f59e0b;
      border-radius: 6px; padding: 8px 12px; font-size: 0.82rem; color: #78350f;
      margin-bottom: 10px; display: flex; align-items: flex-start; gap: 8px;
    }
    .regulated-notice .notice-icon { flex-shrink: 0; font-size: 0.95rem; }
    .regulated-notice a { color: #92400e; font-weight: 600; }
    .regulated-notice a:focus-visible { outline: 2px solid #92400e; outline-offset: 1px; border-radius: 2px; }

    .no-matches, .no-matches-skill {
      color: #6b7280; font-style: italic; font-size: 0.9rem; margin: 8px 0;
    }

    /* ── Match card ── */
    .match-card {
      border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px 16px;
      margin-bottom: 12px; background: #fff;
    }
    .card-top { margin-bottom: 8px; }
    .card-name-row {
      display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 2px;
    }
    .provider-name { font-weight: 700; font-size: 1rem; }

    .structure-chip {
      background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb;
      border-radius: 12px; padding: 1px 9px; font-size: 0.75rem;
    }

    .avail-chip { border-radius: 12px; padding: 1px 9px; font-size: 0.75rem; font-weight: 600; }
    .avail-this-month  { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
    .avail-next-month  { background: #e0f2fe; color: #075985; border: 1px solid #7dd3fc; }
    .avail-flexible    { background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb; }

    .card-location { font-size: 0.82rem; color: #6b7280; }

    /* ── Trust badge pills ──
       Color is NOT the only signal: each badge carries text AND a prefix symbol.
       green (✓) = licensed_verified or registered
       gray  (◎) = self_declared
       red   (✗) = not_in_good_standing                                          */
    .badges-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
    .badge {
      display: inline-flex; align-items: center; gap: 4px;
      border-radius: 12px; padding: 2px 10px; font-size: 0.78rem; font-weight: 600;
      border: 1px solid transparent;
    }
    .badge-icon { font-size: 0.75rem; flex-shrink: 0; }

    .badge-green { background: #d1fae5; color: #065f46; border-color: #6ee7b7; }
    .badge-gray  { background: #f3f4f6; color: #374151; border-color: #e5e7eb; }
    .badge-red   { background: #fee2e2; color: #991b1b; border-color: #fca5a5; }

    /* ── Warning callouts ── */
    .warning-callout {
      background: #fff7ed; border: 1px solid #fed7aa; border-left: 4px solid #f97316;
      border-radius: 6px; padding: 7px 11px; font-size: 0.82rem; color: #9a3412;
      display: flex; align-items: flex-start; gap: 7px; margin-bottom: 6px;
    }
    .warn-icon { flex-shrink: 0; font-size: 0.95rem; }
    .warn-text { flex: 1; }
    .warn-link {
      flex-shrink: 0; color: #9a3412; font-weight: 600; white-space: nowrap;
      text-decoration: underline;
    }
    .warn-link:focus-visible { outline: 2px solid #9a3412; outline-offset: 1px; border-radius: 2px; }

    /* ── Explanation + source label ── */
    .explanation {
      margin: 8px 0 4px; font-size: 0.88rem; color: #374151;
      display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap;
    }
    .explanation-text { flex: 1; }
    .source-label {
      font-size: 0.72rem; white-space: nowrap; border-radius: 10px;
      padding: 2px 8px; align-self: center; font-weight: 500; border: 1px solid transparent;
    }
    /* granite = tinted blue; template = neutral gray */
    .source-ai       { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }
    .source-template { background: #f3f4f6; color: #6b7280; border-color: #e5e7eb; }

    /* ── Card summary ── */
    .card-summary { font-size: 0.82rem; color: #6b7280; margin-bottom: 8px; }

    /* ── Card actions ── */
    .card-actions { display: flex; gap: 8px; margin-top: 2px; }
    .btn-draft {
      background: #f7f8fa; border: 1px solid #d1d5db; border-radius: 6px;
      padding: 6px 14px; font-size: 0.85rem; cursor: pointer; font-weight: 500;
    }
    .btn-draft:hover { background: #e9ecef; }
    .btn-draft:focus-visible { outline: 2px solid #3b82d4; outline-offset: 2px; }
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
    return `avail-chip avail-${a.replace(/_/g, '-')}`;
  }

  /** Used in tests — kept for backward compat. Returns the CSS modifier class string. */
  badgeClass(tier: Tier): string {
    if (tier === 'licensed_verified' || tier === 'registered') return 'badge badge-green';
    if (tier === 'not_in_good_standing') return 'badge badge-red';
    return 'badge badge-gray';
  }

  /** Per-badge ngClass object — each badge gets its own color by text, not just tier. */
  badgeNgClass(badge: string): Record<string, boolean> {
    const level = badgeTierFromText(badge);
    return {
      'badge-green': level === 'green',
      'badge-gray':  level === 'gray',
      'badge-red':   level === 'red',
    };
  }

  /** Symbol prefix so color is NEVER the only trust signal. */
  badgeIcon(badge: string): string {
    const level = badgeTierFromText(badge);
    if (level === 'green') return '✓';
    if (level === 'red')   return '✗';
    return '◎';
  }

  badgeAriaLabel(badge: string): string {
    const level = badgeTierFromText(badge);
    const prefix =
      level === 'green' ? 'Verified: ' :
      level === 'red'   ? 'Warning: ' :
                          'Unverified: ';
    return prefix + badge;
  }

  sourceLabel(src: 'granite' | 'template'): string {
    return src === 'granite' ? 'AI (Granite)' : 'Template';
  }

  sourceLabelClass(src: 'granite' | 'template'): string {
    return src === 'granite' ? 'source-label source-ai' : 'source-label source-template';
  }
}
