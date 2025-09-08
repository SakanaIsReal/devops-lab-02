// Import necessary modules
import React, { useState } from "react";
import { useAuth } from "../contexts/AuthContext"; // Our custom hook!
import { Input } from "./Input"; // Our new reusable component
import { useNavigate } from "react-router-dom";

import logo from "../assets/logo.png";

// The main LoginForm component
export const LoginForm: React.FC = () => {
  // 1. Access our global auth state and functions
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();

  // 2. Create local state for the form fields and errors
  const [formData, setFormData] = useState({
    email: "",
    password: "",
  });
  const [error, setError] = useState<string | null>(null);

  // 3. Handle input changes
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prevState) => ({
      ...prevState, // Spread the previous state
      [name]: value, // Update the specific field that changed
    }));
    // Clear errors when the user starts typing again
    if (error) setError(null);
  };

  // 4. Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); // Prevent the browser from refreshing the page
    setError(null); // Reset any previous errors

    // Basic client-side validation
    if (!formData.email || !formData.password) {
      setError("Please fill in all fields");
      return;
    }

    try {
      // 5. Call the login function from our context
      // This is an async operation that we `await`
      await login(formData.email, formData.password);
      // If login is successful, our AuthContext will update the `user` state.
      // We don't need to do anything else here. A redirect can be handled by a router (next step!).
      console.log("LoginForm: Login successful!");
    } catch (err) {
      // 6. Handle errors from the mock API / context
      setError(
        err instanceof Error
          ? err.message
          : "An unknown error occurred during login."
      );
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-4 p-6 bg-white rounded-xl shadow-md w-full max-w-md"
    >
        <div className="flex justify-center">
        <img src={logo} alt="Company Logo" className="w-48 h-48" />
    </div>
      <h2 className="text-2xl font-bold text-left text-gray-800">Log In</h2>

      {/* Conditionally show general error message */}
      {error && (
        <p className="text-red-500 text-sm bg-red-50 p-2 rounded">{error}</p>
      )}

      {/* Use our reusable Input component */}
      <Input
        label="Email Address"
        type="email"
        name="email"
        value={formData.email}
        onChange={handleInputChange}
        placeholder="user@example.com"
        disabled={isLoading} // Disable while request is in flight
        required
      />

      <Input
        label="Password"
        type="password"
        name="password"
        value={formData.password}
        onChange={handleInputChange}
        placeholder="Your password"
        disabled={isLoading} // Disable while request is in flight
        required
      />

      {/* Submit Button */}
      <button
        type="submit"
        disabled={isLoading} // Disable while request is in flight
        className={`mt-2 py-2 px-4 rounded-lg font-medium text-white transition-colors ${
          isLoading
            ? "bg-gray-400 cursor-not-allowed" // Styles when loading
            : "bg-blue-500 hover:bg-blue-600 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2" // Styles when active
        }`}
      >
        {/* Change button text based on loading state */}
        {isLoading ? "Logging in..." : "Log In"}
      </button>

      <button
        type="button"
        onClick={() => navigate("/signup")}
        disabled={isLoading}
        className={`py-2 px-4 rounded-lg font-medium border transition-colors ${
          isLoading
            ? "bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed"
            : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50 focus:ring-2 focus:ring-gray-400 focus:ring-offset-2"
        }`}
      >
        Sign Up
      </button>

      {/* Hint for using the mock login */}
      <p className="text-xs text-gray-500 text-center mt-4">
        Hint: Use email:{" "}
        <span className="font-mono bg-black text-white">user@example.com</span>{" "}
        and password:{" "}
        <span className="font-mono bg-black text-white"> gg </span>
      </p>
    </form>
  );
};
