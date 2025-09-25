import React from "react";
import { useNavigate, Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';

export default function SplitMoneyPage() {
    const navigate = useNavigate();

      const handleBack = () => {
        navigate(-1);
    };
    return (
        <div className="min-h-screen bg-gray-100 flex flex-col">
            <Navbar />
            <div className="p-4">
                <CircleBackButton
                    onClick={handleBack}
                    className="border-b border-gray-200"
                    iconClassName="text-blue-600"
                />
                <div className="flex flex-col justify-center items-start gap-x-2">
                    <h1 className="text-2xl mb-1 font-bold text-gray-800">Split Method</h1>
                    <p className="text-gray-700">Select Split Method</p>
                </div>
                <div className="flex flex-col mt-4">
                    <Link to="/equalsplit">
                        <div className="flex flex-row p-3 bg-gray-200 rounded-xl mb-3 items-center hover:bg-gray-300 transition">
                            <div className="w-10 h-10 flex items-center justify-center bg-gray-300 rounded-2xl flex-shrink-0">
                                ➗
                            </div>
                            <div className="ml-3 flex flex-col">
                                <p className="font-bold">Equal Split</p>
                                <p className="text-gray-500">
                                    Divide the total amount equally among all participants.
                                </p>
                            </div>
                        </div>
                    </Link>
                    <Link to="/manualsplit">
                        <div className="flex flex-row p-3 bg-gray-200 rounded-xl mb-3 items-center">
                            <div className="w-10 h-10 flex items-center justify-center bg-gray-300 rounded-2xl flex-shrink-0">
                                💰
                            </div>
                            <div className="ml-3 flex flex-col">
                                <p className="font-bold">Manual Split</p>
                                <p className="text-gray-500">
                                    Manually assign amounts or percentages to each participant.
                                </p>
                            </div>
                        </div>
                    </Link>
                </div>

            </div>
            <BottomNav activeTab={undefined} />
        </div>
    )
}