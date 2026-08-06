import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../core/models/game.models';

@Component({
  selector: 'app-win-chance-bar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './win-chance-bar.component.html',
  styleUrl: './win-chance-bar.component.css'
})
export class WinChanceBarComponent implements OnChanges {
  @Input() pieces: Piece[] = [];

  bluePercentage: number = 50;
  titleText: string = '50% / 50%';

  ngOnChanges(): void {
    this.calculateWinChance();
  }

  private calculateWinChance(): void {
    let blueScore = 0;
    let redScore = 0;

    for (const p of this.pieces) {
      if (p.side === 0) {
        blueScore += p.rank;
      } else {
        redScore += p.rank;
      }
    }

    const total = blueScore + redScore;
    if (total === 0) {
      this.bluePercentage = 50;
    } else {
      this.bluePercentage = Math.round((blueScore / total) * 100);
    }

    this.titleText = `${this.bluePercentage}% / ${100 - this.bluePercentage}%`;
  }
}
