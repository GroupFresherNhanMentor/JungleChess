import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Move, Piece, Position } from '../../core/models/game.models';
import { GameRuleService } from '../../core/services/game-rule.service';

@Component({
  selector: 'app-board',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div id="board-container">
      <!-- Col labels top -->
      <div id="col-labels-top" class="coord-labels col-labels">
        <span *ngFor="let colName of colNames">{{ colName }}</span>
      </div>

      <!-- Row labels left (9 to 1 top to bottom) -->
      <div id="row-labels-left" class="coord-labels row-labels">
        <span *ngFor="let rowName of rowNames">{{ rowName }}</span>
      </div>

      <!-- Main Board Grid -->
      <div id="board">
        <ng-container *ngFor="let r of rows">
          <ng-container *ngFor="let c of cols">
            <div
              class="square"
              [ngClass]="getSquareClass(c, r)"
              (click)="onSquareClick(c, r)"
            >
              <!-- Full water background image for rows 4, 5, 6 (index 3, 4, 5) -->
              <img
                *ngIf="isWaterVisual(c, r)"
                src="assets/tiles/water.gif"
                class="terrain-water"
                alt="Water"
              />

              <!-- Trap texture overlay -->
              <div *ngIf="isTrap(c, r)" class="trap-texture-container">
                <img
                  src="assets/tiles/trap.gif"
                  class="terrain-texture-img"
                  alt="Trap"
                />
              </div>

              <!-- Den texture overlay (Chest icon) -->
              <div *ngIf="isDen(c, r)" class="den-texture-container">
                <img
                  src="assets/decorations/chest.png"
                  class="terrain-texture-img"
                  alt="Den Chest"
                />
              </div>

              <!-- Wooden bridge overlay for cols A, D, G on river rows (Rows 4, 5, 6) -->
              <div *ngIf="isBridge(c, r)" class="den-texture-container" style="z-index: 3; opacity: 0.95;">
                <img
                  src="assets/decorations/ladder.png"
                  style="width: 100%; height: 100%; object-fit: cover;"
                  alt="Bridge"
                />
              </div>

              <!-- Flower/Grass decoration on land tiles -->
              <img
                *ngIf="getDecoration(c, r) as decoSrc"
                [src]="decoSrc"
                class="decoration"
                alt="Decoration"
              />

              <!-- Highlight overlay for last move -->
              <div
                class="highlight-overlay"
                [ngClass]="getLastMoveClass(c, r)"
              ></div>

              <!-- Action highlight overlay (possible move / capture move) -->
              <div
                class="action-highlight-overlay"
                [ngClass]="getActionHighlightClass(c, r)"
              ></div>

              <!-- Piece element -->
              <ng-container *ngIf="getPieceAt(c, r) as p">
                <div
                  class="piece"
                  [ngClass]="p.side === 0 ? 'player0' : 'player1'"
                >
                  <img
                    [src]="'assets/images/head_no_background/' + p.type + '.png'"
                    [alt]="p.type"
                  />
                </div>
              </ng-container>
            </div>
          </ng-container>
        </ng-container>
      </div>

      <!-- Row labels right (9 to 1 top to bottom) -->
      <div id="row-labels-right" class="coord-labels row-labels">
        <span *ngFor="let rowName of rowNames">{{ rowName }}</span>
      </div>

      <!-- Col labels bottom -->
      <div id="col-labels-bottom" class="coord-labels col-labels">
        <span *ngFor="let colName of colNames">{{ colName }}</span>
      </div>
    </div>
  `
})
export class BoardComponent {
  @Input() pieces: Piece[] = [];
  @Input() selectedPos: Position | null = null;
  @Input() validMoves: Position[] = [];
  @Input() lastMove: Move | null = null;

  @Output() squareClick = new EventEmitter<Position>();

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

  constructor(private ruleService: GameRuleService) {}

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

  isDen(col: number, row: number): boolean {
    const tile = this.ruleService.getTileInfo(col, row);
    return tile.type === 'den';
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
}
