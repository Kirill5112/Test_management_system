export interface ErrorResponse {
  code: string;        // ErrorCode из tms-common
  message: string;
  timestamp?: string;  // ISO-8601 с offset
  path?: string;
}
