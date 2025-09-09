import React, { useState, useEffect } from "react";
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { useAuth } from '../contexts/AuthContext';
import { ArrowUpOnSquareIcon } from '@heroicons/react/24/outline';
import BalanceSummary from '../components/BalanceSummary';
import GroupList from '../components/GroupList';
export default function SplitMoneyPage() {
    const [activeTab, setActiveTab] = useState<NavTab>('split');

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
    return (
        <div className="min-h-screen bg-gray-100 flex flex-col">
            <Navbar />
            <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />

        </div>
    )
}