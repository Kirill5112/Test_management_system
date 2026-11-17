export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

export interface Project {
  id: string;                      // UUID как строка
  key: string;
  name: string;
  description: string | null;
  ownerId: string;
  status: ProjectStatus;
  createdAt: string;               // ISO-8601 с offset: "2026-11-15T10:00:00+03:00"
  updatedAt: string;
}

export interface ProjectCreateRequest {
  key: string;
  name: string;
  description?: string | null;
  ownerId: string;
  status?: ProjectStatus;          // опционально, бэк ставит ACTIVE по умолчанию
}

export interface ProjectUpdateRequest {
  key: string;
  name: string;
  description?: string | null;
  status: ProjectStatus;
}
