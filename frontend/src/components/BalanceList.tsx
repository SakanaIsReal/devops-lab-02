import { useState, useEffect } from 'react';
import { Balance } from '../types';
import { getBalances } from '../utils/api';
import { useNavigate } from 'react-router-dom';

export default function BalanceList() {
  const [balances, setBalances] = useState<Balance[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchBalances = async () => {
      try {
        const data = await getBalances();
        setBalances(data);
      } catch (error) {
        console.error('Failed to fetch balances', error);
      }
    };

    fetchBalances();
  }, []);

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-lg font-bold">All Balances</h2>
      <div className="space-y-3">
        {balances.map((balance, index) => (
          <div key={index} className="flex items-center justify-between rounded-xl bg-white shadow p-4">
            <div className="flex items-center space-x-3">
              <img src={balance.counterpartyAvatarUrl || `https://avatars.dicebear.com/api/initials/${balance.counterpartyUserName}.svg`} alt={balance.counterpartyUserName} className="h-10 w-10 rounded-full" />
              <div>
                <p className="font-medium text-gray-900">
                  {balance.direction === 'OWES_YOU' ? `${balance.counterpartyUserName} owes you` : `You owe ${balance.counterpartyUserName}`}
                </p>
                <p className="text-sm text-gray-500">{balance.expenseTitle} in {balance.groupName}</p>
              </div>
            </div>
            <div className="text-right">
              <p className={`text-lg font-bold ${balance.direction === 'OWES_YOU' ? 'text-green-500' : 'text-red-500'}`}>
                ${balance.remaining.toFixed(2)}
              </p>
              {balance.direction === 'YOU_OWE' && (
                <button
                  onClick={() => navigate(`/pay/${balance.expenseId}/${balance.counterpartyUserId}`)}
                  className="mt-2 px-6 py-2 rounded-full text-sm font-medium bg-gray-900 text-white"
                >
                  Pay
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
