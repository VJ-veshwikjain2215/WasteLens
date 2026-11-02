import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { App } from './App';
import * as auth from './auth';

describe('WasteLens Frontend Component Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    window.history.pushState({}, '', '/');
  });

  it('renders WasteLens header, demo badge, and login prompt when unauthenticated', async () => {
    vi.spyOn(auth, 'getCurrentUser').mockResolvedValue(null);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('WasteLens')).toBeInTheDocument();
      expect(screen.getByText(/Sign In to WasteLens/i)).toBeInTheDocument();
      expect(screen.getByText(/Pre-provisioned Demo Accounts/i)).toBeInTheDocument();
    });
  });

  it('populates demo credentials when clicking Demo Account A', async () => {
    vi.spyOn(auth, 'getCurrentUser').mockResolvedValue(null);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Demo Account A/i })).toBeInTheDocument();
    });

    const accountAButton = screen.getByRole('button', { name: /Demo Account A/i });
    fireEvent.click(accountAButton);

    const emailInput = screen.getByLabelText(/Email Address/i) as HTMLInputElement;
    const passwordInput = screen.getByLabelText(/Password/i) as HTMLInputElement;

    expect(emailInput.value).toBe('account-a@wastelens.demo');
    expect(passwordInput.value).toBe('WasteLensDemo2026!');
  });

  it('renders authenticated navigation and tenant indicator when logged in', async () => {
    vi.spyOn(auth, 'getCurrentUser').mockResolvedValue({
      id: 'af32b42e-6078-4f81-919c-d3a4b4e85683',
      email: 'account-a@wastelens.demo',
    });

    // Mock fetch for /api/context and /api/weeks
    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/context')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              today: '2026-11-02',
              timezone: 'Asia/Kolkata',
              currentWeekStart: '2026-11-02',
            }),
        });
      }
      if (url.includes('/api/weeks')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              weekStart: '2026-11-02',
              weekEnd: '2026-11-08',
              timezone: 'Asia/Kolkata',
              totalGrams: 0,
              entryCount: 0,
              inProgress: true,
              topCategories: [],
            }),
        });
      }
      return Promise.reject(new Error('Unhandled URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('account-a@wastelens.demo').length).toBeGreaterThan(0);
      expect(screen.getByRole('button', { name: /Sign Out/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Export JSON/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Dashboard/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Log Waste/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Compare/i })).toBeInTheDocument();
    });
  });

  it('navigates to Log Waste view and renders form fields', async () => {
    vi.spyOn(auth, 'getCurrentUser').mockResolvedValue({
      id: 'af32b42e-6078-4f81-919c-d3a4b4e85683',
      email: 'account-a@wastelens.demo',
    });

    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/context')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              today: '2026-11-02',
              timezone: 'Asia/Kolkata',
              currentWeekStart: '2026-11-02',
            }),
        });
      }
      if (url.includes('/api/weeks')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              weekStart: '2026-11-02',
              weekEnd: '2026-11-08',
              timezone: 'Asia/Kolkata',
              totalGrams: 0,
              entryCount: 0,
              inProgress: true,
              topCategories: [],
            }),
        });
      }
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({}),
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('link', { name: /Log Waste/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('link', { name: /Log Waste/i }));

    await waitFor(() => {
      expect(screen.getByText(/Log Food Waste Entry/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Category/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Waste Weight \(Grams\)/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Kitchen Stage/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Primary Reason/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Submit Waste Entry/i })).toBeInTheDocument();
    });
  });
});
