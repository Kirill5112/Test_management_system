import { createFeatureSelector, createSelector } from '@ngrx/store';
import { projectsAdapter, ProjectsState } from './projects.reducer';

export const selectProjectsState = createFeatureSelector<ProjectsState>('projects');
export const { selectAll: selectAllProjects, selectTotal: selectProjectsTotal } =
  projectsAdapter.getSelectors(selectProjectsState);
export const selectProjectsLoading = createSelector(selectProjectsState, s => s.loading);
export const selectProjectsError = createSelector(selectProjectsState, s => s.error);
