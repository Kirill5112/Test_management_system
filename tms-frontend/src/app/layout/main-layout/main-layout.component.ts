import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
  ],
  template: `
    <mat-sidenav-container class="app-container">
      <mat-sidenav mode="side" opened class="app-sidenav">
        <mat-toolbar>TMS</mat-toolbar>
        <mat-nav-list>
          <a mat-list-item routerLink="/projects" routerLinkActive="active">
            <mat-icon matListItemIcon>folder</mat-icon>
            <span matListItemTitle>Projects</span>
          </a>
          <a mat-list-item routerLink="/test-runs" routerLinkActive="active">
            <mat-icon matListItemIcon>play_circle</mat-icon>
            <span matListItemTitle>Test Runs</span>
          </a>
          <a mat-list-item routerLink="/test-results" routerLinkActive="active">
            <mat-icon matListItemIcon>check_circle</mat-icon>
            <span matListItemTitle>Test Results</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="app-content">
        <mat-toolbar color="primary">TMS</mat-toolbar>
        <div class="page">
          <router-outlet />
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .app-container { height: 100vh; }
    .app-sidenav { width: 240px; }
    .app-content { display: flex; flex-direction: column; }
    .page { padding: 24px; flex: 1; overflow: auto; }
    .active { background: rgba(63, 81, 181, 0.12); }
  `],
})
export class MainLayoutComponent {}
