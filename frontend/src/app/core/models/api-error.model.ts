/** The error envelope every backend error response uses (PRD §8). */
export interface ApiError {
  timestamp: string;
  status: number;
  errorCode: string;
  message: string;
  path: string;
  fieldErrors: unknown[];
}
