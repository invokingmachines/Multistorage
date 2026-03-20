import { Component, OnInit } from '@angular/core';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { MetaColumnDto, MetaRelationDto, MetaTableDto } from '../../core/models/multistorage-models';

type ColumnType = 'text' | 'number' | 'boolean' | 'datetime';
type AdminColumn = { key: string; label: string; type: ColumnType; editable: boolean };

@Component({
  selector: 'app-admin',
  standalone: true,
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss'
})
export class AdminComponent implements OnInit {
  protected loading = false;
  protected errorMessage: string | null = null;

  protected metaTables: MetaTableDto[] = [];
  protected metaColumns: MetaColumnDto[] = [];
  protected metaRelations: MetaRelationDto[] = [];
  private readonly tableSnapshots = new WeakMap<object, string>();
  private readonly columnSnapshots = new WeakMap<object, string>();
  private readonly relationSnapshots = new WeakMap<object, string>();

  protected readonly tableColumns: AdminColumn[] = [
    { key: 'name', label: 'name', type: 'text', editable: true },
    { key: 'alias', label: 'alias', type: 'text', editable: true },
    { key: 'createdAt', label: 'createdAt', type: 'datetime', editable: false },
    { key: 'updatedAt', label: 'updatedAt', type: 'datetime', editable: false }
  ];

  protected readonly columnColumns: AdminColumn[] = [
    { key: 'table', label: 'table', type: 'text', editable: true },
    { key: 'name', label: 'name', type: 'text', editable: true },
    { key: 'alias', label: 'alias', type: 'text', editable: true },
    { key: 'dataType', label: 'dataType', type: 'text', editable: true },
    { key: 'readable', label: 'readable', type: 'boolean', editable: true },
    { key: 'searchable', label: 'searchable', type: 'boolean', editable: true },
    { key: 'createdAt', label: 'createdAt', type: 'datetime', editable: false },
    { key: 'updatedAt', label: 'updatedAt', type: 'datetime', editable: false }
  ];

  protected readonly relationColumns: AdminColumn[] = [
    { key: 'fromTable', label: 'fromTable', type: 'text', editable: true },
    { key: 'toTable', label: 'toTable', type: 'text', editable: true },
    { key: 'fromColumn', label: 'fromColumn', type: 'text', editable: true },
    { key: 'toColumn', label: 'toColumn', type: 'text', editable: true },
    { key: 'oneToMany', label: 'oneToMany', type: 'boolean', editable: true },
    { key: 'alias', label: 'alias', type: 'text', editable: true },
    { key: 'cascadeType', label: 'cascadeType', type: 'text', editable: true },
    { key: 'active', label: 'active', type: 'boolean', editable: true },
    { key: 'createdAt', label: 'createdAt', type: 'datetime', editable: false },
    { key: 'updatedAt', label: 'updatedAt', type: 'datetime', editable: false }
  ];

  constructor(private readonly api: MultistorageApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading = true;
    this.errorMessage = null;
    this.api
      .listMetaTables()
      .pipe(
        switchMap((tables) => {
          this.metaTables = tables ?? [];
          const refs = this.metaTables.map((t) => t.alias || t.name).filter((v) => !!v);
          if (refs.length === 0) {
            return of({ columnsByTable: [] as MetaColumnDto[][], relationsByTable: [] as MetaRelationDto[][] });
          }
          return forkJoin({
            columnsByTable: forkJoin(refs.map((ref) => this.api.listMetaColumns(ref))),
            relationsByTable: forkJoin(refs.map((ref) => this.api.listMetaRelations(ref)))
          });
        }),
        finalize(() => (this.loading = false))
      )
      .subscribe({
        next: (result) => {
          this.metaColumns = result.columnsByTable.flat();
          this.metaRelations = result.relationsByTable.flat();
          this.captureSnapshots();
        },
        error: () => {
          this.errorMessage = 'Failed to load admin data.';
        }
      });
  }

  protected asInputDate(value: unknown): string {
    if (!value) {
      return '';
    }
    const date = new Date(String(value));
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  protected setCell(row: Record<string, unknown>, column: AdminColumn, rawValue: unknown): void {
    if (!column.editable) {
      return;
    }
    row[column.key] = this.normalizeValue(column.type, rawValue);
  }

  protected saveMetaTable(row: MetaTableDto): void {
    this.api.upsertMetaTable({ name: String(row.name ?? ''), alias: String(row.alias ?? '') }).subscribe({
      next: () => this.reload(),
      error: () => (this.errorMessage = 'Failed to save meta table.')
    });
  }

  protected saveMetaColumn(row: MetaColumnDto): void {
    const tableRef = String(row.table ?? '');
    this.api
      .upsertMetaColumn(tableRef, {
        table: String(row.table ?? ''),
        name: String(row.name ?? ''),
        alias: String(row.alias ?? ''),
        dataType: String(row.dataType ?? ''),
        readable: Boolean(row.readable),
        searchable: Boolean(row.searchable)
      })
      .subscribe({
        next: () => this.reload(),
        error: () => (this.errorMessage = 'Failed to save meta column.')
      });
  }

  protected saveMetaRelation(row: MetaRelationDto): void {
    this.api
      .upsertMetaRelation({
        fromTable: String(row.fromTable ?? ''),
        toTable: String(row.toTable ?? ''),
        fromColumn: String(row.fromColumn ?? ''),
        toColumn: String(row.toColumn ?? ''),
        oneToMany: Boolean(row.oneToMany),
        alias: String(row.alias ?? ''),
        cascadeType: row.cascadeType ? String(row.cascadeType) : null,
        active: Boolean(row.active)
      } as unknown as Omit<MetaRelationDto, 'createdAt' | 'updatedAt'>)
      .subscribe({
        next: () => this.reload(),
        error: () => (this.errorMessage = 'Failed to save meta relation.')
      });
  }

  protected isMetaTableDirty(row: MetaTableDto): boolean {
    return this.isDirty(row, this.tableColumns, this.tableSnapshots);
  }

  protected isMetaColumnDirty(row: MetaColumnDto): boolean {
    return this.isDirty(row, this.columnColumns, this.columnSnapshots);
  }

  protected isMetaRelationDirty(row: MetaRelationDto): boolean {
    return this.isDirty(row, this.relationColumns, this.relationSnapshots);
  }

  private normalizeValue(type: ColumnType, value: unknown): unknown {
    if (type === 'boolean') {
      return Boolean(value);
    }
    if (type === 'number') {
      const parsed = Number(value);
      return Number.isNaN(parsed) ? null : parsed;
    }
    if (type === 'datetime') {
      if (!value) return null;
      const date = new Date(String(value));
      return Number.isNaN(date.getTime()) ? null : date.toISOString();
    }
    return value == null ? '' : String(value);
  }

  private captureSnapshots(): void {
    this.metaTables.forEach((row) => this.tableSnapshots.set(row, this.serializeEditable(row, this.tableColumns)));
    this.metaColumns.forEach((row) => this.columnSnapshots.set(row, this.serializeEditable(row, this.columnColumns)));
    this.metaRelations.forEach((row) => this.relationSnapshots.set(row, this.serializeEditable(row, this.relationColumns)));
  }

  private isDirty<T extends object>(row: T, columns: AdminColumn[], snapshots: WeakMap<object, string>): boolean {
    const snapshot = snapshots.get(row);
    if (!snapshot) {
      return false;
    }
    return snapshot !== this.serializeEditable(row, columns);
  }

  private serializeEditable(row: object, columns: AdminColumn[]): string {
    const source = row as Record<string, unknown>;
    const payload = columns
      .filter((column) => column.editable)
      .reduce<Record<string, unknown>>((acc, column) => ({ ...acc, [column.key]: source[column.key] ?? null }), {});
    return JSON.stringify(payload);
  }
}
