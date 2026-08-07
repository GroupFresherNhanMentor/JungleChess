import {
  Component,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

const START = 50;
const TARGET = 100;
const HUMAN_TAP = 3;
const AI_TAP = 2;
const AI_INTERVAL_MS = 135;

@Component({
  selector: 'app-battle-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './battle-overlay.component.html',
  styleUrl: './battle-overlay.component.css'
})
export class BattleOverlayComponent implements OnInit, OnDestroy {
  @Input() attacker: Piece | null = null;
  @Input() defender: Piece | null = null;
  @Input() attackerIsAI = false;
  @Input() defenderIsAI = false;

  @Output() battleResult = new EventEmitter<PieceSide>();

  blueProgress = START;
  redProgress = START;
  private aiIntervals: ReturnType<typeof setInterval>[] = [];
  private finished = false;

  constructor(public loc: LocalizationService) {}

  ngOnInit(): void {
    // Auto-mash for AI sides
    if (this.attackerIsAI) {
      this.aiIntervals.push(
        setInterval(() => this.mash(this.attacker!.side), AI_INTERVAL_MS)
      );
    }
    if (this.defenderIsAI) {
      this.aiIntervals.push(
        setInterval(() => this.mash(this.defender!.side), AI_INTERVAL_MS)
      );
    }
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  /** Blue (side 0) = A, Red (side 1) = L (same machine). */
  @HostListener('document:keydown', ['$event'])
  onKeyDown(e: KeyboardEvent): void {
    if (this.finished) return;
    if (e.repeat) return; // ignore held-key auto-repeat

    const k = e.key.toLowerCase();
    if (k === 'a') {
      e.preventDefault();
      this.mash(0);
    } else if (k === 'l') {
      e.preventDefault();
      this.mash(1);
    }
  }

  private mash(side: PieceSide): void {
    if (this.finished) return;

    if (side === 0) {
      this.blueProgress = Math.min(TARGET, this.blueProgress + HUMAN_TAP);
    } else {
      this.redProgress = Math.min(TARGET, this.redProgress + HUMAN_TAP);
    }

    if (this.blueProgress >= TARGET) {
      this.resolve(0);
    } else if (this.redProgress >= TARGET) {
      this.resolve(1);
    }
  }

  private resolve(winningSide: PieceSide): void {
    this.finished = true;
    this.cleanup();
    this.battleResult.emit(winningSide);
  }

  private cleanup(): void {
    for (const id of this.aiIntervals) {
      clearInterval(id);
    }
    this.aiIntervals = [];
  }

  pieceName(p: Piece | null): string {
    return p ? this.loc.translate(`animal_${p.type}`) : '';
  }
}
