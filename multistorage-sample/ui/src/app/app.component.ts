import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { MultistorageApiService } from './core/api/multistorage-api.service';
import { TableDiscoveryDto } from './core/models/multistorage-models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  protected tables: TableDiscoveryDto[] = [];
  protected loadingMeta = false;
  protected errorMessage: string | null = null;

  constructor(private readonly api: MultistorageApiService) {}

  ngOnInit(): void {
    this.loadMeta();
  }

  private loadMeta(): void {
    this.loadingMeta = true;
    this.api
      .getDiscovery()
      .pipe(finalize(() => (this.loadingMeta = false)))
      .subscribe({
        next: (discovery) => {
          this.tables = discovery.tables ?? [];
        },
        error: () => {
          this.errorMessage = 'Failed to load metadata.';
        }
      });
  }
}
