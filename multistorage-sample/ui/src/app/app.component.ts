import { Component } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { PopupNotificationComponent } from './shared/popup-notification/popup-notification.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, PopupNotificationComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected isHomePage = false;
  protected topButtonLabel = 'Home';
  protected topButtonLink: string[] = ['/'];

  constructor(private readonly router: Router) {
    this.updateTopButtonState(this.router.url);
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.updateTopButtonState(this.router.url);
      });
  }

  private updateTopButtonState(url: string): void {
    this.isHomePage = url === '/';
    if (this.isHomePage) {
      this.topButtonLabel = 'Admin';
      this.topButtonLink = ['/admin'];
      return;
    }
    const detailMatch = url.match(/^\/browser\/([^/]+)\/[^/]+/);
    if (detailMatch) {
      this.topButtonLabel = 'Back to list';
      this.topButtonLink = ['/browser', detailMatch[1]];
      return;
    }
    this.topButtonLabel = 'Home';
    this.topButtonLink = ['/'];
  }
}
