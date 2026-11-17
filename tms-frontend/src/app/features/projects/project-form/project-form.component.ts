import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { take } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { ProjectService } from '../../../core/api/project.service';
import { ProjectsActions } from '../store/projects.actions';

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule,
  ],
  template: `
    <h1>{{ isEdit ? 'Edit project' : 'New project' }}</h1>

    <form [formGroup]="form" (ngSubmit)="submit()" class="form">
      <mat-form-field>
        <mat-label>Key</mat-label>
        <input matInput formControlName="key" maxlength="20" />
        @if (form.controls.key.hasError('pattern')) {
          <mat-error>Должно быть ^[A-Z][A-Z0-9_]&#123;1,19&#125;$</mat-error>
        }
      </mat-form-field>

      <mat-form-field>
        <mat-label>Name</mat-label>
        <input matInput formControlName="name" maxlength="255" />
      </mat-form-field>

      <mat-form-field>
        <mat-label>Description</mat-label>
        <textarea matInput formControlName="description" rows="3"></textarea>
      </mat-form-field>

      <mat-form-field>
        <mat-label>Owner ID (UUID)</mat-label>
        <input matInput formControlName="ownerId" />
      </mat-form-field>

      @if (isEdit) {
        <mat-form-field>
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            <mat-option value="ACTIVE">ACTIVE</mat-option>
            <mat-option value="ARCHIVED">ARCHIVED</mat-option>
          </mat-select>
        </mat-form-field>
      }

      <div class="actions">
        <button mat-button type="button" (click)="cancel()">Cancel</button>
        <button mat-flat-button color="primary" [disabled]="form.invalid">Save</button>
      </div>
    </form>
  `,
  styles: [`
    .form { display: flex; flex-direction: column; max-width: 560px; gap: 12px; }
    .actions { display: flex; gap: 8px; }
  `],
})
export class ProjectFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(Store);
  private readonly api = inject(ProjectService);

  readonly form = this.fb.nonNullable.group({
    key:         ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]{1,19}$/)]],
    name:        ['', [Validators.required]],
    description: [null as string | null],
    ownerId:     ['', [Validators.required]],
    status:      ['ACTIVE' as 'ACTIVE' | 'ARCHIVED'],
  });

  get isEdit(): boolean {
    return this.route.snapshot.paramMap.has('id');
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.form.controls.key.disable();
      this.api.getById(id).pipe(take(1)).subscribe(p => {
        this.form.patchValue({
          key: p.key,
          name: p.name,
          description: p.description,
          ownerId: p.ownerId,
          status: p.status,
        });
      });
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.getRawValue();
    const id = this.route.snapshot.paramMap.get('id');

    if (id) {
      this.store.dispatch(ProjectsActions.update({
        id,
        body: { key: raw.key!, name: raw.name, description: raw.description, status: raw.status },
      }));
    } else {
      this.store.dispatch(ProjectsActions.create({
        body: { key: raw.key, name: raw.name, description: raw.description, ownerId: raw.ownerId },
      }));
    }
    this.router.navigate(['/projects']);
  }

  cancel(): void {
    this.router.navigate(['/projects']);
  }
}
