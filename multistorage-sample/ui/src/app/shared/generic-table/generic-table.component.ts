import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';

export interface GenericTableColumn {
  key: string;
  label: string;
  type?: 'text' | 'number' | 'boolean' | 'datetime';
  editable?: boolean;
  searchable?: boolean;
  dataType?: string;
  sortable?: boolean;
}

export interface GenericTableExtraColumn {
  key: string;
  label: string;
  template: TemplateRef<{ $implicit: Record<string, unknown> }>;
}

export interface GenericTableFilterState {
  text?: string;
  intValue?: string;
  dateFrom?: string;
  dateTo?: string;
}

export interface GenericTableFilterChangeEvent {
  column: GenericTableColumn;
  kind: 'text' | 'number' | 'dateFrom' | 'dateTo';
  value: string;
}

export interface GenericTableCellEditEvent {
  row: Record<string, unknown>;
  column: GenericTableColumn;
  value: unknown;
}

@Component({
  selector: 'app-generic-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './generic-table.component.html',
  styleUrl: './generic-table.component.scss'
})
export class GenericTableComponent {
  @Input({ required: true }) columns: GenericTableColumn[] = [];
  @Input({ required: true }) rows: Array<Record<string, unknown>> = [];
  @Input() extraColumns: GenericTableExtraColumn[] = [];
  @Input() emptyText = 'No rows found.';
  @Input() showFilters = false;
  @Input() showDataTypes = false;
  @Input() filterState: Record<string, GenericTableFilterState> = {};
  @Input() sortBy = '';
  @Input() sortOrder: 'ASC' | 'DESC' = 'ASC';

  @Output() sortToggle = new EventEmitter<GenericTableColumn>();
  @Output() filterChange = new EventEmitter<GenericTableFilterChangeEvent>();
  @Output() cellEdit = new EventEmitter<GenericTableCellEditEvent>();

  protected cellValue(row: Record<string, unknown>, column: GenericTableColumn): unknown {
    return row[column.key];
  }

  protected sortMarker(column: GenericTableColumn): string {
    if (this.sortBy !== column.key) {
      return '↕';
    }
    return this.sortOrder === 'ASC' ? '↑' : '↓';
  }

  protected isIntegerColumn(column: GenericTableColumn): boolean {
    const type = (column.dataType ?? '').toLowerCase();
    return /(^|[^a-z])(int|int2|int4|int8|smallint|integer|bigint|serial|bigserial)($|[^a-z])/.test(type) || column.type === 'number';
  }

  protected isDateColumn(column: GenericTableColumn): boolean {
    const type = (column.dataType ?? '').toLowerCase();
    return /(date|timestamp|time)/.test(type) || column.type === 'datetime';
  }

  protected isBooleanColumn(column: GenericTableColumn): boolean {
    return column.type === 'boolean';
  }

  protected isSearchableColumn(column: GenericTableColumn): boolean {
    return column.searchable !== false;
  }

  protected filterValue(column: GenericTableColumn, key: keyof GenericTableFilterState): string {
    return this.filterState[column.key]?.[key] ?? '';
  }

  protected onFilterChange(column: GenericTableColumn, kind: GenericTableFilterChangeEvent['kind'], value: string): void {
    this.filterChange.emit({ column, kind, value });
  }

  protected onCellTextChange(row: Record<string, unknown>, column: GenericTableColumn, value: string): void {
    this.cellEdit.emit({ row, column, value });
  }

  protected onCellNumberChange(row: Record<string, unknown>, column: GenericTableColumn, value: string): void {
    this.cellEdit.emit({ row, column, value });
  }

  protected onCellCheckboxChange(row: Record<string, unknown>, column: GenericTableColumn, value: boolean): void {
    this.cellEdit.emit({ row, column, value });
  }
}
