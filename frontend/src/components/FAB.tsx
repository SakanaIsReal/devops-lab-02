import React from 'react';

interface FABProps {
  onClick: () => void;
}

const FAB: React.FC<FABProps> = ({ onClick }) => {
  return (
    <button
      onClick={onClick}
      data-cy="fab-add-group"
      aria-label="Add New Group"
      className="fixed bottom-20 right-4 bg-gray-900 text-white rounded-full w-14 h-14 flex items-center justify-center shadow-lg"
    >
      <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
      </svg>
    </button>
  );
};

export default FAB;
