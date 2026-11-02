import { useEffect, useState, FormEvent } from 'react';
import { fetchComparison, fetchContext } from '../api';
import { CompareData, ContextData } from '../types';

export function CompareView() {
  const [context, setContext] = useState<ContextData | null>(null);
  const [selectableMondays, setSelectableMondays] = useState<string[]>([]);

  const [weekA, setWeekA] = useState<string>('');
  const [weekB, setWeekB] = useState<string>('');

  const [compareData, setCompareData] = useState<CompareData | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Generate selectable Mondays up to currentWeekStart
  useEffect(() => {
    let mounted = true;
    async function init() {
      try {
        const ctx = await fetchContext();
        if (!mounted) return;
        setContext(ctx);

        const mondays: string[] = [];
        let curr = new Date(ctx.currentWeekStart + 'T00:00:00Z');
        const minDate = new Date('2019-12-30T00:00:00Z');

        // Populate recent 52 weeks or back to 2019-12-30
        let count = 0;
        while (curr >= minDate && count < 52) {
          mondays.push(curr.toISOString().slice(0, 10));
          curr = new Date(curr.getTime() - 7 * 24 * 60 * 60 * 1000);
          count++;
        }

        setSelectableMondays(mondays);

        if (mondays.length >= 2) {
          // Default Week B to current, Week A to previous
          setWeekB(mondays[0]);
          setWeekA(mondays[1]);
        }
      } catch (err: any) {
        if (!mounted) return;
        setError('Failed to fetch context: ' + err.message);
      }
    }
    init();
    return () => {
      mounted = false;
    };
  }, []);

  const handleCompare = async (e?: FormEvent) => {
    if (e) e.preventDefault();
    if (!weekA || !weekB) {
      setError('Please select two weeks to compare.');
      return;
    }
    if (weekA === weekB) {
      setError('Please select two distinct weeks.');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await fetchComparison(weekA, weekB);
      setCompareData(data);
    } catch (err: any) {
      setError(err.message || 'Comparison request failed');
    } finally {
      setLoading(false);
    }
  };

  // Trigger compare once initial weeks are set
  useEffect(() => {
    if (weekA && weekB && weekA !== weekB && !compareData && !loading) {
      handleCompare();
    }
  }, [weekA, weekB]);

  return (
    <div className="compare-container">
      <div className="card">
        <h2>Compare Weekly Waste</h2>
        <p className="subtitle">
          Evaluate food waste reduction trends between two distinct reporting periods.
        </p>

        {error && (
          <div className="alert alert-error" role="alert">
            <strong>Error:</strong> {error}
          </div>
        )}

        <form onSubmit={handleCompare} className="compare-form">
          <div className="form-row">
            <div className="form-group half">
              <label htmlFor="select-week-a">Base Period (Week A)</label>
              <select
                id="select-week-a"
                value={weekA}
                onChange={(e) => setWeekA(e.target.value)}
                disabled={loading}
              >
                {selectableMondays.map((m) => (
                  <option key={`a-${m}`} value={m}>
                    Week of {m} {m === context?.currentWeekStart ? '(Current)' : ''}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group half">
              <label htmlFor="select-week-b">Target Period (Week B)</label>
              <select
                id="select-week-b"
                value={weekB}
                onChange={(e) => setWeekB(e.target.value)}
                disabled={loading}
              >
                {selectableMondays.map((m) => (
                  <option key={`b-${m}`} value={m}>
                    Week of {m} {m === context?.currentWeekStart ? '(Current)' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <button
            type="submit"
            className="button button-primary"
            disabled={loading || !weekA || !weekB || weekA === weekB}
          >
            {loading ? 'Analyzing...' : 'Run Comparison'}
          </button>
        </form>
      </div>

      {compareData && (
        <div className="results-container">
          <div className="card delta-card">
            <h3>Comparison Outcome</h3>
            {!compareData.comparable ? (
              <div className="alert alert-info">
                <strong>Not Comparable:</strong> One or both selected weeks have 0 logged waste entries.
                Both periods must contain at least one entry to calculate meaningful percentage and gram differences.
              </div>
            ) : (
              <div className="delta-grid">
                <div className="delta-item">
                  <span className="delta-label">Absolute Difference (B − A)</span>
                  <span
                    className={`delta-value ${
                      compareData.differenceGrams! <= 0 ? 'text-positive' : 'text-negative'
                    }`}
                  >
                    {compareData.differenceGrams! > 0 ? '+' : ''}
                    {compareData.differenceGrams!.toLocaleString()} g
                  </span>
                  <span className="delta-sub">
                    {compareData.differenceGrams! <= 0 ? 'Waste Reduced' : 'Waste Increased'}
                  </span>
                </div>

                <div className="delta-item">
                  <span className="delta-label">Percentage Change</span>
                  <span
                    className={`delta-value ${
                      compareData.percentChange! <= 0 ? 'text-positive' : 'text-negative'
                    }`}
                  >
                    {compareData.percentChange! > 0 ? '+' : ''}
                    {compareData.percentChange!.toFixed(1)}%
                  </span>
                  <span className="delta-sub">Rounded HALF_UP</span>
                </div>
              </div>
            )}
          </div>

          <div className="comparison-columns">
            {/* Week A Card */}
            <div className="card week-card">
              <div className="week-card-header">
                <h4>Period A: {compareData.weekA.weekStart}</h4>
                {compareData.weekA.inProgress && (
                  <span className="badge badge-in-progress">Ongoing</span>
                )}
              </div>
              <p className="small-text">
                {compareData.weekA.weekStart} to {compareData.weekA.weekEnd}
              </p>
              <div className="metric-row">
                <span>Total Waste:</span>
                <strong>{compareData.weekA.totalGrams.toLocaleString()} g</strong>
              </div>
              <div className="metric-row">
                <span>Logged Entries:</span>
                <strong>{compareData.weekA.entryCount}</strong>
              </div>

              <div className="top-categories-box">
                <h5>Top Categories</h5>
                {compareData.weekA.topCategories.length === 0 ? (
                  <p className="small-text empty">No entries</p>
                ) : (
                  <ul>
                    {compareData.weekA.topCategories.map((c) => (
                      <li key={c.category}>
                        <span>{c.category}</span>
                        <strong>{c.grams.toLocaleString()} g</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>

            {/* Week B Card */}
            <div className="card week-card">
              <div className="week-card-header">
                <h4>Period B: {compareData.weekB.weekStart}</h4>
                {compareData.weekB.inProgress && (
                  <span className="badge badge-in-progress">Ongoing</span>
                )}
              </div>
              <p className="small-text">
                {compareData.weekB.weekStart} to {compareData.weekB.weekEnd}
              </p>
              <div className="metric-row">
                <span>Total Waste:</span>
                <strong>{compareData.weekB.totalGrams.toLocaleString()} g</strong>
              </div>
              <div className="metric-row">
                <span>Logged Entries:</span>
                <strong>{compareData.weekB.entryCount}</strong>
              </div>

              <div className="top-categories-box">
                <h5>Top Categories</h5>
                {compareData.weekB.topCategories.length === 0 ? (
                  <p className="small-text empty">No entries</p>
                ) : (
                  <ul>
                    {compareData.weekB.topCategories.map((c) => (
                      <li key={c.category}>
                        <span>{c.category}</span>
                        <strong>{c.grams.toLocaleString()} g</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
