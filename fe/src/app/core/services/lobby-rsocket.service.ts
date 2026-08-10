import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { StompSubscription } from '@stomp/stompjs';
import { LobbyRoomEntry, LobbySnapshot } from '../models/room-events.models';
import { RSocketService } from './rsocket.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class LobbyRSocketService {
  readonly rooms$ = new BehaviorSubject<LobbyRoomEntry[]>([]);

  private readonly stomp = inject(RSocketService);
  private readonly auth = inject(AuthService);

  private lobbySub: StompSubscription | null = null;
  private privateLobbySub: StompSubscription | null = null;

  connect(): void {
    const token = this.auth.getAccessToken();
    if (!token) return;

    this.stomp.connect(token).subscribe({
      next: () => {
        // Subscribe to live lobby updates
        this.lobbySub?.unsubscribe();
        this.lobbySub = this.stomp.subscribe<LobbySnapshot>('/topic/lobby', snapshot => {
          this.rooms$.next(snapshot.rooms ?? []);
        });

        // Subscribe to personal queue for initial snapshot response
        this.privateLobbySub?.unsubscribe();
        this.privateLobbySub = this.stomp.subscribe<LobbySnapshot>('/user/queue/lobby', snapshot => {
          this.rooms$.next(snapshot.rooms ?? []);
        });

        // Request initial snapshot
        this.stomp.send('/app/lobby.snapshot');
      },
      error: err => console.error('[Lobby] STOMP connect error', err),
    });
  }

  disconnect(): void {
    this.lobbySub?.unsubscribe();
    this.privateLobbySub?.unsubscribe();
    this.lobbySub = null;
    this.privateLobbySub = null;
  }

  getUserId(): string {
    return this.auth.getUserId() ?? '';
  }
}
