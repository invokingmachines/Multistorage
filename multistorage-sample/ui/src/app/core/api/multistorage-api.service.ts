import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
import {
  ColumnDiscoveryDto,
  MetaColumnDto,
  MetaDiscoveryDto,
  MetaRelationDto,
  MetaTableDto,
  SearchRequest,
  SearchResult
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

  constructor(private readonly http: HttpClient) {}

  getDiscovery(): Observable<MetaDiscoveryDto> {
    return this.http.get<MetaDiscoveryDto>('/multistorage/api/search/discovery').pipe(
      map((response) => ({
        tables: response.tables ?? []
      }))
    );
  }

  search(entityPathSegment: string, request: SearchRequest): Observable<SearchResult> {
    const path = `/multistorage/api/${entityPathSegment}/search`;
    return this.http
      .post<SearchResult>(path, request)
      .pipe(
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
    return this.http.post<Record<string, unknown>>(`/multistorage/api/${entityPathSegment}/upsert`, payload);
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

  listMetaTables(): Observable<MetaTableDto[]> {
    return this.http.get<MetaTableDto[]>('/multistorage/admin/meta/tables');
  }

  upsertMetaTable(row: Pick<MetaTableDto, 'id' | 'name' | 'alias'>): Observable<MetaTableDto> {
    return this.http.post<MetaTableDto>('/multistorage/admin/meta/tables', row);
  }

  listMetaColumns(tableRef: string): Observable<MetaColumnDto[]> {
    return this.http.get<MetaColumnDto[]>(`/multistorage/admin/meta/tables/${tableRef}/columns`);
  }

  upsertMetaColumn(tableRef: string, row: Omit<MetaColumnDto, 'createdAt' | 'updatedAt'>): Observable<MetaColumnDto> {
    return this.http.post<MetaColumnDto>(`/multistorage/admin/meta/tables/${tableRef}/columns`, row);
  }

  listMetaRelations(tableRef: string): Observable<MetaRelationDto[]> {
    return this.http.get<MetaRelationDto[]>(`/multistorage/admin/meta/tables/${tableRef}/relations`);
  }

  upsertMetaRelation(row: Omit<MetaRelationDto, 'createdAt' | 'updatedAt'>): Observable<MetaRelationDto> {
    return this.http.post<MetaRelationDto>('/multistorage/admin/meta/relations', row);
  }
}
