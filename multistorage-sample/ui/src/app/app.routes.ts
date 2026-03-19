import { Routes } from '@angular/router';
import { AdminComponent } from './features/admin/admin.component';
import { EntityBrowserComponent } from './features/entity-browser/entity-browser.component';

export const routes: Routes = [
  {
    path: 'admin',
    component: AdminComponent
  },
  {
    path: 'browser/:activeEntity',
    component: EntityBrowserComponent
  },
  {
    path: '',
    redirectTo: 'admin',
    pathMatch: 'full'
  }
];
