import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Move, Piece, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-right-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div id="right-panel">
      <!-- Captured by Blue (Player 0) -->
      <div id="captured-by-player0" class="captured-panel">
        <h2>{{ loc.translate('capturedByBlueLabel') }}</h2>
        <div class="pieces-container">
          <ng-container *ngIf="capturedByBlue.length === 0">
            <span style="font-size: 0.85em; color: #777; width: 100%; text-align: center;">
              {{ loc.translate('capturedNone') }}
            </span>
          </ng-container>
          <div
            *ngFor="let p of capturedByBlue"
            class="captured-piece player1"
            [title]="p.type"
          >
            <img
              [src]="'assets/images/head_no_background/' + p.type + '.png'"
              [alt]="p.type"
            />
          </div>
        </div>
      </div>

      <!-- Move History -->
      <div id="move-history">
        <h2>{{ loc.translate('moveHistoryLabel') }}</h2>
        <ol id="move-list">
          <li *ngFor="let m of moveHistory">
            <span
              class="piece-hist"
              [ngClass]="m.piece.side === 0 ? 'player0' : 'player1'"
            >
              <img
                [src]="'assets/images/head_no_background/' + m.piece.type + '.png'"
                [alt]="m.piece.type"
              />
            </span>
            <span>{{ formatMove(m) }}</span>
          </li>
        </ol>
      </div>

      <!-- Turn Indicator -->
      <p class="turn-info">
        <span>{{ loc.translate('turnLabel') }} </span>
        <span [style.color]="currentTurn === 0 ? 'blue' : 'red'">
          {{ getTurnText() }}
        </span>
      </p>
    </div>
  `
})
export class RightPanelComponent {
  @Input() capturedByBlue: Piece[] = [];
  @Input() moveHistory: Move[] = [];
  @Input() currentTurn: PieceSide = 0;

  constructor(public loc: LocalizationService) {}

  formatMove(move: Move): string {
    const colNames = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
    const fromStr = `${colNames[move.from.col]}${9 - move.from.row}`;
    const toStr = `${colNames[move.to.col]}${9 - move.to.row}`;
    const action = move.capturedPiece ? 'x' : '-';
    return `${fromStr} ${action} ${toStr}`;
  }

  getTurnText(): string {
    return this.currentTurn === 0
      ? this.loc.translate('playerStartsBlue')
      : this.loc.translate('playerStartsRed');
  }
}
