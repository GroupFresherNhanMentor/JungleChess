import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { ModelLoaderService } from '../../core/services/model-loader.service';
import { PieceSide } from '../../core/models/game.models';

@Component({
  selector: 'app-piece-3d',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './piece-3d.component.html',
  styleUrl: './piece-3d.component.css'
})
export class Piece3dComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() type: string = 'rat';
  @Input() side: PieceSide = 0; // 0: Blue, 1: Red

  @ViewChild('canvasContainer', { static: false })
  containerRef!: ElementRef<HTMLDivElement>;

  isLoading: boolean = true;
  hasError: boolean = false;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private animFrameId: number | null = null;
  private modelGroup: THREE.Group | null = null;

  constructor(private modelLoader: ModelLoaderService) {}

  ngAfterViewInit(): void {
    this.initThreeScene();
    this.load3DModel();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      (changes['type'] && !changes['type'].isFirstChange()) ||
      (changes['side'] && !changes['side'].isFirstChange())
    ) {
      this.load3DModel();
    }
  }

  ngOnDestroy(): void {
    if (this.animFrameId !== null) {
      cancelAnimationFrame(this.animFrameId);
    }
    if (this.renderer) {
      this.renderer.dispose();
    }
  }

  private initThreeScene(): void {
    if (!this.containerRef) return;
    const container = this.containerRef.nativeElement;
    const width = container.clientWidth || 60;
    const height = container.clientHeight || 60;

    // 1. Scene
    this.scene = new THREE.Scene();

    // 2. Camera
    this.camera = new THREE.PerspectiveCamera(40, width / height, 0.1, 100);
    this.camera.position.set(0, 1.4, 2.8);
    this.camera.lookAt(0, 0.1, 0);

    // 3. Renderer
    this.renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      preserveDrawingBuffer: true
    });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;

    container.appendChild(this.renderer.domElement);

    // 4. Lighting
    const ambientLight = new THREE.AmbientLight(0xffffff, 1.4);
    this.scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 1.8);
    dirLight.position.set(3, 5, 4);
    dirLight.castShadow = true;
    this.scene.add(dirLight);

    const fillLight = new THREE.DirectionalLight(0x90caf9, 0.6);
    fillLight.position.set(-3, 2, -2);
    this.scene.add(fillLight);
  }

  private async load3DModel(): Promise<void> {
    if (!this.scene) return;

    this.isLoading = true;
    this.hasError = false;

    // Remove existing model if any
    if (this.modelGroup) {
      this.scene.remove(this.modelGroup);
      this.modelGroup = null;
    }

    try {
      const animalModel = await this.modelLoader.loadModel(this.type);

      // Create team base pedestal (3D Disc / Cylinder)
      const baseColor = this.side === 0 ? 0x2563eb : 0xdc2626; // Blue or Red
      const ringColor = this.side === 0 ? 0x60a5fa : 0xf87171;

      const baseGeo = new THREE.CylinderGeometry(0.85, 0.9, 0.18, 32);
      const baseMat = new THREE.MeshStandardMaterial({
        color: baseColor,
        roughness: 0.3,
        metalness: 0.2
      });
      const baseMesh = new THREE.Mesh(baseGeo, baseMat);
      baseMesh.position.set(0, -0.09, 0);
      baseMesh.receiveShadow = true;

      // Outer highlight ring
      const ringGeo = new THREE.TorusGeometry(0.88, 0.04, 16, 32);
      const ringMat = new THREE.MeshBasicMaterial({ color: ringColor });
      const ringMesh = new THREE.Mesh(ringGeo, ringMat);
      ringMesh.rotation.x = Math.PI / 2;
      ringMesh.position.set(0, 0, 0);

      // Auto scale & center animal model
      const box = new THREE.Box3().setFromObject(animalModel);
      const size = new THREE.Vector3();
      box.getSize(size);
      const maxDim = Math.max(size.x, size.y, size.z);
      const scale = maxDim > 0 ? 1.2 / maxDim : 1;

      animalModel.scale.set(scale, scale, scale);

      // Recalculate box after scaling
      const scaledBox = new THREE.Box3().setFromObject(animalModel);
      const center = new THREE.Vector3();
      scaledBox.getCenter(center);

      animalModel.position.x = -center.x;
      animalModel.position.y = -scaledBox.min.y;
      animalModel.position.z = -center.z;

      // Group together base & model
      this.modelGroup = new THREE.Group();
      this.modelGroup.add(baseMesh);
      this.modelGroup.add(ringMesh);
      this.modelGroup.add(animalModel);

      // Face towards player angle
      this.modelGroup.rotation.y = this.side === 0 ? 0 : Math.PI;

      this.scene.add(this.modelGroup);
      this.isLoading = false;

      // Start animation render loop
      this.animate();
    } catch (err) {
      console.warn(`Failed to load 3D model for piece type: ${this.type}`, err);
      this.isLoading = false;
      this.hasError = true;
    }
  }

  private animate = (): void => {
    this.animFrameId = requestAnimationFrame(this.animate);

    if (this.modelGroup) {
      // Gentle idle rotation so 3D effect is clearly visible
      this.modelGroup.rotation.y += 0.008;
    }

    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  };
}
