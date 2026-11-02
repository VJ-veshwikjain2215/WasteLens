import { ContextData, CreateEntryPayload, WasteEntry, WeekSummary, CompareData } from './types';
import { getJwtToken } from './auth';

export class ApiError extends Error {
  code: string;
  status: number;
  requestId?: string;

  constructor(status: number, code: string, message: string, requestId?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = await getJwtToken();
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorCode = 'REQUEST_FAILED';
    let errorMessage = `HTTP error ${response.status}`;
    let requestId: string | undefined;

    try {
      const errorJson = await response.json();
      if (errorJson.error) {
        errorCode = errorJson.error.code || errorCode;
        errorMessage = errorJson.error.message || errorMessage;
        requestId = errorJson.error.requestId;
      }
    } catch (e) {
      // Non-JSON error body
    }

    throw new ApiError(response.status, errorCode, errorMessage, requestId);
  }

  return response.json() as Promise<T>;
}

export async function fetchContext(): Promise<ContextData> {
  return request<ContextData>('/api/context');
}

export async function fetchWeekSummary(monday: string): Promise<WeekSummary> {
  return request<WeekSummary>(`/api/weeks/${encodeURIComponent(monday)}`);
}

export async function fetchComparison(weekA: string, weekB: string): Promise<CompareData> {
  return request<CompareData>(`/api/compare?weekA=${encodeURIComponent(weekA)}&weekB=${encodeURIComponent(weekB)}`);
}

export async function submitEntry(
  payload: CreateEntryPayload,
  submissionId: string
): Promise<{ entry: WasteEntry; replayed: boolean }> {
  return request<{ entry: WasteEntry; replayed: boolean }>('/api/entries', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      submissionId,
      ...payload,
    }),
  });
}

export async function downloadExport(): Promise<{ sizeBytes: number; filename: string }> {
  const token = await getJwtToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/export', {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    let code = 'EXPORT_FAILED';
    let message = 'Export failed';
    try {
      const errorJson = await response.json();
      if (errorJson.error) {
        code = errorJson.error.code;
        message = errorJson.error.message;
      }
    } catch (ignored) {}
    throw new ApiError(response.status, code, message);
  }

  // Parse and validate exported JSON structure
  const blob = await response.blob();
  const text = await blob.text();
  const parsed = JSON.parse(text);

  if (parsed.schemaVersion !== 1 || !Array.isArray(parsed.entries)) {
    throw new Error('Export payload failed schema validation (expected schemaVersion 1 and array of entries)');
  }

  // Trigger browser download via object URL
  const downloadUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = downloadUrl;
  a.download = 'waste-lens-export.json';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(downloadUrl);

  return {
    sizeBytes: blob.size,
    filename: 'waste-lens-export.json',
  };
}
