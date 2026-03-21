import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type PopupNotification = {
  id: number;
  message: string;
  channel: 'info' | 'warning' | 'error';
};

@Injectable({
  providedIn: 'root'
})
export class PopupNotificationService {
  private readonly notificationsSubject = new BehaviorSubject<PopupNotification[]>([]);
  private seq = 0;

  readonly notifications$ = this.notificationsSubject.asObservable();

  show(message: string, ttlMs = 3500, channel: PopupNotification['channel'] = 'info'): void {
    const next: PopupNotification = { id: ++this.seq, message, channel };
    this.notificationsSubject.next([...this.notificationsSubject.value, next]);
    window.setTimeout(() => this.dismiss(next.id), ttlMs);
  }

  dismiss(id: number): void {
    this.notificationsSubject.next(this.notificationsSubject.value.filter((item) => item.id !== id));
  }
}
