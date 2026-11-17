import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { projectsReducer } from './store/projects.reducer';
import { projectsEffects } from './store/projects.effects';

export const PROJECTS_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState({ name: 'projects', reducer: projectsReducer }),
      provideEffects(projectsEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./project-list/project-list.component').then(m => m.ProjectListComponent),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./project-form/project-form.component').then(m => m.ProjectFormComponent),
      },
      {
        path: ':id/edit',
        loadComponent: () =>
          import('./project-form/project-form.component').then(m => m.ProjectFormComponent),
      },
    ],
  },
];
