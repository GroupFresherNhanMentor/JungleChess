export type PieceType = 'rat' | 'cat' | 'wolf' | 'dog' | 'leopard' | 'tiger' | 'lion' | 'elephant';
export type PieceSide = 0 | 1; // 0: Blue, 1: Red

export interface Position {
  col: number; // 0..6
  row: number; // 0..8
}

export interface Piece {
  id: string;
  type: PieceType;
  side: PieceSide;
  rank: number; // 1: rat .. 8: elephant
  position: Position;
}

export interface Move {
  from: Position;
  to: Position;
  piece: Piece;
  capturedPiece?: Piece | null;
  notation?: string;
  /** Equal-rank battle result. 'attacker' = attacker won the clash (normal capture);
   *  'defender' = defender won (counter-attack, attacker is removed). */
  battleOutcome?: 'attacker' | 'defender';
}

export interface ChatMessage {
  id: string;
  sender: string;
  side?: PieceSide; // 0: Blue, 1: Red, undefined: System / Spectator
  text: string;
  timestamp: string;
  isSystem?: boolean;
}

export type TileType = 'land' | 'water' | 'trap' | 'den';

export interface Tile {
  col: number;
  row: number;
  type: TileType;
  side?: PieceSide; // For traps and dens
}

export type GameMode = 'PVP_ONLINE' | 'PVP' | 'PVE' | 'PVA' | 'EVE';
export type DetailedGameMode = GameMode;
export type RoomStatus = 'WAITING' | 'PLAYING' | 'ENDED';

export interface RoomInfo {
  roomId: string;
  roomName: string;
  mode: GameMode;
  hostName: string;
  playerCount: number;
  maxPlayers: number;
  status: RoomStatus;
  isPrivate?: boolean;
  createdAt?: string;
  aiDepth?: number;
}

export type Language = 'en' | 'vn';

export const PIECE_RANKS: Record<PieceType, number> = {
  rat: 1,
  cat: 2,
  dog: 3,
  wolf: 4,
  leopard: 5,
  tiger: 6,
  lion: 7,
  elephant: 8
};

export const PIECE_NAMES_EN: Record<PieceType, string> = {
  rat: 'Rat',
  cat: 'Cat',
  wolf: 'Wolf',
  dog: 'Dog',
  leopard: 'Leopard',
  tiger: 'Tiger',
  lion: 'Lion',
  elephant: 'Elephant'
};

export const PIECE_NAMES_VN: Record<PieceType, string> = {
  rat: 'Chuột',
  cat: 'Mèo',
  wolf: 'Sói',
  dog: 'Chó',
  leopard: 'Báo',
  tiger: 'Hổ',
  lion: 'Sư tử',
  elephant: 'Voi'
};
