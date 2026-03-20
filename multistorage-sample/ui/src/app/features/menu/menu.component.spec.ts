import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MultistorageApiService } from '../../core/api/multistorage-api.service';
import { MetaDiscoveryDto } from '../../core/models/multistorage-models';
import { MenuComponent } from './menu.component';

describe('MenuComponent', () => {
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

  const apiSpy = jasmine.createSpyObj<MultistorageApiService>('MultistorageApiService', ['getDiscovery', 'search']);

  beforeEach(async () => {
    apiSpy.getDiscovery.and.returnValue(of(discoveryMock));
    await TestBed.configureTestingModule({
      imports: [MenuComponent],
      providers: [{ provide: MultistorageApiService, useValue: apiSpy }, provideRouter([])]
    }).compileComponents();
  });

  beforeEach(() => {
    apiSpy.getDiscovery.calls.reset();
    apiSpy.getDiscovery.and.returnValue(of(discoveryMock));
  });

  it('should render buttons from metadata', () => {
    const fixture = TestBed.createComponent(MenuComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(compiled.querySelectorAll('.table-button')) as HTMLAnchorElement[];
    expect(buttons.length).toBe(1);
    expect(buttons[0].textContent).toContain('Parent');
    expect(apiSpy.getDiscovery).toHaveBeenCalled();
  });
});
