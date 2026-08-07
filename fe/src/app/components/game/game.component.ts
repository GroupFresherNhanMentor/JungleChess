import { Component, OnInit, OnDestroy, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Position } from '../../core/models/game.models';
import { GameRoomService, OnlineGameState } from '../../core/services/game-room.service';
import { GameRuleService } from '../../core/services/game-rule.service';
import { LobbyRSocketService } from '../../core/services/lobby-rsocket.service';
import { BoardComponent, MoveAnimation } from '../board/board.component';

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [CommonModule, BoardComponent],
  templateUrl: './game.component.html',
  styleUrl: './game.component.css',
})
export class GameComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameRoom = inject(GameRoomService);
  private readonly ruleService = inject(GameRuleService);
  private readonly lobby = inject(LobbyRSocketService);

  readonly state = toSignal(this.gameRoom.gameState$, {
    initialValue: null as OnlineGameState | null,
  });
  readonly selectedPos = signal<Position | null>(null);
  readonly validMoves = signal<Position[]>([]);
  readonly movingPiece = signal<MoveAnimation | null>(null);

  private roomId = '';
  private animCounter = 0;
  private lastAnimatedMoveNumber = -1;

  constructor() {
    // Trigger piece animation whenever a new move arrives
    effect(() => {
      const s = this.state();
      const lastMove = s?.lastMove;
      const moveNumber = s?.moveNumber ?? -1;

      if (!lastMove || moveNumber <= this.lastAnimatedMoveNumber) return;

      this.lastAnimatedMoveNumber = moveNumber;
      this.movingPiece.set({
        id: ++this.animCounter,
        piece: lastMove.piece,
        from: lastMove.from,
        to: lastMove.to,
        isCapture: !!lastMove.capturedPiece,
      });
    });
  }

  ngOnInit(): void {
    this.roomId = this.route.snapshot.params['roomId'];

    if (this.gameRoom.currentRoomId === this.roomId && this.gameRoom.gameState$.value) {
      // already connected from createRoom/joinRoom navigation
    } else {
      // Browser refresh or direct URL — rejoin
      this.gameRoom.rejoinRoom(this.roomId).subscribe({
        error: () => this.router.navigate(['/']),
      });
    }
  }

  ngOnDestroy(): void {}

  // ── Board interaction ─────────────────────────────────────────────────────

  onSquareClick(pos: Position): void {
    const state = this.state();
    if (!state || state.status !== 'PLAYING') return;
    if (state.currentTurn !== state.yourSide) return;

    const clickedPiece = this.ruleService.getPieceAt(state.pieces, pos.col, pos.row);
    const sel = this.selectedPos();

    if (!sel) {
      if (clickedPiece && clickedPiece.side === state.yourSide) {
        this.selectedPos.set(pos);
        this.validMoves.set(this.ruleService.getValidMoves(clickedPiece, state.pieces));
      }
      return;
    }

    // Same square → deselect
    if (sel.col === pos.col && sel.row === pos.row) {
      this.clearSelection();
      return;
    }

    // Another own piece → switch selection
    if (clickedPiece && clickedPiece.side === state.yourSide) {
      this.selectedPos.set(pos);
      this.validMoves.set(this.ruleService.getValidMoves(clickedPiece, state.pieces));
      return;
    }

    // Valid destination → send move
    if (this.validMoves().some(m => m.col === pos.col && m.row === pos.row)) {
      const from = sel;
      this.clearSelection();
      this.gameRoom.sendMove(this.roomId, from, pos).subscribe({
        error: err => console.error('[Game] move rejected:', err),
      });
      return;
    }

    this.clearSelection();
  }

  onMoveAnimationComplete(): void {
    this.movingPiece.set(null);
  }

  // ── Room actions ──────────────────────────────────────────────────────────

  startGame(): void {
    this.gameRoom.startGame(this.roomId);
  }

  leaveRoom(): void {
    this.gameRoom.leaveRoom(this.roomId);
    this.router.navigate(['/']);
  }

  requestRematch(): void {
    this.gameRoom.requestRematch(this.roomId);
  }

  // ── Template helpers ──────────────────────────────────────────────────────

  get isMyTurn(): boolean {
    const s = this.state();
    return !!s && s.currentTurn === s.yourSide;
  }

  get canStart(): boolean {
    const s = this.state();
    return !!s && s.isCreator && s.status === 'WAITING' && s.players.length >= 2;
  }

  get sideLabel(): string {
    return this.state()?.yourSide === 1 ? 'Red (PLAYER_1)' : 'Blue (PLAYER_2)';
  }

  get turnLabel(): string {
    const s = this.state();
    if (!s) return '';
    return s.currentTurn === 1 ? 'Red' : 'Blue';
  }

  get winnerLabel(): string {
    const s = this.state();
    if (!s || s.winner === undefined) return '';
    return s.winner === 1 ? 'Red wins!' : 'Blue wins!';
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private clearSelection(): void {
    this.selectedPos.set(null);
    this.validMoves.set([]);
  }
}
