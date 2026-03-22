import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, finalize, forkJoin, of, switchMap } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { MetaColumnDto, MetaFeatureDto, MetaRelationDto, MetaTableDto } from '../../core/models/multistorage-models';
import { PopupNotificationService } from '../../core/ui/popup-notification.service';
import {
  GenericTableCellEditEvent,
  GenericTableColumn,
  GenericTableComponent
} from '../../shared/generic-table/generic-table.component';

type ColumnType = 'text' | 'number' | 'boolean' | 'datetime';
type AdminColumn = GenericTableColumn & { type: ColumnType; editable: boolean };

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [GenericTableComponent],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss'
})
export class AdminComponent implements OnInit {
  protected loading = false;
  protected saving = false;

  protected metaTables: MetaTableDto[] = [];
  protected metaColumns: MetaColumnDto[] = [];
  protected metaRelations: MetaRelationDto[] = [];
  protected features: MetaFeatureDto[] = [];
  protected enablingFeatureCode: string | null = null;
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
    { key: 'editable', label: 'editable', type: 'boolean', editable: true },
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

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: MultistorageApiService,
    private readonly notifications: PopupNotificationService
  ) {}

  protected get adminDirty(): boolean {
    return (
      this.metaTables.some((row) => this.isMetaTableDirty(row)) ||
      this.metaColumns.some((row) => this.isMetaColumnDirty(row)) ||
      this.metaRelations.some((row) => this.isMetaRelationDirty(row))
    );
  }

  protected get featuresAvailableToActivate(): MetaFeatureDto[] {
    return this.features.filter((f) => !f.enabled);
  }

  protected get featuresEnabled(): MetaFeatureDto[] {
    return this.features.filter((f) => f.enabled);
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const code = params.get('tenantCode') ?? '';
      this.api.setTenantCode(code);
      if (code.length > 0) {
        this.reload();
      }
    });
  }

  protected reload(): void {
    this.loading = true;
    this.notifications.show('Loading admin data...', 1200);
    forkJoin({
      features: this.api.listFeatures(),
      tables: this.api.listMetaTables()
    })
      .pipe(
        switchMap(({ features, tables }) => {
          this.features = features ?? [];
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
          this.notifications.show('Failed to load admin data.', 3500, 'error');
        }
      });
  }

  protected enableFeature(code: string): void {
    this.enablingFeatureCode = code;
    this.api
      .enableFeature(code)
      .pipe(finalize(() => (this.enablingFeatureCode = null)))
      .subscribe({
        next: () => {
          this.notifications.show(`Feature ${code} activated.`, 3500, 'info');
          this.reload();
        },
        error: () => this.notifications.show(`Failed to enable feature ${code}.`, 3500, 'error')
      });
  }

  protected setCell(row: Record<string, unknown>, column: GenericTableColumn, rawValue: unknown): void {
    if (!column.editable) {
      return;
    }
    row[column.key] = this.normalizeValue((column.type as ColumnType | undefined) ?? 'text', rawValue);
  }

  protected onGenericCellEdit(event: GenericTableCellEditEvent): void {
    this.setCell(event.row, event.column, event.value);
  }

  protected saveAllDirty(): void {
    const dirtyTables = this.metaTables.filter((row) => this.isMetaTableDirty(row));
    const dirtyColumns = this.metaColumns.filter((row) => this.isMetaColumnDirty(row));
    const dirtyRelations = this.metaRelations.filter((row) => this.isMetaRelationDirty(row));
    if (dirtyTables.length === 0 && dirtyColumns.length === 0 && dirtyRelations.length === 0) {
      return;
    }

    const tableReqs: Observable<unknown>[] = dirtyTables.map((row) =>
      this.api.upsertMetaTable({ id: row.id, name: String(row.name ?? ''), alias: String(row.alias ?? '') })
    );
    const columnReqs: Observable<unknown>[] = dirtyColumns.map((row) => {
      const tableRef = String(row.table ?? '');
      return this.api.upsertMetaColumn(tableRef, {
        id: row.id,
        table: String(row.table ?? ''),
        name: String(row.name ?? ''),
        alias: String(row.alias ?? ''),
        dataType: String(row.dataType ?? ''),
        readable: Boolean(row.readable),
        searchable: Boolean(row.searchable),
        editable: row.editable !== false
      });
    });
    const relationReqs: Observable<unknown>[] = dirtyRelations.map((row) =>
      this.api.upsertMetaRelation({
        id: row.id,
        fromTable: String(row.fromTable ?? ''),
        toTable: String(row.toTable ?? ''),
        fromColumn: String(row.fromColumn ?? ''),
        toColumn: String(row.toColumn ?? ''),
        oneToMany: Boolean(row.oneToMany),
        alias: String(row.alias ?? ''),
        cascadeType: row.cascadeType ? String(row.cascadeType) : null,
        active: Boolean(row.active)
      } as Omit<MetaRelationDto, 'createdAt' | 'updatedAt'>)
    );

    this.saving = true;
    forkJoin({
      tables: tableReqs.length > 0 ? forkJoin(tableReqs) : of(null),
      columns: columnReqs.length > 0 ? forkJoin(columnReqs) : of(null),
      relations: relationReqs.length > 0 ? forkJoin(relationReqs) : of(null)
    })
      .pipe(finalize(() => (this.saving = false)))
      .subscribe({
        next: () => {
          this.notifications.show('Admin meta saved.', 3500, 'info');
          this.reload();
        },
        error: () => this.notifications.show('Failed to save admin meta.', 3500, 'error')
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
