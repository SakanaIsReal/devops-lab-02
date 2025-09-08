import React, { useState } from 'react';
// Import the new Navbar component
import Navbar from '../components/Navbar';
import BalanceSummary from '../components/BalanceSummary';
import GroupList from '../components/GroupList';
import { BottomNav, NavTab } from '../components/BottomNav';

export const DashboardPage: React.FC = () => {

  const [activeTab, setActiveTab] = useState<NavTab>('home');

  const renderTabContent = () => {
    switch (activeTab) {
      case 'home':
        return (
          <div>
            <BalanceSummary />
          </div>
          
        )
      case 'groups':
        return (
          <div>
            <GroupList />
          </div>
        );
      case 'split':
        return (
          <div>
            <h2 className="text-2xl font-semibold mb-4">Split Expense</h2>
            <p className="text-gray-600">Create a new expense to split.</p>
          </div>
        );
      default:
        return <div>Page not found.</div>
    }
  }

  // We don't need useAuth here anymore because the Navbar handles it internally!

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col p"> {/* Changed to flex-col */}
      {/* Use our new reusable Navbar component */}
      <Navbar /> 
      

      {/* Main content area - now grows to fill remaining space */}

        <div className='pb-16'>
          {renderTabContent()}
        </div>
      <BottomNav activeTab={activeTab} onTabChange={setActiveTab}/>
    </div>
  );
};