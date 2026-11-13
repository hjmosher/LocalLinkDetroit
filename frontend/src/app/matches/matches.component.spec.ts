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

  const tiers: { tier: Tier; expectedClass: string; label: string }[] = [
    { tier: 'licensed_verified', expectedClass: 'badge-green', label: 'licensed_verified → green' },
    { tier: 'registered',        expectedClass: 'badge-green', label: 'registered → green' },
    { tier: 'self_declared',     expectedClass: 'badge-gray',  label: 'self_declared → gray' },
    { tier: 'not_in_good_standing', expectedClass: 'badge-red', label: 'not_in_good_standing → red' },
  ];

  tiers.forEach(({ tier, expectedClass, label }) => {
    it(`badgeClass: ${label}`, () => {
      const cls = component.badgeClass(tier);
      expect(cls).toContain(expectedClass);
    });
  });

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
    expect(compiled.querySelector('h3')?.textContent).toContain('Electrical');
    expect(compiled.querySelector('.provider-name')?.textContent).toContain('Test Electric LLC');
    // Badge must be green (text signal too)
    const badge = compiled.querySelector('.badge') as HTMLElement;
    expect(badge?.classList).toContain('badge-green');
    // regulated notice must appear
    expect(compiled.querySelector('.regulated-notice')).toBeTruthy();
  });

  it('sourceLabel returns correct display strings', () => {
    expect(component.sourceLabel('granite')).toBe('AI (Granite)');
    expect(component.sourceLabel('template')).toBe('Template');
  });

  it('structureLabel maps all structure values', () => {
    expect(component.structureLabel('sole_prop')).toBe('Sole prop');
    expect(component.structureLabel('llc')).toBe('LLC');
    expect(component.structureLabel('corp')).toBe('Corp');
    expect(component.structureLabel('partnership')).toBe('Partnership');
  });
});
