export interface RoomEvent {
  type: string;
}

export interface RoomCreatedEvent extends RoomEvent {
  type: 'ROOM_CREATED';
  roomId: string;
  yourSide: string;
  mode: string;
  status: string;
  allowSpectator: boolean;
  allowBet: boolean;
  players: ServerPlayerInfo[];
  spectators: ServerSpectatorInfo[];
}

export interface RoomJoinedEvent extends RoomEvent {
  type: 'ROOM_JOINED';
  roomId: string;
  yourSide: string;
  mode: string;
  status: string;
  creatorUserId: string;
  board: string[][];
  currentTurn: string;
  players: ServerPlayerInfo[];
  spectators: ServerSpectatorInfo[];
  recentChat?: ChatMessageRecord[];
}

export interface ChatMessageRecord {
  id: string;
  senderUserId: string;
  senderName: string;
  senderSide: string;
  content: string;
  timestamp: number;
}

export interface ChatMessageEvent extends RoomEvent {
  type: 'CHAT_MESSAGE';
  roomId: string;
  message: ChatMessageRecord;
}

export interface StateUpdatedEvent extends RoomEvent {
  type: 'STATE_UPDATED';
  roomId: string;
  board: string[][];
  currentTurn: string;
  lastMove: ServerMoveRecord | null;
  status: string;
  moveNumber: number;
}

export interface PlayersUpdatedEvent extends RoomEvent {
  type: 'PLAYERS_UPDATED';
  roomId: string;
  players: ServerPlayerInfo[];
  spectators: ServerSpectatorInfo[];
  status: string;
}

export interface GameResultEvent extends RoomEvent {
  type: 'GAME_RESULT';
  roomId: string;
  winner: string;
  reason: string;
}

export interface RoomErrorEvent extends RoomEvent {
  type: 'ROOM_ERROR';
  message: string;
}

export interface ServerMoveRecord {
  from: [number, number];
  to: [number, number];
  movedPiece: string;
  capturedPiece: string | null;
  specialEvent: string | null;
}

export interface ServerPlayerInfo {
  sessionId: string;
  side: string;
  isBot: boolean;
  userId: string;
  displayName?: string;
  username?: string;
}

export interface ServerSpectatorInfo {
  sessionId: string;
  userId: string;
  username?: string;
  displayName?: string;
}

export interface LobbySnapshot {
  rooms: LobbyRoomEntry[];
}

export interface LobbyRoomEntry {
  roomId: string;
  mode: string;
  status: string;
  allowSpectator: boolean;
  allowBet: boolean;
  playerCount: number;
  spectatorCount: number;
  createdAt: string;
}
