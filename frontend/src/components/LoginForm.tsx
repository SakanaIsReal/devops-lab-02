// Import necessary modules
import React, { useState } from "react";
import { useAuth } from "../contexts/AuthContext";
import { Input } from "./Input";
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

  // 3. Handle input changes
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prevState) => ({
      ...prevState,
      [name]: value,
    }));
  };

  // 4. Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return; // Prevent multiple submissions

    // Basic client-side validation
    if (!formData.email.trim() || !formData.password.trim()) {
      alert("Please fill in all fields");
      return;
    }

    try {
      await login(formData.email, formData.password);

      // Navigation after successful login
      setTimeout(() => {
        navigate("/dashboard", { replace: true });
      }, 50);

    } catch (err: any) {
      console.error("Login error details:", err);

      // Enhanced error handling
      if (err?.response?.status === 401) {
        alert("Invalid email or password");
      } else if (err?.message?.includes("401")) {
        alert("Invalid email or password");
      } else if (err?.response?.status >= 500) {
        alert("Server error. Please try again later.");
      } else if (err.message) {
        alert(err.message);
      } else {
        alert("Login failed. Please try again.");
      }
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

      {/* Use our reusable Input component */}
      <Input
        label="Email Address"
        type="email"
        name="email"
        value={formData.email}
        onChange={handleInputChange}
        placeholder="user@example.com"
        disabled={isLoading}
        required
      />

      <Input
        label="Password"
        type="password"
        name="password"
        value={formData.password}
        onChange={handleInputChange}
        placeholder="Your password"
        disabled={isLoading}
        required
      />

      {/* Submit Button */}
      <button
        type="submit"
        disabled={isLoading}
        className={`mt-2 py-2 px-4 rounded-lg font-medium text-white transition-colors ${
          isLoading
            ? "bg-gray-400 cursor-not-allowed"
            : "bg-blue-500 hover:bg-blue-600 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        }`}
      >
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
        <span className="font-mono bg-black text-white">gg</span>
      </p>
    </form>
  );
};