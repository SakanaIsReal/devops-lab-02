// Update TransactionCard.tsx
import React from 'react';
import { useNavigate } from 'react-router-dom';

interface TransactionCardProps {
  transaction: {
    id: number;
    name: string;
    payer: string;
    date: string;
    amount: number;
    status: 'settled' | 'open' | 'canceled' | 'pending' | 'completed'; // Extended status
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
  };
}

const statusColors = {
  settled: 'bg-green-200 text-green-800',
  open: 'bg-yellow-200 text-yellow-800',
  canceled: 'bg-red-200 text-red-800',
  pending: 'bg-yellow-200 text-yellow-800',
  completed: 'bg-green-200 text-green-800',
};

const TransactionCard: React.FC<TransactionCardProps> = ({ transaction }) => {
  const navigate = useNavigate();

  const handleNavigate = () => {
    navigate(`/bill/${transaction.id}`);
  };

  // Map backend status to frontend status for display
  const getDisplayStatus = (status: string) => {
    switch (status) {
      case 'SETTLED': return 'completed';
      case 'OPEN': return 'pending';
      case 'CANCELED': return 'canceled';
      default: return status as 'pending' | 'completed';
    }
  };

  const displayStatus = getDisplayStatus(transaction.status);

  return (
    <div className="bg-white rounded-lg shadow-sm p-4 flex items-center justify-between">
      <div className="flex-1">
        <h3 className="text-lg font-semibold">{transaction.name}</h3>
        <p className="text-gray-600">Paid by {transaction.payer}</p>
        <p className="text-gray-500 text-sm">Amount: ${transaction.amount}</p>
        <p className="text-gray-500 text-sm">{transaction.date}</p>
      </div>
      <div className="flex items-center space-x-4">
        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[displayStatus]}`}>
          {displayStatus}
        </span>
        <button
          onClick={handleNavigate}
          className="px-4 py-2 bg-gray-900 text-white rounded-md text-sm font-medium"
        >
          Detail
        </button>
      </div>
    </div>
  );
};

export default TransactionCard;