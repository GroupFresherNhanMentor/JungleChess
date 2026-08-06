import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-rules-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="onBackdropClick($event)">
      <div class="modal-card">
        <!-- Modal Header -->
        <div class="modal-header">
          <h2>{{ loc.translate('rulesTitle') }}</h2>
          <button class="modal-close-btn" (click)="close.emit()">&times;</button>
        </div>

        <!-- Modal Body -->
        <div class="modal-body">
          <ul id="rules-list">
            <li>{{ loc.translate('ruleMovement') }}</li>
            <li>
              <strong>{{ getRuleRankLabel() }}</strong>
              <div class="rank-hierarchy" style="margin-top: 5px;">
                <ng-container *ngFor="let p of pieceRankList; let last = last">
                  <span class="rank-piece">
                    <img
                      [src]="'assets/images/head_no_background/' + p + '.png'"
                      [alt]="p"
                      class="rank-piece-icon"
                    />
                    {{ getAnimalName(p) }}
                  </span>
                  <span *ngIf="!last" class="rank-separator">&gt;</span>
                </ng-container>
              </div>
            </li>
            <li>{{ loc.translate('ruleCapture') }}</li>
            <li [innerHTML]="loc.translate('ruleRatElephant')"></li>
            <li [innerHTML]="loc.translate('ruleElephantRat')"></li>
            <li [innerHTML]="loc.translate('ruleTraps')"></li>
            <li [innerHTML]="loc.translate('ruleWater')"></li>
            <li [innerHTML]="loc.translate('ruleJump')"></li>
            <li [innerHTML]="loc.translate('ruleDens')"></li>
            <li>{{ loc.translate('ruleWinCondition') }}</li>
          </ul>
        </div>

        <!-- Modal Footer -->
        <div class="modal-footer">
          <button class="modal-action-btn" (click)="close.emit()">
            {{ loc.translate('closeButton') }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .modal-backdrop {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background-color: rgba(0, 0, 0, 0.65);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 1000;
        animation: fadeIn 0.2s ease-out;
      }

      .modal-card {
        background: #ffffff;
        border-radius: 12px;
        width: 90%;
        max-width: 650px;
        max-height: 85vh;
        display: flex;
        flex-direction: column;
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
        overflow: hidden;
        animation: slideUp 0.2s ease-out;
      }

      .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 20px;
        background-color: #f8f9fa;
        border-bottom: 1px solid #e9ecef;
      }

      .modal-header h2 {
        margin: 0;
        font-size: 1.25em;
        color: #2c3e50;
      }

      .modal-close-btn {
        background: transparent;
        border: none;
        font-size: 1.8em;
        line-height: 1;
        color: #888;
        cursor: pointer;
        transition: color 0.15s ease;
      }

      .modal-close-btn:hover {
        color: #e74c3c;
      }

      .modal-body {
        padding: 20px;
        overflow-y: auto;
        font-size: 0.92em;
        line-height: 1.6;
        color: #444;
      }

      .modal-body ul {
        margin: 0;
        padding-left: 20px;
      }

      .modal-body li {
        margin-bottom: 10px;
      }

      .modal-footer {
        padding: 12px 20px;
        background-color: #f8f9fa;
        border-top: 1px solid #e9ecef;
        display: flex;
        justify-content: flex-end;
      }

      .modal-action-btn {
        background-color: #3498db;
        color: #ffffff;
        border: none;
        padding: 8px 24px;
        border-radius: 6px;
        font-size: 14px;
        font-weight: 600;
        cursor: pointer;
        transition: background-color 0.15s ease;
      }

      .modal-action-btn:hover {
        background-color: #2980b9;
      }

      @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes slideUp {
        from { transform: translateY(20px); }
        to { transform: translateY(0); }
      }
    `
  ]
})
export class GameRulesModalComponent {
  @Input() isOpen: boolean = false;
  @Output() close = new EventEmitter<void>();

  pieceRankList = [
    'elephant',
    'lion',
    'tiger',
    'leopard',
    'dog',
    'wolf',
    'cat',
    'rat'
  ];

  constructor(public loc: LocalizationService) { }

  getRuleRankLabel(): string {
    return this.loc.currentLanguage === 'vn'
      ? 'Thứ hạng sức mạnh (từ mạnh nhất đến yếu nhất):'
      : 'Rank Hierarchy (strongest to weakest):';
  }

  getAnimalName(type: string): string {
    return this.loc.translate(`animal_${type}`);
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close.emit();
    }
  }
}
