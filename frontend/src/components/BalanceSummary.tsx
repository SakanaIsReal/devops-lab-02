import { ExclamationTriangleIcon, CheckCircleIcon } from "@heroicons/react/24/solid";
import { useState, useEffect } from "react";
import { Transaction } from "../types";
import { getTransactions } from "../utils/api";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

export default function BalanceSummary() {
  const [activeFilter, setActiveFilter] = useState("All");
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const navigate = useNavigate();
  const { user } = useAuth();

  useEffect(() => {
    const fetchTransactions = async () => {
      try {
        const data = await getTransactions();
        setTransactions(data);
      } catch (error) {
        console.error("Failed to fetch transactions", error);
      }
    };

    fetchTransactions();
  }, []);

  const sortedTransactions = [...transactions].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const filteredTransactions = sortedTransactions.filter(transaction => {
    if (activeFilter === "All") {
      return true;
    }
    const now = new Date();
    const transactionDate = new Date(transaction.createdAt);
    if (activeFilter === "This Week") {
      const oneWeekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      return transactionDate >= oneWeekAgo;
    }
    if (activeFilter === "This Month") {
      const oneMonthAgo = new Date(now.getFullYear(), now.getMonth() - 1, now.getDate());
      return transactionDate >= oneMonthAgo;
    }
    return true;
  });

  return (
    <div className="p-4 space-y-4">
      {/* Top summary cards */}
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-white shadow p-4">
          <p className="text-gray-500 text-sm">You owe</p>
          <p className="text-2xl font-bold text-red-500">$43.50</p>
        </div>
        <div className="rounded-xl bg-white shadow p-4">
          <p className="text-gray-500 text-sm">You are owed</p>
          <p className="text-2xl font-bold text-green-500">$20.00</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex space-x-2">
        {["All", "This Week", "This Month"].map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveFilter(tab)}
            className={`px-4 py-2 rounded-full text-sm font-medium ${
              activeFilter === tab
                ? "bg-gray-200 text-gray-900"
                : "text-gray-500 hover:bg-gray-100"
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Transactions */}
      <div className="space-y-3">
        {filteredTransactions.map((t) => {
          const isOwed = String(t.payerUserId) === user?.id;
          return (
          <div
            key={t.id}
            className="flex items-center justify-between rounded-xl bg-white shadow p-4"
          >
            <div className="flex items-center space-x-3">
              {!isOwed ? (
                <ExclamationTriangleIcon className="h-8 w-8 mx-2 text-red-400" />
              ) : (
                <CheckCircleIcon className="h-8 w-8 mx-2 text-green-400" />
              )}
              <div>
                <p className="font-medium text-gray-900">
                  {!isOwed
                    ? `You owe ${t.name}`
                    : `${t.name} owes you`}
                </p>
                <p className="font-semibold text-gray-900">
                ${t.amount.toFixed(2)}
              </p>
                <p className="text-sm text-gray-500">{t.title}</p>
              </div>
            </div>
            <div className="text-right">
              
              <button
                onClick={() => navigate(`/pay/${t.id}`)}
                className={`mt-2 px-6 py-2 rounded-full text-sm font-medium ${
                  !isOwed
                    ? "bg-gray-900 text-white"
                    : "bg-gray-200 text-gray-600"
                }`}
              >
                {!isOwed ? "Pay" : "Remind"}
              </button>
            </div>
          </div>
        )})}
      </div>
    </div>
  );
}
