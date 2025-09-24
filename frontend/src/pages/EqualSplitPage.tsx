import React, { useState } from "react";
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
// import BalanceSummary from '../components/BalanceSummary';
// import GroupList from '../components/GroupList';

export default function EqualSplitPage() {
    const [expenseName, setExpenseName] = useState("");
    const [amount, setAmount] = useState("");
    const [excludeOpen, setExcludeOpen] = useState(false);
    const [excluded, setExcluded] = useState<number[]>([]);
    const [activeTab, setActiveTab] = useState<NavTab>('split');
    const navigate = useNavigate();

    const participants = [
        { id: 1, name: "Krittanon" },
        { id: 2, name: "Jutichot" },
        { id: 3, name: "Mark" },
        { id: 4, name: "Nice" },
    ];

    const toggleExclude = (id: number) => {
        setExcluded((prev) =>
            prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
        );
    };

    const handleSubmit = () => {
        console.log({ expenseName, amount, excluded });
        alert("Expense submitted!");
    };

    const handleBack = () => {
        navigate(-1);
    };

    return (
        <div className="min-h-screen bg-white flex flex-col">
            {/* Navbar fixed on top */}
            <Navbar />

            {/* Scrollable Content - Fixed height with proper overflow */}
            <div className="flex-1 overflow-y-auto pt-4 pb-20 px-4 sm:px-6">
                <CircleBackButton
                    onClick={handleBack}
                    className="border-b border-gray-200"
                    iconClassName="text-blue-600"
                />

                <div className="flex flex-col justify-center items-start mb-4 mt-4">
                    <h1 className="text-xl sm:text-2xl font-bold text-gray-800 mb-1">
                        Expense Management
                    </h1>
                    <p className="text-base sm:text-lg font-bold text-gray-700">
                        Method : Equal Split
                    </p>
                </div>

                {/* Expense Name */}
                <label className="block text-gray-700 font-medium mb-2">
                    Expense Name
                </label>
                <input
                    type="text"
                    value={expenseName}
                    onChange={(e) => setExpenseName(e.target.value)}
                    placeholder="Enter your expense name (e.g. ส้มตำเจ๊แต๋ว)"
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 
                     focus:outline-none focus:ring-2 focus:ring-blue-500"
                />

                {/* Total Amount */}
                <label className="block text-gray-700 font-medium mb-2">
                    Total Amount
                </label>
                <input
                    type="number"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="Enter expense total"
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 
                     focus:outline-none focus:ring-2 focus:ring-blue-500"
                />

                {/* Exclude Participants */}
                <div className="mb-6">
                    <button
                        type="button"
                        onClick={() => setExcludeOpen(!excludeOpen)}
                        className="flex gap-3 items-center p-3 rounded-lg 
                        transition"
                    >
                        <span className="text-gray-700 font-medium">
                            Exclude Participants
                        </span>
                        <span className="text-gray-500">{excludeOpen ? "▲" : "▼"}</span>
                    </button>

                    {excludeOpen && (
                        <div className="mt-2 border rounded-xl p-3 bg-white shadow-sm">
                            {participants.map((p) => (
                                <label
                                    key={p.id}
                                    className="flex items-center gap-2 mb-2 cursor-pointer"
                                >
                                    <input
                                        type="checkbox"
                                        checked={excluded.includes(p.id)}
                                        onChange={() => toggleExclude(p.id)}
                                        className="w-4 h-4 text-blue-500 rounded focus:ring-0"
                                    />
                                    <span className="text-gray-700">{p.name}</span>
                                </label>
                            ))}
                        </div>
                    )}
                </div>

                {/* Finish Button */}
                <button
                    onClick={handleSubmit}
                    className="w-full bg-blue-500 text-white font-bold py-3 rounded-xl 
                     hover:bg-blue-600 transition mb-8"
                >
                    FINISH
                </button>
            </div>

            {/* Bottom Nav fixed at bottom */}
            <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
        </div>
    );
}