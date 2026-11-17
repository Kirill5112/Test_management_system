import { createReducer, on } from '@ngrx/store';
import { createEntityAdapter, EntityState } from '@ngrx/entity';
import { Project } from '../../../core/models/project.model';
import { ProjectsActions } from './projects.actions';

export interface ProjectsState extends EntityState<Project> {
  totalElements: number;
  page: number;
  size: number;
  loading: boolean;
  error: string | null;
}

export const projectsAdapter = createEntityAdapter<Project>();

const initialState: ProjectsState = projectsAdapter.getInitialState({
  totalElements: 0,
  page: 0,
  size: 20,
  loading: false,
  error: null,
});

export const projectsReducer = createReducer(
  initialState,
  on(ProjectsActions.loadList, (s, { page, size }) => ({ ...s, loading: true, error: null, page, size })),
  on(ProjectsActions.loadListSuccess, (s, { page }) =>
    projectsAdapter.setAll(page.content, {
      ...s,
      totalElements: page.totalElements,
      page: page.number,
      size: page.size,
      loading: false,
    })),
  on(ProjectsActions.loadListFailure, (s, { error }) => ({ ...s, loading: false, error })),

  on(ProjectsActions.createSuccess, (s, { project }) =>
    projectsAdapter.upsertOne(project, s)),
  on(ProjectsActions.updateSuccess, (s, { project }) =>
    projectsAdapter.updateOne({ id: project.id, changes: project }, s)),
  on(ProjectsActions.deleteSuccess, (s, { id }) =>
    projectsAdapter.removeOne(id, s)),
);
