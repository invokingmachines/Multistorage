import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { ColumnDiscoveryDto, SearchRequest, TableDiscoveryDto } from '../../core/models/multistorage-models';
import { EntityMultiselectComponent } from './entity-multiselect.component';
import { QueryBodyPreviewComponent } from './query-body-preview.component';
import { MenuComponent } from '../menu/menu.component';

@Component({
  selector: 'app-entity-browser',
  standalone: true,
  imports: [QueryBodyPreviewComponent, MenuComponent, EntityMultiselectComponent],
  templateUrl: './entity-browser.component.html',
  styleUrl: './entity-browser.component.scss'
})
export class EntityBrowserComponent implements OnInit {
  protected readonly pageSize = 20;
  protected selectedTable: TableDiscoveryDto | null = null;
  protected selectedColumns: ColumnDiscoveryDto[] = [];
  protected rows: Array<Record<string, unknown>> = [];
  protected loading = false;
  protected errorMessage: string | null = null;
  protected filters: Record<string, { text?: string; intValue?: string; dateFrom?: string; dateTo?: string }> = {};
  protected requestBody: SearchRequest = { page: 0, size: 20 };
  protected responseBody: unknown = {};
  protected requestPath = '';
  protected sortBy = '';
  protected sortOrder: 'ASC' | 'DESC' = 'ASC';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: MultistorageApiService
  ) {}

  ngOnInit(): void {
    this.api.searchDebug$.subscribe((debug) => {
      this.requestPath = debug.path || this.requestPath;
      this.requestBody = debug.request ?? { page: 0, size: 20 };
      this.responseBody = debug.responseSample ?? {};
    });
    this.route.paramMap.subscribe((params) => {
      const activeEntity = params.get('activeEntity');
      if (!activeEntity) {
        this.errorMessage = 'Entity is not selected.';
        this.selectedTable = null;
        this.selectedColumns = [];
        this.rows = [];
        return;
      }
      this.loadEntity(activeEntity);
    });
  }

  protected cellValue(row: Record<string, unknown>, column: ColumnDiscoveryDto): string {
    const key = this.columnKey(column);
    const value = row[key];
    return value === null || value === undefined ? '' : String(value);
  }

  private columnKey(column: ColumnDiscoveryDto): string {
    return column.alias?.trim() ? column.alias : column.name;
  }

  private loadEntity(activeEntity: string): void {
    this.loading = true;
    this.errorMessage = null;
    this.api
      .getDiscovery()
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (discovery) => {
          const table = (discovery.tables ?? []).find((item) => item.pathSegment === activeEntity) ?? null;
          if (!table) {
            this.selectedTable = null;
            this.selectedColumns = [];
            this.rows = [];
            this.errorMessage = `Entity "${activeEntity}" is not found in metadata.`;
            return;
          }

          this.selectedTable = table;
          this.selectedColumns = table.columns ?? [];
          this.rows = [];
          this.filters = {};
          this.requestPath = `/multistorage/api/${table.pathSegment}/search`;
          this.sortBy = '';
          this.sortOrder = 'ASC';
          this.search();
        },
        error: () => {
          this.errorMessage = 'Failed to load metadata.';
        }
      });
  }

  protected onFilterChange(column: ColumnDiscoveryDto, value: string): void {
    const key = this.columnKey(column);
    this.filters = { ...this.filters, [key]: { ...this.filterState(column), text: value } };
  }

  protected filterValue(column: ColumnDiscoveryDto): string {
    const key = this.columnKey(column);
    return this.filters[key]?.text ?? '';
  }

  protected onIntFilterChange(column: ColumnDiscoveryDto, value: string): void {
    const key = this.columnKey(column);
    this.filters = { ...this.filters, [key]: { ...this.filterState(column), intValue: value } };
  }

  protected intFilterValue(column: ColumnDiscoveryDto): string {
    const key = this.columnKey(column);
    return this.filters[key]?.intValue ?? '';
  }

  protected onDateFromChange(column: ColumnDiscoveryDto, value: string): void {
    const key = this.columnKey(column);
    this.filters = { ...this.filters, [key]: { ...this.filterState(column), dateFrom: value } };
  }

  protected onDateToChange(column: ColumnDiscoveryDto, value: string): void {
    const key = this.columnKey(column);
    this.filters = { ...this.filters, [key]: { ...this.filterState(column), dateTo: value } };
  }

  protected dateFromValue(column: ColumnDiscoveryDto): string {
    const key = this.columnKey(column);
    return this.filters[key]?.dateFrom ?? '';
  }

  protected dateToValue(column: ColumnDiscoveryDto): string {
    const key = this.columnKey(column);
    return this.filters[key]?.dateTo ?? '';
  }

  protected isIntegerColumn(column: ColumnDiscoveryDto): boolean {
    const type = this.columnType(column);
    return /(^|[^a-z])(int|int2|int4|int8|smallint|integer|bigint|serial|bigserial)($|[^a-z])/.test(type);
  }

  protected isDateColumn(column: ColumnDiscoveryDto): boolean {
    const type = this.columnType(column);
    return /(date|timestamp|time)/.test(type);
  }

  protected isSearchableColumn(column: ColumnDiscoveryDto): boolean {
    return column.searchable !== false;
  }

  protected search(): void {
    if (!this.selectedTable) {
      return;
    }
    const request = this.buildRequest();
    this.requestBody = request;
    this.loading = true;
    this.api
      .search(this.selectedTable.pathSegment, request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (result) => {
          this.rows = result.content ?? [];
          this.responseBody = this.rows[0] ?? {};
        },
        error: () => {
          this.errorMessage = 'Failed to load table data.';
          this.responseBody = {};
        }
      });
  }

  protected toggleSort(column: ColumnDiscoveryDto): void {
    const columnKey = this.columnKey(column);
    if (this.sortBy !== columnKey) {
      this.sortBy = columnKey;
      this.sortOrder = 'ASC';
      this.search();
      return;
    }
    if (this.sortOrder === 'ASC') {
      this.sortOrder = 'DESC';
      this.search();
      return;
    }
    this.sortBy = '';
    this.sortOrder = 'ASC';
    this.search();
  }

  protected sortMarker(column: ColumnDiscoveryDto): string {
    const columnKey = this.columnKey(column);
    if (this.sortBy !== columnKey) {
      return '↕';
    }
    return this.sortOrder === 'ASC' ? '↑' : '↓';
  }

  private buildRequest(): SearchRequest {
    const criteria = this.selectedColumns.reduce<NonNullable<SearchRequest['where']>['criteria']>((acc, column) => {
      const key = this.columnKey(column);
      if (!this.isSearchableColumn(column)) {
        return acc;
      }
      if (this.isIntegerColumn(column)) {
        const raw = this.intFilterValue(column).trim();
        if (raw.length === 0) {
          return acc;
        }
        const parsed = Number(raw);
        if (Number.isNaN(parsed)) {
          return acc;
        }
        acc.push({
          operator: 'EQ',
          value: parsed,
          field: [key]
        });
        return acc;
      }

      if (this.isDateColumn(column)) {
        const from = this.toIsoDate(this.dateFromValue(column));
        const to = this.toIsoDate(this.dateToValue(column));
        if (from) {
          acc.push({
            operator: 'GT',
            value: from,
            field: [key]
          });
        }
        if (to) {
          acc.push({
            operator: 'LT',
            value: to,
            field: [key]
          });
        }
        return acc;
      }

      const value = this.filterValue(column).trim();
      if (value.length > 0) {
        acc.push({
          operator: 'LIKE',
          value: `%${value}%`,
          field: [key]
        });
      }
      return acc;
    }, []);

    const requestBase: SearchRequest = this.sortBy
      ? { page: 0, size: this.pageSize, sort: { by: this.sortBy, order: this.sortOrder } }
      : { page: 0, size: this.pageSize };

    return criteria.length === 0
      ? requestBase
      : {
          ...requestBase,
          where: {
            logician: 'AND',
            criteria
          }
        };
  }

  private filterState(column: ColumnDiscoveryDto): { text?: string; intValue?: string; dateFrom?: string; dateTo?: string } {
    const key = this.columnKey(column);
    return this.filters[key] ?? {};
  }

  private columnType(column: ColumnDiscoveryDto): string {
    return (column.dataType ?? '').toLowerCase();
  }

  private toIsoDate(value: string): string | null {
    if (!value || value.trim().length === 0) {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
}
