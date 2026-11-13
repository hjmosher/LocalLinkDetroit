import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface IntroDialogData {
  providerName: string;
  source: 'granite' | 'template';
  body: string;
}

@Component({
  selector: 'app-intro-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- backdrop -->
    <div class="backdrop" (click)="close.emit()" aria-hidden="true"></div>

    <!-- dialog -->
    <div
      class="dialog"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="'Draft intro for ' + data?.providerName"
    >
      <div class="dialog-header">
        <h2 id="dialog-title">Draft intro – {{ data?.providerName }}</h2>
        <button class="btn-close" type="button" aria-label="Close dialog" (click)="close.emit()">×</button>
      </div>

      <div *ngIf="loading" class="loading-msg">Generating draft…</div>
      <div *ngIf="error" class="error-msg" role="alert">{{ error }}</div>

      <ng-container *ngIf="!loading && data">
        <div class="source-row">
          <span class="source-label">{{ sourceLabel(data.source) }}</span>
          <span class="source-note">Draft only — never sent automatically.</span>
        </div>

        <textarea
          class="intro-text"
          [(ngModel)]="data.body"
          rows="8"
          aria-label="Editable intro text"
        ></textarea>

        <div class="dialog-actions">
          <button class="btn-copy" type="button" (click)="copy()">
            {{ copied ? 'Copied!' : 'Copy to clipboard' }}
          </button>
          <button class="btn-cancel" type="button" (click)="close.emit()">Close</button>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    .backdrop {
      position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 100;
    }
    .dialog {
      position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
      background: #fff; border-radius: 10px; box-shadow: 0 8px 32px rgba(0,0,0,0.18);
      padding: 24px 28px; width: 580px; max-width: 94vw; z-index: 101;
      display: flex; flex-direction: column; gap: 12px;
    }
    .dialog-header { display: flex; justify-content: space-between; align-items: center; }
    h2 { margin: 0; font-size: 1.1rem; }
    .btn-close {
      background: none; border: none; font-size: 1.5rem; cursor: pointer;
      color: #6b7280; line-height: 1; padding: 0 4px;
    }
    .btn-close:hover { color: #111; }
    .loading-msg { color: #57606a; font-size: 0.9rem; }
    .error-msg { color: #b91c1c; font-size: 0.85rem; }
    .source-row { display: flex; align-items: center; gap: 10px; }
    .source-label {
      background: #f3f4f6; color: #374151; border-radius: 10px;
      padding: 2px 10px; font-size: 0.75rem;
    }
    .source-note { font-size: 0.75rem; color: #9ca3af; }
    .intro-text {
      width: 100%; box-sizing: border-box; border: 1px solid #d1d5db;
      border-radius: 6px; padding: 10px; font-size: 0.9rem; font-family: inherit;
      resize: vertical; min-height: 120px;
    }
    .dialog-actions { display: flex; gap: 10px; justify-content: flex-end; }
    .btn-copy {
      background: #3b82d4; color: #fff; border: none; border-radius: 6px;
      padding: 8px 18px; font-size: 0.9rem; font-weight: 600; cursor: pointer;
    }
    .btn-copy:hover { background: #2563eb; }
    .btn-cancel {
      background: #f7f8fa; border: 1px solid #d1d5db; border-radius: 6px;
      padding: 8px 18px; font-size: 0.9rem; cursor: pointer;
    }
    .btn-cancel:hover { background: #e9ecef; }
  `],
})
export class IntroDialogComponent {
  @Input() data: IntroDialogData | null = null;
  @Input() loading = false;
  @Input() error: string | null = null;
  @Output() close = new EventEmitter<void>();

  copied = false;

  sourceLabel(src: 'granite' | 'template'): string {
    return src === 'granite' ? 'AI (Granite)' : 'Template';
  }

  copy(): void {
    if (!this.data?.body) return;
    navigator.clipboard.writeText(this.data.body).then(() => {
      this.copied = true;
      setTimeout(() => (this.copied = false), 2000);
    });
  }
}
