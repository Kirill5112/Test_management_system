import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { PageResponse } from '../../../core/models/page-response.model';
import { Project, ProjectCreateRequest, ProjectUpdateRequest } from '../../../core/models/project.model';

export const ProjectsActions = createActionGroup({
  source: 'Projects',
  events: {
    'Load List':      props<{ page: number; size: number }>(),
    'Load List Success': props<{ page: PageResponse<Project> }>(),
    'Load List Failure': props<{ error: string }>(),

    'Create':         props<{ body: ProjectCreateRequest }>(),
    'Create Success': props<{ project: Project }>(),
    'Create Failure': props<{ error: string }>(),

    'Update':         props<{ id: string; body: ProjectUpdateRequest }>(),
    'Update Success': props<{ project: Project }>(),
    'Update Failure': props<{ error: string }>(),

    'Delete':         props<{ id: string }>(),
    'Delete Success': props<{ id: string }>(),
    'Delete Failure': props<{ error: string }>(),
  },
});
