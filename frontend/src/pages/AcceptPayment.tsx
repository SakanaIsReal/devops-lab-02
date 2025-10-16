import React from 'react';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';

export const AcceptPaymentPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-100 flex flex-col p"> 
      <Navbar /> 
        <div className='pb-16'>
        </div>
      <BottomNav activeTab={'groups'}/>
    </div>
  );
};