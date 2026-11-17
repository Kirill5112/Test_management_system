import { Routes } from '@angular/router';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: '', redirectTo: 'projects', pathMatch: 'full' },
      {
        path: 'projects',
        loadChildren: () =>
          import('./features/projects/projects.routes').then(m => m.PROJECTS_ROUTES),
      },
/*      {
        path: 'test-runs',
        loadChildren: () =>
          import('./features/test-runs/test-runs.routes').then(m => m.TEST_RUNS_ROUTES),
      },
      {
        path: 'test-results',
        loadChildren: () =>
          import('./features/test-results/test-results.routes').then(m => m.TEST_RESULTS_ROUTES),
      },*/
    ],
  },
  { path: '**', redirectTo: '' },
];
