import { useState, FormEvent } from 'react';
import { AuthUser, signIn } from '../auth';

interface LoginViewProps {
  onLoginSuccess: (user: AuthUser) => void;
}

export function LoginView({ onLoginSuccess }: LoginViewProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please provide both email and password.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const user = await signIn(email, password);
      onLoginSuccess(user);
    } catch (err: any) {
      setError(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  const fillCredentials = (demoEmail: string) => {
    setEmail(demoEmail);
    setPassword('WasteLensDemo2026!');
    setError(null);
  };

  return (
    <div className="card login-card">
      <h2>Sign In to WasteLens</h2>
      <p className="subtitle">
        Access your tenant-isolated synthetic food waste dashboard.
      </p>

      {error && (
        <div className="alert alert-error" role="alert" id="login-error">
          <strong>Error:</strong> {error}
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label htmlFor="login-email">Email Address</label>
          <input
            id="login-email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={loading}
            placeholder="e.g. account-a@wastelens.demo"
          />
        </div>

        <div className="form-group">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={loading}
          />
        </div>

        <button type="submit" className="button button-primary" disabled={loading}>
          {loading ? 'Authenticating...' : 'Sign In'}
        </button>
      </form>

      <div className="demo-credentials-box">
        <h3>Pre-provisioned Demo Accounts</h3>
        <p className="small-text">Click an account below to populate credentials:</p>
        <div className="button-group">
          <button
            type="button"
            className="button button-outline"
            onClick={() => fillCredentials('account-a@wastelens.demo')}
            disabled={loading}
          >
            Demo Account A
          </button>
          <button
            type="button"
            className="button button-outline"
            onClick={() => fillCredentials('account-b@wastelens.demo')}
            disabled={loading}
          >
            Demo Account B
          </button>
        </div>
      </div>
    </div>
  );
}
