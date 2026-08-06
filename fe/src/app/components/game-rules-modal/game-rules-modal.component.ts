import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-game-rules-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './game-rules-modal.component.html',
  styleUrl: './game-rules-modal.component.css'
})
export class GameRulesModalComponent {
  @Input() isOpen: boolean = false;
  @Output() close = new EventEmitter<void>();

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

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close.emit();
    }
  }
}
