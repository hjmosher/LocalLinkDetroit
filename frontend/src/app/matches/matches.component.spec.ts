import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatchesComponent } from './matches.component';
import { MatchResponse, Tier } from '../models';

describe('MatchesComponent – badge rendering', () => {
  let fixture: ComponentFixture<MatchesComponent>;
  let component: MatchesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatchesComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MatchesComponent);
    component = fixture.componentInstance;
  });

  // ── badgeClass (backward-compat method used in older tests) ───────────────

  const tiers: { tier: Tier; expectedClass: string; label: string }[] = [
    { tier: 'licensed_verified',   expectedClass: 'badge-green', label: 'licensed_verified → green' },
    { tier: 'registered',          expectedClass: 'badge-green', label: 'registered → green' },
    { tier: 'self_declared',       expectedClass: 'badge-gray',  label: 'self_declared → gray' },
    { tier: 'not_in_good_standing', expectedClass: 'badge-red',  label: 'not_in_good_standing → red' },
  ];

  tiers.forEach(({ tier, expectedClass, label }) => {
    it(`badgeClass: ${label}`, () => {
      const cls = component.badgeClass(tier);
      expect(cls).toContain(expectedClass);
    });
  });

  // ── badgeNgClass (per-badge color by text) ────────────────────────────────

  it('badgeNgClass: "Licensed electrician (simulated)" → badge-green', () => {
    const cls = component.badgeNgClass('Licensed electrician (simulated)');
    expect(cls['badge-green']).toBe(true);
    expect(cls['badge-gray']).toBe(false);
    expect(cls['badge-red']).toBe(false);
  });

  it('badgeNgClass: "Registered with LARA, in good standing (simulated)" → badge-green', () => {
    const cls = component.badgeNgClass('Registered with LARA, in good standing (simulated)');
    expect(cls['badge-green']).toBe(true);
  });

  it('badgeNgClass: "Self-declared" → badge-gray', () => {
    const cls = component.badgeNgClass('Self-declared');
    expect(cls['badge-gray']).toBe(true);
    expect(cls['badge-green']).toBe(false);
    expect(cls['badge-red']).toBe(false);
  });

  it('badgeNgClass: "Not in good standing (simulated)" → badge-red', () => {
    const cls = component.badgeNgClass('Not in good standing (simulated)');
    expect(cls['badge-red']).toBe(true);
    expect(cls['badge-green']).toBe(false);
  });

  // ── badgeIcon (symbol prefix, non-color signal) ───────────────────────────

  it('badgeIcon: licensed → ✓', () => {
    expect(component.badgeIcon('Licensed electrician (simulated)')).toBe('✓');
  });

  it('badgeIcon: registered → ✓', () => {
    expect(component.badgeIcon('Registered with LARA (simulated)')).toBe('✓');
  });

  it('badgeIcon: not in good standing → ✗', () => {
    expect(component.badgeIcon('Not in good standing (simulated)')).toBe('✗');
  });

  it('badgeIcon: self-declared → ◎', () => {
    expect(component.badgeIcon('Some unverified claim')).toBe('◎');
  });

  // ── badgeAriaLabel ────────────────────────────────────────────────────────

  it('badgeAriaLabel: green badge has "Verified:" prefix', () => {
    const label = component.badgeAriaLabel('Licensed electrician (simulated)');
    expect(label).toContain('Verified:');
  });

  it('badgeAriaLabel: red badge has "Warning:" prefix', () => {
    const label = component.badgeAriaLabel('Not in good standing (simulated)');
    expect(label).toContain('Warning:');
  });

  it('badgeAriaLabel: gray badge has "Unverified:" prefix', () => {
    const label = component.badgeAriaLabel('Self-declared service provider');
    expect(label).toContain('Unverified:');
  });

  // ── sourceLabel and sourceLabelClass ─────────────────────────────────────

  it('sourceLabel returns correct display strings', () => {
    expect(component.sourceLabel('granite')).toBe('AI (Granite)');
    expect(component.sourceLabel('template')).toBe('Template');
  });

  it('sourceLabelClass: granite → source-ai class', () => {
    expect(component.sourceLabelClass('granite')).toContain('source-ai');
  });

  it('sourceLabelClass: template → source-template class', () => {
    expect(component.sourceLabelClass('template')).toContain('source-template');
  });

  // ── structureLabel ────────────────────────────────────────────────────────

  it('structureLabel maps all structure values', () => {
    expect(component.structureLabel('sole_prop')).toBe('Sole prop');
    expect(component.structureLabel('llc')).toBe('LLC');
    expect(component.structureLabel('corp')).toBe('Corp');
    expect(component.structureLabel('partnership')).toBe('Partnership');
  });

  // ── DOM rendering ─────────────────────────────────────────────────────────

  it('renders match cards grouped by skill', () => {
    const mockResponse: MatchResponse = {
      groups: [
        {
          skillId: 'electrical',
          label: 'Electrical',
          regulatedHint: true,
          matches: [
            {
              providerId: 'demo-001',
              name: 'Test Electric LLC',
              structure: 'llc',
              city: 'Royal Oak',
              distanceMiles: 3.2,
              tier: 'licensed_verified',
              score: 0.95,
              badges: ['Licensed electrician (simulated)'],
              warnings: [],
              summary: 'Full-service electrical contractor.',
              availability: 'this_month',
              explanation: 'Nearby licensed electrician available this month.',
              explanationSource: 'template',
            },
          ],
        },
      ],
    };

    component.city = 'Royal Oak';
    component.matchResponse = mockResponse;
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;

    // Group heading
    expect(compiled.querySelector('h3')?.textContent).toContain('Electrical');

    // Provider name
    expect(compiled.querySelector('.provider-name')?.textContent).toContain('Test Electric LLC');

    // Badge: green (via ngClass, not [class])
    const badge = compiled.querySelector('.badge') as HTMLElement;
    expect(badge?.classList).toContain('badge-green');

    // Badge icon (non-color signal)
    const icon = badge?.querySelector('.badge-icon');
    expect(icon?.textContent?.trim()).toBe('✓');

    // Badge aria-label carries "Verified:" prefix
    expect(badge?.getAttribute('aria-label')).toContain('Verified:');

    // Regulated notice must appear
    expect(compiled.querySelector('.regulated-notice')).toBeTruthy();
    // Regulated notice has role="note"
    expect(compiled.querySelector('.regulated-notice')?.getAttribute('role')).toBe('note');

    // Source label present
    const sourceLabel = compiled.querySelector('.source-label');
    expect(sourceLabel?.textContent?.trim()).toBe('Template');
    // Source label class for template
    expect(sourceLabel?.classList).toContain('source-template');

    // Draft intro button accessible label
    const draftBtn = compiled.querySelector('.btn-draft') as HTMLButtonElement;
    expect(draftBtn?.getAttribute('aria-label')).toContain('Test Electric LLC');
  });

  it('renders warning callout with role=alert and ⚠ icon', () => {
    const mockResponse: MatchResponse = {
      groups: [
        {
          skillId: 'plumbing',
          label: 'Plumbing',
          regulatedHint: false,
          matches: [
            {
              providerId: 'demo-002',
              name: 'Bob Plumbing LLC',
              structure: 'llc',
              city: 'Detroit',
              distanceMiles: 5.0,
              tier: 'not_in_good_standing',
              score: 0.10,
              badges: [],
              warnings: ['Not in good standing per LARA (simulated)'],
              summary: '',
              availability: 'flexible',
              explanation: 'Listed but not in good standing.',
              explanationSource: 'template',
            },
          ],
        },
      ],
    };

    component.city = 'Detroit';
    component.matchResponse = mockResponse;
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    const warning = compiled.querySelector('.warning-callout');
    expect(warning).toBeTruthy();
    expect(warning?.getAttribute('role')).toBe('alert');
    expect(warning?.querySelector('.warn-icon')?.textContent?.trim()).toBe('⚠');
  });

  it('shows license lookup link in warning when warning mentions "state license"', () => {
    const mockResponse: MatchResponse = {
      groups: [
        {
          skillId: 'electrical',
          label: 'Electrical',
          regulatedHint: true,
          matches: [
            {
              providerId: 'demo-003',
              name: 'Spark Co',
              structure: 'sole_prop',
              city: 'Southfield',
              distanceMiles: 10.0,
              tier: 'self_declared',
              score: 0.5,
              badges: [],
              warnings: ['This type of work may require a state license'],
              summary: '',
              availability: 'next_month',
              explanation: 'Self-declared; check license status.',
              explanationSource: 'template',
            },
          ],
        },
      ],
    };

    component.city = 'Southfield';
    component.matchResponse = mockResponse;
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    const link = compiled.querySelector('.warn-link') as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(link.href).toContain('lara');
  });

  it('shows AI (Granite) source-label with source-ai class', () => {
    const mockResponse: MatchResponse = {
      groups: [
        {
          skillId: 'concrete',
          label: 'Concrete',
          regulatedHint: false,
          matches: [
            {
              providerId: 'demo-004',
              name: 'Metro Concrete Inc',
              structure: 'corp',
              city: 'Warren',
              distanceMiles: 8.5,
              tier: 'registered',
              score: 0.87,
              badges: ['Registered with LARA (simulated)'],
              warnings: [],
              summary: '',
              availability: 'this_month',
              explanation: 'Registered concrete contractor with verified LARA status.',
              explanationSource: 'granite',
            },
          ],
        },
      ],
    };

    component.city = 'Warren';
    component.matchResponse = mockResponse;
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    const sourceLabel = compiled.querySelector('.source-label') as HTMLElement;
    expect(sourceLabel?.classList).toContain('source-ai');
    expect(sourceLabel?.textContent?.trim()).toBe('AI (Granite)');
  });
});
