import React, { createContext, useContext, useState, ReactNode, JSX, useEffect } from "react";
import { User, AuthContextType } from "../types";
import { loginApi } from "../utils/api";

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps): JSX.Element {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initializeAuth = async () => {
      const token = localStorage.getItem('accessToken');
      const userData = localStorage.getItem('userData');
      
      if (token && userData) {
        try {
          const parsedUser = JSON.parse(userData);
          setUser(parsedUser);
        } catch (error) {
          console.error("Error parsing stored user data:", error);
          // Clear invalid data
          localStorage.removeItem('accessToken');
          localStorage.removeItem('userData');
        }
      }
      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  const login = async (email: string, password: string): Promise<void> => {
    setIsLoading(true);
    try {
      const response = await loginApi(email, password);
      
      // FIX: Use the properly mapped response
      setUser(response.user);
      
      // FIX: Consistent token storage
      localStorage.setItem('accessToken', response.token); // Store as 'accessToken'
      localStorage.setItem('userData', JSON.stringify(response.user));
      
      console.log("Login successful! User:", response.user);
      
    } catch (error) {
      setUser(null);
      // Clear any existing invalid data on login failure
      localStorage.removeItem('accessToken');
      localStorage.removeItem('userData');
      
      console.error("Login failed:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = (): void => {
    setUser(null);
    // FIX: Consistent token removal
    localStorage.removeItem('accessToken');
    localStorage.removeItem('userData');
    console.log("User logged out.");
  };

  const updateUser = (user: User): void => {
    setUser(user);
    localStorage.setItem('userData', JSON.stringify(user));
  };

  const value: AuthContextType = {
    user,
    login,
    logout,
    isLoading,
    updateUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}