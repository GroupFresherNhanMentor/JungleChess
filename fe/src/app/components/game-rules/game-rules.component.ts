import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-rules',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div id="game-rules">
      <h2>{{ loc.translate('rulesTitle') }}</h2>
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
  `
})
export class GameRulesComponent {
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
}
