import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * One ambient falling leaf/flower. The outer element falls straight down and
 * carries the gust push (windX/windY, 0 while calm); the inner element does the
 * continuous left-right flutter. Leaves are grouped into 4 horizontal bands
 * (cluster) so a gust visibly blows through one clump of the canopy at a time.
 */
interface FallingItem {
  id: number;
  left: number;        // vw %
  size: number;        // px
  duration: number;    // fall seconds
  delay: number;       // seconds (negative = already mid-fall on load)
  char: string;
  opacity: number;
  swayAmp: number;     // px left-right range of the idle flutter
  swayDur: number;     // s one-way of the flutter
  cluster: number;     // 0..3 horizontal band this leaf belongs to
  windX: number;       // px pushed sideways while this leaf's cluster is gusting
  windY: number;       // px lifted while gusting
  gustDelay: number;   // s stagger so a gust sweeps across the band, not at once
}

@Component({
  selector: 'app-falling-decor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './falling-decor.component.html',
  styleUrl: './falling-decor.component.css'
})
export class FallingDecorComponent implements OnInit, OnDestroy {
  items: FallingItem[] = [];

  private readonly CHARS = ['🍂', '🍁', '🍃', '🌸', '🌺', '🌼'];
  private readonly COUNT = 24;
  private readonly CLUSTERS = 4;
  /** How long the gust holds its push before easing back to calm (ms). */
  private readonly GUST_HOLD = 3000;

  private gustTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    const items: FallingItem[] = [];
    for (let i = 0; i < this.COUNT; i++) {
      const left = Math.random() * 100;
      items.push({
        id: i,
        left,
        size: 18 + Math.random() * 22,
        duration: 9 + Math.random() * 11,   // 9–20s to cross the screen
        delay: -Math.random() * 20,         // negative → spread across the sky at load
        char: this.CHARS[Math.floor(Math.random() * this.CHARS.length)],
        opacity: 0.5 + Math.random() * 0.45,
        swayAmp: 16 + Math.random() * 30,   // 16–46px of visible flutter
        swayDur: 1.8 + Math.random() * 2,   // 1.8–3.8s per one-way swing
        cluster: Math.min(this.CLUSTERS - 1, Math.floor(left / (100 / this.CLUSTERS))),
        windX: 0,
        windY: 0,
        gustDelay: Math.random() * 1.5
      });
    }
    this.items = items;
    this.scheduleNextGust();
  }

  /** Reuse the same element across gust state changes (id never changes). */
  trackById(_index: number, item: FallingItem): number {
    return item.id;
  }

  ngOnDestroy(): void {
    if (this.gustTimer) {
      clearTimeout(this.gustTimer);
      this.gustTimer = null;
    }
  }

  /**
   * A gust blows every ~10s. One random cluster (a horizontal band of leaves)
   * gets pushed sideways + slightly lifted for GUST_HOLD, then eases back to
   * its normal straight fall. The other clusters keep fluttering as usual.
   */
  private scheduleNextGust(): void {
    const wait = 8000 + Math.random() * 5000; // 8–13s, ~every 10s
    this.gustTimer = setTimeout(() => this.startGust(), wait);
  }

  private startGust(): void {
    const gustDir = Math.random() < 0.5 ? 1 : -1;                 // blow left or right
    const cluster = Math.floor(Math.random() * this.CLUSTERS);    // one clump per gust
    this.items = this.items.map((it) =>
      it.cluster === cluster
        ? {
            ...it,
            windX: gustDir * (80 + Math.random() * 80),  // 80–160px sideways
            windY: -(10 + Math.random() * 20)             // slight lift
          }
        : { ...it, windX: 0, windY: 0 }
    );

    this.gustTimer = setTimeout(() => {
      this.items = this.items.map((it) => ({ ...it, windX: 0, windY: 0 }));
      this.scheduleNextGust();
    }, this.GUST_HOLD);
  }
}