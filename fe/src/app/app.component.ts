import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** A single firework particle; dx/dy precomputed in px (no CSS trig). */
interface FireworkParticle {
  dx: number;
  dy: number;
  size: number;
  delay: number;
  color: string;
}

/** One radially-symmetric explosion anchored at a viewport %. */
interface FireworkBurst {
  id: number;
  x: number; // vw %
  y: number; // vh %
  hue: 'blue' | 'red' | 'gold';
  particles: FireworkParticle[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class AppComponent {}
