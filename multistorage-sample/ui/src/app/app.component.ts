import { Component } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { PopupNotificationComponent } from './shared/popup-notification/popup-notification.component';

type TopLink = { label: string; link: string[] };

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, PopupNotificationComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected topLinks: TopLink[] = [];

  constructor(private readonly router: Router) {
    this.updateTopLinks(this.router.url);
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.updateTopLinks(this.router.url);
      });
  }

  private updateTopLinks(url: string): void {
    const path = url.split('?')[0] ?? '';
    if (path === '/' || path === '') {
      this.topLinks = [];
      return;
    }
    const links: TopLink[] = [{ label: 'Home', link: ['/'] }];
    const detail = path.match(/^\/([^/]+)\/browser\/([^/]+)\/([^/]+)$/);
    if (detail) {
      links.push({ label: 'Back to list', link: ['/', detail[1], 'browser', detail[2]] });
      links.push({ label: 'Admin', link: ['/', detail[1], 'admin'] });
      this.topLinks = links;
      return;
    }
    const browserList = path.match(/^\/([^/]+)\/browser\/([^/]+)$/);
    if (browserList) {
      links.push({ label: 'Admin', link: ['/', browserList[1], 'admin'] });
      this.topLinks = links;
      return;
    }
    const admin = path.match(/^\/([^/]+)\/admin$/);
    if (admin) {
      links.push({ label: 'Browser', link: ['/', admin[1], 'browser', 'parent'] });
      this.topLinks = links;
      return;
    }
    this.topLinks = links;
  }
}
