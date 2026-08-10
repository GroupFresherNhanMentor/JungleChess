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
import { Move, Piece, PieceType, Position, Tile } from '../../core/models/game.models';
import { GameRuleService } from '../../core/services/game-rule.service';
import { Scene3dService } from '../../core/services/scene3d.service';

export interface MoveAnimation {
  /** Monotonic id so the setter always re-fires, even on identical moves. */
  id: number;
  piece: Piece;
  from: Position;
  to: Position;
  isCapture: boolean;
}

/** World units per grid cell (7 cols × 9 rows). */
const CELL = 2.0;
/** Target largest-extent height of each animal model. */
const PIECE_SIZE = 1.15;
/** Slide duration (ms) mirrors the old 2D ghost timing. */
const SLIDE_MS = 320;
/** Height of grass pads used for land/trap/den. */
const PAD_TOP = 0.42;

/**
 * 3D Jungle Chess board. Exposes the exact same selector (`app-board`) and
 * input/output contract as the old image-grid board, so `AppComponent` keeps
 * its existing game logic / AI / battle-overlay flow untouched.
 *
 * Terrain is built procedurally: raised pads for land, a translucent water band
 * with wooden bridges down the middle, glowing trap rings, and colored cave dens
 * at both ends. Pieces are GLB animal clones which slide on `movingPiece`.
 */
@Component({
  selector: 'app-board',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="board3d-root">
      <canvas #canvas (pointerup)="onPointerUp($event)"></canvas>
    </div>
  `,
  styles: [
    `
      :host { display: block; }
      .board3d-root {
        position: relative;
        width: min(72vw, 720px);
        aspect-ratio: 7 / 9;
      }
      canvas {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        display: block;
        cursor: pointer;
        border: 4px solid var(--wood-border);
        border-radius: 12px;
        box-shadow: 0 0 0 2px var(--gold-accent), 0 6px 22px rgba(30, 15, 5, 0.6);
      }
    `
  ]
})
export class BoardComponent implements AfterViewInit, OnDestroy {
  @Input() pieces: Piece[] = [];
  @Input() selectedPos: Position | null = null;
  @Input() validMoves: Position[] = [];
  @Input() lastMove: Move | null = null;

  private _movingPiece: MoveAnimation | null = null;
  private animTimer: ReturnType<typeof setTimeout> | null = null;

  @Input()
  set movingPiece(v: MoveAnimation | null) {
    this._movingPiece = v;
    this.handleMovingPiece(v);
  }
  get movingPiece(): MoveAnimation | null {
    return this._movingPiece;
  }

  @Output() squareClick = new EventEmitter<Position>();
  @Output() moveAnimationComplete = new EventEmitter<void>();

  @ViewChild('canvas') canvasEl!: ElementRef<HTMLCanvasElement>;

  // --- three.js core ---
  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private rafId = 0;
  private clock = new THREE.Clock();
  private disposed = false;

  // --- scene nodes ---
  private boardPlane!: THREE.Mesh;
  private selRing!: THREE.Mesh;
  private validRings: THREE.Mesh[] = [];
  private captureRings: THREE.Mesh[] = [];
  private lastMoveRings: { from: THREE.Mesh; to: THREE.Mesh } | null = null;
  /** id -> piece model clone currently in the scene. */
  private livePieces = new Map<string, THREE.Group>();
  /** Normalized animal source models, keyed by PieceType. */
  private animalSources = new Map<PieceType, THREE.Group>();

  /** Active slide  animation (board input locked, click disabled). */
  private tween: {
    moveId: number;
    pieceId: string;
    piece: Piece;
    from: Position;
    to: Position;
    isCapture: boolean;
    start: number;
  } | null = null;
  private lastMoveId = 0;

  constructor(
    private ruleService: GameRuleService,
    private threeSvc: Scene3dService,
    private ngZone: NgZone
  ) {}

  ngAfterViewInit(): void {
    this.ngZone.runOutsideAngular(() => {
      const { renderer, scene, camera } = this.threeSvc.createRenderer(
        this.canvasEl.nativeElement
      );
      this.renderer = renderer;
      this.scene = scene;
      this.camera = camera;
      this.scene.fog = new THREE.Fog(0x0a1d10, 16, 46);
      this.threeSvc.addLights(scene);

      this.buildTerrain();
      this.buildOverlays();
      this.buildBoardPlane();
      this.spawnAmbient();

      void this.loadPieceSources();

      this.camera.position.set(5.2, 9.4, 12.4);
      this.camera.lookAt(0, 0.3, 0);

      this.raf();
      this.resize();
    });
  }

  /* ================= Terrain geometry ================= */

  /** Convert board col/row → world XZ center. */
  private cellCenter(c: number, r: number): THREE.Vector3 {
    return new THREE.Vector3((c - 3) * CELL, 0, (r - 4) * CELL);
  }

  /** Ground height at the foot of a tile (water sits lower than pads). */
  private tileFootY(c: number, r: number): number {
    return this.ruleService.getTileInfo(c, r).type === 'water' ? 0.16 : PAD_TOP;
  }

  private buildTerrain(): void {
    const ground = new THREE.Mesh(
      new THREE.CircleGeometry(30, 42),
      new THREE.MeshStandardMaterial({ color: 0x1b3523, roughness: 1 })
    );
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.42;
    this.scene.add(ground);

    const platform = new THREE.Mesh(
      new THREE.BoxGeometry(7 * CELL, 0.8, 9 * CELL),
      new THREE.MeshStandardMaterial({ color: 0x8a6a46, roughness: 0.85 })
    );
    platform.position.y = -0.12;
    this.scene.add(platform);

    for (let r = 0; r < 9; r++) {
      for (let c = 0; c < 7; c++) {
        this.buildCell(c, r);
      }
    }
  }

  private buildCell(c: number, r: number): void {
    const tile = this.ruleService.getTileInfo(c, r);
    const center = this.cellCenter(c, r);

    if (tile.type === 'water') {
      this.buildWaterBand(center);
      return;
    }

    const pad = new THREE.Mesh(
      new THREE.BoxGeometry(CELL * 0.9, 0.18, CELL * 0.9),
      new THREE.MeshStandardMaterial({
        color: tile.type === 'trap' ? 0x6b4a2f : tile.type === 'den' ? 0x4a3a28 : 0x3f7d3a,
        roughness: 0.9
      })
    );
    pad.position.set(center.x, PAD_TOP - 0.09, center.z);
    this.scene.add(pad);

    if (tile.type === 'trap') {
      this.buildTrap(center);
    } else if (tile.type === 'den') {
      this.buildDen(tile, center);
    }
  }

  /** Vertical translucent water column spanning the full 3-row band on this column. */
  private buildWaterBand(center: THREE.Vector3): void {
    const water = new THREE.Mesh(
      new THREE.BoxGeometry(CELL * 0.92, 0.14, CELL * 3.0),
      new THREE.MeshStandardMaterial({
        color: 0x1f5a8a,
        roughness: 0.2,
        metalness: 0.1,
        transparent: true,
        opacity: 0.78
      })
    );
    water.position.set(center.x, 0.2, 0);
    this.scene.add(water);

    // Individual wet glints under each of the 3 band cells.
    for (const oz of [-CELL, 0, CELL]) {
      const gloss = new THREE.Mesh(
        new THREE.PlaneGeometry(CELL * 0.92, CELL * 0.92),
        new THREE.MeshBasicMaterial({ color: 0x5aa0e8, transparent: true, opacity: 0.16 })
      );
      gloss.rotation.x = -Math.PI / 2;
      gloss.position.set(center.x, 0.3, oz);
      this.scene.add(gloss);
    }
  }

  private buildTrap(center: THREE.Vector3): void {
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(0.4, 0.62, 24),
      new THREE.MeshBasicMaterial({
        color: 0xffc24b,
        transparent: true,
        opacity: 0.55,
        side: THREE.DoubleSide
      })
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.set(center.x, PAD_TOP + 0.06, center.z);
    this.scene.add(ring);

    for (let i = 0; i < 4; i++) {
      const spike = new THREE.Mesh(
        new THREE.ConeGeometry(0.09, 0.24, 4),
        new THREE.MeshStandardMaterial({ color: 0x8a8a8a, roughness: 0.5 })
      );
      const a = (i / 4) * Math.PI * 2 + Math.PI / 4;
      spike.position.set(
        center.x + Math.cos(a) * 0.5,
        PAD_TOP + 0.12,
        center.z + Math.sin(a) * 0.5
      );
      this.scene.add(spike);
    }
  }

  private buildDen(tile: Tile, center: THREE.Vector3): void {
    const color = (tile.side ?? 0) === 0 ? 0x2d6bd0 : 0xc0342f;
    const mound = new THREE.Mesh(
      new THREE.DodecahedronGeometry(0.6, 0),
      new THREE.MeshStandardMaterial({ color, roughness: 0.7 })
    );
    mound.position.set(center.x, PAD_TOP + 0.22, center.z);
    mound.scale.y = 0.7;
    this.scene.add(mound);

    const rim = new THREE.Mesh(
      new THREE.TorusGeometry(0.6, 0.07, 12, 28),
      new THREE.MeshStandardMaterial({ color: 0x1a1208, roughness: 0.6 })
    );
    rim.rotation.x = Math.PI / 2;
    rim.position.set(center.x, PAD_TOP + 0.04, center.z);
    this.scene.add(rim);
  }

  /* ================= Static ring overlays ================= */

  private makeRing(color: number, outer: number, thickness: number, opacity: number): THREE.Mesh {
    const mesh = new THREE.Mesh(
      new THREE.RingGeometry(outer - thickness, outer, 28),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity,
        side: THREE.DoubleSide,
        depthWrite: false
      })
    );
    mesh.rotation.x = -Math.PI / 2;
    return mesh;
  }

  private buildOverlays(): void {
    this.selRing = this.makeRing(0x7cf0a0, 0.74, 0.14, 0.85);
    this.selRing.visible = false;
    this.scene.add(this.selRing);

    for (let i = 0; i < 8; i++) {
      const r = this.makeRing(0x66ff88, 0.7, 0.1, 0.32);
      r.visible = false;
      this.scene.add(r);
      this.validRings.push(r);
    }
    for (let i = 0; i < 4; i++) {
      const r = this.makeRing(0xff5544, 0.76, 0.14, 0.6);
      r.visible = false;
      this.scene.add(r);
      this.captureRings.push(r);
    }
  }

  private updateOverlays(): void {
    // Valid-move rings, cycling the pool.
    let vIdx = 0;
    let cIdx = 0;
    for (const m of this.validMoves) {
      const centered = this.cellCenter(m.col, m.row);
      const occupant = this.pieces.find(
        (p) => p.position.col === m.col && p.position.row === m.row
      );
      const ring = occupant ? this.captureRings[cIdx] : this.validRings[vIdx];
      if (occupant) cIdx++; else vIdx++;
      if (!ring) continue;
      ring.position.set(centered.x, 0.42, centered.z);
      ring.visible = true;
    }
    for (let i = vIdx; i < this.validRings.length; i++) this.validRings[i].visible = false;
    for (let i = cIdx; i < this.captureRings.length; i++) this.captureRings[i].visible = false;

    // Selected tile ring.
    if (this.selectedPos && !this.tween) {
      const p = this.cellCenter(this.selectedPos.col, this.selectedPos.row);
      this.selRing.position.set(p.x, 0.42, p.z);
      this.selRing.visible = true;
    } else {
      this.selRing.visible = false;
    }

    // Last-move marker.
    if (this.lastMove) {
      if (!this.lastMoveRings) {
        this.lastMoveRings = {
          from: this.makeRing(0xffe07a, 0.72, 0.1, 0.45),
          to: this.makeRing(0xffe07a, 0.72, 0.1, 0.45)
        };
        this.scene.add(this.lastMoveRings.from);
        this.scene.add(this.lastMoveRings.to);
      }
      const f = this.cellCenter(this.lastMove.from.col, this.lastMove.from.row);
      const t = this.cellCenter(this.lastMove.to.col, this.lastMove.to.row);
      this.lastMoveRings.from.position.set(f.x, 0.42, f.z);
      this.lastMoveRings.to.position.set(t.x, 0.42, t.z);
      this.lastMoveRings.from.visible = true;
      this.lastMoveRings.to.visible = true;
    } else if (this.lastMoveRings) {
      this.lastMoveRings.from.visible = false;
      this.lastMoveRings.to.visible = false;
    }
  }

  /* ================= Click handling ================= */

  private buildBoardPlane(): void {
    const plane = new THREE.Mesh(
      new THREE.PlaneGeometry(CELL * 7, CELL * 9),
      new THREE.MeshBasicMaterial({
        color: 0xffffff,
        transparent: true,
        opacity: 0,
        depthWrite: false
      })
    );
    plane.rotation.x = -Math.PI / 2;
    plane.position.y = 0.5;
    plane.name = 'board-click-plane';
    this.boardPlane = plane;
    this.scene.add(plane);
  }

  private updatePointer(ev: PointerEvent): void {
    const rect = this.canvasEl.nativeElement.getBoundingClientRect();
    this.pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1;
  }

  onPointerUp(ev: PointerEvent): void {
    if (!this.renderer || this.tween) return;
    this.updatePointer(ev);
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObject(this.boardPlane, false);
    if (hits.length === 0) return;
    const p = hits[0].point;
    const col = Math.round(p.x / CELL + 3);
    const row = Math.round(p.z / CELL + 4);
    if (col < 0 || col > 6 || row < 0 || row > 8) return;
    this.ngZone.run(() => this.squareClick.emit({ col, row }));
  }

  /* ================= Pieces ================= */

  private loadPieceSources(): void {
    void this.threeSvc.loadAnimals()?.then((models) => {
      for (const [type, grp] of Object.entries(models) as [PieceType, THREE.Group][]) {
        if (!this.pieceSources.has(type)) {
          const n = grp.clone(true);
          this.threeSvc.normalize(n, PIECE_SIZE);
          this.pieceSources.set(type, n);
        }
      }
      this.syncAllPieces();
    });
  }
  private pieceSources = new Map<PieceType, THREE.Group>();

  private syncAllPieces(): void {
    // Remove models for pieces that no longer exist.
    const liveIds = new Set(this.pieces.map((p) => p.id));
    for (const [id, model] of [...this.livePieces.entries()]) {
      if (!liveIds.has(id)) {
        this.scene.remove(model);
        this.livePieces.delete(id);
      }
    }
    // Add / reposition the rest.
    this.pieces.forEach((p) => this.syncOnePiece(p));
  }

  private syncOnePiece(p: Piece): void {
    const model = this.livePieces.get(p.id);
    if (model) {
      this.positionPiece(p, model);
      return;
    }
    const proto = this.pieceSources.get(p.type);
    if (!proto) return;
    const clone = proto.clone(true);
    const disc = new THREE.Mesh(
      new THREE.CircleGeometry(0.85, 26),
      new THREE.MeshBasicMaterial({
        color: p.side === 0 ? 0x2d6bd0 : 0xc0342f,
        transparent: true,
        opacity: 0.9
      })
    );
    disc.rotation.x = -Math.PI / 2;
    disc.position.y = 0.03;
    clone.add(disc);
    clone.userData['pieceId'] = p.id;
    this.livePieces.set(p.id, clone);
    this.positionPiece(p, clone);
    this.scene.add(clone);
  }

  private positionPiece(p: Piece, model: THREE.Group): void {
    const c = this.cellCenter(p.position.col, p.position.row);
    model.position.set(c.x, this.tileFootY(p.position.col, p.position.row), c.z);
  }

  /* ================= Ghost tween ================= */

  private handleMovingPiece(v: MoveAnimation | null): void {
    if (this.animTimer) {
      clearTimeout(this.animTimer);
      this.animTimer = null;
    }
    if (!v) {
      this.tween = null;
      return;
    }
    // Ignore re-set of the same animation (e.g. CD re-run).
    if (this.lastMoveId === v.id) {
      return;
    }
    this.lastMoveId = v.id;
    this.tween = {
      moveId: v.id,
      pieceId: v.piece.id,
      piece: v.piece,
      from: v.from,
      to: v.to,
      isCapture: v.isCapture,
      start: performance.now()
    };
    this.animTimer = setTimeout(() => {
      this.animTimer = null;
      this.tween = null;
      this.ngZone.run(() => this.moveAnimationComplete.emit());
    }, SLIDE_MS + 60);
  }

  private updateTween(now: number): void {
    if (!this.tween) return;
    const { from, to } = this.tween;
    const progress = Math.min((now - this.tween.start) / SLIDE_MS, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic

    const f = this.cellCenter(from.col, from.row);
    const t = this.cellCenter(to.col, to.row);
    const model = this.livePieces.get(this.tween.pieceId);
    if (model) {
      model.position.x = f.x + (t.x - f.x) * eased;
      model.position.z = f.z + (t.z - f.z) * eased;
      model.position.y =
        this.tileFootY(from.col, from.row) + Math.sin(progress * Math.PI) * (this.tween.isCapture ? 1.4 : 0.55);
    }
  }

  /* ================= Ambient decor ================= */

  private spawnAmbient(): void {
    const treeNames = ['tree2.glb', 'tree6.glb'];
    void Promise.all(treeNames.map((f) => this.threeSvc.load(f)))
      .then(([t1, t2]) => {
        if (this.disposed) return;
        for (let i = 0; i < 20; i++) {
          const a = (i / 20) * Math.PI * 2;
          const dist = 11 + (i % 3) * 1.5;
          const model = (i % 2 === 0 ? t1 : t2).clone(true);
          this.threeSvc.normalize(model, 2.4 + (i % 4));
          model.position.set(
            Math.cos(a) * dist,
            model.position.y - 0.1,
            Math.sin(a) * dist
          );
          model.rotation.y = Math.random() * Math.PI * 2;
          this.scene.add(model);
        }
      })
      .catch(() => {});
  }

  /* ================= RAF loop ================= */

  private raf(): void {
    if (this.disposed) return;
    this.rafId = requestAnimationFrame(() => this.raf());
    const now = performance.now();
    this.updateOverlays();
    this.updateTween(now);
    this.camera.position.y = 9.4 + Math.sin(this.clock.elapsedTime * 0.5) * 0.15;
    this.camera.lookAt(0, 0.3, 0);
    this.renderer.render(this.scene, this.camera);
  }

  /* ================= Resize / destroy ================= */

  private resize = (): void => {
    const w = window.innerWidth;
    const h = window.innerHeight;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h, false);
  };

  ngOnDestroy(): void {
    this.disposed = true;
    cancelAnimationFrame(this.rafId);
    window.removeEventListener('resize', this.resize);
    if (this.animTimer) clearTimeout(this.animTimer);
    this.renderer?.dispose();
  }
}