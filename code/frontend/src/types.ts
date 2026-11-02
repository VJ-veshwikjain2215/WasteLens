export type Category = 'produce' | 'grains' | 'dairy' | 'protein' | 'prepared' | 'other';
export type Stage = 'preparation' | 'service' | 'storage';
export type Reason = 'overproduction' | 'spoilage' | 'trimming' | 'plate_return' | 'other';

export interface WasteEntry {
  id: string;
  submissionId: string;
  entryDate: string;
  category: Category;
  grams: number;
  stage: Stage;
  reason: Reason;
  createdAt: string;
}

export interface CreateEntryPayload {
  entryDate: string;
  category: Category;
  grams: number;
  stage: Stage;
  reason: Reason;
}

export interface ContextData {
  today: string;
  timezone: string;
  currentWeekStart: string;
}

export interface CategoryGrams {
  category: Category;
  grams: number;
}

export interface WeekSummary {
  weekStart: string;
  weekEnd: string;
  timezone: string;
  totalGrams: number;
  entryCount: number;
  inProgress: boolean;
  topCategories: CategoryGrams[];
}

export interface CompareData {
  weekA: WeekSummary;
  weekB: WeekSummary;
  differenceGrams: number | null;
  percentChange: number | null;
  comparable: boolean;
}

export interface PendingSubmission {
  submissionId: string;
  payload: CreateEntryPayload;
  originalOwnerId: string;
  originalOwnerEmail: string;
  status: 'idle' | 'in_flight' | 'uncertain' | 'confirmed';
  replayed?: boolean;
  lastError?: string;
}

export interface UserSession {
  user: {
    id: string;
    email: string;
  };
  token: string;
}
