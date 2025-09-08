import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

interface PublicRouteProps {
  children: React.ReactNode;
}

// This component is the opposite of ProtectedRoute.
// It only allows access to its children if the user is NOT authenticated (e.g., Login, Register pages).
export const PublicRoute: React.FC<PublicRouteProps> = ({ children }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  // If the user IS authenticated, redirect them to the dashboard
  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  // If the user is not authenticated, render the public page (e.g., Login)
  return <>{children}</>;
};