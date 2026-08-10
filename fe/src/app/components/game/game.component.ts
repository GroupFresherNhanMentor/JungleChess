import { Component, OnInit, OnDestroy, inject, signal, effect, HostBinding, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Move, Piece, PieceSide, PieceType, Position } from '../../core/models/game.models';
import { GameRoomService, OnlineGameState } from '../../core/services/game-room.service';
import { GameRuleService } from '../../core/services/game-rule.service';
import { AudioService } from '../../core/services/audio.service';
import { LocalizationService } from '../../core/services/localization.service';
import { AuthService } from '../../core/services/auth.service';
import { BoardComponent, MoveAnimation } from '../board/board.component';
import { LeftPanelComponent } from '../left-panel/left-panel.component';
import { RightPanelComponent } from '../right-panel/right-panel.component';
import { WinChanceBarComponent } from '../win-chance-bar/win-chance-bar.component';
import { GameRulesModalComponent } from '../game-rules-modal/game-rules-modal.component';
import { EatenPopupComponent, EatenItem } from '../eaten-popup/eaten-popup.component';

/** A single firework particle; dx/dy precomputed in px (no CSS trig). */
interface FireworkParticle {
  dx: number;
  dy: number;
  size: number;
  delay: number;
  color: string;
}

/** One radially-symmetric explosion anchored at a viewport %. */
interface FireworkBurst {
  id: number;
  x: number; // vw %
  y: number; // vh %
  hue: 'blue' | 'red' | 'gold';
  particles: FireworkParticle[];
}

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [
    CommonModule,
    BoardComponent,
    LeftPanelComponent,
    RightPanelComponent,
    WinChanceBarComponent,
    GameRulesModalComponent,
    EatenPopupComponent,
  ],
  templateUrl: './game.component.html',
  styleUrl: './game.component.css',
})
export class GameComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameRoom = inject(GameRoomService);
  private readonly ruleService = inject(GameRuleService);
  private readonly audioService = inject(AudioService);
  readonly loc = inject(LocalizationService);
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly ngZone = inject(NgZone);

  readonly state = toSignal(this.gameRoom.gameState$, {
    initialValue: null as OnlineGameState | null,
  });
  readonly selectedPos = signal<Position | null>(null);
  readonly validMoves = signal<Position[]>([]);
  readonly movingPiece = signal<MoveAnimation | null>(null);

  // ── Rich-screen state ─────────────────────────────────────────────────────
  readonly capturedByRed = signal<Piece[]>([]);    // Blue pieces captured by Red
  readonly capturedByBlue = signal<Piece[]>([]);   // Red pieces captured by Blue
  readonly moveHistory = signal<Move[]>([]);
  readonly eatenPopups = signal<EatenItem[]>([]);
  readonly banner = signal<{ title: string; subtitle?: string; side?: PieceSide } | null>(null);
  readonly screenShaking = signal(false);
  readonly isRulesModalOpen = signal(false);
  readonly statusMessage = signal('');

  // Victory fireworks + defeat clown state
  fireworkBursts: FireworkBurst[] = [];
  fireworkSide: PieceSide | null = null;
  fireworkTitle: string = '';
  fireworkSubtitle: string = '';
  showClown: boolean = false;

  private roomId = '';
  private animCounter = 0;
  private lastAnimatedMoveNumber = -1;
  private fireworkSeq = 0;
  private eatenSeq = 0;
  private lastAnnouncedResult = '';

  private bannerTimer: ReturnType<typeof setTimeout> | null = null;
  private shakeTimers: ReturnType<typeof setTimeout>[] = [];
  private eatenTimers: ReturnType<typeof setTimeout>[] = [];
  private fireworkTimer: ReturnType<typeof setTimeout> | null = null;
  private clownTimer: ReturnType<typeof setTimeout> | null = null;

  @HostBinding('class.screen-shake') get shakeActive(): boolean {
    return this.screenShaking();
  }

  private static readonly FIREWORK_COLORS: Record<'blue' | 'red' | 'gold', string[]> = {
    blue: ['#3b82f6', '#60a5fa', '#93c5fd', '#ffffff'],
    red:  ['#ef4444', '#f97316', '#fbbf24', '#ffd97a'],
    gold: ['#ffd97a', '#f0c268', '#ff9f43', '#ffffff'],
  };

  constructor() {
    // Trigger piece animation + capture effects whenever a new move arrives.
    effect(() => {
      const s = this.state();
      const lastMove = s?.lastMove;
      const moveNumber = s?.moveNumber ?? -1;

      // New room / rejoin → reset accumulation (moveNumber 0, lastMove null).
      if (!s || !lastMove) {
        if (moveNumber === 0) {
          this.resetAccumulatedState();
        }
        return;
      }
      if (moveNumber <= this.lastAnimatedMoveNumber) return;
      this.lastAnimatedMoveNumber = moveNumber;

      // Board ghost animation
      this.movingPiece.set({
        id: ++this.animCounter,
        piece: lastMove.piece,
        from: lastMove.from,
        to: lastMove.to,
        isCapture: !!lastMove.capturedPiece,
      });

      // Capture bookkeeping + effects. Winner side = the moved piece's side.
      if (lastMove.capturedPiece) {
        const loser = lastMove.capturedPiece;
        if (lastMove.piece.side === 0) {
          this.capturedByBlue.set([...this.capturedByBlue(), loser]);
        } else {
          this.capturedByRed.set([...this.capturedByRed(), loser]);
        }
        this.audioService.playCapture(loser.type);
        this.shakeScreen();
        this.launchEatenPopup(loser.type);
      } else {
        this.audioService.playMove();
      }

      this.moveHistory.set([...this.moveHistory(), lastMove]);
    });

    // Announce game result (victory / defeat) once per room.
    effect(() => {
      const s = this.state();
      if (!s || s.status !== 'ENDED' || s.winner === undefined) return;
      const key = s.roomId;
      if (key === this.lastAnnouncedResult) return;
      this.lastAnnouncedResult = key;

      if (s.winner === s.yourSide) {
        this.audioService.playVictory();
        this.launchVictoryFireworks(s.winner);
      } else {
        this.audioService.playDefeat();
        this.playLossClown();
      }
      this.showBanner(this.winnerLabel);
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

  ngOnDestroy(): void {
    this.clearAllTimers();
  }

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

  onBackToLobby(): void {
    this.gameRoom.leaveRoom(this.roomId);
    this.router.navigate(['/lobby']);
  }

  requestRematch(): void {
    this.gameRoom.requestRematch(this.roomId);
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      error: () => this.router.navigate(['/login']),
    });
  }

  toggleLanguage(): void {
    const next = this.loc.currentLanguage === 'vn' ? 'en' : 'vn';
    this.loc.setLanguage(next);
  }

  // ── Rules modal ───────────────────────────────────────────────────────────

  openRules(): void {
    this.isRulesModalOpen.set(true);
  }

  closeRules(): void {
    this.isRulesModalOpen.set(false);
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

  getLastMove(): Move | null {
    return this.state()?.lastMove ?? null;
  }

  // ── Effects ───────────────────────────────────────────────────────────────

  /** Show a transient big announcement banner on screen. */
  private showBanner(title: string, subtitle?: string, side?: PieceSide): void {
    if (this.bannerTimer) {
      clearTimeout(this.bannerTimer);
    }
    this.banner.set({ title, subtitle, side });
    this.cdr.detectChanges();
    this.bannerTimer = setTimeout(() => {
      this.banner.set(null);
      this.cdr.detectChanges();
    }, 1800);
  }

  /** Brief full-screen shake after a capture. */
  private shakeScreen(): void {
    this.screenShaking.set(true);
    const t = setTimeout(() => {
      this.screenShaking.set(false);
      this.shakeTimers = this.shakeTimers.filter(x => x !== t);
    }, 550);
    this.shakeTimers.push(t);
  }

  /** Push an eaten animal to the popup list; it removes itself after 2s. */
  private launchEatenPopup(type: PieceType): void {
    const item: EatenItem = { id: ++this.eatenSeq, type };
    this.eatenPopups.set([...this.eatenPopups(), item]);
    const t = setTimeout(() => {
      this.eatenPopups.set(this.eatenPopups().filter(i => i.id !== item.id));
      this.eatenTimers = this.eatenTimers.filter(x => x !== t);
    }, 2000);
    this.eatenTimers.push(t);
  }

  /** Launch a sequence of fireworks in the winner's color scheme. */
  private launchVictoryFireworks(winner: PieceSide): void {
    this.fireworkSide = winner;
    const winnerName = this.loc.translate(
      winner === 0 ? 'playerStartsBlue' : 'playerStartsRed'
    );
    this.fireworkTitle = this.loc.translate('victoryCongratsTitle', {
      winner: winnerName,
    });
    this.fireworkSubtitle =
      winner === 0
        ? this.loc.translate('victoryCongratsSub')
        : this.loc.translate('victoryCongratsSubRed');

    const colors: ('blue' | 'red' | 'gold')[] =
      winner === 0 ? ['blue', 'blue', 'gold'] : ['red', 'red', 'gold'];

    for (let i = 0; i < 6; i++) {
      const delay = i * 550 + Math.random() * 150; // 0 → ~3.3s window
      const cx = 12 + Math.random() * 76;          // vw %
      const cy = 15 + Math.random() * 55;          // vh %
      const hue = colors[i % colors.length];

      this.fireworkTimer = setTimeout(() => {
        this.ngZone.run(() => {
          const burst = this.makeFirework(hue, cx, cy, ++this.fireworkSeq);
          this.fireworkBursts = [...this.fireworkBursts, burst];
          this.cdr.detectChanges();
          // Drop this burst once its last particle animation ends (~1.9s).
          setTimeout(() => {
            this.fireworkBursts = this.fireworkBursts.filter(
              b => b.id !== burst.id
            );
            this.cdr.detectChanges();
          }, 2100);
        });
      }, delay);
    }
  }

  private makeFirework(
    hue: 'blue' | 'red' | 'gold',
    cx: number,
    cy: number,
    id: number
  ): FireworkBurst {
    const palette = GameComponent.FIREWORK_COLORS[hue];
    const particleCount = 28;
    const particles: FireworkParticle[] = [];

    for (let i = 0; i < particleCount; i++) {
      const angle = (i / particleCount) * Math.PI * 2 + Math.random() * 0.15;
      const dist = 70 + Math.random() * 110; // explosion radius in px
      particles.push({
        dx: Math.cos(angle) * dist,
        dy: Math.sin(angle) * dist,
        size: Math.floor(4 + Math.random() * 4),
        delay: Math.floor(Math.random() * 80),
        color: palette[Math.floor(Math.random() * palette.length)],
      });
    }

    return { id, x: cx, y: cy, hue, particles };
  }

  /** Show clown face animation when the player loses. */
  private playLossClown(): void {
    this.showClown = true;
    this.cdr.detectChanges();
    this.clownTimer = setTimeout(() => {
      this.ngZone.run(() => {
        this.showClown = false;
        this.cdr.detectChanges();
      });
    }, 3200);
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private clearSelection(): void {
    this.selectedPos.set(null);
    this.validMoves.set([]);
  }

  private resetAccumulatedState(): void {
    this.capturedByRed.set([]);
    this.capturedByBlue.set([]);
    this.moveHistory.set([]);
    this.eatenPopups.set([]);
    this.lastAnimatedMoveNumber = -1;
    this.lastAnnouncedResult = '';
  }

  private clearAllTimers(): void {
    if (this.bannerTimer) clearTimeout(this.bannerTimer);
    this.shakeTimers.forEach(t => clearTimeout(t));
    this.eatenTimers.forEach(t => clearTimeout(t));
    if (this.fireworkTimer) clearTimeout(this.fireworkTimer);
    if (this.clownTimer) clearTimeout(this.clownTimer);
    this.bannerTimer = null;
    this.shakeTimers = [];
    this.eatenTimers = [];
    this.fireworkTimer = null;
    this.clownTimer = null;
  }
}
