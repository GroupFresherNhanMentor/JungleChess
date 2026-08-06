import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  GameMode,
  Language,
  Move,
  Piece,
  PieceSide,
  Position
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
import { GameRulesComponent } from './components/game-rules/game-rules.component';

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
    GameRulesComponent
  ],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  currentLang: Language = 'vn';
  gameMode: GameMode = 'PVA';
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

  statusMessage: string = '';
  isGameOver: boolean = false;
  isAiThinking: boolean = false;

  constructor(
    public loc: LocalizationService,
    private ruleService: GameRuleService,
    private audioService: AudioService,
    private aiBotService: AiBotService
  ) {}

  ngOnInit(): void {
    this.loc.currentLang$.subscribe(() => {
      this.updateStatusMessage();
    });
    this.initGame();
  }

  public initGame(): void {
    this.pieces = this.ruleService.getInitialPieces();
    this.currentTurn = this.firstMoveSide;
    this.selectedPos = null;
    this.validMoves = [];
    this.moveHistory = [];
    this.capturedByRed = [];
    this.capturedByBlue = [];
    this.isGameOver = false;
    this.isAiThinking = false;
    this.actualAiDepth = 0;

    this.updateStatusMessage();

    // If PvA mode and AI (Red = 1) moves first
    if (this.gameMode === 'PVA' && this.currentTurn === 1) {
      this.triggerAiMove();
    }
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
      }
    }
  }

  public executeMove(from: Position, to: Position): void {
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
      this.pieces = this.pieces.filter((p) => p.id !== targetPiece.id);
      if (piece.side === 0) {
        this.capturedByBlue.push(targetPiece);
      } else {
        this.capturedByRed.push(targetPiece);
      }
    }

    // Move piece
    piece.position = { col: to.col, row: to.row };

    const moveRecord: Move = {
      from,
      to,
      piece: { ...piece },
      capturedPiece: targetPiece ? { ...targetPiece } : null
    };
    this.moveHistory.push(moveRecord);

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
      return;
    }

    // Toggle turn
    this.currentTurn = (1 - this.currentTurn) as PieceSide;
    this.updateStatusMessage();

    // Trigger AI if PVA mode and AI turn
    if (this.gameMode === 'PVA' && this.currentTurn === 1 && !this.isGameOver) {
      this.triggerAiMove();
    }
  }

  private triggerAiMove(): void {
    this.isAiThinking = true;
    this.statusMessage = this.loc.translate('statusAIThinking', {
      aiName: this.loc.translate('aiName')
    });

    setTimeout(async () => {
      const aiMove = await this.aiBotService.computeMove(
        this.pieces,
        1, // Red AI
        this.aiDepth,
        this.aiTimeLimit
      );

      this.isAiThinking = false;

      if (aiMove) {
        this.actualAiDepth = aiMove.depthAchieved;
        this.executeMove(aiMove.from, aiMove.to);
      } else {
        // AI has no moves -> Player wins
        this.isGameOver = true;
        this.audioService.playVictory();
        this.statusMessage = this.loc.translate('errorAINoMoves');
      }
    }, 400);
  }

  public undoMove(): void {
    if (this.moveHistory.length === 0 || this.isAiThinking) return;

    const undoCount = this.gameMode === 'PVA' && this.moveHistory.length >= 2 ? 2 : 1;

    for (let i = 0; i < undoCount; i++) {
      const lastMove = this.moveHistory.pop();
      if (!lastMove) break;

      // Restore moved piece position
      const p = this.pieces.find(
        (x) =>
          x.position.col === lastMove.to.col &&
          x.position.row === lastMove.to.row
      );
      if (p) {
        p.position = { ...lastMove.from };
      }

      // Restore captured piece if any
      if (lastMove.capturedPiece) {
        this.pieces.push({ ...lastMove.capturedPiece });
        if (lastMove.piece.side === 0) {
          this.capturedByBlue.pop();
        } else {
          this.capturedByRed.pop();
        }
      }

      this.currentTurn = lastMove.piece.side;
    }

    this.selectedPos = null;
    this.validMoves = [];
    this.isGameOver = false;
    this.updateStatusMessage();
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

    // Assign positions to pieces
    this.pieces.forEach((p, idx) => {
      if (idx < landPositions.length) {
        p.position = { ...landPositions[idx] };
      }
    });

    this.selectedPos = null;
    this.validMoves = [];
    this.audioService.playMove();
    this.updateStatusMessage();
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
