import React from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';

export const BillDetailPage: React.FC = () => {
  const navigate = useNavigate();

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
      default:
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
          <button className="bg-[#111827] text-white font-semibold text-base px-6 py-2 rounded-lg">
            Detail
          </button>
        </div>
        {/* Page content will go here */}
      </div>
      <BottomNav activeTab={undefined} onTabChange={handleTabChange} />
    </div>
  );
};