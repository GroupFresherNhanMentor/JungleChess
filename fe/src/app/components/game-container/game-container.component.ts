import { ChangeDetectorRef, Component, NgZone, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  ChatMessage,
  DetailedGameMode,
  GameMode,
  Language,
  Move,
  Piece,
  PieceSide,
  Position,
  RoomInfo
} from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';
import { GameRuleService } from '../../core/services/game-rule.service';
import { AudioService } from '../../core/services/audio.service';
import { AiBotService } from '../../core/services/ai-bot.service';
import { AuthService } from '../../core/services/auth.service';

import { GameControlsComponent } from '../game-controls/game-controls.component';
import { BoardComponent, MoveAnimation } from '../board/board.component';
import { BattleOverlayComponent } from '../battle-overlay/battle-overlay.component';
import { LeftPanelComponent } from '../left-panel/left-panel.component';
import { RightPanelComponent } from '../right-panel/right-panel.component';
import { WinChanceBarComponent } from '../win-chance-bar/win-chance-bar.component';
import { GameRulesModalComponent } from '../game-rules-modal/game-rules-modal.component';
import { ChatComponent } from '../chat/chat.component';
import { LobbyComponent } from '../lobby/lobby.component';

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
  selector: 'app-game-container',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    GameControlsComponent,
    BoardComponent,
    BattleOverlayComponent,
    LeftPanelComponent,
    RightPanelComponent,
    WinChanceBarComponent,
    GameRulesModalComponent,
    ChatComponent,
    LobbyComponent
  ],
  templateUrl: './game-container.component.html'
})
export class GameContainerComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  activeView: 'LOBBY' | 'GAME' = 'LOBBY';
  currentRoom: RoomInfo | null = null;

  /** Stream thông tin user đang đăng nhập (null = guest / chưa load) */
  currentUser$ = this.authService.currentUser$;

  currentLang: Language = 'vn';
  gameMode: GameMode = 'PVA';
  detailedGameMode: DetailedGameMode = 'PVP_ONLINE';
  firstMoveSide: PieceSide = 0; // 0: Blue, 1: Red
  aiDepth: number = 9;
  aiTimeLimit: number = 5000;
  actualAiDepth: number = 0;

  pieces: Piece[] = [];
  currentTurn: PieceSide = 0;
  selectedPos: Position | null = null;
  validMoves: Position[] = [];
  moveHistory: Move[] = [];
  capturedByRed: Piece[] = []; // Blue pieces captured by Red
  capturedByBlue: Piece[] = []; // Red pieces captured by Blue
  chatMessages: ChatMessage[] = [];

  statusMessage: string = '';
  isGameOver: boolean = false;
  isAiThinking: boolean = false;
  isRulesModalOpen: boolean = false;

  /** Big center-screen announcement (turn start / win). */
  banner: { title: string; subtitle?: string; side?: PieceSide } | null = null;
  private bannerTimer: ReturnType<typeof setTimeout> | null = null;

  // --- Victory fireworks + defeat clown effect state ---
  fireworkBursts: FireworkBurst[] = [];
  fireworkSide: PieceSide | null = null;
  fireworkTitle: string = '';
  fireworkSubtitle: string = '';
  showClown: boolean = false;
  private fireworkTimer: ReturnType<typeof setTimeout> | null = null;
  private fireworkSeq = 0;
  private clownTimer: ReturnType<typeof setTimeout> | null = null;

  // --- Move animation + equal-rank battle state ---
  /** Ghost currently sliding on the board (null when idle). */
  movingPiece: MoveAnimation | null = null;
  /** True while a ghost is sliding or a battle overlay is open. */
  isAnimating = false;
  /** Battle arena shown when two equal-rank pieces clash. */
  battle: {
    attacker: Piece;
    defender: Piece;
    from: Position;
    to: Position;
  } | null = null;
  /** Hoisted win-check / turn-toggle / AI-trigger, run after animation. */
  private pendingFinalize: (() => void) | null = null;
  private animSeq = 0;

  private botTaunts = [
    'Tôi đã tính trước 9 nước cờ rồi đó! 🤖',
    'Nước cờ này chuẩn bị bị bắt nhé! 🔥',
    'Chơi khéo đấy, nhưng tôi vẫn dẫn trước! 😎',
    'Tập trung đi bạn ơi! ⚔️',
    'Cơ hội thắng của tôi là rất cao! 🚀'
  ];

  constructor(
    public loc: LocalizationService,
    private ruleService: GameRuleService,
    private audioService: AudioService,
    private aiBotService: AiBotService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.loc.currentLang$.subscribe((lang) => {
      this.currentLang = lang;
      this.updateStatusMessage();
      this.cdr.markForCheck();
    });
  }

  public toggleLanguage(): void {
    const newLang: Language = this.currentLang === 'vn' ? 'en' : 'vn';
    this.loc.setLanguage(newLang);
    this.cdr.detectChanges();
  }

  public onSelectRoomFromLobby(data: { room: RoomInfo; side: PieceSide }): void {
    this.currentRoom = data.room;
    this.detailedGameMode = data.room.mode;
    this.gameMode = data.room.mode === 'PVE' ? 'PVA' : 'PVP';
    this.firstMoveSide = data.side;
    if (data.room.aiDepth) {
      this.aiDepth = data.room.aiDepth;
    }
    this.activeView = 'GAME';
    this.initGame();
  }

  public onBackToLobby(): void {
    this.activeView = 'LOBBY';
    this.cdr.detectChanges();
  }

  public onLogout(): void {
    this.authService.logout().subscribe({
      error: () => this.router.navigate(['/login']),
    });
  }

  public initGame(): void {
    this.pieces = [...this.ruleService.getInitialPieces()];
    this.currentTurn = this.firstMoveSide;
    this.selectedPos = null;
    this.validMoves = [];
    this.moveHistory = [];
    this.capturedByRed = [];
    this.capturedByBlue = [];
    this.isGameOver = false;
    this.isAiThinking = false;
    this.actualAiDepth = 0;
    this.movingPiece = null;
    this.isAnimating = false;
    this.battle = null;
    this.pendingFinalize = null;

    // Clear any running victory/defeat effects.
    this.fireworkBursts = [];
    this.fireworkSide = null;
    this.fireworkTitle = '';
    this.fireworkSubtitle = '';
    this.showClown = false;
    if (this.fireworkTimer) clearTimeout(this.fireworkTimer);
    if (this.clownTimer) clearTimeout(this.clownTimer);
    this.fireworkTimer = null;
    this.clownTimer = null;

    const timeStr = new Date().toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });

    const roomTitle = this.currentRoom ? `[${this.currentRoom.roomId}] ${this.currentRoom.roomName}` : 'Trận đấu mới';

    this.chatMessages = [
      {
        id: 'sys-start',
        sender: 'Hệ thống',
        text: `${this.currentLang === 'vn' ? 'Chào mừng bạn vào phòng' : 'Welcome to room'} ${roomTitle}!`,
        timestamp: timeStr,
        isSystem: true
      }
    ];

    // Announce who moves first (big banner)
    this.showBanner(
      this.loc.translate('bannerFirstMove', {
        player: this.loc.translate(this.currentTurn === 0 ? 'playerStartsBlue' : 'playerStartsRed')
      }),
      undefined,
      this.currentTurn
    );

    this.updateStatusMessage();
    this.cdr.detectChanges();

    // If PvA mode and AI (Red = 1) moves first
    if ((this.gameMode === 'PVA' || this.detailedGameMode === 'EVE') && this.currentTurn === 1) {
      this.triggerAiMove();
    }
  }

  /** Show a transient big announcement banner on screen. */
  public showBanner(
    title: string,
    subtitle?: string,
    side?: PieceSide
  ): void {
    if (this.bannerTimer) {
      clearTimeout(this.bannerTimer);
    }
    this.banner = { title, subtitle, side };
    this.cdr.detectChanges();
    this.bannerTimer = setTimeout(() => {
      this.banner = null;
      this.cdr.detectChanges();
    }, 1800);
  }

  public onSendChatMessage(text: string): void {
    const timeStr = new Date().toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });

    const senderSide = this.gameMode === 'PVA' ? 0 : this.currentTurn;
    const senderName =
      senderSide === 0
        ? this.loc.translate('playerStartsBlue')
        : this.loc.translate('playerStartsRed');

    const userMsg: ChatMessage = {
      id: `msg-${Date.now()}`,
      sender: senderName,
      side: senderSide,
      text,
      timestamp: timeStr
    };

    this.chatMessages = [...this.chatMessages, userMsg];
    this.cdr.detectChanges();

    // In PVA mode, AI bot occasionally replies with a taunt
    if (this.gameMode === 'PVA' || this.detailedGameMode === 'PVE') {
      setTimeout(() => {
        this.ngZone.run(() => {
          const randomTaunt =
            this.botTaunts[Math.floor(Math.random() * this.botTaunts.length)];
          const botMsg: ChatMessage = {
            id: `bot-msg-${Date.now()}`,
            sender: 'Máy (Red AI)',
            side: 1,
            text: randomTaunt,
            timestamp: new Date().toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit'
            })
          };
          this.chatMessages = [...this.chatMessages, botMsg];
          this.cdr.detectChanges();
        });
      }, 1000);
    }
  }

  public openRulesModal(): void {
    this.isRulesModalOpen = true;
    this.cdr.detectChanges();
  }

  public closeRulesModal(): void {
    this.isRulesModalOpen = false;
    this.cdr.detectChanges();
  }

  public onGameModeChange(mode: GameMode): void {
    this.gameMode = mode;
    this.initGame();
  }

  public onFirstMoveSideChange(side: PieceSide): void {
    this.firstMoveSide = side;
    this.initGame();
  }

  public onSquareClick(pos: Position): void {
    if (this.isGameOver || this.isAnimating || this.isAiThinking) {
      return;
    }

    // In PVA mode, human can only play Blue (0)
    if (this.gameMode === 'PVA' && this.currentTurn === 1) {
      return;
    }

    const clickedPiece = this.ruleService.getPieceAt(
      this.pieces,
      pos.col,
      pos.row
    );

    if (this.selectedPos === null) {
      // Select piece
      if (clickedPiece && clickedPiece.side === this.currentTurn) {
        this.selectedPos = pos;
        this.validMoves = this.ruleService.getValidMoves(
          clickedPiece,
          this.pieces
        );
        this.updateStatusMessage(clickedPiece);
        this.cdr.detectChanges();
      }
    } else {
      // Check if clicking same square -> deselect
      if (
        this.selectedPos.col === pos.col &&
        this.selectedPos.row === pos.row
      ) {
        this.selectedPos = null;
        this.validMoves = [];
        this.updateStatusMessage();
        this.cdr.detectChanges();
        return;
      }

      // Check if clicking another piece of same turn -> select that piece
      if (clickedPiece && clickedPiece.side === this.currentTurn) {
        this.selectedPos = pos;
        this.validMoves = this.ruleService.getValidMoves(
          clickedPiece,
          this.pieces
        );
        this.updateStatusMessage(clickedPiece);
        this.cdr.detectChanges();
        return;
      }

      // Check if clicking valid move destination
      const isValid = this.validMoves.some(
        (m) => m.col === pos.col && m.row === pos.row
      );

      if (isValid) {
        const from = { ...this.selectedPos };
        this.selectedPos = null;
        this.validMoves = [];
        this.executeMove(from, pos);
      } else {
        // Deselect
        this.selectedPos = null;
        this.validMoves = [];
        this.updateStatusMessage();
        this.cdr.detectChanges();
      }
    }
  }

  public executeMove(from: Position, to: Position): void {
    const piece = this.ruleService.getPieceAt(this.pieces, from.col, from.row);
    if (!piece) return;

    const targetPiece = this.ruleService.getPieceAt(this.pieces, to.col, to.row);

    // Equal-rank clash -> open the battle arena instead of resolving instantly
    if (targetPiece && this.shouldBattle(piece, targetPiece)) {
      this.isAnimating = true;
      this.battle = { attacker: piece, defender: targetPiece, from, to };
      this.statusMessage = this.loc.translate('battleIntro');
      this.cdr.detectChanges();
      return;
    }

    this.applyMove(from, to, 'attacker');
  }

  /** True when a capture is a true equal-rank clash (not auto-captured via trap). */
  private shouldBattle(attacker: Piece, defender: Piece): boolean {
    if (attacker.side === defender.side) return false;

    const defenderTile = this.ruleService.getTileInfo(defender.position.col, defender.position.row);

    // Defender in attacker's trap -> effective rank 0 -> instant capture, no battle
    if (defenderTile.type === 'trap' && defenderTile.side === attacker.side) {
      return false;
    }

    // Only equal nominal ranks trigger the clash (rat/elephant exceptions never equal)
    return attacker.rank === defender.rank;
  }

  /** Called by the battle overlay with the winning side. */
  public onBattleResult(winningSide: PieceSide): void {
    const b = this.battle;
    this.battle = null;
    if (!b) return;

    const attackerWon = b.attacker.side === winningSide;
    const outcome: 'attacker' | 'defender' = attackerWon ? 'attacker' : 'defender';
    this.statusMessage = attackerWon
      ? this.loc.translate('battleWin', {
          winner: this.loc.translate(winningSide === 0 ? 'playerStartsBlue' : 'playerStartsRed')
        })
      : this.loc.translate('battleCounter');

    this.applyMove(b.from, b.to, outcome);
  }

  /**
   * Resolve a move (with a known outcome) and animate the ghost.
   * Win-check / turn-toggle / AI trigger are deferred to onMoveAnimationComplete.
   */
  private applyMove(from: Position, to: Position, outcome: 'attacker' | 'defender'): void {
    this.ngZone.run(() => {
      const piece = this.ruleService.getPieceAt(this.pieces, from.col, from.row);
      if (!piece) return;

      const targetPiece = this.ruleService.getPieceAt(this.pieces, to.col, to.row);
      const targetTile = this.ruleService.getTileInfo(to.col, to.row);

      // Determine which piece is actually removed (the loser of the clash)
      const loser = outcome === 'attacker' ? targetPiece : piece;
      const winnerSide = outcome === 'attacker' ? piece.side : (targetPiece?.side ?? piece.side);

      // Audio effect
      if (outcome === 'defender') {
        this.audioService.playCapture(piece.type); // attacker eaten (counter)
      } else if (targetTile.type === 'den') {
        this.audioService.playDenCapture();
      } else if (targetPiece) {
        this.audioService.playCapture(targetPiece.type);
      } else if (this.ruleService.isWaterTile(to.col, to.row)) {
        this.audioService.playSwim();
      } else {
        this.audioService.playMove();
      }

      // Capture bookkeeping: the loser goes to the winner's captured list
      if (loser) {
        if (winnerSide === 0) {
          this.capturedByBlue = [...this.capturedByBlue, loser];
        } else {
          this.capturedByRed = [...this.capturedByRed, loser];
        }
      }

      // Update pieces array immutably
      this.pieces = this.pieces
        .filter((p) => !(loser && p.id === loser.id))
        .map((p) => {
          if (outcome === 'attacker' && p.id === piece.id) {
            return {
              ...p,
              position: { col: to.col, row: to.row }
            };
          }
          return p;
        });

      const moveRecord: Move = {
        from,
        to,
        piece: { ...piece, position: { col: to.col, row: to.row } },
        capturedPiece: loser ? { ...loser } : null,
        battleOutcome: outcome === 'defender' ? 'defender' : undefined
      };
      this.moveHistory = [...this.moveHistory, moveRecord];

      this.isAnimating = true;
      this.movingPiece = {
        id: ++this.animSeq,
        piece,
        from,
        to,
        isCapture: !!loser
      };
      this.cdr.detectChanges();

      this.pendingFinalize = () => this.finalizeMove(piece.side);
    });
  }

  /** Runs once the ghost slide finishes. */
  public onMoveAnimationComplete(): void {
    this.movingPiece = null;
    this.isAnimating = false;

    const fn = this.pendingFinalize;
    this.pendingFinalize = null;
    if (fn) fn();
  }

  private finalizeMove(movedSide: PieceSide): void {
    this.ngZone.run(() => {
      // Check Win Condition
      const winCheck = this.ruleService.checkWinCondition(
        this.pieces,
        (1 - this.currentTurn) as PieceSide
      );

      if (winCheck.gameOver) {
        this.isGameOver = true;
        this.isAiThinking = false;
        if (winCheck.winner === 0) {
          this.audioService.playVictory();
          this.statusMessage = this.loc.translate('statusWin', {
            winner: this.loc.translate('playerStartsBlue')
          });
          this.showBanner(
            this.loc.translate('bannerWin', {
              winner: this.loc.translate('playerStartsBlue')
            }),
            undefined,
            0
          );
          this.launchVictoryFireworks(0);
        } else if (winCheck.winner === 1) {
          if (this.gameMode === 'PVA') {
            this.audioService.playDefeat();
            this.playLossClown();
          } else {
            this.audioService.playVictory();
            this.launchVictoryFireworks(1);
          }
          this.statusMessage = this.loc.translate('statusWin', {
            winner: this.loc.translate('playerStartsRed')
          });
          this.showBanner(
            this.loc.translate('bannerWin', {
              winner: this.loc.translate('playerStartsRed')
            }),
            undefined,
            1
          );
        } else {
          this.audioService.playDraw();
          this.statusMessage = this.loc.translate('statusDraw');
          this.showBanner(this.loc.translate('statusDraw'));
        }
        this.cdr.detectChanges();
        return;
      }

      // Toggle turn
      this.currentTurn = (1 - this.currentTurn) as PieceSide;
      this.updateStatusMessage();
      this.cdr.detectChanges();

      // Trigger AI if PVA mode and AI turn; otherwise release the input lock.
      if (this.gameMode === 'PVA' && this.currentTurn === 1 && !this.isGameOver) {
        this.triggerAiMove();
      } else {
        this.isAiThinking = false;
      }
    });
  }

  private triggerAiMove(): void {
    this.isAiThinking = true;
    this.statusMessage = this.loc.translate('statusAIThinking', {
      aiName: this.loc.translate('aiName')
    });
    this.cdr.detectChanges();

    setTimeout(async () => {
      const aiMove = await this.aiBotService.computeMove(
        this.pieces,
        1, // Red AI
        this.aiDepth,
        this.aiTimeLimit
      );

      this.ngZone.run(() => {
        if (aiMove) {
          this.actualAiDepth = aiMove.depthAchieved;
          // Step 1: Highlight AI's selected piece & target move square
          this.selectedPos = { ...aiMove.from };
          this.validMoves = [{ ...aiMove.to }];
          this.cdr.detectChanges();

          // Step 2: Brief delay so user visually sees AI selecting & moving piece
          setTimeout(() => {
            this.ngZone.run(() => {
              this.selectedPos = null;
              this.validMoves = [];
              this.executeMove(aiMove.from, aiMove.to);
              this.cdr.detectChanges();
            });
          }, 350);
        } else {
          // AI has no moves -> Player wins
          this.isGameOver = true;
          this.isAiThinking = false;
          this.audioService.playVictory();
          this.statusMessage = this.loc.translate('errorAINoMoves');
          this.launchVictoryFireworks(0);
          this.cdr.detectChanges();
        }
      });
    }, 400);
  }

  public undoMove(): void {
    if (this.moveHistory.length === 0 || this.isAiThinking || this.isAnimating) return;

    const undoCount = this.gameMode === 'PVA' && this.moveHistory.length >= 2 ? 2 : 1;

    for (let i = 0; i < undoCount; i++) {
      const lastMove = this.moveHistory.pop();
      if (!lastMove) break;

      if (lastMove.battleOutcome === 'defender') {
        // Counter-attack: the attacker was removed, the defender stayed at `to`.
        // Re-add the attacker at `from`; leave the defender in place.
        this.pieces = [
          ...this.pieces,
          { ...lastMove.piece, position: { ...lastMove.from } }
        ];
        // The removed attacker was pushed to the defender's captured list.
        const defenderSide = lastMove.piece.side === 0 ? 1 : 0;
        if (defenderSide === 0) {
          this.capturedByBlue = this.capturedByBlue.slice(0, -1);
        } else {
          this.capturedByRed = this.capturedByRed.slice(0, -1);
        }
      } else {
        // Normal move: attacker is at `to`; defender was captured (if any).
        // Restore the moved piece back to `from` (match by id or by landing cell).
        this.pieces = this.pieces.map((p) => {
          if (
            p.id === lastMove.piece.id ||
            (p.position.col === lastMove.to.col && p.position.row === lastMove.to.row)
          ) {
            return {
              ...p,
              position: { ...lastMove.from }
            };
          }
          return p;
        });

        // Restore captured piece if any
        if (lastMove.capturedPiece) {
          this.pieces = [...this.pieces, { ...lastMove.capturedPiece }];
          if (lastMove.piece.side === 0) {
            this.capturedByBlue = this.capturedByBlue.slice(0, -1);
          } else {
            this.capturedByRed = this.capturedByRed.slice(0, -1);
          }
        }
      }

      this.currentTurn = lastMove.piece.side;
    }

    this.selectedPos = null;
    this.validMoves = [];
    this.isGameOver = false;
    this.updateStatusMessage();
    this.cdr.detectChanges();
  }

  public randomizeBoard(): void {
    if (this.isAiThinking || this.isAnimating) return;

    // Available non-water land positions
    const landPositions: Position[] = [];
    for (let r = 0; r < 9; r++) {
      for (let c = 0; c < 7; c++) {
        const tile = this.ruleService.getTileInfo(c, r);
        if (tile.type === 'land' || tile.type === 'trap') {
          landPositions.push({ col: c, row: r });
        }
      }
    }

    // Shuffle land positions
    for (let i = landPositions.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [landPositions[i], landPositions[j]] = [
        landPositions[j],
        landPositions[i]
      ];
    }

    // Assign positions to pieces immutably
    this.pieces = this.pieces.map((p, idx) => {
      if (idx < landPositions.length) {
        return {
          ...p,
          position: { ...landPositions[idx] }
        };
      }
      return p;
    });

    this.selectedPos = null;
    this.validMoves = [];
    this.audioService.playMove();
    this.updateStatusMessage();
    this.cdr.detectChanges();
  }

  public updateStatusMessage(selectedPiece?: Piece): void {
    if (this.isGameOver) return;

    let playerStr =
      this.currentTurn === 0
        ? this.loc.translate('playerName')
        : this.loc.translate('player2Name');

    if (selectedPiece) {
      const pieceName = this.loc.translate(`animal_${selectedPiece.type}`);
      this.statusMessage = this.loc.translate('statusPlayerSelected', {
        player: playerStr,
        pieceName
      });
    } else {
      this.statusMessage = this.loc.translate('statusWaitingPlayer', {
        player: playerStr
      });
    }
  }

  public getLastMove(): Move | null {
    return this.moveHistory.length > 0
      ? this.moveHistory[this.moveHistory.length - 1]
      : null;
  }

  // --- Victory / Defeat Effects ---

  private static readonly FIREWORK_COLORS: Record<'blue' | 'red' | 'gold', string[]> = {
    blue: ['#3b82f6', '#60a5fa', '#93c5fd', '#ffffff'],
    red:  ['#ef4444', '#f97316', '#fbbf24', '#ffd97a'],
    gold: ['#ffd97a', '#f0c268', '#ff9f43', '#ffffff']
  };

  /** Launch a sequence of fireworks in the winner's color scheme. */
  private launchVictoryFireworks(winner: PieceSide): void {
    this.fireworkSide = winner;
    const winnerName = this.loc.translate(
      winner === 0 ? 'playerStartsBlue' : 'playerStartsRed'
    );
    this.fireworkTitle = this.loc.translate('victoryCongratsTitle', {
      winner: winnerName
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

      setTimeout(() => {
        this.ngZone.run(() => {
          const burst = this.makeFirework(hue, cx, cy, ++this.fireworkSeq);
          this.fireworkBursts = [...this.fireworkBursts, burst];
          this.cdr.detectChanges();
          // Drop this burst once its last particle animation ends (~1.9s).
          setTimeout(() => {
            this.fireworkBursts = this.fireworkBursts.filter(
              (b) => b.id !== burst.id
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
    const palette = GameContainerComponent.FIREWORK_COLORS[hue];
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
        color: palette[Math.floor(Math.random() * palette.length)]
      });
    }

    return { id, x: cx, y: cy, hue, particles };
  }

  /** Show clown face animation when human loses to PVA. */
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
}
