import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { ColumnDiscoveryDto, MetaRelationDto, TableDiscoveryDto } from '../../core/models/multistorage-models';
import { PopupNotificationService } from '../../core/ui/popup-notification.service';
import {
  GenericTableCellEditEvent,
  GenericTableColumn,
  GenericTableComponent
} from '../../shared/generic-table/generic-table.component';
import { QueryBodyPreviewComponent } from '../entity-browser/query-body-preview.component';

type RelationSection = {
  title: string;
  entityPath: string;
  idKey: string;
  columns: GenericTableColumn[];
  rows: Array<Record<string, unknown>>;
};

type FormField = {
  key: string;
  label: string;
  editable: boolean;
  type: 'text' | 'number' | 'boolean' | 'datetime';
};

@Component({
  selector: 'app-entity-detail',
  standalone: true,
  imports: [CommonModule, GenericTableComponent, QueryBodyPreviewComponent, RouterLink],
  templateUrl: './entity-detail.component.html',
  styleUrl: './entity-detail.component.scss'
})
export class EntityDetailComponent implements OnInit {
  protected tenantCode = '';
  protected loading = false;
  protected saving = false;
  protected selectedTable: TableDiscoveryDto | null = null;
  protected formFields: FormField[] = [];
  protected formModel: Record<string, unknown> = {};
  protected relationSections: RelationSection[] = [];
  protected idField = 'id';
  protected debugPath = '';
  protected debugMethod: 'POST' | 'GET' = 'POST';
  protected requestBody: unknown = {};
  protected responseBody: unknown = {};

  private activeEntity = '';
  private entityId = '';
  private relationsMeta: MetaRelationDto[] = [];
  private formBaseline = '';

  protected get formDirty(): boolean {
    if (!this.selectedTable) {
      return false;
    }
    return this.serializeFormModel(this.formModel) !== this.formBaseline;
  }

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: MultistorageApiService,
    private readonly notifications: PopupNotificationService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.tenantCode = params.get('tenantCode') ?? '';
      this.api.setTenantCode(this.tenantCode);
      this.activeEntity = params.get('activeEntity') ?? '';
      this.entityId = params.get('id') ?? '';
      if (!this.activeEntity || !this.entityId) {
        this.notifications.show('Invalid entity route.', 3500, 'warning');
        return;
      }
      this.load();
    });
  }

  protected save(): void {
    if (!this.selectedTable) {
      return;
    }
    const payload = this.buildUpsertPayload(this.formModel, this.relationsMeta, this.selectedTable, this.idField);
    this.saving = true;
    this.api
      .upsertEntity(this.selectedTable.pathSegment, payload)
      .pipe(finalize(() => (this.saving = false)))
      .subscribe({
        next: (result) => {
          this.setDebug('POST', this.api.entityUpsertPath(this.selectedTable!.pathSegment), payload, result ?? {});
          this.notifications.show('Saved.', 3500, 'info');
          this.load();
        },
        error: () => {
          this.notifications.show('Failed to save entity.', 3500, 'error');
        }
      });
  }

  private load(): void {
    this.loading = true;
    this.notifications.show('Loading entity...', 1200);
    this.api
      .getDiscovery()
      .pipe(
        switchMap((discovery) => {
          const table = (discovery.tables ?? []).find((item) => item.pathSegment === this.activeEntity) ?? null;
          if (!table) {
            return of({ table: null, row: null, relations: [], tables: [] as TableDiscoveryDto[] });
          }
          const columns = table.columns ?? [];
          this.idField = this.api.resolveIdField(columns);
          const rowRequest = {
            page: 0,
            size: 1,
            select: [['*'], ...(table.relations ?? []).map((relationAlias) => [relationAlias, '*'])],
            where: {
              logician: 'AND' as const,
              criteria: [{ operator: 'EQ' as const, value: this.entityId, field: [this.idField] }]
            }
          };
          this.setDebug('POST', this.api.entitySearchPath(table.pathSegment), rowRequest, {});
          return forkJoin({
            row: this.api.search(table.pathSegment, rowRequest).pipe(
              map((result) => {
                const row = result.content?.[0] ?? null;
                this.setDebug('POST', this.api.entitySearchPath(table.pathSegment), rowRequest, row ?? {});
                return row;
              })
            ),
            relationsMeta: this.api.listMetaRelations(table.alias || table.name)
          }).pipe(
            map((result) => ({
              table,
              row: result.row,
              relationsMeta: result.relationsMeta ?? [],
              discoveryTables: discovery.tables ?? []
            }))
          );
        }),
        finalize(() => (this.loading = false))
      )
      .subscribe({
        next: (state) => {
          if (!state.table || !state.row) {
            this.selectedTable = null;
            this.formFields = [];
            this.formModel = {};
            this.relationSections = [];
            this.relationsMeta = [];
            this.formBaseline = '';
            this.notifications.show('Entity or metadata not found.', 3500, 'warning');
            return;
          }
          this.relationsMeta = state.relationsMeta ?? [];
          this.selectedTable = state.table;
          this.formFields = this.toFormFields(state.table.columns ?? [], this.idField);
          this.formModel = { ...(state.row as Record<string, unknown>) };
          this.relationSections = this.toRelationSectionsFromRow(
            this.formModel,
            state.table.relations ?? [],
            state.relationsMeta ?? [],
            state.table,
            state.discoveryTables ?? []
          );
          this.formBaseline = this.serializeFormModel(this.formModel);
        },
        error: () => {
          this.notifications.show('Failed to load entity details.', 3500, 'error');
        }
      });
  }

  private serializeFormModel(model: Record<string, unknown>): string {
    return JSON.stringify(model);
  }

  private setDebug(method: 'POST' | 'GET', path: string, request: unknown, response: unknown): void {
    this.debugMethod = method;
    this.debugPath = path;
    this.requestBody = request;
    this.responseBody = response;
  }

  protected fieldValue(field: FormField): unknown {
    return this.formModel[field.key];
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

  protected onFieldChange(field: FormField, value: unknown): void {
    if (field.editable === false) {
      return;
    }
    this.formModel = { ...this.formModel, [field.key]: this.normalizeFieldValue(field.type, value) };
  }

  private toFormFields(columns: ColumnDiscoveryDto[], idField: string): FormField[] {
    return columns.map((column) => {
      const key = column.alias?.trim() ? column.alias : column.name;
      const metaEditable = column.editable !== false;
      return {
        key,
        label: column.alias || column.name,
        editable: metaEditable && key !== idField,
        type: this.toColumnType(column.dataType)
      };
    });
  }

  private toRelationSectionsFromRow(
    row: Record<string, unknown>,
    relationAliases: string[],
    relationsMeta: MetaRelationDto[],
    currentTable: TableDiscoveryDto,
    discoveryTables: TableDiscoveryDto[]
  ): RelationSection[] {
    return relationAliases
      .map((alias) => {
        const relationValue = row[alias];
        const rows = this.normalizeRelationRows(relationValue);
        const rel = relationsMeta.find(
          (meta) => (meta.alias || '').toLowerCase() === alias.toLowerCase() && meta.active !== false
        );
        const cascadeWritable = rel != null && this.isCascadeWritableForNested(rel.cascadeType);
        const childPath = this.resolveRelationEntityPath(alias, relationsMeta, currentTable, discoveryTables);
        const childTable =
          discoveryTables.find((t) => (t.pathSegment || '').toLowerCase() === (childPath || '').toLowerCase()) ?? null;
        const childIdKey = childTable ? this.api.resolveIdField(childTable.columns ?? []) : this.resolveRowIdKey(rows);
        const columns = this.buildRelationTableColumns(rows, cascadeWritable, childTable, childIdKey);
        return {
          title: alias,
          entityPath: childPath,
          idKey: childIdKey,
          columns,
          rows
        };
      })
      .filter((section) => section.columns.length > 0 || section.rows.length > 0);
  }

  private resolveRelationEntityPath(
    relationAlias: string,
    relationsMeta: MetaRelationDto[],
    currentTable: TableDiscoveryDto,
    discoveryTables: TableDiscoveryDto[]
  ): string {
    const currentRefs = [(currentTable.name || '').toLowerCase(), (currentTable.alias || '').toLowerCase()].filter((x) => x.length > 0);
    const relation = relationsMeta.find((meta) => (meta.alias || '').toLowerCase() === relationAlias.toLowerCase() && meta.active !== false);
    if (!relation) {
      return relationAlias;
    }
    const from = (relation.fromTable || '').toLowerCase();
    const to = (relation.toTable || '').toLowerCase();
    const targetRef = currentRefs.includes(from) ? relation.toTable : currentRefs.includes(to) ? relation.fromTable : relation.toTable;
    const normalizedTarget = (targetRef || '').toLowerCase();
    const targetTable =
      discoveryTables.find(
        (table) => (table.name || '').toLowerCase() === normalizedTarget || (table.alias || '').toLowerCase() === normalizedTarget
      ) ?? null;
    return targetTable?.pathSegment || relationAlias;
  }

  private normalizeRelationRows(value: unknown): Array<Record<string, unknown>> {
    if (Array.isArray(value)) {
      return value.filter((item): item is Record<string, unknown> => !!item && typeof item === 'object');
    }
    if (value && typeof value === 'object') {
      return [value as Record<string, unknown>];
    }
    return [];
  }

  private isCascadeWritableForNested(cascadeType: string | undefined): boolean {
    if (cascadeType == null || cascadeType === '') {
      return false;
    }
    const u = cascadeType.toUpperCase();
    return u === 'PERSIST' || u === 'MERGE' || u === 'PERSIST_MERGE';
  }

  private findChildColumnDiscovery(childTable: TableDiscoveryDto | null, fieldKey: string): ColumnDiscoveryDto | null {
    if (!childTable?.columns?.length) {
      return null;
    }
    const kl = fieldKey.toLowerCase();
    return (
      childTable.columns.find(
        (c) => (c.alias || '').toLowerCase() === kl || (c.name || '').toLowerCase() === kl
      ) ?? null
    );
  }

  private buildRelationTableColumns(
    rows: Array<Record<string, unknown>>,
    cascadeWritable: boolean,
    childTable: TableDiscoveryDto | null,
    childIdKey: string
  ): GenericTableColumn[] {
    const first = rows[0];
    if (!first) {
      return [];
    }
    const idLower = childIdKey.toLowerCase();
    return Object.keys(first).map((key) => {
      const childCol = this.findChildColumnDiscovery(childTable, key);
      const label = (childCol?.alias || childCol?.name || key).trim() || key;
      const colEditable = childCol ? childCol.editable !== false : true;
      const type = childCol ? this.toColumnType(childCol.dataType ?? '') : 'text';
      return {
        key,
        label,
        dataType: childCol?.dataType,
        type,
        editable: cascadeWritable && colEditable && key.toLowerCase() !== idLower
      };
    });
  }

  protected onRelationCellEdit(section: RelationSection, event: GenericTableCellEditEvent): void {
    const colDef = section.columns.find((c) => c.key === event.column.key);
    if (colDef?.editable !== true) {
      return;
    }
    const nextVal = this.normalizeRelationCellValue(colDef.type ?? 'text', event.value);
    (event.row as Record<string, unknown>)[event.column.key] = nextVal;
    this.touchRelationInFormModel(section.title);
  }

  private normalizeRelationCellValue(type: FormField['type'], value: unknown): unknown {
    return this.normalizeFieldValue(type, value);
  }

  private touchRelationInFormModel(relationAlias: string): void {
    const cur = this.formModel[relationAlias];
    if (Array.isArray(cur)) {
      this.formModel = { ...this.formModel, [relationAlias]: [...cur] };
      return;
    }
    if (cur && typeof cur === 'object') {
      this.formModel = { ...this.formModel, [relationAlias]: { ...(cur as Record<string, unknown>) } };
      return;
    }
    this.formModel = { ...this.formModel };
  }

  private resolveRowIdKey(rows: Array<Record<string, unknown>>): string {
    const first = rows[0];
    if (!first) {
      return 'id';
    }
    const keys = Object.keys(first);
    return keys.find((k) => k.toLowerCase() === 'id') ?? 'id';
  }

  private toColumnType(dataType: string): 'text' | 'number' | 'boolean' | 'datetime' {
    const type = (dataType ?? '').toLowerCase();
    if (/(bool)/.test(type)) {
      return 'boolean';
    }
    if (/(date|timestamp|time)/.test(type)) {
      return 'datetime';
    }
    if (/(^|[^a-z])(int|int2|int4|int8|smallint|integer|bigint|serial|bigserial)($|[^a-z])/.test(type)) {
      return 'number';
    }
    return 'text';
  }

  private buildUpsertPayload(
    model: Record<string, unknown>,
    relationsMeta: MetaRelationDto[],
    table: TableDiscoveryDto,
    idField: string
  ): Record<string, unknown> {
    const outgoingAliases = new Set((table.relations ?? []).map((a) => a.toLowerCase()));
    const stripKeys = new Set(
      relationsMeta
        .filter((r) => r.active !== false && this.isCascadeNone(r.cascadeType))
        .map((r) => (r.alias || '').toLowerCase())
        .filter((a) => a.length > 0 && outgoingAliases.has(a))
    );
    const idKeyLower = (idField || 'id').toLowerCase();
    const nonEditableColumnKeys = new Set(
      (table.columns ?? [])
        .filter((c) => c.editable === false)
        .map((c) => (c.alias?.trim() ? c.alias : c.name).toLowerCase())
    );
    const out: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(model)) {
      const kl = key.toLowerCase();
      if (stripKeys.has(kl)) {
        continue;
      }
      if (nonEditableColumnKeys.has(kl) && kl !== idKeyLower) {
        continue;
      }
      out[key] = value;
    }
    return out;
  }

  private isCascadeNone(cascadeType: string | undefined): boolean {
    if (cascadeType == null || cascadeType === '') {
      return true;
    }
    return cascadeType.toUpperCase() === 'NONE';
  }

  private normalizeFieldValue(type: FormField['type'], value: unknown): unknown {
    if (type === 'boolean') {
      return Boolean(value);
    }
    if (type === 'number') {
      const parsed = Number(value);
      return Number.isNaN(parsed) ? null : parsed;
    }
    if (type === 'datetime') {
      if (!value) {
        return null;
      }
      const date = new Date(String(value));
      return Number.isNaN(date.getTime()) ? null : date.toISOString();
    }
    return value == null ? '' : String(value);
  }
}
