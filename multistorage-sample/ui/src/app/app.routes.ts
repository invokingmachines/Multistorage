import { Routes } from '@angular/router';
import { AdminComponent } from './features/admin/admin.component';
import { EntityBrowserComponent } from './features/entity-browser/entity-browser.component';
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
  { path: '**', redirectTo: '' }
];
