import { Component } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected isAdminPage = false;

  constructor(private readonly router: Router) {
    this.isAdminPage = this.router.url.startsWith('/admin');
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.isAdminPage = this.router.url.startsWith('/admin');
      });
  }

  protected goBack(): void {
    if (window.history.length > 1) {
      window.history.back();
      return;
    }
    this.router.navigate(['/browser', 'parent']);
  }
}
