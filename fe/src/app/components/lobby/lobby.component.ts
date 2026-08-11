import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { BotInfo, GameMode, PieceSide, RoomInfo, RoomStatus } from '../../core/models/game.models';
import { AuthService } from '../../core/services/auth.service';
import { LobbyRoomEntry } from '../../core/models/room-events.models';
import { GameRoomService } from '../../core/services/game-room.service';
import { LobbyRSocketService } from '../../core/services/lobby-rsocket.service';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.css',
})
export class LobbyComponent implements OnInit {
  /** Kept for compatibility with game-container.component.html binding. Not emitted by this online lobby. */
  @Output() selectRoom = new EventEmitter<{ room: RoomInfo; side: PieceSide }>();

  private readonly lobbyRSocket = inject(LobbyRSocketService);
  private readonly gameRoom = inject(GameRoomService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly rooms = toSignal(this.lobbyRSocket.rooms$, { initialValue: [] as LobbyRoomEntry[] });

  searchQuery = '';
  filterStatus: 'ALL' | RoomStatus = 'ALL';
  filterMode: 'ALL' | GameMode = 'ALL';

  isCreateModalOpen = false;
  newRoomMode: GameMode = 'PVP_ONLINE';
  newRoomDifficulty: 'EASY' | 'MEDIUM' | 'HARD' = 'MEDIUM';
  newRoomAllowSpectator = false;

  onlineBots: BotInfo[] = [];
  selectedPlayer1BotId = '';
  selectedPlayer2BotId = '';
  isLoadingBots = false;

  isJoining = false;
  joinRoomId = '';

  toastMessage: string | null = null;
  isLoading = false;

  constructor() {}

  ngOnInit(): void {
    this.lobbyRSocket.connect();
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      error: () => this.router.navigate(['/login']),
    });
  }

  get filteredRooms(): LobbyRoomEntry[] {
    return this.rooms().filter(r => {
      const matchSearch =
        !this.searchQuery ||
        r.roomId.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchStatus = this.filterStatus === 'ALL' || r.status === this.filterStatus;
      const matchMode =
        this.filterMode === 'ALL' || this.backendModeToFrontend(r.mode) === this.filterMode;
      return matchSearch && matchStatus && matchMode;
    });
  }

  openCreateModal(): void {
    this.newRoomMode = 'PVP_ONLINE';
    this.newRoomDifficulty = 'MEDIUM';
    this.newRoomAllowSpectator = false;
    this.selectedPlayer1BotId = '';
    this.selectedPlayer2BotId = '';
    this.isCreateModalOpen = true;
    this.fetchOnlineBots();
  }

  private fetchOnlineBots(): void {
    this.isLoadingBots = true;
    this.gameRoom.getOnlineBots().subscribe({
      next: bots => {
        this.onlineBots = bots;
        this.selectedPlayer1BotId = bots[0]?.id ?? '';
        this.selectedPlayer2BotId = bots[0]?.id ?? '';
        this.isLoadingBots = false;
      },
      error: () => {
        this.onlineBots = [];
        this.isLoadingBots = false;
      },
    });
  }

  get needsBotSelection(): boolean {
    return this.newRoomMode === 'PVE' || this.newRoomMode === 'EVE';
  }

  closeCreateModal(): void {
    this.isCreateModalOpen = false;
  }

  onCreateRoom(): void {
    this.isLoading = true;
    this.closeCreateModal();

    const backendMode = this.frontendModeToBackend(this.newRoomMode);
    const difficulty = (this.newRoomMode === 'PVE' || this.newRoomMode === 'EVE') ? this.newRoomDifficulty : undefined;
    const allowSpectator = this.newRoomMode !== 'PVP_ONLINE' || this.newRoomAllowSpectator;
    const player1BotId = this.newRoomMode === 'EVE' ? (this.selectedPlayer1BotId || undefined) : undefined;
    const player2BotId = (this.newRoomMode === 'PVE' || this.newRoomMode === 'EVE')
      ? (this.selectedPlayer2BotId || undefined) : undefined;

    this.gameRoom.createRoom(backendMode, difficulty, allowSpectator, player1BotId, player2BotId).subscribe({
      next: roomId => {
        this.isLoading = false;
        this.router.navigate(['/game', roomId]);
      },
      error: err => {
        this.isLoading = false;
        this.showToast('Failed to create room: ' + (err?.message ?? err));
      },
    });
  }

  onJoinRoom(roomId: string): void {
    this.isLoading = true;
    this.gameRoom.joinRoom(roomId).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/game', roomId]);
      },
      error: err => {
        this.isLoading = false;
        this.showToast('Failed to join: ' + (err?.message ?? err));
      },
    });
  }

  onWatchRoom(roomId: string): void {
    this.isLoading = true;
    this.gameRoom.watchRoom(roomId).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/game', roomId]);
      },
      error: err => {
        this.isLoading = false;
        this.showToast('Cannot spectate: ' + (err?.message ?? err));
      },
    });
  }

  getModeLabel(mode: string): string {
    const map: Record<string, string> = {
      PVP: 'PvP Online',
      PVE: 'Player vs Bot',
      EVE: 'Bot vs Bot',
    };
    return map[mode] ?? mode;
  }

  canJoin(room: LobbyRoomEntry): boolean {
    return room.mode === 'PVP' && room.status === 'WAITING' && room.playerCount < 2;
  }

  canWatch(room: LobbyRoomEntry): boolean {
    return room.allowSpectator && room.status !== 'ENDED' && !this.canJoin(room);
  }

  copiedRoomId: string | null = null;

  copyRoomCode(roomId: string, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    const successMsg = `Đã sao chép mã phòng: ${roomId}`;
    navigator.clipboard.writeText(roomId).then(() => {
      this.copiedRoomId = roomId;
      this.showToast(successMsg);
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
        this.showToast(successMsg);
        setTimeout(() => {
          if (this.copiedRoomId === roomId) {
            this.copiedRoomId = null;
          }
        }, 2000);
      } catch (err) {
        this.showToast('Failed to copy: ' + err);
      }
    });
  }

  private frontendModeToBackend(mode: GameMode): string {
    return mode === 'PVP_ONLINE' ? 'PVP' : mode;
  }

  private backendModeToFrontend(mode: string): GameMode {
    return mode === 'PVP' ? 'PVP_ONLINE' : (mode as GameMode);
  }

  private showToast(msg: string): void {
    this.toastMessage = msg;
    setTimeout(() => (this.toastMessage = null), 3000);
  }
}
