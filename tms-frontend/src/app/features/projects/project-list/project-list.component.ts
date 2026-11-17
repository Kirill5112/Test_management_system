import {Component, OnInit, inject} from '@angular/core';
import {AsyncPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {Store} from '@ngrx/store';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatChipsModule} from '@angular/material/chips';
import {projectsAdapter} from '../store/projects.reducer';
import {ProjectsActions} from '../store/projects.actions';
import {selectAllProjects, selectProjectsLoading, selectProjectsTotal} from '../store/projects.selectors';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [
    AsyncPipe, RouterLink,
    MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatChipsModule,
  ],
  template: `
    <div class="header">
      <h1>Projects</h1>
      <button mat-flat-button color="primary" routerLink="new">
        <mat-icon>add</mat-icon>
        New project
      </button>
    </div>

    @if (loading$ | async) {
      <mat-progress-spinner mode="indeterminate" diameter="40"/>
    }

    <table mat-table [dataSource]="(projects$ | async) ?? []" class="mat-elevation-z2">
      <ng-container matColumnDef="key">
        <th mat-header-cell *matHeaderCellDef>Key</th>
        <td mat-cell *matCellDef="let p"><code>{{ p.key }}</code></td>
      </ng-container>
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let p">{{ p.name }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let p">
          <mat-chip [highlighted]="p.status === 'ACTIVE'">{{ p.status }}</mat-chip>
        </td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let p">
          <a mat-icon-button [routerLink]="[p.id, 'edit']">
            <mat-icon>edit</mat-icon>
          </a>
          <button mat-icon-button (click)="remove(p.id)">
            <mat-icon>delete</mat-icon>
          </button>
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>

    <mat-paginator
      [length]="(total$ | async) ?? 0"
      [pageSize]="20"
      [pageSizeOptions]="[10, 20, 50]"
      (page)="onPage($event)"/>
  `,
  styles: [`
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    table {
      width: 100%;
    }
  `],
})
export class ProjectListComponent implements OnInit {
  private readonly store = inject(Store);

  readonly projects$ = this.store.select(selectAllProjects);
  readonly loading$ = this.store.select(selectProjectsLoading);
  readonly total$ = this.store.select(selectProjectsTotal);
  readonly cols = ['key', 'name', 'status', 'actions'];

  ngOnInit(): void {
    this.store.dispatch(ProjectsActions.loadList({page: 0, size: 20}));
  }

  onPage(e: PageEvent): void {
    this.store.dispatch(ProjectsActions.loadList({page: e.pageIndex, size: e.pageSize}));
  }

  remove(id: string): void {
    if (confirm('Delete project?')) {
      this.store.dispatch(ProjectsActions.delete({id: id}));
    }
  }
}
