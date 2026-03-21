import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { PopupNotificationService } from '../../core/ui/popup-notification.service';

@Component({
  selector: 'app-popup-notification',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './popup-notification.component.html',
  styleUrl: './popup-notification.component.scss'
})
export class PopupNotificationComponent {
  protected readonly notifications$;

  constructor(private readonly notifications: PopupNotificationService) {
    this.notifications$ = this.notifications.notifications$;
  }

  protected dismiss(id: number): void {
    this.notifications.dismiss(id);
  }
}
