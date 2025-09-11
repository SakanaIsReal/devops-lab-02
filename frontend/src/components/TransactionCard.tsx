import React from 'react';
import { useNavigate } from 'react-router-dom';

interface TransactionCardProps {
  transaction: {
    id: string;
    name: string;
    payer: string;
    date: string;
    status: 'pending' | 'completed';
  };
}

const statusColors = {
  pending: 'bg-yellow-200 text-yellow-800',
  completed: 'bg-green-200 text-green-800',
};

const TransactionCard: React.FC<TransactionCardProps> = ({ transaction }) => {
  const navigate = useNavigate();

  const handleNavigate = () => {
    navigate(`/bill/${transaction.id}`);
  };

  return (
    <div className="bg-white rounded-lg shadow-sm p-4 flex items-center justify-between">
      <div>
        <h3 className="text-lg font-semibold">{transaction.name}</h3>
        <p className="text-gray-600">Paid by {transaction.payer}</p>
        <p className="text-gray-500 text-sm">{transaction.date}</p>
      </div>
      <div className="flex items-center">
        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[transaction.status]}`}>
          {transaction.status}
        </span>
        <button
          onClick={handleNavigate}
          className="ml-4 px-4 py-2 bg-gray-900 text-white rounded-md text-sm font-medium"
        >
          Detail
        </button>
      </div>
    </div>
  );
};

export default TransactionCard;
