// 1. Import necessary modules from React and our types
import React, { createContext, useContext, useState, ReactNode, JSX } from "react";
import { User, AuthContextType } from "../types";
import { mockLoginApi } from "../utils/mockApi";

// 2. Create the Context object.
// We provide a default value that matches the AuthContextType shape.
// This is what consumers of the context will get if they are used outside an AuthProvider.
const AuthContext = createContext<AuthContextType | undefined>(undefined);

// 3. Create a custom hook to easily access our context.
// This is a best practice. It ensures we get our context correctly and
// throws a helpful error if we try to use it outside the Provider.
export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

// 4. Define the Props type for our Provider component
interface AuthProviderProps {
  children: ReactNode; // ReactNode is a type for anything React can render (components, strings, etc.)
}

// 5. Create the Provider Component.
// This component will wrap our entire app and manage the authentication state.
export function AuthProvider({ children }: AuthProviderProps): JSX.Element {
  // State to hold the current user object. Starts as `null` (no user logged in).
  const [user, setUser] = useState<User | null>(null);
  // State to track if a login request is in progress. For showing loading spinners.
  const [isLoading, setIsLoading] = useState(false);

  // The login function that will be called from our Login Form
  const login = async (email: string, password: string): Promise<void> => {
    setIsLoading(true); // Start loading
    try {
      // Call our mock API function
      const response = await mockLoginApi(email, password);
      // If successful, update the user state with the response
      setUser(response.user);
      // In a real app, you would also store the token (e.g., in localStorage)
      console.log("Login successful! Token:", response.token);
    } catch (error) {
      // If failed, reset the user to null and throw the error back to the form
      setUser(null);
      console.error("Login failed:", error);
      throw error; // This allows the Login Form to catch it and show an error message.
    } finally {
      setIsLoading(false); // Stop loading regardless of success or failure
    }
  };

  // Simple logout function
  const logout = (): void => {
    setUser(null);
    // In a real app, you would also clear the stored token here
    console.log("User logged out.");
  };

  // 6. The value that will be supplied to any component consuming this context.
  // It's an object containing our state and functions.
  const value: AuthContextType = {
    user,
    login,
    logout,
    isLoading,
  };

  // 7. The Provider component returns the Context.Provider component.
  // We pass the `value` (our state and functions) to the Provider.
  // This Provider will wrap the entire app, making the `value` available everywhere.
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
