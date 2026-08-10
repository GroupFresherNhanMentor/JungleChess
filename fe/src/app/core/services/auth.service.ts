import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * Local mock authentication. Mirrors a JWT auth experience but stores
 * users + the active session in localStorage only (no backend required).
 * Exposes the same conceptual surface as the real backend auth flow:
 * login / register / guest / logout.
 */
export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  guest: boolean;
  createdAt: string;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  user: AuthUser;
}

const USERS_KEY = 'jc_mock_users';
const SESSION_KEY = 'jc_mock_session';

interface StoredUser extends AuthUser {
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private sessionSubject = new BehaviorSubject<AuthSession | null>(this.loadSession());
  public session$ = this.sessionSubject.asObservable();

  constructor() {}

  public get session(): AuthSession | null {
    return this.sessionSubject.value;
  }

  public get isLoggedIn(): boolean {
    return !!this.sessionSubject.value;
  }

  public get currentUser(): AuthUser | null {
    return this.sessionSubject.value?.user ?? null;
  }

  /** True while the app is waiting (fake network latency) — lets UI show a spinner. */
  public async login(username: string, password: string): Promise<AuthSession> {
    await this.simulateLatency();
    const users = this.readUsers();
    const user = users.find((u) => u.username.toLowerCase() === username.trim().toLowerCase());
    if (!user || user.password !== password) {
      throw new Error('INVALID_CREDENTIALS');
    }
    const session = this.buildSession(user);
    this.saveSession(session);
    return session;
  }

  public async register(username: string, password: string, fullName?: string): Promise<AuthSession> {
    await this.simulateLatency();
    const name = username.trim();
    if (name.length < 3 || name.length > 24) {
      throw new Error('USERNAME_INVALID');
    }
    if (!/^[a-zA-Z0-9_]+$/.test(name)) {
      throw new Error('USERNAME_CHARS');
    }
    if (password.length < 6) {
      throw new Error('PASSWORD_TOO_SHORT');
    }
    const users = this.readUsers();
    if (users.some((u) => u.username.toLowerCase() === name.toLowerCase())) {
      throw new Error('USERNAME_EXISTS');
    }
    const user: StoredUser = {
      id: this.genId(),
      username: name,
      fullName: fullName?.trim() || name,
      guest: false,
      createdAt: new Date().toISOString(),
      password
    };
    users.push(user);
    this.writeUsers(users);
    const session = this.buildSession(user);
    this.saveSession(session);
    return session;
  }

  /** One-click play without credentials. */
  public async guest(): Promise<AuthSession> {
    await this.simulateLatency();
    const username = `guest_${this.randAlnum(10)}`;
    const user: StoredUser = {
      id: this.genId(),
      username,
      fullName: 'Khách',
      guest: true,
      createdAt: new Date().toISOString(),
      password: ''
    };
    const users = this.readUsers();
    users.push(user);
    this.writeUsers(users);
    const session = this.buildSession(user);
    this.saveSession(session);
    return session;
  }

  public logout(): void {
    localStorage.removeItem(SESSION_KEY);
    this.sessionSubject.next(null);
  }

  // --- internals ---

  private readUsers(): StoredUser[] {
    try {
      return JSON.parse(localStorage.getItem(USERS_KEY) || '[]') as StoredUser[];
    } catch {
      return [];
    }
  }

  private writeUsers(users: StoredUser[]): void {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
  }

  private loadSession(): AuthSession | null {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      return raw ? (JSON.parse(raw) as AuthSession) : null;
    } catch {
      return null;
    }
  }

  private saveSession(session: AuthSession): void {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    this.sessionSubject.next(session);
  }

  private buildSession(user: StoredUser): AuthSession {
    // Token-shaped strings, purely cosmetic for the mock.
    const payload = { uid: user.id, sub: user.username, role: 'USER' };
    const b64 = btoa(JSON.stringify(payload));
    return {
      accessToken: `mock.access.${b64}`,
      refreshToken: `mock.refresh.${b64}`,
      tokenType: 'Bearer',
      user: {
        id: user.id,
        username: user.username,
        fullName: user.fullName,
        guest: user.guest,
        createdAt: user.createdAt
      }
    };
  }

  private genId(): string {
    // localStorage-safe uid
    return `u_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  }

  private randAlnum(len: number): string {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let out = '';
    for (let i = 0; i < len; i++) {
      out += chars[Math.floor(Math.random() * chars.length)];
    }
    return out;
  }

  private simulateLatency(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 350 + Math.random() * 300));
  }
}