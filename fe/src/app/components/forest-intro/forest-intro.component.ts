import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  NgZone,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import * as THREE from 'three';
import { Scene3dService } from '../../core/services/scene3d.service';
import { LocalizationService } from '../../core/services/localization.service';
import { AuthService } from '../../core/services/auth.service';

/**
 * Cinematic forest walk. An auto-gliding camera descends a winding forest path
 * flanked by trees; pressing Space toggles a login/register menu (mock auth);
 * on success the camera walks into a glowing portal and emits `entered`.
 */
@Component({
  selector: 'app-forest-intro',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="forest-root">
      <canvas #canvas></canvas>
      <div class="forest-hint" [class.hidden]="passingPortal">
        <span>{{ loc.translate(showAuth ? 'forestPromptClose' : 'forestPromptSpace') }}</span>
      </div>

      <div class="auth-modal" *ngIf="showAuth">
        <form (ngSubmit)="onSubmit()" class="auth-card">
          <h2>{{ loc.translate(mode === 'login' ? 'authLoginTitle' : 'authRegisterTitle') }}</h2>

          <div *ngIf="error" class="auth-error">{{ error }}</div>

          <label>{{ loc.translate('authUsername') }}</label>
          <input [(ngModel)]="username" name="username" type="text" autocomplete="username" />

          <label>{{ loc.translate('authPassword') }}</label>
          <input
            [(ngModel)]="password"
            name="password"
            type="password"
            autocomplete="current-password"
          />

          <ng-container *ngIf="mode === 'register'">
            <label>{{ loc.translate('authFullName') }}</label>
            <input [(ngModel)]="fullName" name="fullName" type="text" autocomplete="name" />
          </ng-container>

          <button type="submit" class="auth-ok" [disabled]="busy">
            {{
              busy
                ? loc.translate('authBusy')
                : loc.translate(mode === 'login' ? 'authLoginBtn' : 'authRegisterBtn')
            }}
          </button>

          <button type="button" class="auth-link" (click)="toggleMode()" [disabled]="busy">
            {{ loc.translate(mode === 'login' ? 'authGotoRegister' : 'authGotoLogin') }}
          </button>
          <button type="button" class="auth-guest" (click)="onGuest()" [disabled]="busy">
            {{ loc.translate('authGuest') }}
          </button>

          <button type="button" class="auth-close" (click)="closeAuth()">✕</button>
        </form>
      </div>
    </div>
  `,
  styles: [
    `
      :host { display: block; width: 100vw; height: 100vh; overflow: hidden; }
      .forest-root { position: relative; width: 100%; height: 100%; }
      canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }

      .forest-hint {
        position: absolute; bottom: 26px; left: 0; right: 0; text-align: center;
        color: #eaf6ea; font-size: 15px; letter-spacing: 0.5px;
        text-shadow: 0 2px 8px rgba(0,0,0,0.8); pointer-events: none;
        animation: hintPulse 2.4s ease-in-out infinite;
      }
      .forest-hint.hidden { display: none; }
      @keyframes hintPulse { 0%,100% { opacity: .85; } 50% { opacity: .45; } }

      .auth-modal {
        position: absolute; inset: 0; display: flex; align-items: center;
        justify-content: center; background: rgba(6,18,10,0.55); z-index: 5;
      }
      .auth-card {
        position: relative; width: 340px; background: var(--wood-panel-solid);
        border: 3px solid var(--gold-accent); border-radius: 14px; padding: 22px 26px;
        box-shadow: 0 0 0 3px rgba(0,0,0,0.35), 0 12px 40px rgba(0,0,0,0.55);
        display: flex; flex-direction: column; gap: 8px; color: var(--ink-strong);
      }
      .auth-card h2 { margin: 0 0 6px; text-align: center; color: var(--gold-accent-bright); }
      label { font-size: 0.82em; font-weight: 600; margin-top: 4px; }
      input {
        padding: 9px 11px; border-radius: 7px; border: 1px solid var(--wood-border-soft);
        background: var(--wood-inset); color: var(--ink-strong); font-size: 0.95em; outline: none;
      }
      input:focus { border-color: var(--gold-accent); box-shadow: 0 0 0 3px rgba(233,180,92,0.25); }
      .auth-ok {
        margin-top: 10px; padding: 10px; border: none; border-radius: 8px; cursor: pointer;
        background: var(--btn-gold); color: var(--ink-strong); font-weight: 700; font-size: 0.95em;
      }
      .auth-ok:disabled { opacity: 0.7; cursor: not-allowed; }
      .auth-link { background: none; border: none; color: var(--gold-accent-bright); font-size: 0.8em; cursor: pointer; }
      .auth-guest {
        padding: 8px; border-radius: 8px; border: 1px solid var(--wood-border-soft);
        background: var(--btn-wood); color: var(--ink-strong); font-weight: 600; cursor: pointer;
      }
      .auth-close {
        position: absolute; top: 6px; right: 10px; background: none; border: none;
        color: var(--ink-muted); font-size: 1.1em; cursor: pointer;
      }
      .auth-error { color: #ffbe8a; font-size: 0.8em; text-align: center; }
    `
  ]
})
export class ForestIntroComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvasEl!: ElementRef<HTMLCanvasElement>;
  @Output() entered = new EventEmitter<void>();

  showAuth = false;
  busy = false;
  error = '';
  mode: 'login' | 'register' = 'login';
  username = '';
  password = '';
  fullName = '';
  /** Public for template binding [class.hidden]. */
  passingPortal = false;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private rafId = 0;
  private clock = new THREE.Clock();
  private portal: THREE.Group | null = null;

  private path!: THREE.CatmullRomCurve3;
  private travel = 0;
  private speed = 0.06; // normalized walk speed
  private enteredEmitted = false;
  private disposed = false;

  constructor(
    public loc: LocalizationService,
    private auth: AuthService,
    private three: Scene3dService,
    private ngZone: NgZone
  ) {}

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.code !== 'Space') return;
    event.preventDefault();
    if (this.passingPortal) return;
    this.toggleAuth();
  }

  toggleAuth(): void {
    this.showAuth = !this.showAuth;
    this.mode = 'login';
    this.error = '';
  }

  closeAuth(): void {
    this.showAuth = false;
  }

  toggleMode(): void {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.error = '';
  }

  async onSubmit(): Promise<void> {
    this.busy = true;
    this.error = '';
    try {
      if (this.mode === 'login') {
        await this.auth.login(this.username, this.password);
      } else {
        await this.auth.register(this.username, this.password, this.fullName);
      }
      this.passingPortal = true;
      this.showAuth = false;
    } catch (e) {
      this.error = this.translateAuthError(e);
    } finally {
      this.busy = false;
    }
  }

  async onGuest(): Promise<void> {
    this.busy = true;
    this.error = '';
    try {
      await this.auth.guest();
      this.passingPortal = true;
      this.showAuth = false;
    } catch (e) {
      this.error = this.translateAuthError(e);
    } finally {
      this.busy = false;
    }
  }

  private translateAuthError(e: unknown): string {
    const code = (e as Error)?.message ?? '';
    switch (code) {
      case 'INVALID_CREDENTIALS': return this.loc.translate('authErrInvalid');
      case 'USERNAME_EXISTS': return this.loc.translate('authErrExists');
      case 'PASSWORD_TOO_SHORT': return this.loc.translate('authErrShort');
      case 'USERNAME_CHARS': return this.loc.translate('authErrChars');
      default: return this.loc.translate('authErrGeneric');
    }
  }

  ngAfterViewInit(): void {
    this.ngZone.runOutsideAngular(() => {
      const { renderer, scene, camera } = this.three.createRenderer(this.canvasEl.nativeElement);
      this.renderer = renderer;
      this.scene = scene;
      this.camera = camera;

      this.buildForest();
      this.raf();
      this.resize();
    });
  }

  private buildForest(): void {
    const ground = new THREE.Mesh(
      new THREE.CircleGeometry(90, 48),
      new THREE.MeshStandardMaterial({ color: 0x1c3d24, roughness: 1 })
    );
    ground.rotation.x = -Math.PI / 2;
    this.scene.add(ground);
    this.scene.fog = new THREE.Fog(0x0b2a1a, 18, 70);

    this.three.addLights(this.scene);

    // Winding path the camera auto-follows.
    this.path = new THREE.CatmullRomCurve3([
      new THREE.Vector3(-0.5, 1.6, -24),
      new THREE.Vector3(0.6, 1.6, -14),
      new THREE.Vector3(-0.4, 1.6, -5),
      new THREE.Vector3(0.3, 1.6, 4),
      new THREE.Vector3(-0.2, 1.6, 13),
      new THREE.Vector3(0.5, 1.6, 21)
    ]);

    // Trees on BOTH sides along the path.
    const treeNames = ['tree2.glb', 'tree6.glb'];
    const self = this;
    void Promise.all(treeNames.map((f) => this.three.load(f)))
      .then(([t1, t2]) => {
        if (self.disposed) return;
        for (let i = 0; i < 46; i++) {
          const t = i / 45;
          const pos = self.path.getPoint(t);
          const side = i % 2 === 0 ? 1 : -1;
          const off = 2.6 + Math.sin(i * 1.7) * 1.4;
          const model = i % 2 === 0 ? t1 : t2;
          const tree = model.clone(true);
          self.three.normalize(tree, 3 + (i % 3));
          tree.position.set(pos.x + side * off, tree.position.y, pos.z + (Math.random() - 0.5) * 1.5);
          tree.rotation.y = Math.random() * Math.PI * 2;
          self.scene.add(tree);
        }
      })
      .catch(() => {});

    // Portal at the end of the path.
    this.portal = this.makePortal();
    const end = this.path.getPoint(1);
    this.portal.position.copy(end).add(new THREE.Vector3(0, 0.9, 0.2));
    this.scene.add(this.portal);
  }

  private makePortal(): THREE.Group {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(1.1, 0.18, 16, 48),
      new THREE.MeshBasicMaterial({ color: 0xc8ff8a })
    );
    const inner = new THREE.Mesh(
      new THREE.CircleGeometry(1.05, 40),
      new THREE.MeshBasicMaterial({ color: 0x21672e })
    );
    const group = new THREE.Group();
    group.add(ring, inner);
    group.rotation.y = Math.PI / 2.4;
    return group;
  }

  private raf(): void {
    if (this.disposed) return;
    this.rafId = requestAnimationFrame(() => this.raf());

    const dt = this.clock.getDelta();

    if (!this.passingPortal) {
      // Auto-glide along the path; slight upward sway.
      if (this.travel < 1) {
        this.travel += dt * this.speed;
      }
      const p = this.path.getPoint(Math.min(this.travel, 1));
      this.camera.position.set(p.x, 1.9 + Math.sin(this.clock.elapsedTime * 0.9) * 0.05, p.z);
      const look = this.path.getPoint(Math.min(this.travel + 0.12, 1));
      this.camera.lookAt(look);
    } else {
      // Rush toward the portal, then emit `entered`.
      if (this.travel < 1) {
        this.travel += dt * this.speed * 2.4;
        const p = this.path.getPoint(Math.min(this.travel, 1));
        this.camera.position.set(p.x, 1.7, p.z);
        this.camera.lookAt(p.x, 2.2, p.z + 1);
      } else if (!this.enteredEmitted) {
        this.enteredEmitted = true;
        this.ngZone.run(() => this.entered.emit());
      }
    }

    if (this.portal) {
      this.portal.rotation.y += dt * 0.8;
      const s = 1 + Math.sin(this.clock.elapsedTime * 2.2) * 0.08;
      this.portal.scale.set(s, s, s);
    }

    this.renderer.render(this.scene, this.camera);
  }

  private resize = (): void => {
    const w = window.innerWidth, h = window.innerHeight;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h, false);
  };

  ngOnDestroy(): void {
    this.disposed = true;
    cancelAnimationFrame(this.rafId);
    window.removeEventListener('resize', this.resize);
    this.renderer?.dispose();
  }
}