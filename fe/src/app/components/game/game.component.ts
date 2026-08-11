import { Component, OnInit, OnDestroy, inject, signal, effect, HostBinding, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationStart, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Move, Piece, PieceType, PlayerDisplayInfo, Position } from '../../core/models/game.models';
import { GameRoomService, OnlineGameState } from '../../core/services/game-room.service';
import { GameRuleService } from '../../core/services/game-rule.service';
import { AudioService } from '../../core/services/audio.service';
import { LocalizationService } from '../../core/services/localization.service';
import { AuthService } from '../../core/services/auth.service';
import { RSocketService } from '../../core/services/rsocket.service';
import { BoardComponent, MoveAnimation } from '../board/board.component';
import { LeftPanelComponent } from '../left-panel/left-panel.component';
import { RightPanelComponent } from '../right-panel/right-panel.component';
import { WinChanceBarComponent } from '../win-chance-bar/win-chance-bar.component';
import { ChatComponent } from '../chat/chat.component';
import { ChatMessage } from '../../core/models/game.models';
import { GameRulesModalComponent } from '../game-rules-modal/game-rules-modal.component';
import { EatenPopupComponent, EatenItem } from '../eaten-popup/eaten-popup.component';

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
    ChatComponent,
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
  private readonly rsocket = inject(RSocketService);

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
  readonly screenShaking = signal(false);
  readonly isRulesModalOpen = signal(false);

  // Victory / defeat result flow:
  //   Phase 1 — full-screen celebration (trophy / defeat icon + line), click to dismiss
  //   Phase 2 — small "{X} wins!" popup over the still-visible board
  readonly showResultCelebration = signal(false);
  readonly resultPopupOpen = signal(false);
  readonly resultWon = signal(false);
  readonly resultText = signal('');

  // Resign & Leave confirmation dialog
  readonly confirmModalOpen = signal(false);
  readonly confirmModalTitle = signal('');
  readonly confirmModalMessage = signal('');
  readonly confirmAction = signal<'resign' | 'leave' | null>(null);

  private roomId = '';
  private hasLeftRoom = false;
  private routeEventsSub: { unsubscribe: () => void } | null = null;
  private animCounter = 0;
  private lastAnimatedMoveNumber = -1;
  private eatenSeq = 0;
  private lastAnnouncedResult = '';

  private shakeTimers: ReturnType<typeof setTimeout>[] = [];
  private eatenTimers: ReturnType<typeof setTimeout>[] = [];

  @HostBinding('class.screen-shake') get shakeActive(): boolean {
    return this.screenShaking();
  }

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
      // Move number rolled back (Undo executed on server)
      if (moveNumber < this.lastAnimatedMoveNumber) {
        this.lastAnimatedMoveNumber = moveNumber;
        this.movingPiece.set(null);
        const trimmedHistory = this.moveHistory().slice(0, moveNumber);
        this.moveHistory.set(trimmedHistory);

        const redCaptures: Piece[] = [];
        const blueCaptures: Piece[] = [];
        for (const m of trimmedHistory) {
          if (m.capturedPiece) {
            if (m.piece.side === 0) {
              blueCaptures.push(m.capturedPiece);
            } else {
              redCaptures.push(m.capturedPiece);
            }
          }
        }
        this.capturedByBlue.set(blueCaptures);
        this.capturedByRed.set(redCaptures);
        this.clearSelection();
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

    // Game result once per room. The board stays visible; first show a
    // full-screen celebration, then on click a small "{X} wins!" popup.
    effect(() => {
      const s = this.state();
      if (!s || s.status !== 'ENDED' || s.winner === undefined) return;
      const key = s.roomId;
      if (key === this.lastAnnouncedResult) return;
      this.lastAnnouncedResult = key;

      const won = s.winner === s.yourSide;
      this.resultWon.set(won);
      this.resultText.set(this.winnerLabel);
      this.showResultCelebration.set(true);
      this.resultPopupOpen.set(false);

      if (won) {
        this.audioService.playVictory();
      } else {
        this.audioService.playDefeat();
      }
    });
  }

  ngOnInit(): void {
    this.roomId = this.route.snapshot.params['roomId'];

    this.routeEventsSub = this.router.events.subscribe(event => {
      if (event instanceof NavigationStart && !event.url.startsWith(`/game/${this.roomId}`)) {
        this.leaveCurrentRoom();
      }
    });

    if (this.gameRoom.currentRoomId === this.roomId && this.gameRoom.gameState$.value) {
      // already connected from createRoom/joinRoom navigation
    } else {
      // Browser refresh or direct URL — rejoin
      const token = this.authService.getAccessToken();
      if (!token) {
        this.router.navigate(['/']);
        return;
      }

      this.rsocket.connect(token).subscribe({
        next: () => {
          this.gameRoom.setupPersonalSubscription();
          this.gameRoom.rejoinRoom(this.roomId).subscribe({
            error: (err) => {
              console.error('[Game] rejoin failed:', err);
              this.router.navigate(['/']);
            },
          });
        },
        error: (err) => {
          console.error('[Game] WebSocket connection failed:', err);
          this.router.navigate(['/']);
        },
      });
    }
  }

  ngOnDestroy(): void {
    this.clearAllTimers();
    this.routeEventsSub?.unsubscribe();
  }

  private leaveCurrentRoom(): void {
    if (!this.roomId || this.hasLeftRoom) return;
    this.hasLeftRoom = true;
    this.gameRoom.leaveRoom(this.roomId);
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
    const s = this.state();
    if (s && (s.status === 'PLAYING' || s.status === 'WAITING')) {
      this.confirmModalTitle.set(this.loc.currentLanguage === 'vn' ? 'Rời Phòng?' : 'Leave Room?');
      this.confirmModalMessage.set(this.loc.currentLanguage === 'vn' ? 'Bạn có chắc chắn muốn rời khỏi phòng chơi không?' : 'Are you sure you want to leave the room?');
      this.confirmAction.set('leave');
      this.confirmModalOpen.set(true);
      return;
    }
    this.leaveCurrentRoom();
    this.router.navigate(['/lobby']);
  }

  onResign(): void {
    const s = this.state();
    if (s && s.status === 'PLAYING') {
      this.confirmModalTitle.set(this.loc.currentLanguage === 'vn' ? 'Xác Nhận Đầu Hàng?' : 'Confirm Resignation?');
      this.confirmModalMessage.set(this.loc.currentLanguage === 'vn' ? 'Bạn có chắc chắn muốn đầu hàng trận đấu này không?' : 'Are you sure you want to resign from this match?');
      this.confirmAction.set('resign');
      this.confirmModalOpen.set(true);
      return;
    }
    if (this.roomId) {
      this.leaveCurrentRoom();
      this.router.navigate(['/lobby']);
    }
  }

  onConfirmAction(): void {
    this.confirmModalOpen.set(false);
    this.leaveCurrentRoom();
    this.router.navigate(['/lobby']);
  }

  onCancelAction(): void {
    this.confirmModalOpen.set(false);
    this.confirmAction.set(null);
  }

  requestRematch(): void {
    this.gameRoom.requestRematch(this.roomId);
  }

  copiedRoomId: string | null = null;

  copyRoomCode(roomId: string): void {
    if (!roomId) return;
    navigator.clipboard.writeText(roomId).then(() => {
      this.copiedRoomId = roomId;
      setTimeout(() => {
        if (this.copiedRoomId === roomId) {
          this.copiedRoomId = null;
        }
      }, 2000);
    }).catch(() => {
      try {
        const textArea = document.createElement('textarea');
        textArea.value = roomId;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        this.copiedRoomId = roomId;
        setTimeout(() => {
          if (this.copiedRoomId === roomId) {
            this.copiedRoomId = null;
          }
        }, 2000);
      } catch (e) {
        console.error(e);
      }
    });
  }

  onUndoMove(): void {
    const s = this.state();
    if (s && s.mode === 'PVE' && s.status === 'PLAYING' && (s.moveNumber ?? 0) > 0) {
      this.gameRoom.undoMove(s.roomId);
    }
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

  get canUndo(): boolean {
    const s = this.state();
    return !!s && s.mode === 'PVE' && s.status === 'PLAYING' && (s.moveNumber ?? 0) > 0;
  }
  get redPlayer(): PlayerDisplayInfo {
    const s = this.state();
    if (!s) return { name: 'Phe Đỏ', isBot: false, isYou: false };
    const p = s.players.find(x => x.side === 'PLAYER_1');
    const isYou = !s.isSpectator && s.yourSide === 1;
    if (!p) {
      return {
        name: s.mode === 'EVE' ? 'Bot 1 (Đỏ)' : 'Người chơi Đỏ',
        isBot: s.mode === 'EVE' || s.mode === 'PVE',
        isYou,
      };
    }
    return {
      name: p.displayName || p.username || p.userId || (p.isBot ? 'Bot Đỏ' : 'Phe Đỏ'),
      isBot: p.isBot,
      isYou,
    };
  }

  get bluePlayer(): PlayerDisplayInfo {
    const s = this.state();
    if (!s) return { name: 'Phe Xanh', isBot: false, isYou: false };
    const p = s.players.find(x => x.side === 'PLAYER_2');
    const isYou = !s.isSpectator && s.yourSide === 0;
    if (!p) {
      return {
        name: s.mode === 'EVE' ? 'Bot 2 (Xanh)' : 'Người chơi Xanh',
        isBot: s.mode === 'EVE' || s.mode === 'PVE',
        isYou,
      };
    }
    return {
      name: p.displayName || p.username || p.userId || (p.isBot ? 'Bot Xanh' : 'Phe Xanh'),
      isBot: p.isBot,
      isYou,
    };
  }

  get isRedTurn(): boolean {
    return this.state()?.currentTurn === 1;
  }

  get isBlueTurn(): boolean {
    return this.state()?.currentTurn === 0;
  }

  get isMyTurn(): boolean {
    const s = this.state();
    return !!s && !s.isSpectator && s.currentTurn === s.yourSide;
  }

  get canStart(): boolean {
    const s = this.state();
    return !!s && s.isCreator && s.status === 'WAITING' && s.players.length >= 2;
  }

  get canRematch(): boolean {
    const s = this.state();
    if (!s || s.status !== 'ENDED') return false;
    if (s.mode === 'PVP') return s.resultReason !== 'OPPONENT_DISCONNECTED_TIMEOUT';
    // PVE/EVE: creator can always rematch (bots are re-assigned)
    return s.isCreator;
  }

  get player1Name(): string {
    return this.state()?.players.find(p => p.side === 'PLAYER_1')?.displayName ?? 'Player 1';
  }

  get player2Name(): string {
    return this.state()?.players.find(p => p.side === 'PLAYER_2')?.displayName ?? 'Player 2';
  }

  get turnPlayerName(): string {
    const s = this.state();
    if (!s) return '';
    // currentTurn: 1 = PLAYER_1 (Red), 0 = PLAYER_2 (Blue)
    return s.currentTurn === 1 ? this.player1Name : this.player2Name;
  }

  get sideLabel(): string {
    const raw = this.state()?.yourSideRaw;
    if (raw === 'PLAYER_1') return 'Red (PLAYER_1)';
    if (raw === 'PLAYER_2') return 'Blue (PLAYER_2)';
    return '';
  }

  get turnLabel(): string {
    const s = this.state();
    if (!s) return '';
    return s.currentTurn === 1 ? 'Red' : 'Blue';
  }

  get winnerLabel(): string {
    const s = this.state();
    if (!s) return '';
    if (s.winner === null || s.winner === undefined) {
      return this.loc.currentLanguage === 'vn' ? 'Trận đấu Hòa!' : 'Game Draw!';
    }
    const isVn = this.loc.currentLanguage === 'vn';
    const winnerSideStr = s.winner === 1 ? 'PLAYER_1' : 'PLAYER_2';
    const winnerPlayer = s.players?.find(p => p.side === winnerSideStr);
    const winnerName = winnerPlayer?.displayName || winnerPlayer?.username || (s.winner === 1 ? (isVn ? 'Phe Đỏ' : 'Red Clan') : (isVn ? 'Phe Xanh' : 'Blue Clan'));
    const sideName = s.winner === 1 ? (isVn ? 'Phe Đỏ' : 'Red Clan') : (isVn ? 'Phe Xanh' : 'Blue Clan');

    return isVn
      ? `🏆 Người chơi ${winnerName} (${sideName}) Chiến Thắng!`
      : `🏆 Player ${winnerName} (${sideName}) Wins!`;
  }

  getLastMove(): Move | null {
    return this.state()?.lastMove ?? null;
  }

  // ── Effects ───────────────────────────────────────────────────────────────

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

  /**
   * Click on the full-screen celebration: hide it and reveal the small
   * "{X} wins!" popup over the (still visible) board.
   */
  dismissResultCelebration(): void {
    if (!this.showResultCelebration()) return;
    this.showResultCelebration.set(false);
    this.resultPopupOpen.set(true);
  }

  /** Close the result popup (user chose to continue). */
  closeResultPopup(): void {
    this.resultPopupOpen.set(false);
  }

  get convertedChatMessages(): ChatMessage[] {
    const s = this.state();
    if (!s || !s.chatMessages) return [];
    return s.chatMessages.map((m) => ({
      id: m.id,
      sender: m.senderName,
      side: m.senderSide === 'PLAYER_1' ? 1 : m.senderSide === 'PLAYER_2' ? 0 : undefined,
      text: m.content,
      timestamp: new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }));
  }

  onSendChat(content: string): void {
    const s = this.state();
    if (s?.roomId) {
      this.gameRoom.sendChatMessage(s.roomId, content);
    }
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
    this.showResultCelebration.set(false);
    this.resultPopupOpen.set(false);
    this.lastAnimatedMoveNumber = -1;
    this.lastAnnouncedResult = '';
  }

  private clearAllTimers(): void {
    this.shakeTimers.forEach(t => clearTimeout(t));
    this.eatenTimers.forEach(t => clearTimeout(t));
    this.shakeTimers = [];
    this.eatenTimers = [];
  }
}
