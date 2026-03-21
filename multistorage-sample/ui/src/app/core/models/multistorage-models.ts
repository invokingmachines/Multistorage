export interface MetaDiscoveryDto {
  tables: TableDiscoveryDto[];
}

export interface TableDiscoveryDto {
  alias: string;
  pathSegment: string;
  name: string;
  relations: string[];
  columns: ColumnDiscoveryDto[];
}

export interface ColumnDiscoveryDto {
  alias: string;
  name: string;
  dataType: string;
  searchable?: boolean;
}

export interface SearchResult {
  content: Array<Record<string, unknown>>;
  totalElements: number | null;
  totalPages: number | null;
  size: number | null;
  number: number | null;
}

export interface SearchRequest {
  page: number;
  size: number;
  select?: string[][];
  sort?: {
    by: string;
    order: 'ASC' | 'DESC';
  };
  where?: {
    logician: 'AND' | 'OR' | 'NOT';
    criteria: Array<{
      operator: 'LIKE' | 'EQ' | 'GT' | 'LT';
      value: string | number;
      field: string[];
    }>;
  };
}

export interface MetaTableDto {
  id?: string;
  name: string;
  alias: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface MetaColumnDto {
  id?: string;
  table: string;
  name: string;
  alias: string;
  dataType: string;
  readable: boolean;
  searchable: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface MetaRelationDto {
  id?: string;
  fromTable: string;
  toTable: string;
  fromColumn: string;
  toColumn: string;
  oneToMany: boolean;
  alias: string;
  cascadeType?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}
