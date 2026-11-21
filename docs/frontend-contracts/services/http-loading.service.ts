import { Injectable, signal } from '@angular/core';

/** One shared, reference-counted loader for every HttpClient request. */
@Injectable({ providedIn: 'root' })
export class HttpLoadingService {
  private readonly pending = signal(0);
  readonly visible = signal(false);
  private revealTimer: ReturnType<typeof setTimeout> | null = null;

  begin(): void {
    this.pending.update(count => count + 1);
    if (this.pending() === 1) {
      this.revealTimer = setTimeout(() => {
        if (this.pending() > 0) this.visible.set(true);
        this.revealTimer = null;
      }, 170);
    }
  }

  end(): void {
    this.pending.update(count => Math.max(0, count - 1));
    if (this.pending() !== 0) return;

    if (this.revealTimer !== null) {
      clearTimeout(this.revealTimer);
      this.revealTimer = null;
    }
    this.visible.set(false);
  }
}
