import { ArrowLeftIcon } from '@heroicons/react/24/outline';
import React from 'react';

interface CircleBackButtonProps {
  onClick: () => void;
  className?: string;
  iconClassName?: string;
}

const CircleBackButton: React.FC<CircleBackButtonProps> = ({
  onClick,
  className = '',
  iconClassName = '',
}) => {
  return (
    <button
      onClick={onClick}
      data-cy="btn-back"
      className={`
        w-10 m-4
        flex items-center justify-start p-3 rounded-full mb-4
        bg-white hover:bg-gray-50 active:bg-gray-100
        transition-colors duration-200
        focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
        ${className}
      `}
      aria-label="Go back"
    >
      
        <ArrowLeftIcon className={`w-6 h-6 text-gray-600 ${iconClassName}`} />
      
    </button>
  );
};

export default CircleBackButton;