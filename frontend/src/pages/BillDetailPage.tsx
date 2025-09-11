import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { mockGetBillDetailApi } from '../utils/mockApi';
import type { BillDetail } from '../types';

const getStatusStyle = (status: 'done' | 'pay' | 'check') => {
    switch (status) {
        case 'done':
            return { backgroundColor: '#52bf52' };
        case 'pay':
            return { backgroundColor: '#0d78f2' };
        case 'check':
            return { backgroundColor: '#efac4e' };
    }
}

export const BillDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { billId } = useParams<{ billId: string }>();
  const [bill, setBill] = useState<BillDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (billId) {
      mockGetBillDetailApi(billId)
        .then((data) => {
          setBill(data);
          setLoading(false);
        })
        .catch((err) => {
          setError(err.message);
          setLoading(false);
        });
    }
  }, [billId]);

  const handleTabChange = (tab: NavTab) => {
    switch (tab) {
      case 'home':
        navigate('/dashboard');
        break;
      case 'groups':
        navigate('/creategroup');
        break;
      case 'split':
        navigate('/dashboard');
        break;
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <Navbar />
      <div className="p-4 flex-grow pb-16">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div className="flex items-center justify-between mt-4 mb-6">
          <h1 className="text-2xl font-bold text-[#2c4359]">Bill Detail</h1>
        </div>

        {loading && <p>Loading...</p>}
        {error && <p className="text-red-500">Error: {error}</p>}

        {bill && (
          <>
            <div className="flex items-center">
              <p className="text-lg font-semibold" style={{color: '#0c0c0c'}}>ร้าน: {bill.storeName}</p>
              <p className="text-lg font-semibold ml-8" style={{color: '#0c0c0c'}}>Payer: {bill.payer}</p>
            </div>
            <p className="text-lg font-semibold mb-4" style={{color: '#0c0c0c'}}>Date: {bill.date}</p>

            {bill.members.map((member, idx) => (
              <div
                key={idx}
                className="bg-white p-3 rounded-lg shadow-lg flex items-center justify-between mb-4"
              >
                <div className="flex items-center">
                  <img
                    src={member.avatar}
                    alt="avatar"
                    className="w-12 h-12 rounded-full mr-3"
                  />
                  <div>
                    <p className="font-semibold">{member.name}</p>
                    <p className="text-sm" style={{color: '#628fa6'}}>
                      Pay : {member.amount} Bath
                    </p>
                  </div>
                </div>
                <div className="flex justify-center">
                  <button
                    className="w-24 text-center px-4 py-2 rounded-lg text-white font-bold"
                    style={getStatusStyle(member.status)}
                  >
                    {member.status === 'done'
                      ? 'Done'
                      : member.status === 'pay'
                      ? 'Pay'
                      : 'Check'}
                  </button>
                </div>
              </div>
            ))}
          </>
        )}
      </div>
      <BottomNav activeTab={undefined} onTabChange={handleTabChange} />
    </div>
  );
};
