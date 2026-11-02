import { useEffect, useState, MouseEvent } from 'react';
import { downloadExport } from './api';
import { AuthUser, getCurrentUser, signOut } from './auth';
import { PendingSubmission } from './types';
import { CompareView } from './views/CompareView';
import { DashboardView } from './views/DashboardView';
import { LoginView } from './views/LoginView';
import { NewEntryView } from './views/NewEntryView';

export function App() {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [authInitialized, setAuthInitialized] = useState(false);

  // Root-level pending submission store (PRD FR-E5)
  // Preserved across route changes and re-authentications
  const [pendingSubmission, setPendingSubmission] = useState<PendingSubmission | null>(null);

  // Export state & guidance notice (FR-X2, T-EXP-05)
  const [isExporting, setIsExporting] = useState(false);
  const [exportNotice, setExportNotice] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function checkAuth() {
      try {
        const user = await getCurrentUser();
        if (mounted && user) {
          setCurrentUser(user);
        }
      } catch (ignored) {
      } finally {
        if (mounted) setAuthInitialized(true);
      }
    }
    checkAuth();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const navigate = (path: string, e?: MouseEvent) => {
    if (e) e.preventDefault();
    if (window.location.pathname !== path) {
      window.history.pushState({}, '', path);
      setCurrentPath(path);
    }
  };

  const handleSignOut = async () => {
    await signOut();
    setCurrentUser(null);
    navigate('/login');
  };

  const handleExport = async () => {
    setIsExporting(true);
    setExportNotice(null);
    try {
      const result = await downloadExport();
      setExportNotice(
        `Export complete: ${result.filename} (${(result.sizeBytes / 1024).toFixed(1)} KB) downloaded. Notice: This export contains synthetic food waste records. Please store or delete responsibly on your device.`
      );
    } catch (err: any) {
      alert('Export failed: ' + (err.message || 'Unknown error'));
    } finally {
      setIsExporting(false);
    }
  };

  if (!authInitialized) {
    return (
      <div className="card loading-card" style={{ maxWidth: '400px', margin: '4rem auto' }}>
        <p>Initializing WasteLens session...</p>
      </div>
    );
  }

  // Guard protected routes
  const isProtectedPath = ['/', '/dashboard', '/entries/new', '/compare'].includes(currentPath);
  if (!currentUser && isProtectedPath) {
    return (
      <>
        <header>
          <div className="header-inner">
            <h1 className="logo">WasteLens</h1>
          </div>
        </header>
        <main>
          <div className="demo-banner">
            <strong>Synthetic Demonstration:</strong> Isolated multi-tenant food waste tracking with strict idempotency and weekly comparisons.
          </div>
          <LoginView
            onLoginSuccess={(user) => {
              setCurrentUser(user);
              navigate(currentPath === '/' ? '/dashboard' : currentPath);
            }}
          />
        </main>
      </>
    );
  }

  // Render view
  const renderContent = () => {
    switch (currentPath) {
      case '/':
      case '/dashboard':
        return <DashboardView onNavigate={(p) => navigate(p)} />;
      case '/entries/new':
        return (
          <NewEntryView
            currentUser={currentUser!}
            pendingSubmission={pendingSubmission}
            onUpdatePending={setPendingSubmission}
            onNavigate={(p) => navigate(p)}
          />
        );
      case '/compare':
        return <CompareView />;
      case '/login':
        return (
          <LoginView
            onLoginSuccess={(user) => {
              setCurrentUser(user);
              navigate('/dashboard');
            }}
          />
        );
      default:
        return (
          <div className="card">
            <h2>Page Not Found</h2>
            <p>The requested route does not exist.</p>
            <p>
              <a href="/dashboard" onClick={(e) => navigate('/dashboard', e)}>
                Return to Dashboard
              </a>
            </p>
          </div>
        );
    }
  };

  return (
    <>
      <header>
        <div className="header-inner">
          <div className="header-brand">
            <h1 className="logo" onClick={() => navigate('/dashboard')}>
              WasteLens
            </h1>
            <span className="badge badge-subtle">Synthetic Demo</span>
          </div>

          <nav>
            <a
              href="/dashboard"
              className={currentPath === '/' || currentPath === '/dashboard' ? 'active' : ''}
              onClick={(e) => navigate('/dashboard', e)}
            >
              Dashboard
            </a>
            <a
              href="/entries/new"
              className={currentPath === '/entries/new' ? 'active' : ''}
              onClick={(e) => navigate('/entries/new', e)}
            >
              Log Waste
            </a>
            <a
              href="/compare"
              className={currentPath === '/compare' ? 'active' : ''}
              onClick={(e) => navigate('/compare', e)}
            >
              Compare
            </a>
            <button
              type="button"
              className="button button-nav-export"
              onClick={handleExport}
              disabled={isExporting}
            >
              {isExporting ? 'Exporting...' : 'Export JSON'}
            </button>
          </nav>

          <div className="user-badge">
            <span className="user-email" title={`Owner ID: ${currentUser?.id}`}>
              {currentUser?.email}
            </span>
            <button
              type="button"
              className="button button-small button-outline"
              onClick={handleSignOut}
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      <main>
        <div className="demo-banner">
          <strong>Tenant Isolation:</strong> Logged in as <code>{currentUser?.email}</code>. All entries and comparisons are strictly scoped to your private tenant.
        </div>

        {exportNotice && (
          <div className="alert alert-info" role="alert">
            {exportNotice}
            <button
              className="button button-small button-outline"
              style={{ marginLeft: '1rem' }}
              onClick={() => setExportNotice(null)}
            >
              Dismiss
            </button>
          </div>
        )}

        {renderContent()}
      </main>

      <footer>
        <p className="small-text">
          WasteLens &bull; Synthetic Food Waste Tracker &bull; Powered by Neon Postgres &amp; Managed Better Auth
        </p>
      </footer>
    </>
  );
}
