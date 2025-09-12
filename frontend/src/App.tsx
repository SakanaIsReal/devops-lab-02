// Import React
import React from 'react';
// Import Router components
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
// Import our Context
import { AuthProvider } from './contexts/AuthContext';
// Import our Pages
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { GroupDetailPage } from './pages/GroupDetailPage';
import CreateGroupPage from './pages/CreateGroupPage';
import PayPage from './pages/PayPage';
import SignUpPage from './pages/SignUpPage';
import { AccountPage } from './pages/AccountPage';
//Split Money 
import SplitMoneyPage from './pages/SplitMoneyPage';
import ManualSplitPage from './pages/ManualSplitPage';
import EqualSplitPage from './pages/EqualSplitPage';
// Import our Protected Route component
import { ProtectedRoute } from './components/ProtectedRoute';
import { PublicRoute } from './components/PublicRoute';

function App() {
  return (
    // 1. Wrap the entire app with the Router component to enable routing
    <Router>
      {/* 2. Wrap everything in our AuthProvider to give access to user state */}
      <AuthProvider>
        {/* 3. Define our application's routes */}
        <Routes>
          {/* 
            Route for the login page. 
            If a logged-in user somehow navigates to /login, redirect them to the dashboard.
          */}
          <Route
            path="/login"
            element={
              <PublicRoute>
                <LoginPage />
              </PublicRoute>
            }
          />

          <Route
            path="/signup"
            element={
              <PublicRoute>
                <SignUpPage />
              </PublicRoute>
            }
          />

          {/* 
            Protected route for the main dashboard.
            The ProtectedRoute component will handle the redirect if the user is not logged in.
          */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/group/:groupId"
            element={
              <ProtectedRoute>
                <GroupDetailPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/creategroup"
            element={
              <ProtectedRoute>
                <CreateGroupPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/pay/:transactionId"
            element={
              <ProtectedRoute>
                <PayPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/account"
            element={
              <ProtectedRoute>
                <AccountPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/splitmoney"
            element={
              <SplitMoneyPage />
            }
          />
          <Route
            path="/manualsplit"
            element={
              <ManualSplitPage />
            }
          />
          <Route
            path="/equalsplit"
            element={
              <EqualSplitPage />
            }
          />

          {/* 
            Catch-all route. 
            If the user goes to the root URL '/', redirect them to /dashboard.
            The `Replace` prop is used to replace the current entry in the history stack.
          */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </Router>
  );
}

export default App;