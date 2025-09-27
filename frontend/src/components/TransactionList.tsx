// Update TransactionList.tsx
import React, { useState, useEffect } from 'react';
import TransactionCard from './TransactionCard';
import { getGroupTransactions } from '../utils/api';

interface TransactionListProps {
  groupId: string;
}

type Status = 'all' | 'pending' | 'completed';

const TransactionList: React.FC<TransactionListProps> = ({ groupId }) => {
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<Status>('all');

  useEffect(() => {
    const fetchTransactions = async () => {
      try {
        const fetchedTransactions = await getGroupTransactions(groupId);
        setTransactions(fetchedTransactions);
      } catch (error) {
        console.error("Error fetching transactions:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchTransactions();
  }, [groupId]);

  const filteredTransactions = transactions.filter(transaction => {
    if (filter === 'all') return true;
    if (filter === 'pending') return transaction.status === 'OPEN';
    if (filter === 'completed') return transaction.status === 'SETTLED';
    return true;
  });

  if (loading) {
    return (
      <div className="flex justify-center items-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  return (
    <div className="space-y-4 mt-6 mb-20">
      <h2 className="text-xl font-bold">Transactions</h2>
      <div className="flex space-x-2">
        <button onClick={() => setFilter('all')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'all' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>All</button>
        <button onClick={() => setFilter('pending')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'pending' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>In Progress</button>
        <button onClick={() => setFilter('completed')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'completed' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>Completed</button>
      </div>
      {filteredTransactions.length === 0 ? (
        <p className="text-gray-500 text-center py-4">No transactions found</p>
      ) : (
        filteredTransactions.map((transaction) => (
          <TransactionCard key={transaction.id} transaction={transaction} />
        ))
      )}
    </div>
  );
};

export default TransactionList;