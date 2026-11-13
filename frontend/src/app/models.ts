// API response/request models matching docs/ARCHITECTURE.md

export interface City {
  name: string;
}

export interface Skill {
  id: string;
  label: string;
  group: string;
  regulatedHint: boolean;
}

export interface MetaResponse {
  cities: City[];
  skills: Skill[];
  demoData: boolean;
}

// POST /api/needs/decompose
export interface DecomposeRequest {
  text: string;
  city: string;
}

export interface DecomposedSkill {
  skillId: string;
  label: string;
  reason: string;
  regulatedHint: boolean;
}

export interface DecomposeResponse {
  summary: string;
  skills: DecomposedSkill[];
  clarifyingQuestion: string | null;
  source: 'granite' | 'fallback';
}

// POST /api/matches
export interface MatchRequest {
  city: string;
  skillIds: string[];
  radiusMiles: number;
}

export type Tier = 'licensed_verified' | 'registered' | 'self_declared' | 'not_in_good_standing';
export type Availability = 'this_month' | 'next_month' | 'flexible';
export type Structure = 'sole_prop' | 'partnership' | 'llc' | 'corp';

export interface ProviderMatch {
  providerId: string;
  name: string;
  structure: Structure;
  city: string;
  distanceMiles: number;
  tier: Tier;
  score: number;
  badges: string[];
  warnings: string[];
  summary: string;
  availability: Availability;
  explanation: string;
  explanationSource: 'granite' | 'template';
}

export interface SkillGroup {
  skillId: string;
  label: string;
  regulatedHint: boolean;
  matches: ProviderMatch[];
}

export interface MatchResponse {
  groups: SkillGroup[];
}

// POST /api/intros/draft
export interface IntroRequest {
  needText: string;
  city: string;
  providerId: string;
  skillId: string;
}

export interface IntroResponse {
  body: string;
  source: 'granite' | 'template';
}

// Golden needs (sample buttons)
export interface GoldenNeed {
  id: string;
  persona: string;
  city: string;
  text: string;
}
