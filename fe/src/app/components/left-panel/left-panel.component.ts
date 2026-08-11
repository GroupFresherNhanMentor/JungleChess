import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-left-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './left-panel.component.html',
  styleUrl: './left-panel.component.css'
})
export class LeftPanelComponent {
  @Input() capturedByRed: Piece[] = [];
  @Input() canUndo: boolean = false;
  /** Online mode: hide local-only controls (undo / random board) the server doesn't support. */
  @Input() onlineMode: boolean = false;
  /** Display name of the PLAYER_1 (Red) player. */
  @Input() playerName = '';
  @Input() showUndo: boolean = false;

  @Output() resetGame = new EventEmitter<void>();
  @Output() undoMove = new EventEmitter<void>();
  @Output() randomizeBoard = new EventEmitter<void>();
  @Output() openRules = new EventEmitter<void>();
  @Output() backToLobby = new EventEmitter<void>();

  constructor(public loc: LocalizationService) {}
}
