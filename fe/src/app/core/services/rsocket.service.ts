import { Injectable } from '@angular/core';
import { Observable, of, shareReplay } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

@Injectable({ providedIn: 'root' })
export class RSocketService {
  private client: Client | null = null;
  private connecting$: Observable<void> | null = null;

  connect(accessToken: string): Observable<void> {
    if (this.client?.active) {
      console.log('[STOMP] already connected, reusing');
      return of(void 0);
    }
    if (this.connecting$) {
      console.log('[STOMP] connection in progress, waiting');
      return this.connecting$;
    }

    console.log('[STOMP] connecting to', WS_URL);
    this.connecting$ = new Observable<void>(observer => {
      this.client = new Client({
        brokerURL: WS_URL,
        connectHeaders: {
          Authorization: `Bearer ${accessToken}`,
        },
        reconnectDelay: 0,
        onConnect: () => {
          console.log('[STOMP] connected OK');
          observer.next();
          observer.complete();
        },
        onStompError: frame => {
          console.error('[STOMP] error', frame);
          this.connecting$ = null;
          observer.error(new Error(frame.headers['message'] || 'STOMP connection error'));
        },
        onDisconnect: () => {
          console.log('[STOMP] disconnected');
        },
      });
      this.client.activate();
    }).pipe(shareReplay(1));

    return this.connecting$;
  }

  subscribe<T>(destination: string, callback: (data: T) => void): StompSubscription {
    if (!this.client?.active) throw new Error('[STOMP] not connected');
    return this.client.subscribe(destination, (msg: IMessage) => {
      try {
        const data = JSON.parse(msg.body) as T;
        callback(data);
      } catch (e) {
        console.error('[STOMP] failed to parse message from', destination, e);
      }
    });
  }

  send(destination: string, body?: unknown): void {
    if (!this.client?.active) {
      console.warn('[STOMP] send called but not connected, dest=', destination);
      return;
    }
    this.client.publish({
      destination,
      body: body !== undefined ? JSON.stringify(body) : '',
    });
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.connecting$ = null;
    console.log('[STOMP] disconnected and cleaned up');
  }
}
