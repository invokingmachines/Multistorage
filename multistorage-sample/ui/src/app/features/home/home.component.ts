import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { TenantResponse } from '../../core/models/multistorage-models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  protected tenants: TenantResponse[] = [];
  protected loading = false;
  protected errorMessage: string | null = null;

  constructor(private readonly api: MultistorageApiService) {}

  ngOnInit(): void {
    this.api.clearTenantCode();
    this.loading = true;
    this.api
      .listTenants()
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (rows) => {
          this.tenants = rows ?? [];
        },
        error: () => {
          this.errorMessage = 'Failed to load tenants.';
        }
      });
  }
}
