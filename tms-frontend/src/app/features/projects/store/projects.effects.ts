import {inject} from '@angular/core';
import {Actions, createEffect, ofType} from '@ngrx/effects';
import {catchError, map, of, switchMap} from 'rxjs';
import {ErrorResponse} from '../../../core/models/error-response.model';
import {ProjectService} from '../../../core/api/project.service';
import {ProjectsActions} from './projects.actions';

export const loadProjects$ = createEffect(
  (actions$ = inject(Actions), svc = inject(ProjectService)) =>
    actions$.pipe(
      ofType(ProjectsActions.loadList),
      switchMap(({page, size}) =>
        svc.list({page, size, sort: 'createdAt,desc'}).pipe(
          map(pageResponse => ProjectsActions.loadListSuccess({page: pageResponse})),
          catchError(err => of(ProjectsActions.loadListFailure({error: (err.error as ErrorResponse)?.code ?? 'UNKNOWN'}))),
        ),
      ),
    ),
  {functional: true},
);

export const createProject$ = createEffect(
  (actions$ = inject(Actions), svc = inject(ProjectService)) =>
    actions$.pipe(
      ofType(ProjectsActions.create),
      switchMap(({body}) =>
        svc.create(body).pipe(
          map(project => ProjectsActions.createSuccess({project})),
          catchError(err => of(ProjectsActions.createFailure({error: (err.error as ErrorResponse)?.code ?? 'UNKNOWN'}))),
        ),
      ),
    ),
  {functional: true},
);

export const updateProject$ = createEffect(
  (actions$ = inject(Actions), svc = inject(ProjectService)) =>
    actions$.pipe(
      ofType(ProjectsActions.update),
      switchMap(({id, body}) =>
        svc.update(id, body).pipe(
          map(project => ProjectsActions.updateSuccess({project})),
          catchError(err => of(ProjectsActions.updateFailure({error: (err.error as ErrorResponse)?.code ?? 'UNKNOWN'}))),
        ),
      ),
    ),
  {functional: true},
);

export const deleteProject$ = createEffect(
  (actions$ = inject(Actions), svc = inject(ProjectService)) =>
    actions$.pipe(
      ofType(ProjectsActions.delete),
      switchMap(({id}) =>
        svc.delete(id).pipe(
          map(() => ProjectsActions.deleteSuccess({id})),
          catchError(err => of(ProjectsActions.deleteFailure({error: (err.error as ErrorResponse)?.code ?? 'UNKNOWN'}))),
        ),
      ),
    ),
  {functional: true},
);

export const projectsEffects = {loadProjects$, createProject$, updateProject$, deleteProject$};
