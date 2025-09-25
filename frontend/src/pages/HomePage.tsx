import React from 'react';
import Navbar from '../components/Navbar';
import BalanceSummary from '../components/BalanceSummary';
import { BottomNav, NavTab } from '../components/BottomNav';

export const HomePage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-100 flex flex-col p"> 
      <Navbar /> 
      
        <div className='pb-16'>
          <BalanceSummary />
        </div>
      <BottomNav activeTab={'home'}/>
    </div>
  );
};