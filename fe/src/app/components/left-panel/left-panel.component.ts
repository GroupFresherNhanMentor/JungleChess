import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-left-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div id="left-panel">
      <!-- Captured by Red (Player 1) -->
      <div id="captured-by-player1" class="captured-panel">
        <h2>{{ loc.translate('capturedByRedLabel') }}</h2>
        <div class="pieces-container">
          <ng-container *ngIf="capturedByRed.length === 0">
            <span style="font-size: 0.85em; color: #777; width: 100%; text-align: center;">
              {{ loc.translate('capturedNone') }}
            </span>
          </ng-container>
          <div
            *ngFor="let p of capturedByRed"
            class="captured-piece player0"
            [title]="p.type"
          >
            <img
              [src]="'assets/images/head_no_background/' + p.type + '.png'"
              [alt]="p.type"
            />
          </div>
        </div>
      </div>

      <!-- Action buttons -->
      <div id="left-panel-actions">
        <div id="action-buttons-group" class="panel-button-group">
          <button id="reset-button" (click)="resetGame.emit()">
            {{ loc.translate('resetButton') }}
          </button>
          <button
            id="undo-button"
            [disabled]="!canUndo"
            (click)="undoMove.emit()"
          >
            {{ loc.translate('undoButton') }}
          </button>
        </div>
        <button id="randomize-board-button" (click)="randomizeBoard.emit()">
          {{ loc.translate('randomizeBoardButton') }}
        </button>
      </div>
    </div>
  `
})
export class LeftPanelComponent {
  @Input() capturedByRed: Piece[] = [];
  @Input() canUndo: boolean = false;

  @Output() resetGame = new EventEmitter<void>();
  @Output() undoMove = new EventEmitter<void>();
  @Output() randomizeBoard = new EventEmitter<void>();

  constructor(public loc: LocalizationService) {}
}
