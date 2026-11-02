import { useEffect, useState } from 'react';
import { fetchContext, fetchWeekSummary } from '../api';
import { ContextData, WeekSummary } from '../types';

interface DashboardViewProps {
  onNavigate: (path: string) => void;
}

export function DashboardView({ onNavigate }: DashboardViewProps) {
  const [context, setContext] = useState<ContextData | null>(null);
  const [summary, setSummary] = useState<WeekSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadData() {
      try {
        setLoading(true);
        setError(null);
        const ctx = await fetchContext();
        if (!mounted) return;
        setContext(ctx);

        const sum = await fetchWeekSummary(ctx.currentWeekStart);
        if (!mounted) return;
        setSummary(sum);
      } catch (err: any) {
        if (!mounted) return;
        setError(err.message || 'Failed to load dashboard data');
      } finally {
        if (mounted) setLoading(false);
      }
    }

    loadData();
    return () => {
      mounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="card loading-card">
        <p>Loading weekly waste metrics from server...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="card">
        <div className="alert alert-error" role="alert">
          <strong>Dashboard Error:</strong> {error}
        </div>
        <button className="button button-outline" onClick={() => window.location.reload()}>
          Retry
        </button>
      </div>
    );
  }

  if (!summary || !context) {
    return null;
  }

  return (
    <div className="dashboard-container">
      <div className="card header-card">
        <div className="header-flex">
          <div>
            <h2>Current Week Summary</h2>
            <p className="subtitle">
              Period: <strong>{summary.weekStart}</strong> to <strong>{summary.weekEnd}</strong> ({summary.timezone})
            </p>
          </div>
          {summary.inProgress && (
            <span className="badge badge-in-progress" title="Data for this ongoing week is accumulating">
              ● Ongoing Week
            </span>
          )}
        </div>
      </div>

      <div className="metrics-grid">
        <div className="card metric-card">
          <span className="metric-label">Total Waste</span>
          <span className="metric-value">{(summary.totalGrams ?? 0).toLocaleString()} <span className="unit">g</span></span>
          <span className="metric-sub">{((summary.totalGrams ?? 0) / 1000).toFixed(2)} kg</span>
        </div>

        <div className="card metric-card">
          <span className="metric-label">Logged Entries</span>
          <span className="metric-value">{summary.entryCount ?? 0}</span>
          <span className="metric-sub">Submissions this week</span>
        </div>
      </div>

      <div className="card">
        <h3>Top Waste Categories</h3>
        {(summary.entryCount ?? 0) === 0 ? (
          <div className="empty-state">
            <p>No food waste entries recorded for this week yet.</p>
            <button
              className="button button-primary"
              onClick={() => onNavigate('/entries/new')}
            >
              Log First Entry
            </button>
          </div>
        ) : (
          <div className="categories-list">
            {(summary.topCategories ?? []).map((cat, idx) => {
              const total = summary.totalGrams ?? 0;
              const pct = total > 0
                ? (((cat.grams ?? 0) / total) * 100).toFixed(1)
                : '0.0';
              return (
                <div key={cat.category} className="category-item">
                  <div className="category-header">
                    <span className="category-rank">#{idx + 1}</span>
                    <span className="category-name">{cat.category}</span>
                    <span className="category-grams">{(cat.grams ?? 0).toLocaleString()} g ({pct}%)</span>
                  </div>
                  <div className="progress-bar-container">
                    <div
                      className="progress-bar-fill"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="card actions-card">
        <h3>Quick Actions</h3>
        <div className="button-group">
          <button
            className="button button-primary"
            onClick={() => onNavigate('/entries/new')}
          >
            + Log Waste Entry
          </button>
          <button
            className="button button-outline"
            onClick={() => onNavigate('/compare')}
          >
            Compare Previous Weeks
          </button>
        </div>
      </div>
    </div>
  );
}
