// src/components/SignUpForm.tsx
import React, { useState } from "react";
import { Input } from "./Input";
import logo from "../assets/logo.png";
import { useNavigate } from "react-router-dom";
import { mockSignUpApi } from "../utils/mockApi";
import { useAuth } from "../contexts/AuthContext";

export const SignUpForm: React.FC = () => {
  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [error, setError] = useState<string | null>(null);
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prevState) => ({
      ...prevState,
      [name]: value,
    }));
    if (error) setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (
      !formData.firstName ||
      !formData.lastName ||
      !formData.email ||
      !formData.password ||
      !formData.confirmPassword
    ) {
      setError("Please fill in all fields");
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    try {
      await mockSignUpApi(formData.firstName, formData.lastName, formData.email, formData.password);
      // Log in with a user that is known to exist in the mock API
      await login("user@example.com", "gg");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "An unknown error occurred during sign up."
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
      <h2 className="text-2xl font-bold text-left text-gray-800">Sign Up</h2>

      {error && (
        <p className="text-red-500 text-sm bg-red-50 p-2 rounded">{error}</p>
      )}

      <Input
        label="First Name"
        type="text"
        name="firstName"
        value={formData.firstName}
        onChange={handleInputChange}
        placeholder="John"
        disabled={isLoading}
        required
      />

      <Input
        label="Last Name"
        type="text"
        name="lastName"
        value={formData.lastName}
        onChange={handleInputChange}
        placeholder="Doe"
        disabled={isLoading}
        required
      />

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

      <Input
        label="Confirm Password"
        type="password"
        name="confirmPassword"
        value={formData.confirmPassword}
        onChange={handleInputChange}
        placeholder="Confirm your password"
        disabled={isLoading}
        required
      />

      <button
        type="submit"
        disabled={isLoading}
        className={`mt-2 py-2 px-4 rounded-lg font-medium text-white transition-colors ${
          isLoading
            ? "bg-gray-400 cursor-not-allowed"
            : "bg-blue-500 hover:bg-blue-600 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        }`}
      >
        {isLoading ? "Signing up..." : "Sign Up"}
      </button>

      <button
        type="button"
        onClick={() => navigate("/login")}
        disabled={isLoading}
        className={`py-2 px-4 rounded-lg font-medium border transition-colors ${
          isLoading
            ? "bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed"
            : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50 focus:ring-2 focus:ring-gray-400 focus:ring-offset-2"
        }`}
      >
        Already have an account?
      </button>
    </form>
  );
};
