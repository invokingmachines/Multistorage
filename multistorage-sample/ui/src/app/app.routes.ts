import { Routes } from '@angular/router';
import { AdminComponent } from './features/admin/admin.component';
import { EntityBrowserComponent } from './features/entity-browser/entity-browser.component';
import { EntityDetailComponent } from './features/entity-detail/entity-detail.component';
import { HomeComponent } from './features/home/home.component';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'admin',
    component: AdminComponent
  },
  {
    path: 'browser/:activeEntity',
    component: EntityBrowserComponent
  },
  {
    path: 'browser/:activeEntity/:id',
    component: EntityDetailComponent
  },
  { path: '**', redirectTo: '' }
];
