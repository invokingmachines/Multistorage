import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { ColumnDiscoveryDto, SearchRequest } from '../../core/models/multistorage-models';

@Component({
  selector: 'app-entity-multiselect',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './entity-multiselect.component.html',
  styleUrl: './entity-multiselect.component.scss'
})
export class EntityMultiselectComponent implements OnChanges {
  @Input({ required: true }) entityPathSegment = '';
  @Input({ required: true }) columns: ColumnDiscoveryDto[] = [];

  protected query = '';
  protected loading = false;
  protected options: Array<Record<string, unknown>> = [];
  protected selected: Array<Record<string, unknown>> = [];

  private readonly queryInput$ = new Subject<string>();

  constructor(private readonly api: MultistorageApiService) {
    this.queryInput$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) => this.search(term))
      )
      .subscribe((rows) => {
        this.loading = false;
        this.options = rows;
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['entityPathSegment']) {
      this.query = '';
      this.options = [];
      this.selected = [];
    }
  }

  protected onQueryChange(value: string): void {
    this.query = value;
    this.loading = true;
    this.queryInput$.next(value);
  }

  protected toggle(row: Record<string, unknown>): void {
    const key = this.rowKey(row);
    const exists = this.selected.some((item) => this.rowKey(item) === key);
    this.selected = exists
      ? this.selected.filter((item) => this.rowKey(item) !== key)
      : [...this.selected, row];
  }

  protected isSelected(row: Record<string, unknown>): boolean {
    const key = this.rowKey(row);
    return this.selected.some((item) => this.rowKey(item) === key);
  }

  protected label(row: Record<string, unknown>): string {
    const stringColumn = this.columns.find((column) => this.isStringColumn(column));
    if (stringColumn) {
      const key = stringColumn.alias?.trim() ? stringColumn.alias : stringColumn.name;
      const value = row[key];
      if (value != null && String(value).length > 0) {
        return String(value);
      }
    }
    const id = row['id'];
    if (id != null) {
      return `id: ${String(id)}`;
    }
    return JSON.stringify(row);
  }

  private search(term: string) {
    const searchTerm = term.trim();
    if (!this.entityPathSegment || searchTerm.length === 0) {
      return of([] as Array<Record<string, unknown>>);
    }
    const stringColumns = this.columns.filter((column) => this.isStringColumn(column) && column.searchable !== false);
    if (stringColumns.length === 0) {
      return of([] as Array<Record<string, unknown>>);
    }

    const request: SearchRequest = {
      page: 0,
      size: 5,
      where: {
        logician: 'OR',
        criteria: stringColumns.map((column) => ({
          operator: 'LIKE',
          value: `%${searchTerm}%`,
          field: [column.alias?.trim() ? column.alias : column.name]
        }))
      }
    };
    return this.api.search(this.entityPathSegment, request).pipe(switchMap((result) => of(result.content ?? [])));
  }

  private isStringColumn(column: ColumnDiscoveryDto): boolean {
    const type = (column.dataType ?? '').toLowerCase();
    return /(char|text|string|varchar|citext)/.test(type);
  }

  protected rowKey(row: Record<string, unknown>): string {
    if (row['id'] != null) {
      return `id:${String(row['id'])}`;
    }
    return JSON.stringify(row);
  }
}
