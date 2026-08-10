import { Injectable, NgZone } from '@angular/core';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { PieceType } from '../models/game.models';

/**
 * type key → asset filename in public/assets/3D/
 * (whole public/ dir is copied as static assets by angular.json)
 */
const ASSET_LOADER_BASE = 'assets/3D/';

export const PIECE_MODEL_MAP: Record<PieceType, string> = {
  rat: 'Rat by Poly by Google - 6hsesZHvcPI.glb',
  cat: 'Cat by Poly by Google - 6dM1J6f6pm9.glb',
  dog: 'Beagle by Poly by Google - 0BnDT3T1wTE.glb',
  wolf: 'Wolf by Lee Mason - 7TSeDU7o6V8.glb',
  leopard: 'Jaguar by Poly by Google - 4fb-oMr2uUF.glb',
  tiger: 'Tiger by Poly by Google - 5A3w06FXUup.glb',
  lion: 'Lion by Poly by Google - 3XAJojWxSWz.glb',
  elephant: 'Elephant by Poly by Google - a27MA0rXyyj.glb'
};

export const TREE_MODELS = ['tree2.glb', 'tree6.glb'];

/**
 * Singleton shared 3D asset cache + model factory.
 * - Loads + caches each GLB exactly once (keyed by filename).
 * - clone() returns a fresh detached clone so each scene owns its own copy.
 * - normalizeGltf() re-bases a model so its largest side == 1 world unit and
 *   it sits with feet on the plane (y==0), centered horizontally.
 */
@Injectable({
  providedIn: 'root'
})
export class Scene3dService {
  private loader = new GLTFLoader();
  private cache = new Map<string, THREE.Group>();
  private pending = new Map<string, Promise<THREE.Group>>();

  constructor(private ngZone: NgZone) {}

  /** Load + cache a GLB by its filename; returns its root Group. */
  load(filename: string): Promise<THREE.Group> {
    const cached = this.cache.get(filename);
    if (cached) return Promise.resolve(cached);
    const existing = this.pending.get(filename);
    if (existing) return existing;

    const p = new Promise<THREE.Group>((resolve, reject) => {
      this.loader.load(
        ASSET_LOADER_BASE + filename,
        (gltf) => {
          const root = gltf.scene;
          this.cache.set(filename, root.clone(true));
          this.pending.delete(filename);
          resolve(root);
        },
        undefined,
        (err) => {
          this.pending.delete(filename);
          console.error(`[Scene3d] failed to load ${filename}`, err);
          reject(err);
        }
      );
    });
    this.pending.set(filename, p);
    return p;
  }

  /** Promise.all-style load of the 8 animal models (parallel). */
  loadAnimals(): Promise<Record<PieceType, THREE.Group>> {
    const entries = Object.entries(PIECE_MODEL_MAP) as [PieceType, string][];
    return Promise.all(entries.map(([t, f]) => this.load(f).then((g) => ({ t, g })))).then(
      (pairs) => {
        const out = {} as Record<PieceType, THREE.Group>;
        pairs.forEach(({ t, g }) => (out[t] = g));
        return out;
      }
    );
  }

  /** Cache a pre-added (already normalized) model under a key. */
  register(key: string, group: THREE.Group): void {
    this.cache.set(key, group);
  }

  /** Deep-clone a cached model; returns a fresh instance you may add to your scene. */
  clone(key: string): THREE.Group {
    const src = this.cache.get(key);
    if (!src) throw new Error(`[Scene3d] no cached model for "${key}"`);
    return src.clone(true);
  }

  /**
   * Normalize a loaded model in place: scale so its largest extent is `size`
   * world units, center it horizontally, and sit it on y=0.
   */
  normalize(group: THREE.Group, size: number = 1): THREE.Group {
    const box = new THREE.Box3().setFromObject(group);
    const dim = new THREE.Vector3();
    box.getSize(dim);
    const largest = Math.max(dim.x, dim.y, dim.z) || 1;
    const s = size / largest;
    group.scale.set(s, s, s);

    const box2 = new THREE.Box3().setFromObject(group);
    const center = new THREE.Vector3();
    box2.getCenter(center);
    box2.getSize(dim);

    // Translate so its footprint center is at origin and the base sits on y=0.
    group.position.x -= center.x;
    group.position.z -= center.z;
    group.position.y -= box2.min.y;
    return group;
  }

  /** Build a standard foggy forest renderer on a canvas + return renderer/scene/camera bundle. */
  createRenderer(
    canvas: HTMLCanvasElement
  ): { renderer: THREE.WebGLRenderer; scene: THREE.Scene; camera: THREE.PerspectiveCamera } {
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0b2a1a); // deep forest dusk
    const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 500);

    return { renderer, scene, camera };
  }

  /** Standard warm directional key light + subtle ambient. Call once per scene. */
  addLights(scene: THREE.Scene): void {
    const ambient = new THREE.AmbientLight(0xffffff, 0.55);
    scene.add(ambient);

    const sun = new THREE.DirectionalLight(0xffedc0, 1.0);
    sun.position.set(6, 12, 4);
    sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    scene.add(sun);

    const fill = new THREE.DirectionalLight(0xbfd8ff, 0.35);
    fill.position.set(-6, 6, -6);
    scene.add(fill);
  }

  /** Canvas-plane geometry lookup for raycasting clicks on flat boards. */
  makeGround(size: number): THREE.Mesh {
    const geo = new THREE.PlaneGeometry(size, size);
    const mat = new THREE.MeshBasicMaterial({ visible: false, transparent: true, opacity: 0 });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.rotation.x = -Math.PI / 2;
    mesh.name = 'click-plane';
    return mesh;
  }
}