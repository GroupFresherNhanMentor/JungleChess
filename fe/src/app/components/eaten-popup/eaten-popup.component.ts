import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PieceType } from '../../core/models/game.models';

/** One eaten animal that pops out of the bottom-left corner with a meme "?". */
export interface EatenItem {
  id: number;
  type: PieceType;
}

@Component({
  selector: 'app-eaten-popup',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './eaten-popup.component.html',
  styleUrl: './eaten-popup.component.css'
})
export class EatenPopupComponent {
  @Input() items: EatenItem[] = [];

  imageFor(type: PieceType): string {
    return `assets/images/head_no_background/${type}.png`;
  }
}
