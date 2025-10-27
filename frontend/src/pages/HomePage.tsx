import React from 'react';
import Navbar from '../components/Navbar';
import BalanceSummary from '../components/BalanceSummary';
import { BottomNav, NavTab } from '../components/BottomNav';
import BalanceList from '../components/BalanceList';

export const HomePage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-100 flex flex-col p"> 
      <div className="navbar">
        <Navbar />
      </div>
      
        <div className='pb-16'>
          <div className="balance-summary">
            <BalanceSummary />
          </div>
          <div className="balance-list">
            <BalanceList />
          </div>
        </div>
      <div className="bottom-nav">
        <BottomNav activeTab={'home'}/>
      </div>
    </div>
  );
};