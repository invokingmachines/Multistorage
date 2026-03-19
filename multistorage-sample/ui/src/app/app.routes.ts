import { Routes } from '@angular/router';
import { EntityBrowserComponent } from './features/entity-browser/entity-browser.component';

export const routes: Routes = [
  {
    path: 'browser/:activeEntity',
    component: EntityBrowserComponent
  }
];
