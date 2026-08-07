import { ChangeDetectorRef, Component, NgZone, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
} from './core/models/game.models';
import { LocalizationService } from './core/services/localization.service';
import { GameRuleService } from './core/services/game-rule.service';
import { AudioService } from './core/services/audio.service';
import { AiBotService } from './core/services/ai-bot.service';

import { GameControlsComponent } from './components/game-controls/game-controls.component';
import { BoardComponent } from './components/board/board.component';
import { LeftPanelComponent } from './components/left-panel/left-panel.component';
import { RightPanelComponent } from './components/right-panel/right-panel.component';
import { WinChanceBarComponent } from './components/win-chance-bar/win-chance-bar.component';
import { GameRulesModalComponent } from './components/game-rules-modal/game-rules-modal.component';
import { ChatComponent } from './components/chat/chat.component';
import { LobbyComponent } from './components/lobby/lobby.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    GameControlsComponent,
    BoardComponent,
    LeftPanelComponent,
    RightPanelComponent,
    WinChanceBarComponent,
    GameRulesModalComponent,
    ChatComponent,
    LobbyComponent
  ],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  activeView: 'LOBBY' | 'GAME' = 'LOBBY';
  currentRoom: RoomInfo | null = null;

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

    this.updateStatusMessage();
    this.cdr.detectChanges();

    // If PvA mode and AI (Red = 1) moves first
    if ((this.gameMode === 'PVA' || this.detailedGameMode === 'EVE') && this.currentTurn === 1) {
      this.triggerAiMove();
    }
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
    if (this.isGameOver || this.isAiThinking) {
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
    this.ngZone.run(() => {
      const piece = this.ruleService.getPieceAt(this.pieces, from.col, from.row);
      if (!piece) return;

      const targetPiece = this.ruleService.getPieceAt(this.pieces, to.col, to.row);
      const targetTile = this.ruleService.getTileInfo(to.col, to.row);

      // Audio effect
      if (targetTile.type === 'den') {
        this.audioService.playDenCapture();
      } else if (targetPiece) {
        this.audioService.playCapture(targetPiece.type);
      } else if (this.ruleService.isWaterTile(to.col, to.row)) {
        this.audioService.playSwim();
      } else {
        this.audioService.playMove();
      }

      // Capture piece logic
      if (targetPiece) {
        if (piece.side === 0) {
          this.capturedByBlue = [...this.capturedByBlue, targetPiece];
        } else {
          this.capturedByRed = [...this.capturedByRed, targetPiece];
        }
      }

      // Update pieces array immutably
      this.pieces = this.pieces
        .filter((p) => !(targetPiece && p.id === targetPiece.id))
        .map((p) => {
          if (p.id === piece.id) {
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
        capturedPiece: targetPiece ? { ...targetPiece } : null
      };
      this.moveHistory = [...this.moveHistory, moveRecord];

      // Check Win Condition
      const winCheck = this.ruleService.checkWinCondition(
        this.pieces,
        (1 - this.currentTurn) as PieceSide
      );

      if (winCheck.gameOver) {
        this.isGameOver = true;
        if (winCheck.winner === 0) {
          this.audioService.playVictory();
          this.statusMessage = this.loc.translate('statusWin', {
            winner: this.loc.translate('playerStartsBlue')
          });
        } else if (winCheck.winner === 1) {
          if (this.gameMode === 'PVA') {
            this.audioService.playDefeat();
          } else {
            this.audioService.playVictory();
          }
          this.statusMessage = this.loc.translate('statusWin', {
            winner: this.loc.translate('playerStartsRed')
          });
        } else {
          this.audioService.playDraw();
          this.statusMessage = this.loc.translate('statusDraw');
        }
        this.cdr.detectChanges();
        return;
      }

      // Toggle turn
      this.currentTurn = (1 - this.currentTurn) as PieceSide;
      this.updateStatusMessage();
      this.cdr.detectChanges();

      // Trigger AI if PVA mode and AI turn
      if (this.gameMode === 'PVA' && this.currentTurn === 1 && !this.isGameOver) {
        this.triggerAiMove();
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
        this.isAiThinking = false;

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
          this.audioService.playVictory();
          this.statusMessage = this.loc.translate('errorAINoMoves');
          this.cdr.detectChanges();
        }
      });
    }, 400);
  }

  public undoMove(): void {
    if (this.moveHistory.length === 0 || this.isAiThinking) return;

    const undoCount = this.gameMode === 'PVA' && this.moveHistory.length >= 2 ? 2 : 1;

    for (let i = 0; i < undoCount; i++) {
      const lastMove = this.moveHistory.pop();
      if (!lastMove) break;

      // Restore moved piece position
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

      this.currentTurn = lastMove.piece.side;
    }

    this.selectedPos = null;
    this.validMoves = [];
    this.isGameOver = false;
    this.updateStatusMessage();
    this.cdr.detectChanges();
  }

  public randomizeBoard(): void {
    if (this.isAiThinking) return;

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
}
