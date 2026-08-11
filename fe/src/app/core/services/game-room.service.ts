import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject, first } from 'rxjs';
import { StompSubscription } from '@stomp/stompjs';
import { Move, Piece, PieceSide, Position, RoomStatus } from '../models/game.models';
import {
  GameResultEvent,
  LobbyRoomEntry,
  PlayersUpdatedEvent,
  RoomCreatedEvent,
  RoomEvent,
  RoomJoinedEvent,
  ServerPlayerInfo,
  ServerSpectatorInfo,
  StateUpdatedEvent,
} from '../models/room-events.models';
import { RSocketService } from './rsocket.service';
import { BoardAdapterService } from './board-adapter.service';
import { GameRuleService } from './game-rule.service';

export interface OnlineGameState {
  roomId: string;
  yourSide: PieceSide;
  yourSideRaw: string;
  isCreator: boolean;
  isSpectator: boolean;
  mode: string;
  status: RoomStatus;
  pieces: Piece[];
  currentTurn: PieceSide;
  moveNumber: number;
  lastMove: Move | null;
  winner?: PieceSide;
  resultReason?: string;
  players: ServerPlayerInfo[];
  spectators: ServerSpectatorInfo[];
}

@Injectable({ providedIn: 'root' })
export class GameRoomService {
  readonly gameState$ = new BehaviorSubject<OnlineGameState | null>(null);

  currentRoomId: string | null = null;

  private personalSub: StompSubscription | null = null;
  private roomSub: StompSubscription | null = null;

  constructor(
    private stomp: RSocketService,
    private adapter: BoardAdapterService,
    private ruleService: GameRuleService,
  ) {}

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  setupPersonalSubscription(): void {
    this.personalSub?.unsubscribe();
    this.personalSub = this.stomp.subscribe<RoomEvent>('/user/queue/events', event => {
      console.log('[GameRoom] personal event', event);
      this.handleEvent(event);
    });
  }

  private subscribeToRoom(roomId: string): void {
    this.roomSub?.unsubscribe();
    this.roomSub = this.stomp.subscribe<RoomEvent>(`/topic/room/${roomId}`, event => {
      console.log('[GameRoom] room event', event);
      this.handleEvent(event);
    });
  }

  // ── Create ────────────────────────────────────────────────────────────────

  createRoom(mode: string, botDifficulty?: string, allowSpectator = false): Observable<string> {
    this.cleanupRoom();
    this.gameState$.next(null);
    const result$ = new Subject<string>();

    const tempSub = this.stomp.subscribe<RoomEvent>('/user/queue/events', event => {
      console.log('[GameRoom] create event', event);
      this.handleEvent(event);
      if (event.type === 'ROOM_CREATED' && !result$.closed) {
        const roomId = (event as RoomCreatedEvent).roomId;
        this.subscribeToRoom(roomId);
        // Keep personal queue active for late PLAYERS_UPDATED events (bots may join after subscription)
        this.setupPersonalSubscription();
        // Request current state in case bots joined before our room-topic subscription reached the server
        this.stomp.send(`/app/room.${roomId}.sync`);
        tempSub.unsubscribe();
        result$.next(roomId);
        result$.complete();
      }
      if (event.type === 'ROOM_ERROR' && !result$.closed) {
        tempSub.unsubscribe();
        result$.error(new Error((event as any).message));
      }
    });

    this.stomp.send('/app/room.create', {
      mode,
      allowSpectator,
      allowBet: false,
      botDifficulty: botDifficulty ?? null,
    });

    return result$.asObservable();
  }

  // ── Join ──────────────────────────────────────────────────────────────────

  joinRoom(roomId: string): Observable<void> {
    this.cleanupRoom();
    this.gameState$.next(null);
    const result$ = new Subject<void>();

    const tempSub = this.stomp.subscribe<RoomEvent>('/user/queue/events', event => {
      this.handleEvent(event);
      if (event.type === 'ROOM_JOINED' && !result$.closed) {
        this.subscribeToRoom(roomId);
        tempSub.unsubscribe();
        result$.next();
        result$.complete();
      }
      if (event.type === 'ROOM_ERROR' && !result$.closed) {
        tempSub.unsubscribe();
        result$.error(new Error((event as any).message));
      }
    });

    this.stomp.send(`/app/room.${roomId}.join`);
    return result$.asObservable();
  }

  // ── Rejoin (browser refresh) ──────────────────────────────────────────────

  rejoinRoom(roomId: string): Observable<void> {
    this.cleanupRoom();
    this.gameState$.next(null);
    const result$ = new Subject<void>();

    const tempSub = this.stomp.subscribe<RoomEvent>('/user/queue/events', event => {
      this.handleEvent(event);
      if (event.type === 'ROOM_JOINED' && !result$.closed) {
        this.subscribeToRoom(roomId);
        tempSub.unsubscribe();
        result$.next();
        result$.complete();
      }
      if (event.type === 'ROOM_ERROR' && !result$.closed) {
        tempSub.unsubscribe();
        result$.error(new Error((event as any).message));
      }
    });

    this.stomp.send(`/app/room.${roomId}.rejoin`);
    return result$.asObservable();
  }

  // ── Watch (spectator) ─────────────────────────────────────────────────────

  watchRoom(roomId: string): Observable<void> {
    this.cleanupRoom();
    this.gameState$.next(null);
    const result$ = new Subject<void>();

    const tempSub = this.stomp.subscribe<RoomEvent>('/user/queue/events', event => {
      this.handleEvent(event);
      if (event.type === 'ROOM_JOINED' && !result$.closed) {
        this.subscribeToRoom(roomId);
        tempSub.unsubscribe();
        result$.next();
        result$.complete();
      }
      if (event.type === 'ROOM_ERROR' && !result$.closed) {
        tempSub.unsubscribe();
        result$.error(new Error((event as any).message));
      }
    });

    this.stomp.send(`/app/room.${roomId}.watch`);
    return result$.asObservable();
  }

  // ── Game actions ──────────────────────────────────────────────────────────

  startGame(roomId: string): void {
    this.stomp.send(`/app/room.${roomId}.start`);
  }

  sendMove(roomId: string, from: Position, to: Position): Observable<void> {
    const result$ = new Subject<void>();
    this.stomp.send(`/app/room.${roomId}.move`, {
      from: this.adapter.toBackendPos(from),
      to: this.adapter.toBackendPos(to),
    });
    // Move is acknowledged via STATE_UPDATED event on the room topic
    result$.next();
    result$.complete();
    return result$.asObservable();
  }

  leaveRoom(roomId: string): void {
    this.stomp.send(`/app/room.${roomId}.leave`);
    this.cleanupRoom();
    this.gameState$.next(null);
    this.currentRoomId = null;
  }

  requestRematch(roomId: string): void {
    this.stomp.send(`/app/room.${roomId}.rematch`);
  }

  undoMove(roomId: string): void {
    this.stomp.send(`/app/room.${roomId}.undo`);
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  private handleEvent(event: RoomEvent): void {
    switch (event.type) {
      case 'ROOM_CREATED': {
        const e = event as RoomCreatedEvent;
        this.currentRoomId = e.roomId;
        this.gameState$.next({
          roomId: e.roomId,
          yourSide: this.adapter.toSide(e.yourSide),
          yourSideRaw: e.yourSide,
          isCreator: true,
          isSpectator: e.yourSide === 'SPECTATOR',
          mode: e.mode,
          status: e.status as RoomStatus,
          pieces: this.ruleService.getInitialPieces(),
          currentTurn: 1,
          moveNumber: 0,
          lastMove: null,
          players: e.players ?? [],
          spectators: e.spectators ?? [],
        });
        break;
      }
      case 'ROOM_JOINED': {
        const e = event as RoomJoinedEvent;
        this.currentRoomId = e.roomId;
        const current = this.gameState$.value;
        this.gameState$.next({
          roomId: e.roomId,
          yourSide: this.adapter.toSide(e.yourSide),
          yourSideRaw: e.yourSide,
          isCreator: current?.isCreator ?? false,
          isSpectator: e.yourSide === 'SPECTATOR',
          mode: e.mode ?? current?.mode ?? '',
          status: e.status as RoomStatus,
          pieces: this.adapter.toPieces(e.board),
          currentTurn: this.adapter.toSide(e.currentTurn),
          moveNumber: 0,
          lastMove: null,
          players: e.players ?? [],
          spectators: e.spectators ?? [],
        });
        break;
      }
      case 'STATE_UPDATED': {
        const e = event as StateUpdatedEvent;
        const current = this.gameState$.value;
        if (!current) return;
        this.gameState$.next({
          ...current,
          pieces: this.adapter.toPieces(e.board),
          currentTurn: this.adapter.toSide(e.currentTurn),
          status: e.status as RoomStatus,
          moveNumber: e.moveNumber,
          lastMove: e.lastMove ? this.adapter.toMove(e.lastMove) : null,
        });
        break;
      }
      case 'PLAYERS_UPDATED': {
        const e = event as PlayersUpdatedEvent;
        const current = this.gameState$.value;
        if (!current) return;
        this.gameState$.next({
          ...current,
          status: e.status as RoomStatus,
          players: e.players,
          spectators: e.spectators,
        });
        break;
      }
      case 'GAME_RESULT': {
        const e = event as GameResultEvent;
        const current = this.gameState$.value;
        if (!current) return;
        this.gameState$.next({
          ...current,
          status: 'ENDED',
          winner: this.adapter.toSide(e.winner),
          resultReason: e.reason,
        });
        break;
      }
    }
  }

  private cleanupRoom(): void {
    this.roomSub?.unsubscribe();
    this.roomSub = null;
  }
}
