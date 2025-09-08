import React, { useState, useEffect } from 'react';
import TransactionCard from './TransactionCard';
import { mockGetTransactionsApi } from '../utils/mockApi';

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
        const fetchedTransactions = await mockGetTransactionsApi(groupId);
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
    return transaction.status === filter;
  });

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  return (
    <div className="space-y-4 mt-6">
      <h2 className="text-xl font-bold">Transactions</h2>
      <div className="flex space-x-2">
        <button onClick={() => setFilter('all')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'all' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>All</button>
        <button onClick={() => setFilter('pending')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'pending' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>In Progress</button>
        <button onClick={() => setFilter('completed')} className={`px-3 py-1 rounded-full text-sm font-medium ${filter === 'completed' ? 'bg-gray-900 text-white' : 'bg-gray-200 text-gray-600'}`}>Completed</button>
      </div>
      {filteredTransactions.map((transaction) => (
        <TransactionCard key={transaction.id} transaction={transaction} />
      ))}
    </div>
  );
};

export default TransactionList;
