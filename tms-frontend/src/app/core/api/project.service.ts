import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { Project, ProjectCreateRequest, ProjectStatus, ProjectUpdateRequest } from '../models/project.model';

export interface ProjectListQuery {
  page?: number;
  size?: number;
  sort?: string;
  status?: ProjectStatus;
  ownerId?: string;
}

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/repository/projects`;

  list(query: ProjectListQuery = {}): Observable<PageResponse<Project>> {
    let params = new HttpParams();
    if (query.page !== undefined)   params = params.set('page', query.page);
    if (query.size !== undefined)   params = params.set('size', query.size);
    if (query.sort)                 params = params.set('sort', query.sort);
    if (query.status)               params = params.set('status', query.status);
    if (query.ownerId)              params = params.set('ownerId', query.ownerId);

    return this.http.get<PageResponse<Project>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/${id}`);
  }

  getByKey(key: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/by-key/${key}`);
  }

  create(body: ProjectCreateRequest): Observable<Project> {
    return this.http.post<Project>(this.baseUrl, body);
  }

  update(id: string, body: ProjectUpdateRequest): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/${id}`, body);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
