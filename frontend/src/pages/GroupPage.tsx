import React from 'react';
import Navbar from '../components/Navbar';
import GroupList from '../components/GroupList';
import { BottomNav, NavTab } from '../components/BottomNav';

export const GroupPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-100 flex flex-col p"> 
      <Navbar /> 
      
        <div className='pb-16'>
          <GroupList />
        </div>
      <BottomNav activeTab={'groups'}/>
    </div>
  );
};