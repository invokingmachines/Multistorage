import { Component, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { TableDiscoveryDto } from '../../core/models/multistorage-models';

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './menu.component.html',
  styleUrl: './menu.component.scss'
})
export class MenuComponent implements OnInit {
  @Input() centered = false;
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
