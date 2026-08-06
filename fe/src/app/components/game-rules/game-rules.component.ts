import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-rules',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './game-rules.component.html',
  styleUrl: './game-rules.component.css'
})
export class GameRulesComponent {
  pieceRankList = [
    'elephant',
    'lion',
    'tiger',
    'leopard',
    'wolf',
    'dog',
    'cat',
    'rat'
  ];

  constructor(public loc: LocalizationService) {}

  getRuleRankLabel(): string {
    return this.loc.currentLanguage === 'vn'
      ? 'Thứ hạng sức mạnh (từ mạnh nhất đến yếu nhất):'
      : 'Rank Hierarchy (strongest to weakest):';
  }

  getAnimalName(type: string): string {
    return this.loc.translate(`animal_${type}`);
  }
}
