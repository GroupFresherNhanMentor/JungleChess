import { Injectable } from '@angular/core';
import { PieceType } from '../models/game.models';

@Injectable({
  providedIn: 'root'
})
export class AudioService {
  private muted = false;

  private playSound(path: string): void {
    if (this.muted) return;
    try {
      const audio = new Audio(path);
      audio.volume = 0.6;
      audio.play().catch(() => {
        // Ignore autoplay restriction errors
      });
    } catch (e) {
      // Audio fallback
    }
  }

  public playMove(): void {
    this.playSound('assets/sounds/move.mp3');
  }

  public playSwim(): void {
    this.playSound('assets/sounds/sound_swim.mp3');
  }

  public playCapture(pieceType: PieceType): void {
    this.playSound(`assets/sounds/capture_${pieceType}.mp3`);
  }

  public playVictory(): void {
    this.playSound('assets/sounds/victory.mp3');
  }

  public playDefeat(): void {
    this.playSound('assets/sounds/defeat.mp3');
  }

  public playDraw(): void {
    this.playSound('assets/sounds/draw.mp3');
  }

  public playDenCapture(): void {
    this.playSound('assets/sounds/sound_wow.mp3');
  }
}
