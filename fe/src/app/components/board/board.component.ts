import {
  Component,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  Output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Move, Piece, Position } from '../../core/models/game.models';
import { GameRuleService } from '../../core/services/game-rule.service';

export interface MoveAnimation {
  /** Monotonic id so the setter always re-fires, even on identical moves. */
  id: number;
  piece: Piece;
  from: Position;
  to: Position;
  isCapture: boolean;
}

interface GhostState {
  id: number;
  piece: Piece;
  /** Start pixel position (top-left of the 50px piece). */
  fx: number;
  fy: number;
  /** End pixel position. */
  tx: number;
  ty: number;
  isCapture: boolean;
}

const CELL = 68;
const PIECE = 60;
const OFFSET = (CELL - PIECE) / 2; // 4px
const SLIDE_MS = 320;
const POUNCE_DELAY_MS = 300;
const POUNCE_MS = 400;

@Component({
  selector: 'app-board',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './board.component.html',
  styleUrl: './board.component.css'
})
export class BoardComponent implements OnDestroy {
  @Input() pieces: Piece[] = [];
  @Input() selectedPos: Position | null = null;
  @Input() validMoves: Position[] = [];
  @Input() lastMove: Move | null = null;

  private _movingPiece: MoveAnimation | null = null;
  private animSeq = 0;
  private animTimer: ReturnType<typeof setTimeout> | null = null;

  ghostState: GhostState | null = null;

  @Input()
  set movingPiece(v: MoveAnimation | null) {
    this._movingPiece = v;
    this.handleMovingPiece(v);
  }
  get movingPiece(): MoveAnimation | null {
    return this._movingPiece;
  }

  @Output() squareClick = new EventEmitter<Position>();
  @Output() moveAnimationComplete = new EventEmitter<void>();

  cols = [0, 1, 2, 3, 4, 5, 6];
  rows = [0, 1, 2, 3, 4, 5, 6, 7, 8];

  colNames = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
  rowNames = ['9', '8', '7', '6', '5', '4', '3', '2', '1'];

  // Map of fixed flower decorations matching reference board screenshot
  private flowerMap: Record<string, string> = {
    '0-1': 'assets/decorations/decorations_1.png', // F8 (row 1, col 5)
    '0-7': 'assets/decorations/decorations_1.png', // A8 (row 1, col 0)
    '1-1': 'assets/decorations/decorations_2.png', // B7 (row 2, col 1)
    '3-2': 'assets/decorations/decorations_1.png', // D3 (row 6, col 3)
    '5-2': 'assets/decorations/decorations_2.png', // F3 (row 6, col 5)
    '1-7': 'assets/decorations/decorations_2.png', // B2 (row 7, col 1)
    '3-7': 'assets/decorations/decorations_1.png', // D2 (row 7, col 3)
    '5-7': 'assets/decorations/decorations_2.png'  // F2 (row 7, col 5)
  };

  constructor(
    private ruleService: GameRuleService,
    private ngZone: NgZone
  ) {}

  ngOnDestroy(): void {
    if (this.animTimer) {
      clearTimeout(this.animTimer);
    }
  }

  getPieceAt(col: number, row: number): Piece | undefined {
    return this.pieces.find(
      (p) => p.position.col === col && p.position.row === row
    );
  }

  isWaterVisual(col: number, row: number): boolean {
    // Full water background across rows 4, 5, 6 (index 3, 4, 5)
    return row >= 3 && row <= 5;
  }

  isTrap(col: number, row: number): boolean {
    const tile = this.ruleService.getTileInfo(col, row);
    return tile.type === 'trap';
  }

  isBridge(col: number, row: number): boolean {
    // Wooden bridge on cols A, D, G (0, 3, 6) for rows 4, 5, 6 (index 3, 4, 5)
    return row >= 3 && row <= 5 && (col === 0 || col === 3 || col === 6);
  }

  getDecoration(col: number, row: number): string | null {
    const key = `${col}-${row}`;
    return this.flowerMap[key] || null;
  }

  getSquareClass(col: number, row: number): Record<string, boolean> {
    const tile = this.ruleService.getTileInfo(col, row);
    const isSelected =
      this.selectedPos !== null &&
      this.selectedPos.col === col &&
      this.selectedPos.row === row;

    return {
      'terrain-land': tile.type === 'land' && !this.isWaterVisual(col, row),
      'terrain-water': this.isWaterVisual(col, row),
      'terrain-trap': tile.type === 'trap',
      'terrain-player0-den': tile.type === 'den' && tile.side === 0,
      'terrain-player1-den': tile.type === 'den' && tile.side === 1,
      selected: isSelected
    };
  }

  /** Hide the real piece while its ghost is animating the same piece id. */
  isPieceAnimating(pieceId: string): boolean {
    return (
      this.ghostState !== null && this.ghostState.piece.id === pieceId
    );
  }

  getLastMoveClass(col: number, row: number): string {
    if (!this.lastMove) return '';
    const side = this.lastMove.piece.side;
    const pStr = side === 0 ? 'p0' : 'p1';

    if (
      this.lastMove.from.col === col &&
      this.lastMove.from.row === row
    ) {
      return `last-move-start-${pStr}`;
    }
    if (
      this.lastMove.to.col === col &&
      this.lastMove.to.row === row
    ) {
      return `last-move-end-${pStr}`;
    }
    return '';
  }

  getActionHighlightClass(col: number, row: number): string {
    const isValid = this.validMoves.some(
      (m) => m.col === col && m.row === row
    );
    if (!isValid) return '';

    const occupant = this.getPieceAt(col, row);
    if (occupant) {
      return 'capture-move';
    }
    return 'possible-move';
  }

  onSquareClick(col: number, row: number) {
    this.squareClick.emit({ col, row });
  }

  private handleMovingPiece(v: MoveAnimation | null): void {
    if (this.animTimer) {
      clearTimeout(this.animTimer);
      this.animTimer = null;
    }

    if (!v) {
      this.ghostState = null;
      return;
    }

    // Ignore re-set of the same animation (e.g. CD re-run) — keyed on the
    // monotonic animation id so consecutive moves by the same piece restart.
    if (this.ghostState && this.ghostState.id === v.id) {
      return;
    }

    const fromX = v.from.col * CELL + OFFSET;
    const fromY = v.from.row * CELL + OFFSET;
    const toX = v.to.col * CELL + OFFSET;
    const toY = v.to.row * CELL + OFFSET;

    this.ghostState = {
      id: v.id,
      piece: v.piece,
      fx: fromX,
      fy: fromY,
      tx: toX,
      ty: toY,
      isCapture: v.isCapture
    };

    const total =
      v.isCapture
        ? SLIDE_MS + POUNCE_DELAY_MS + POUNCE_MS
        : SLIDE_MS + 50;

    this.animTimer = setTimeout(() => {
      this.animTimer = null;
      this.ngZone.run(() => {
        this.moveAnimationComplete.emit();
      });
    }, total);
  }
}
