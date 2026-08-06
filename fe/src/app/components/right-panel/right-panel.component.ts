import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Move, Piece, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-right-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './right-panel.component.html',
  styleUrl: './right-panel.component.css'
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
