import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { MetaDiscoveryDto, SearchRequest, SearchResult } from '../../core/models/multistorage-models';
import { EntityBrowserComponent } from './entity-browser.component';

describe('EntityBrowserComponent', () => {
  const paramMap$ = new BehaviorSubject(convertToParamMap({ tenantCode: 'demo', activeEntity: 'parent' }));

  const discoveryMock: MetaDiscoveryDto = {
    tables: [
      {
        alias: 'Parent',
        pathSegment: 'parent',
        name: 'parent',
        relations: [],
        columns: [
          { alias: 'id', name: 'id', dataType: 'BIGINT' },
          { alias: 'name', name: 'name', dataType: 'VARCHAR' }
        ]
      }
    ]
  };

  const searchMock: SearchResult = {
    content: [{ id: 1, name: 'Parent 1' }],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0
  };
  const expectedInitialRequest: SearchRequest = { page: 0, size: 20 };

  const apiSpy = jasmine.createSpyObj<MultistorageApiService>('MultistorageApiService', ['getDiscovery', 'search', 'setTenantCode']);

  beforeEach(async () => {
    apiSpy.getDiscovery.and.returnValue(of(discoveryMock));
    apiSpy.search.and.returnValue(of(searchMock));

    await TestBed.configureTestingModule({
      imports: [EntityBrowserComponent],
      providers: [
        provideRouter([]),
        { provide: MultistorageApiService, useValue: apiSpy },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$.asObservable() } }
      ]
    }).compileComponents();
  });

  beforeEach(() => {
    apiSpy.getDiscovery.calls.reset();
    apiSpy.search.calls.reset();
    apiSpy.getDiscovery.and.returnValue(of(discoveryMock));
    apiSpy.search.and.returnValue(of(searchMock));
    paramMap$.next(convertToParamMap({ tenantCode: 'demo', activeEntity: 'parent' }));
  });

  it('should render table for active entity', () => {
    const fixture = TestBed.createComponent(EntityBrowserComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const headerCells = compiled.querySelectorAll('thead th');
    const bodyCells = compiled.querySelectorAll('tbody td');

    expect(apiSpy.search).toHaveBeenCalledWith('parent', expectedInitialRequest);
    expect(headerCells.length).toBe(4);
    expect(bodyCells[0].textContent).toContain('1');
    expect(bodyCells[1].textContent).toContain('Parent 1');
  });
});
