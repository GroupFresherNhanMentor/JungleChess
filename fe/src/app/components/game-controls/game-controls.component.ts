import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameMode, Language, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-controls',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div id="game-controls">
      <!-- Language Selection -->
      <div>
        <label id="lang-select-label" for="lang-select">
          {{ loc.translate('languageLabel') }}
        </label>
        <select
          id="lang-select"
          [ngModel]="currentLang"
          (ngModelChange)="onLangChange($event)"
        >
          <option value="en">English</option>
          <option value="vn">Tiếng Việt</option>
        </select>
      </div>

      <!-- Game Mode -->
      <div>
        <label for="game-mode">{{ loc.translate('gameModeLabel') }}</label>
        <select
          id="game-mode"
          [ngModel]="gameMode"
          (ngModelChange)="gameModeChange.emit($event)"
        >
          <option value="PVA">{{ loc.translate('modePVA') }}</option>
          <option value="PVP">{{ loc.translate('modePVP') }}</option>
        </select>
      </div>

      <!-- Player Starts -->
      <div>
        <label for="player-starts-select">
          {{ loc.translate('playerStartsLabel') }}
        </label>
        <select
          id="player-starts-select"
          [ngModel]="firstMoveSide"
          (ngModelChange)="firstMoveSideChange.emit(+$event === 1 ? 1 : 0)"
        >
          <option [value]="0">{{ loc.translate('playerStartsBlue') }}</option>
          <option [value]="1">{{ loc.translate('playerStartsRed') }}</option>
        </select>
      </div>

      <!-- Rules Modal Trigger Button -->
      <div>
        <button
          type="button"
          (click)="openRulesModal.emit()"
          style="padding: 6px 14px; font-size: 14px; font-weight: 600; background-color: #3498db; color: #fff; border: none; border-radius: 4px; cursor: pointer; transition: background-color 0.15s ease;"
        >
          📖 {{ loc.translate('rulesButton') }}
        </button>
      </div>

      <!-- AI Controls -->
      <div id="ai-controls" *ngIf="gameMode === 'PVA'">
        <div>
          <label for="difficulty">
            {{ loc.translate('aiDifficultyLabel') }}
          </label>
          <select
            id="difficulty"
            [ngModel]="aiDepth"
            (ngModelChange)="aiDepthChange.emit(+$event)"
          >
            <option *ngFor="let d of depthOptions" [value]="d">{{ d }}</option>
          </select>
        </div>
        <div>
          <label for="time-limit">
            {{ loc.translate('aiTimeLimitLabel') }}
          </label>
          <input
            type="number"
            id="time-limit"
            [ngModel]="aiTimeLimit"
            (ngModelChange)="aiTimeLimitChange.emit(+$event)"
            min="100"
            step="100"
          />
        </div>
        <div class="ai-actual-depth-group">
          <span class="ai-info">{{ loc.translate('aiDepthInfo') }}</span>
          <span id="ai-depth-achieved">{{ actualAiDepth }}</span>
        </div>
      </div>
    </div>
  `
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

  constructor(public loc: LocalizationService) {}

  onLangChange(lang: Language) {
    this.loc.setLanguage(lang);
    this.currentLangChange.emit(lang);
  }
}
