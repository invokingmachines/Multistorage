import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ColumnDiscoveryDto,
  MetaColumnDto,
  MetaDiscoveryDto,
  MetaFeatureDto,
  MetaRelationDto,
  MetaTableDto,
  SearchRequest,
  SearchResult,
  TenantResponse
} from '../models/multistorage-models';

@Injectable({
  providedIn: 'root'
})
export class MultistorageApiService {
  private readonly searchDebugSubject = new BehaviorSubject<{
    path: string;
    request: SearchRequest | null;
    responseSample: unknown;
  }>({
    path: '',
    request: null,
    responseSample: {}
  });

  readonly searchDebug$ = this.searchDebugSubject.asObservable();

  private tenantCode: string | null = null;

  constructor(private readonly http: HttpClient) {}

  setTenantCode(code: string): void {
    this.tenantCode = code;
  }

  clearTenantCode(): void {
    this.tenantCode = null;
  }

  getTenantCode(): string | null {
    return this.tenantCode;
  }

  listTenants(): Observable<TenantResponse[]> {
    return this.http.get<TenantResponse[]>(`${this.tenantPublicApiPrefix()}/tenants`);
  }

  getDiscovery(): Observable<MetaDiscoveryDto> {
    return this.http.get<MetaDiscoveryDto>(`${this.tenantDataApiRoot()}/discovery`).pipe(
      map((response) => ({
        tables: response.tables ?? []
      }))
    );
  }

  search(entityPathSegment: string, request: SearchRequest): Observable<SearchResult> {
    const path = `${this.tenantDataApiRoot()}/${entityPathSegment}/search`;
    return this.http.post<SearchResult>(path, request).pipe(
      map((response) => ({
        content: response.content ?? [],
        totalElements: response.totalElements ?? null,
        totalPages: response.totalPages ?? null,
        size: response.size ?? null,
        number: response.number ?? null
      })),
      tap((result) => {
        this.searchDebugSubject.next({
          path,
          request,
          responseSample: result.content?.[0] ?? {}
        });
      })
    );
  }

  findById(entityPathSegment: string, idField: string, id: string): Observable<Record<string, unknown> | null> {
    return this.search(entityPathSegment, {
      page: 0,
      size: 1,
      where: {
        logician: 'AND',
        criteria: [{ operator: 'EQ', value: id, field: [idField] }]
      }
    }).pipe(map((result) => result.content?.[0] ?? null));
  }

  upsertEntity(entityPathSegment: string, payload: Record<string, unknown>): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.tenantDataApiRoot()}/${entityPathSegment}`, payload);
  }

  resolveIdField(columns: ColumnDiscoveryDto[]): string {
    return (
      columns.find((c) => (c.alias || c.name || '').toLowerCase() === 'id')?.alias ||
      columns.find((c) => (c.alias || c.name || '').toLowerCase() === 'id')?.name ||
      columns[0]?.alias ||
      columns[0]?.name ||
      'id'
    );
  }

  listFeatures(): Observable<MetaFeatureDto[]> {
    return this.http.get<MetaFeatureDto[]>(`${this.tenantScopedApiRoot()}/meta/features`);
  }

  enableFeature(code: string): Observable<void> {
    return this.http.post<void>(
      `${this.tenantScopedApiRoot()}/meta/features/${encodeURIComponent(code)}/enable`,
      null
    );
  }

  listMetaTables(): Observable<MetaTableDto[]> {
    return this.http.get<MetaTableDto[]>(`${this.tenantScopedApiRoot()}/meta/tables`);
  }

  upsertMetaTable(row: Pick<MetaTableDto, 'id' | 'name' | 'alias'>): Observable<MetaTableDto> {
    return this.http.post<MetaTableDto>(`${this.tenantScopedApiRoot()}/meta/tables`, row);
  }

  listMetaColumns(tableRef: string): Observable<MetaColumnDto[]> {
    return this.http.get<MetaColumnDto[]>(`${this.tenantScopedApiRoot()}/meta/tables/${tableRef}/columns`);
  }

  upsertMetaColumn(tableRef: string, row: Omit<MetaColumnDto, 'createdAt' | 'updatedAt'>): Observable<MetaColumnDto> {
    return this.http.post<MetaColumnDto>(`${this.tenantScopedApiRoot()}/meta/tables/${tableRef}/columns`, row);
  }

  listMetaRelations(tableRef: string): Observable<MetaRelationDto[]> {
    return this.http.get<MetaRelationDto[]>(`${this.tenantScopedApiRoot()}/meta/tables/${tableRef}/relations`);
  }

  upsertMetaRelation(row: Omit<MetaRelationDto, 'createdAt' | 'updatedAt'>): Observable<MetaRelationDto> {
    return this.http.post<MetaRelationDto>(`${this.tenantScopedApiRoot()}/meta/relations`, row);
  }

  entitySearchPath(entityPathSegment: string): string {
    return `${this.tenantDataApiRoot()}/${entityPathSegment}/search`;
  }

  entityUpsertPath(entityPathSegment: string): string {
    return `${this.tenantDataApiRoot()}/${entityPathSegment}`;
  }

  private tenantPublicApiPrefix(): string {
    const p = environment.apiTenantPrefix ?? '/api';
    return p.endsWith('/') ? p.slice(0, -1) : p;
  }

  private tenantScopedApiRoot(): string {
    return this.substituteTenantInApiTemplate(environment.apiPrefix);
  }

  private tenantDataApiRoot(): string {
    return `${this.tenantScopedApiRoot()}/data`;
  }

  private substituteTenantInApiTemplate(template: string): string {
    const t = this.tenantCode;
    if (!t) {
      throw new Error('Tenant is not selected');
    }
    const s = template.trim().replace(/\/+$/, '');
    return s.replaceAll('{tenantId}', t).replaceAll('{tenantCode}', t);
  }
}
