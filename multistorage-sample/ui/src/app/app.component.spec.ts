import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { MultistorageApiService } from './core/api/multistorage-api.service';
import { MetaDiscoveryDto } from './core/models/multistorage-models';

describe('AppComponent', () => {
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
      imports: [AppComponent],
      providers: [{ provide: MultistorageApiService, useValue: apiSpy }, provideRouter([])]
    }).compileComponents();
  });

  beforeEach(() => {
    apiSpy.getDiscovery.calls.reset();
    apiSpy.getDiscovery.and.returnValue(of(discoveryMock));
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render buttons from metadata', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(compiled.querySelectorAll('.table-button')) as HTMLButtonElement[];
    expect(buttons.length).toBe(1);
    expect(buttons[0].textContent).toContain('Parent');
    expect(apiSpy.getDiscovery).toHaveBeenCalled();
  });

  it('should render link to browser route', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('.table-button') as HTMLAnchorElement;
    expect(link.getAttribute('ng-reflect-router-link')).toContain('/browser,parent');
  });
});
