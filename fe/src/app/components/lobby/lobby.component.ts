import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  DetailedGameMode,
  PieceSide,
  RoomInfo,
  RoomStatus
} from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.css'
})
export class LobbyComponent {
  @Output() selectRoom = new EventEmitter<{ room: RoomInfo; side: PieceSide }>();

  searchQuery: string = '';
  selectedFilterStatus: 'ALL' | 'WAITING' | 'PLAYING' = 'ALL';
  selectedFilterMode: 'ALL' | 'PVP_ONLINE' | 'PVE' | 'EVE' = 'ALL';

  isCreateRoomModalOpen: boolean = false;
  isJoinCodeModalOpen: boolean = false;
  toastMessage: string | null = null;

  // Create Room form fields
  newRoomName: string = '';
  newRoomMode: DetailedGameMode = 'PVP_ONLINE';
  newRoomFirstSide: PieceSide = 0;
  newRoomAiDepth: number = 9;

  // Join by code input
  joinCodeInput: string = '';

  // Initial Mock Rooms Data for demonstration
  rooms: RoomInfo[] = [
    {
      roomId: 'ROOM-1024',
      roomName: 'Trận đấu giao hữu #1',
      mode: 'PVP_ONLINE',
      hostName: 'TinPT15',
      playerCount: 1,
      maxPlayers: 2,
      status: 'WAITING',
      createdAt: '10:15'
    },
    {
      roomId: 'ROOM-5582',
      roomName: 'Thách đấu Cao Thủ Cờ Thú',
      mode: 'PVP_ONLINE',
      hostName: 'AnhKhoi_Pro',
      playerCount: 2,
      maxPlayers: 2,
      status: 'PLAYING',
      createdAt: '09:45'
    },
    {
      roomId: 'ROOM-8831',
      roomName: 'Luyện tập với AI Bot (PvE)',
      mode: 'PVE',
      hostName: 'Player_Guest',
      playerCount: 1,
      maxPlayers: 2,
      status: 'PLAYING',
      aiDepth: 9,
      createdAt: '10:00'
    },
    {
      roomId: 'ROOM-9940',
      roomName: 'Đại chiến Bot vs Bot (EvE)',
      mode: 'EVE',
      hostName: 'System_Bot',
      playerCount: 0,
      maxPlayers: 2,
      status: 'PLAYING',
      aiDepth: 9,
      createdAt: '10:10'
    }
  ];

  constructor(public loc: LocalizationService) {}

  get filteredRooms(): RoomInfo[] {
    return this.rooms.filter((room) => {
      // Filter by search query
      const matchSearch =
        !this.searchQuery ||
        room.roomName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        room.roomId.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        room.hostName.toLowerCase().includes(this.searchQuery.toLowerCase());

      // Filter by status
      const matchStatus =
        this.selectedFilterStatus === 'ALL' ||
        room.status === this.selectedFilterStatus;

      // Filter by mode
      const matchMode =
        this.selectedFilterMode === 'ALL' ||
        room.mode === this.selectedFilterMode;

      return matchSearch && matchStatus && matchMode;
    });
  }

  getModeLabel(mode: DetailedGameMode): string {
    switch (mode) {
      case 'PVP_ONLINE':
        return this.loc.translate('modePVPOnline');
      case 'PVE':
        return this.loc.translate('modePVE');
      case 'EVE':
        return this.loc.translate('modeEVE');
      case 'PVP_LOCAL':
        return 'PvP Local';
      default:
        return mode;
    }
  }

  refreshRooms(): void {
    this.showToast('Đã cập nhật danh sách phòng mới nhất!');
  }

  copyRoomCode(code: string): void {
    navigator.clipboard.writeText(code);
    this.showToast(this.loc.translate('copyCodeSuccess'));
  }

  openCreateRoomModal(): void {
    this.newRoomName = `Phòng chơi của ${this.loc.translate('playerName')}`;
    this.newRoomMode = 'PVP_ONLINE';
    this.newRoomFirstSide = 0;
    this.newRoomAiDepth = 9;
    this.isCreateRoomModalOpen = true;
  }

  closeCreateRoomModal(): void {
    this.isCreateRoomModalOpen = false;
  }

  openJoinCodeModal(): void {
    this.joinCodeInput = '';
    this.isJoinCodeModalOpen = true;
  }

  closeJoinCodeModal(): void {
    this.isJoinCodeModalOpen = false;
  }

  onCreateRoomSubmit(event: Event): void {
    event.preventDefault();
    if (!this.newRoomName.trim()) return;

    const newId = `ROOM-${Math.floor(1000 + Math.random() * 9000)}`;
    const newRoom: RoomInfo = {
      roomId: newId,
      roomName: this.newRoomName.trim(),
      mode: this.newRoomMode,
      hostName: 'Player_Guest',
      playerCount: 1,
      maxPlayers: 2,
      status: this.newRoomMode === 'EVE' ? 'PLAYING' : 'WAITING',
      aiDepth: this.newRoomAiDepth,
      createdAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    this.rooms = [newRoom, ...this.rooms];
    this.closeCreateRoomModal();
    this.showToast(`Tạo phòng ${newId} thành công!`);

    // Launch game room
    this.selectRoom.emit({ room: newRoom, side: this.newRoomFirstSide });
  }

  onJoinCodeSubmit(event: Event): void {
    event.preventDefault();
    const code = this.joinCodeInput.trim().toUpperCase();
    if (!code) return;

    const foundRoom = this.rooms.find((r) => r.roomId.toUpperCase() === code);
    this.closeJoinCodeModal();

    if (foundRoom) {
      this.onJoinRoom(foundRoom);
    } else {
      // Create ad-hoc room with code
      const adhocRoom: RoomInfo = {
        roomId: code,
        roomName: `Phòng ${code}`,
        mode: 'PVP_ONLINE',
        hostName: 'Chủ phòng',
        playerCount: 2,
        maxPlayers: 2,
        status: 'PLAYING',
        createdAt: 'Bây giờ'
      };
      this.selectRoom.emit({ room: adhocRoom, side: 1 });
    }
  }

  onJoinRoom(room: RoomInfo): void {
    this.selectRoom.emit({ room, side: 1 });
  }

  onSpectateRoom(room: RoomInfo): void {
    this.selectRoom.emit({ room, side: 0 });
  }

  private showToast(msg: string): void {
    this.toastMessage = msg;
    setTimeout(() => {
      this.toastMessage = null;
    }, 2500);
  }
}
