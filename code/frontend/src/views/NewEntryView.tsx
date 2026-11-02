import { useEffect, useState, FormEvent } from 'react';
import { fetchContext, submitEntry } from '../api';
import { AuthUser } from '../auth';
import { Category, CreateEntryPayload, PendingSubmission, Reason, Stage } from '../types';

interface NewEntryViewProps {
  currentUser: AuthUser;
  pendingSubmission: PendingSubmission | null;
  onUpdatePending: (pending: PendingSubmission | null) => void;
  onNavigate: (path: string) => void;
}

export function NewEntryView({
  currentUser,
  pendingSubmission,
  onUpdatePending,
  onNavigate,
}: NewEntryViewProps) {
  const [serverToday, setServerToday] = useState<string>('');
  const [loadingContext, setLoadingContext] = useState<boolean>(true);

  // Form fields
  const [entryDate, setEntryDate] = useState<string>('');
  const [category, setCategory] = useState<Category>('produce');
  const [grams, setGrams] = useState<string>('500');
  const [stage, setStage] = useState<Stage>('preparation');
  const [reason, setReason] = useState<Reason>('trimming');

  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Load server today from /api/context
  useEffect(() => {
    let mounted = true;
    async function loadServerTime() {
      try {
        const ctx = await fetchContext();
        if (!mounted) return;
        setServerToday(ctx.today);
        if (!entryDate) {
          setEntryDate(ctx.today);
        }
      } catch (err: any) {
        if (!mounted) return;
        setFormError('Failed to synchronize with server clock: ' + err.message);
      } finally {
        if (mounted) setLoadingContext(false);
      }
    }
    loadServerTime();
    return () => {
      mounted = false;
    };
  }, []);

  // Sync form with existing pending submission if present
  useEffect(() => {
    if (pendingSubmission) {
      setEntryDate(pendingSubmission.payload.entryDate);
      setCategory(pendingSubmission.payload.category);
      setGrams(String(pendingSubmission.payload.grams));
      setStage(pendingSubmission.payload.stage);
      setReason(pendingSubmission.payload.reason);
    }
  }, [pendingSubmission]);

  const isMismatchedAccount =
    pendingSubmission &&
    pendingSubmission.status === 'uncertain' &&
    pendingSubmission.originalOwnerId !== currentUser.id;

  const isLocked: boolean =
    loadingContext ||
    Boolean(
      pendingSubmission &&
        (pendingSubmission.status === 'in_flight' || pendingSubmission.status === 'uncertain')
    );

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSuccessMessage(null);

    const parsedGrams = parseInt(grams, 10);
    if (isNaN(parsedGrams) || parsedGrams < 1 || parsedGrams > 100000) {
      setFormError('Grams must be a whole number between 1 and 100,000.');
      return;
    }

    if (!entryDate) {
      setFormError('Entry date is required.');
      return;
    }

    if (entryDate < '2020-01-01') {
      setFormError('Entry date cannot be earlier than 2020-01-01.');
      return;
    }

    if (serverToday && entryDate > serverToday) {
      setFormError(`Entry date cannot be in the future (today in Kolkata is ${serverToday}).`);
      return;
    }

    const payload: CreateEntryPayload = {
      entryDate,
      category,
      grams: parsedGrams,
      stage,
      reason,
    };

    const submissionId = pendingSubmission?.submissionId || crypto.randomUUID();

    const newPending: PendingSubmission = {
      submissionId,
      payload,
      originalOwnerId: currentUser.id,
      originalOwnerEmail: currentUser.email,
      status: 'in_flight',
    };
    onUpdatePending(newPending);

    try {
      const response = await submitEntry(payload, submissionId);
      onUpdatePending(null); // Cleared on confirmed save
      setSuccessMessage(
        response.replayed
          ? 'Confirmed: This entry was already safely recorded (idempotent replay acknowledged).'
          : `Success: Waste entry (${response.entry.grams}g of ${response.entry.category}) recorded successfully!`
      );
    } catch (err: any) {
      onUpdatePending({
        ...newPending,
        status: 'uncertain',
        lastError: err.message || 'Network request failed',
      });
      setFormError(
        'Submission outcome unresolved due to network error. Your exact entry has been preserved. Please retry.'
      );
    }
  };

  const handleRetry = async () => {
    if (!pendingSubmission) return;
    setFormError(null);
    setSuccessMessage(null);

    onUpdatePending({
      ...pendingSubmission,
      status: 'in_flight',
    });

    try {
      const response = await submitEntry(
        pendingSubmission.payload,
        pendingSubmission.submissionId
      );
      onUpdatePending(null);
      setSuccessMessage(
        response.replayed
          ? 'Confirmed: Entry committed successfully via idempotent retry.'
          : 'Success: Entry recorded successfully!'
      );
    } catch (err: any) {
      onUpdatePending({
        ...pendingSubmission,
        status: 'uncertain',
        lastError: err.message || 'Retry failed',
      });
      setFormError('Retry failed: ' + err.message);
    }
  };

  const handleDiscard = () => {
    const confirmed = window.confirm(
      'Notice: An earlier attempt may have already committed to the server. Discarding locally will not undo an entry that already committed. Do you wish to discard?'
    );
    if (confirmed) {
      onUpdatePending(null);
      setFormError(null);
      setSuccessMessage(null);
    }
  };

  return (
    <div className="card form-card">
      <h2>Log Food Waste Entry</h2>
      <p className="subtitle">
        Record an immutable food waste measurement scoped to your account.
      </p>

      {successMessage && (
        <div className="alert alert-success" role="alert">
          {successMessage}
          <div className="button-group" style={{ marginTop: '0.75rem' }}>
            <button className="button button-outline" onClick={() => onNavigate('/dashboard')}>
              Go to Dashboard
            </button>
            <button
              className="button button-primary"
              onClick={() => {
                setSuccessMessage(null);
                setGrams('500');
              }}
            >
              Log Another Entry
            </button>
          </div>
        </div>
      )}

      {/* Account Mismatch Warning (FR-E5) */}
      {isMismatchedAccount && (
        <div className="alert alert-warning" role="alert">
          <strong>Account Mismatch:</strong> This pending entry was created by another account (
          <em>{pendingSubmission.originalOwnerEmail}</em>). Sign back into that account to retry, or discard the entry.
          <div className="button-group" style={{ marginTop: '0.75rem' }}>
            <button className="button button-danger" onClick={handleDiscard}>
              Discard Entry
            </button>
          </div>
        </div>
      )}

      {/* Uncertain Status Banner (FR-E5) */}
      {pendingSubmission &&
        pendingSubmission.status === 'uncertain' &&
        !isMismatchedAccount && (
          <div className="alert alert-warning" role="alert">
            <strong>Unresolved Submission:</strong> The network request did not return a confirmation, but the entry may have committed.
            <p className="small-text">
              Submission UUID: <code>{pendingSubmission.submissionId}</code>
            </p>
            <div className="button-group" style={{ marginTop: '0.75rem' }}>
              <button className="button button-primary" onClick={handleRetry}>
                Retry Same Submission
              </button>
              <button className="button button-danger" onClick={handleDiscard}>
                Discard
              </button>
            </div>
          </div>
        )}

      {formError && (
        <div className="alert alert-error" role="alert">
          <strong>Validation Error:</strong> {formError}
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label htmlFor="entry-date">Entry Date (Asia/Kolkata Calendar)</label>
          <input
            id="entry-date"
            type="date"
            required
            min="2020-01-01"
            max={serverToday || undefined}
            value={entryDate}
            onChange={(e) => setEntryDate(e.target.value)}
            disabled={isLocked}
          />
          <small>Independent of device timezone (server today: {serverToday || 'loading...'})</small>
        </div>

        <div className="form-group">
          <label htmlFor="entry-category">Category</label>
          <select
            id="entry-category"
            value={category}
            onChange={(e) => setCategory(e.target.value as Category)}
            disabled={isLocked}
          >
            <option value="produce">Produce (fruits, vegetables, herbs)</option>
            <option value="grains">Grains (rice, pasta, bread, flour)</option>
            <option value="dairy">Dairy (milk, cheese, butter, yogurt)</option>
            <option value="protein">Protein (meat, poultry, seafood, tofu)</option>
            <option value="prepared">Prepared (cooked dishes, sauces, soups)</option>
            <option value="other">Other (miscellaneous food items)</option>
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="entry-grams">Waste Weight (Grams)</label>
          <input
            id="entry-grams"
            type="number"
            step="1"
            min="1"
            max="100000"
            required
            value={grams}
            onChange={(e) => setGrams(e.target.value)}
            disabled={isLocked}
            placeholder="1 to 100,000 grams"
          />
          <small>Integer only (1 g to 100,000 g)</small>
        </div>

        <div className="form-row">
          <div className="form-group half">
            <label htmlFor="entry-stage">Kitchen Stage</label>
            <select
              id="entry-stage"
              value={stage}
              onChange={(e) => setStage(e.target.value as Stage)}
              disabled={isLocked}
            >
              <option value="preparation">Preparation (prep, trimming, peeling)</option>
              <option value="service">Service (line, plate returns, buffet)</option>
              <option value="storage">Storage (walk-in, pantry, dry stock)</option>
            </select>
          </div>

          <div className="form-group half">
            <label htmlFor="entry-reason">Primary Reason</label>
            <select
              id="entry-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value as Reason)}
              disabled={isLocked}
            >
              <option value="trimming">Trimming / Inedible parts</option>
              <option value="spoilage">Spoilage / Decomposition</option>
              <option value="overproduction">Overproduction / Unserved</option>
              <option value="plate_return">Plate Return / Customer uneaten</option>
              <option value="other">Other reason</option>
            </select>
          </div>
        </div>

        <div className="form-actions">
          <button
            type="submit"
            className="button button-primary"
            disabled={isLocked || !!isMismatchedAccount}
          >
            {pendingSubmission?.status === 'in_flight' ? 'Recording...' : 'Submit Waste Entry'}
          </button>
        </div>
      </form>
    </div>
  );
}
