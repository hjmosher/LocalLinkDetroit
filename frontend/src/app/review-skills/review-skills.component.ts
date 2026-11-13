import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DecomposedSkill, Skill } from '../models';

export interface ReviewSkillsOutput {
  skillIds: string[];
  city: string;
}

@Component({
  selector: 'app-review-skills',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="state-panel">
      <div class="back-row">
        <button class="btn-back" type="button" (click)="back.emit()">← Back</button>
      </div>

      <h2>Review skills found</h2>
      <p class="summary-text" *ngIf="summary">{{ summary }}</p>
      <p class="hint">Remove skills you don't need, or add missing ones.</p>

      <div class="chips-area" role="group" aria-label="Selected skills">
        <span
          *ngFor="let s of selectedSkills"
          class="chip"
          [class.regulated]="s.regulatedHint"
        >
          <span class="chip-label">{{ s.label }}</span>
          <span class="regulated-mark" *ngIf="s.regulatedHint" title="May require a state license">⚑</span>
          <button
            class="chip-remove"
            type="button"
            [attr.aria-label]="'Remove ' + s.label"
            (click)="removeSkill(s.skillId)"
          >×</button>
        </span>

        <span *ngIf="selectedSkills.length === 0" class="no-skills">No skills selected.</span>
      </div>

      <div class="add-skill-row">
        <label for="add-skill-select" class="add-label">Add a skill:</label>
        <select id="add-skill-select" [(ngModel)]="pickerSkillId">
          <option value="">— choose —</option>
          <option *ngFor="let s of availableToAdd" [value]="s.id">{{ s.label }}</option>
        </select>
        <button
          class="btn-add"
          type="button"
          [disabled]="!pickerSkillId"
          (click)="addSkill()"
        >Add</button>
      </div>

      <div class="actions">
        <button
          class="btn-primary"
          type="button"
          [disabled]="selectedSkills.length === 0 || loading"
          (click)="submit()"
        >
          <span *ngIf="loading">Finding matches…</span>
          <span *ngIf="!loading">Show matches →</span>
        </button>
      </div>

      <div *ngIf="error" class="error-msg" role="alert">{{ error }}</div>
    </div>
  `,
  styles: [`
    .state-panel { max-width: 680px; margin: 0 auto; }
    .back-row { margin-bottom: 12px; }
    .btn-back { background: none; border: none; color: #3b82d4; cursor: pointer; font-size: 0.9rem; padding: 0; }
    .btn-back:hover { text-decoration: underline; }
    h2 { margin: 0 0 4px; font-size: 1.3rem; }
    .summary-text { margin: 0 0 8px; color: #1f2328; font-style: italic; }
    .hint { margin: 0 0 16px; color: #57606a; font-size: 0.9rem; }
    .chips-area {
      display: flex; flex-wrap: wrap; gap: 8px;
      min-height: 42px; margin-bottom: 16px;
      padding: 10px; background: #f7f8fa; border-radius: 8px; border: 1px solid #e5e7eb;
    }
    .chip {
      display: inline-flex; align-items: center; gap: 4px;
      background: #dbeafe; border: 1px solid #93c5fd; border-radius: 20px;
      padding: 4px 10px; font-size: 0.85rem;
    }
    .chip.regulated { background: #fef9c3; border-color: #fde047; }
    .chip-label { color: #1e3a5f; }
    .chip.regulated .chip-label { color: #713f12; }
    .regulated-mark { font-size: 0.75rem; color: #92400e; }
    .chip-remove {
      background: none; border: none; cursor: pointer; font-size: 1rem; line-height: 1;
      color: #6b7280; padding: 0 2px;
    }
    .chip-remove:hover { color: #b91c1c; }
    .no-skills { color: #9ca3af; font-size: 0.85rem; }
    .add-skill-row { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }
    .add-label { font-size: 0.85rem; font-weight: 600; }
    select {
      border: 1px solid #d1d5db; border-radius: 6px; padding: 6px 10px;
      font-size: 0.9rem; font-family: inherit; background: #fff;
    }
    .btn-add {
      background: #f7f8fa; border: 1px solid #d1d5db; border-radius: 6px;
      padding: 6px 14px; font-size: 0.9rem; cursor: pointer;
    }
    .btn-add:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn-add:not(:disabled):hover { background: #e9ecef; }
    .actions { display: flex; gap: 12px; }
    .btn-primary {
      background: #3b82d4; color: #fff; border: none; border-radius: 6px;
      padding: 10px 22px; font-size: 0.95rem; font-weight: 600; cursor: pointer;
    }
    .btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
    .btn-primary:not(:disabled):hover { background: #2563eb; }
    .error-msg { color: #b91c1c; font-size: 0.85rem; margin-top: 8px; }
  `],
})
export class ReviewSkillsComponent {
  @Input() summary = '';
  @Input() city = '';
  @Input() allSkills: Skill[] = [];
  @Input() loading = false;
  @Input() error: string | null = null;
  @Output() back = new EventEmitter<void>();
  @Output() submitted = new EventEmitter<ReviewSkillsOutput>();

  private _decomposedSkills: DecomposedSkill[] = [];
  selectedSkills: DecomposedSkill[] = [];

  @Input() set decomposedSkills(val: DecomposedSkill[]) {
    this._decomposedSkills = val;
    this.selectedSkills = [...val];
  }
  get decomposedSkills(): DecomposedSkill[] {
    return this._decomposedSkills;
  }

  pickerSkillId = '';

  get availableToAdd(): Skill[] {
    const selectedIds = new Set(this.selectedSkills.map((s) => s.skillId));
    return this.allSkills.filter((s) => !selectedIds.has(s.id));
  }

  removeSkill(skillId: string): void {
    this.selectedSkills = this.selectedSkills.filter((s) => s.skillId !== skillId);
  }

  addSkill(): void {
    if (!this.pickerSkillId) return;
    const skill = this.allSkills.find((s) => s.id === this.pickerSkillId);
    if (!skill) return;
    const already = this.selectedSkills.some((s) => s.skillId === skill.id);
    if (already) {
      this.pickerSkillId = '';
      return;
    }
    this.selectedSkills = [
      ...this.selectedSkills,
      { skillId: skill.id, label: skill.label, reason: 'Added manually', regulatedHint: skill.regulatedHint },
    ];
    this.pickerSkillId = '';
  }

  submit(): void {
    if (this.selectedSkills.length === 0) return;
    this.submitted.emit({ skillIds: this.selectedSkills.map((s) => s.skillId), city: this.city });
  }
}
