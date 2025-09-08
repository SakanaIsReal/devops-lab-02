// src/pages/SignUpPage.tsx
import React from "react";
import { SignUpForm } from "../components/SignUpForm";

const SignUpPage: React.FC = () => {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100">
      <SignUpForm />
    </div>
  );
};

export default SignUpPage;
