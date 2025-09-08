// Import React and the necessary type for component props
import React from 'react';

// 1. Define the TypeScript interface for the component's props
// This is like a contract that says "This component accepts these properties"
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  // We are EXTENDING the default input element props.
  // This means our component accepts all standard input attributes (type, placeholder, etc.)
  // plus the custom ones we define below.

  label: string;
  error?: string; // The '?' means this prop is optional
}

// 2. Create the component using React.FC (Functional Component) and generic type <InputProps>
// The `React.forwardRef` is needed to properly forward the ref to the underlying input element.
export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, id, ...props }, ref) => {
    // The component receives its `props` and a `ref`
    // `...props` collects all other standard input props (type, placeholder, onChange, etc.)

    // Generate an ID for accessibility if one wasn't provided
    const inputId = id || label.toLowerCase().replace(/\s+/g, '-');

    return (
      <div className="flex flex-col gap-1 w-full">
        {/* Label for the input */}
        <label htmlFor={inputId} className="text-sm font-medium text-gray-700">
          {label}
        </label>

        {/* The input element itself */}
        <input
          id={inputId}
          ref={ref} // Forward the ref here
          {...props} // Spread all the standard input props onto the element
          className={`px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all ${
            error ? 'border-red-500 ring-2 ring-red-200' : 'border-gray-300'
          }`}
        />

        {/* Conditionally render an error message */}
        {error && <p className="text-red-500 text-xs">{error}</p>}
      </div>
    );
  }
);

// This is helpful for debugging in React DevTools
Input.displayName = 'Input';