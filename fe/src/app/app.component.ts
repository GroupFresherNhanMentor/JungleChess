import { Component, ChangeDetectorRef, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { FallingDecorComponent } from './components/falling-decor/falling-decor.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, FallingDecorComponent],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly ngZone = inject(NgZone);

  /** True while the camera-zoom transition overlay is covering the screen. */
  viewTransitioning = false;
  /** Reverse = zooming back OUT to the lobby (game → lobby). */
  viewTransitionReverse = false;

  private viewTransitionTimer: ReturnType<typeof setTimeout> | null = null;
  private prevUrl = '';
  private firstNav = true;

  ngOnInit(): void {
    // Watch route changes: lobby → /game/:roomId zooms INTO the forest;
    // back to /lobby from a game zooms back OUT.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationStart))
      .subscribe((e) => {
        const url = (e as NavigationStart).url;
        const toGame = url.startsWith('/game');
        const fromGame = this.prevUrl.startsWith('/game');
        const toLobby = url === '/lobby' || url === '/';

        if (!this.firstNav && (toGame || (fromGame && toLobby))) {
          this.playViewTransition(() => {}, toGame ? false : true);
        }
        this.firstNav = false;
        this.prevUrl = url;
      });
  }

  /**
   * Camera-zoom transition between lobby and game. Forward (lobby → game):
   * the overlay zooms INTO the forest while the game view fades in on top.
   * Reverse (game → lobby): a zoomed overlay shrinks back to the lobby.
   */
  private playViewTransition(done: () => void, reverse = false): void {
    this.ngZone.run(() => {
      if (this.viewTransitionTimer) {
        clearTimeout(this.viewTransitionTimer);
        this.viewTransitionTimer = null;
      }

      // A fresh <div> is created each time by *ngIf, so the zoom animation
      // always restarts cleanly for both directions.
      this.viewTransitionReverse = reverse;
      this.viewTransitioning = true;
      this.cdr.detectChanges();

      // Let the overlay paint at its initial state before the zoom starts.
      requestAnimationFrame(() => {
        this.viewTransitionTimer = setTimeout(() => {
          this.ngZone.run(() => {
            this.viewTransitioning = false;
            this.cdr.detectChanges();
          });
        }, 1000);
      });

      done();
    });
  }

  ngOnDestroy(): void {
    if (this.viewTransitionTimer) clearTimeout(this.viewTransitionTimer);
    this.viewTransitionTimer = null;
  }
}
