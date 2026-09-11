import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom';
import { AuthBoundary, useSession } from './auth';
import { post } from './api';
import Jobs from './pages/Jobs';
import JobDetail from './pages/JobDetail';
import Profile from './pages/Profile';
import Resumes from './pages/Resumes';
import Outreach from './pages/Outreach';
import Tracker from './pages/Tracker';
import Activity from './pages/Activity';
import Settings from './pages/Settings';
import './style.css';
import Analytics from './pages/Analytics';
function Shell() {
  const session = useSession();
  return (
    <div className="app">
      <aside className="sidebar">
        <NavLink className="brand" to="/">
          <span className="brandmark">M</span> MyJobAI
        </NavLink>
        <p className="nav-label">YOUR WORKSPACE</p>
        <nav aria-label="Main navigation">
          {[
            ['/', '◈', 'Opportunities'],
            ['/profile', '◎', 'My profile'],
            ['/resumes', '▤', 'Resumes'],
            ['/tracker', '▥', 'Applications'],
            ['/outreach', '↗', 'Outreach'],
            ['/analytics', '◴', 'Insights'],
            ['/activity', '◷', 'Activity'],
            ['/settings', '⚙', 'Connections'],
          ].map(([path, icon, name]) => (
            <NavLink end={path === '/'} key={path} to={path}>
              <span aria-hidden>{icon}</span>
              {name}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">
          <span>YOU'RE IN CONTROL</span>
          <p>
            Your experience is the source.
            <br />
            Your approval is the final word.
          </p>
        </div>
        <div className="account">
          <span className="avatar">{session.email[0].toUpperCase()}</span>
          <div>
            <small>{session.email}</small>
            <button
              className="text-button"
              onClick={() =>
                post('/auth/logout')
                  .then(() => location.reload())
                  .catch(() => alert('Logout failed; please retry.'))
              }
            >
              Sign out
            </button>
          </div>
        </div>
      </aside>
      <main id="main" className="content">
        <Routes>
          <Route path="/" element={<Jobs />} />
          <Route path="/jobs/:id" element={<JobDetail />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/resumes" element={<Resumes />} />
          <Route path="/outreach" element={<Outreach />} />
          <Route path="/tracker" element={<Tracker />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/activity" element={<Activity />} />
          <Route path="/settings" element={<Settings />} />
          <Route
            path="*"
            element={
              <p>
                Page not found. <NavLink to="/">Return to opportunities</NavLink>
              </p>
            }
          />
        </Routes>
      </main>
    </div>
  );
}
class ErrorBoundary extends React.Component<{ children: React.ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <main className="login">
        <h1>This view could not be loaded.</h1>
        <button onClick={() => location.reload()}>Reload workspace</button>
      </main>
    ) : (
      this.props.children
    );
  }
}
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <AuthBoundary>
          <Shell />
        </AuthBoundary>
      </BrowserRouter>
    </ErrorBoundary>
  </React.StrictMode>,
);
