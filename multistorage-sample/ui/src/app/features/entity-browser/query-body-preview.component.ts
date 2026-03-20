import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-query-body-preview',
  standalone: true,
  templateUrl: './query-body-preview.component.html',
  styleUrl: './query-body-preview.component.scss'
})
export class QueryBodyPreviewComponent {
  @Input({ required: true }) body: unknown = {};
  @Input() title = 'Request body';
  @Input({ required: true }) path = '';
  @Input() method = 'POST';

  protected formattedBody(): string {
    return JSON.stringify(this.body, null, 2);
  }
}
