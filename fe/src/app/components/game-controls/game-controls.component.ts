import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameMode, Language, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-controls',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './game-controls.component.html',
  styleUrl: './game-controls.component.css'
})
export class GameControlsComponent {
  @Input() currentLang: Language = 'vn';
  @Output() currentLangChange = new EventEmitter<Language>();

  @Input() gameMode: GameMode = 'PVA';
  @Output() gameModeChange = new EventEmitter<GameMode>();

  @Input() firstMoveSide: PieceSide = 0;
  @Output() firstMoveSideChange = new EventEmitter<PieceSide>();

  @Input() aiDepth: number = 9;
  @Output() aiDepthChange = new EventEmitter<number>();

  @Input() aiTimeLimit: number = 5000;
  @Output() aiTimeLimitChange = new EventEmitter<number>();

  @Input() actualAiDepth: number = 0;

  @Output() openRulesModal = new EventEmitter<void>();

  depthOptions = [4, 5, 6, 7, 8, 9, 10, 11];

  /** Whether the Settings modal is currently open. */
  settingsOpen = false;

  constructor(public loc: LocalizationService) {}

  openSettings(): void {
    this.settingsOpen = true;
  }

  closeSettings(): void {
    this.settingsOpen = false;
  }

  /** Close when clicking the dark backdrop (but not inside the card). */
  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('settings-backdrop')) {
      this.settingsOpen = false;
    }
  }

  onLangChange(lang: Language) {
    this.loc.setLanguage(lang);
    this.currentLangChange.emit(lang);
  }
}
