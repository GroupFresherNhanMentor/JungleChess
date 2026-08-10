import { Injectable } from '@angular/core';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

export const GLTF_MODEL_MAP: Record<string, string> = {
  rat: 'assets/3D/Rat by Poly by Google - 6hsesZHvcPI.glb',
  cat: 'assets/3D/Cat by Poly by Google - 6dM1J6f6pm9.glb',
  wolf: 'assets/3D/Wolf by Lee Mason - 7TSeDU7o6V8.glb',
  dog: 'assets/3D/Beagle by Poly by Google - 0BnDT3T1wTE.glb',
  leopard: 'assets/3D/Jaguar by Poly by Google - 4fb-oMr2uUF.glb',
  tiger: 'assets/3D/Tiger by Poly by Google - 5A3w06FXUup.glb',
  lion: 'assets/3D/Lion by Poly by Google - 3XAJojWxSWz.glb',
  elephant: 'assets/3D/Elephant by Poly by Google - a27MA0rXyyj.glb'
};

@Injectable({
  providedIn: 'root'
})
export class ModelLoaderService {
  private loader = new GLTFLoader();
  private cache = new Map<string, THREE.Group>();
  private loadingPromises = new Map<string, Promise<THREE.Group>>();

  /**
   * Preload a 3D GLTF model by animal type
   */
  public loadModel(type: string): Promise<THREE.Group> {
    if (this.cache.has(type)) {
      return Promise.resolve(this.cache.get(type)!.clone(true));
    }

    if (this.loadingPromises.has(type)) {
      return this.loadingPromises.get(type)!.then((group) => group.clone(true));
    }

    const url = GLTF_MODEL_MAP[type.toLowerCase()];
    if (!url) {
      return Promise.reject(new Error(`No GLTF model path found for type: ${type}`));
    }

    const promise = new Promise<THREE.Group>((resolve, reject) => {
      this.loader.load(
        url,
        (gltf) => {
          const sceneGroup = gltf.scene;

          // Enable shadows and auto-center model bounding box
          sceneGroup.traverse((child) => {
            if ((child as THREE.Mesh).isMesh) {
              child.castShadow = true;
              child.receiveShadow = true;
            }
          });

          this.cache.set(type, sceneGroup);
          this.loadingPromises.delete(type);
          resolve(sceneGroup.clone(true));
        },
        undefined,
        (err) => {
          this.loadingPromises.delete(type);
          reject(err);
        }
      );
    });

    this.loadingPromises.set(type, promise);
    return promise;
  }
}
