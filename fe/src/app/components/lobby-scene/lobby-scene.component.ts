import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { Scene3dService } from '../../core/services/scene3d.service';
import { LocalizationService } from '../../core/services/localization.service';
import { AuthService } from '../../core/services/auth.service';
import { RoomInfo, PieceSide } from '../../core/models/game.models';

export interface LobbySelection {
  room: RoomInfo;
  side: PieceSide;
}

/**
 * Interactive 3D lobby: a clearing with a wooden hall backdrop where each room
 * is a glowing stone portal doorway the player can click (raycast). Rooms are
 * laid out in an arc in front of the camera; a DOM HUD shows their names and
 * lets you filter / create / join-by-code.
 */
@Component({
  selector: 'app-lobby-scene',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="lobby-root">
      <canvas #canvas (pointerup)="onPointerUp($event)"></canvas>

      <!-- HUD -->
      <div class="lobby-hud">
        <div class="lobby-user" *ngIf="user">
          <span class="lobby-avatar">{{ user.fullName.charAt(0) || '?' }}</span>
          <span>{{ user.fullName }}</span>
          <button class="hud-logout" (click)="logout.emit()">✕</button>
        </div>
        <div class="lobby-footer">
          <button class="hud-btn" (click)="createRoom.emit()">＋ {{ loc.translate('createRoomBtn') }}</button>
          <button class="hud-btn" (click)="joinCode.emit()">⌁ {{ loc.translate('joinRoomBtn') }}</button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      :host { display: block; width: 100vw; height: 100vh; overflow: hidden; }
      .lobby-root { position: relative; width: 100%; height: 100%; }
      canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; cursor: pointer; }

      .lobby-hud { position: absolute; top: 0; left: 0; right: 0; display: flex;
        flex-direction: column; padding: 14px 20px 0; align-items: stretch; pointer-events: none; z-index: 3; }
      .lobby-user {
        background: var(--wood-panel-solid); border: 2px solid var(--gold-accent);
        border-radius: 999px; color: var(--ink-strong); align-self: flex-end;
        display: flex; align-items: center; gap: 8px; padding: 6px 12px; pointer-events: auto;
        font-size: 0.9em; font-weight: 600;
      }
      .lobby-avatar {
        width: 24px; height: 24px; border-radius: 50%; background: var(--btn-gold);
        display: inline-flex; align-items: center; justify-content: center; font-weight: 800;
      }
      .hud-logout { background: none; border: none; color: var(--clan-red-bright); cursor: pointer; }

      .lobby-footer {
        margin-top: auto; margin-bottom: 20px; display: flex; justify-content: center; gap: 18px;
        pointer-events: auto;
      }
      .hud-btn {
        padding: 12px 26px; border: none; border-radius: 10px; cursor: pointer;
        background: var(--btn-gold); color: var(--ink-strong); font-size: 1em; font-weight: 700;
        box-shadow: 0 6px 18px rgba(0,0,0,0.45); transition: transform 0.12s ease;
      }
      .hud-btn:hover { transform: translateY(-2px); background: var(--btn-gold-hot); }

      .lobby-fog { position: absolute; inset: 0; pointer-events: none; z-index: 1;
        background: radial-gradient(ellipse at center, transparent 45%, rgba(8,24,12,0.6)); }
    `
  ]
})
export class LobbyScene3DComponent implements AfterViewInit, OnDestroy {
  @Input() rooms: RoomInfo[] = [];
  @Input() selectedRoom: RoomInfo | null = null;
  @Output() selectRoom = new EventEmitter<LobbySelection>();
  @Output() createRoom = new EventEmitter<void>();
  @Output() joinCode = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();

  @ViewChild('canvas') canvasEl!: ElementRef<HTMLCanvasElement>;

  constructor(
    public loc: LocalizationService,
    public auth: AuthService,
    private three: Scene3dService,
    private ngZone: NgZone
  ) {}

  get user() { return this.auth.currentUser; }

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private roomGroups: { room: RoomInfo; obj: THREE.Object3D }[] = [];
  private rafId = 0;
  private clock = new THREE.Clock();
  private disposed = false;

  ngAfterViewInit(): void {
    this.ngZone.runOutsideAngular(() => {
      const { renderer, scene, camera } = this.three.createRenderer(this.canvasEl.nativeElement);
      this.renderer = renderer;
      this.scene = scene;
      this.camera = camera;
      this.buildWorld();
      this.raf();
      this.resize();
    });
  }

  private buildWorld(): void {
    const ground = new THREE.Mesh(
      new THREE.CircleGeometry(60, 40),
      new THREE.MeshStandardMaterial({ color: 0x22402a, roughness: 1 })
    );
    ground.rotation.x = -Math.PI / 2;
    this.scene.add(ground);
    this.scene.fog = new THREE.Fog(0x0b1d10, 20, 60);
    this.three.addLights(this.scene);

    // A ring of ambient trees around the platform.
    const treeNames = ['tree2.glb', 'tree6.glb'];
    void Promise.all(treeNames.map((f) => this.three.load(f)))
      .then(([t1, t2]) => {
        if (this.disposed) return;
        for (let i = 0; i < 24; i++) {
          const a = (i / 24) * Math.PI * 2;
          const r = 16 + (i % 2) * 3;
          const model = i % 2 === 0 ? t1 : t2;
          const tree = model.clone(true);
          this.three.normalize(tree, 2.5 + (i % 3));
          tree.position.set(Math.cos(a) * r, tree.position.y, Math.sin(a) * r);
          tree.rotation.y = Math.random() * Math.PI * 2;
          this.scene.add(tree);
        }
      })
      .catch(() => {});

    // Camera: fixed seated position looking at the portal row.
    this.camera.position.set(0, 3.2, 12);
    this.camera.lookAt(0, 1.1, 0);

    // Build room portals from the supplied rooms.
    this.buildPortals();
  }

  private buildPortals(): void {
    this.roomGroups.forEach(({ obj }) => this.scene.remove(obj));
    this.roomGroups = [];

    const avail = this.rooms.slice(0, 6); // only first 5-ish shown in 3D
    avail.forEach((room, i) => {
      const par = this.makePortal(room, i);
      const x = (i - (avail.length - 1) / 2) * 2.4;
      par.position.set(x, 0, 0);
      this.scene.add(par);
      this.roomGroups.push({ room, obj: par });
    });
  }

  /** A stone ring doorway with the room's name floating on a small sign. */
  private makePortal(room: RoomInfo, index: number): THREE.Group {
    const g = new THREE.Group();

    const base = new THREE.Mesh(
      new THREE.CylinderGeometry(1.25, 1.5, 0.4, 28),
      new THREE.MeshStandardMaterial({ color: 0x6e6a5a, roughness: 0.9 })
    );
    base.position.y = 0.2;
    g.add(base);

    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(0.9, 0.12, 16, 40),
      new THREE.MeshStandardMaterial({ color: room.status === 'PLAYING' ? 0xc24c4c : 0x74c05e, roughness: 0.35 })
    );
    ring.position.y = 1.7;
    g.add(ring);

    const inner = new THREE.Mesh(
      new THREE.CircleGeometry(0.9, 36),
      new THREE.MeshBasicMaterial({ color: room.status === 'PLAYING' ? 0x3c1a1a : 0x123b16 })
    );
    inner.position.y = 1.7;
    g.add(inner);

    // Sign post with a simple name badge (we use a small plane, text is DOM HUD)
    const sign = new THREE.Mesh(
      new THREE.BoxGeometry(1.0, 0.5, 0.06),
      new THREE.MeshStandardMaterial({ color: 0xc9a05a, roughness: 0.6 })
    );
    sign.position.set(0, 0.9, 0.78);
    g.add(sign);

    g.userData = { roomId: room.roomId };
    return g;
  }

  private onPointer(ev: PointerEvent): void {
    const rect = this.canvasEl.nativeElement.getBoundingClientRect();
    this.pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1;
  }

  onPointerUp(ev: PointerEvent): void {
    if (!this.renderer) return;
    this.onPointer(ev);
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.roomGroups.map((r) => r.obj), true);
    if (hits.length) {
      const found = this.findRoomByObject(hits[0].object);
      if (found) {
        this.ngZone.run(() => this.selectRoom.emit({ room: found.room, side: 1 }));
      }
    }
  }

  private findRoomByObject(obj: THREE.Object3D): { room: RoomInfo } | null {
    let cur: THREE.Object3D | null = obj;
    while (cur) {
      const roomId = cur.userData?.['roomId'] as string | undefined;
      if (roomId) {
        const room = this.rooms.find((r) => r.roomId === roomId);
        if (room) return { room };
      }
      cur = cur.parent ?? null;
    }
    return null;
  }

  private raf(): void {
    if (this.disposed) return;
    this.rafId = requestAnimationFrame(() => this.raf());
    const dt = this.clock.getDelta();
    this.roomGroups.forEach(({ obj }) => {
      obj.rotation.y += dt * 0.4;
    });
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