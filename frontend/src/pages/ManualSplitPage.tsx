import React, { useState } from "react";
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav, NavTab } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
// import BalanceSummary from '../components/BalanceSummary';
// import GroupList from '../components/GroupList';

type ExpenseItem  = {
    name: string;
    amount: string;
    personId: number | null;
    sharedWith: number[];
    splitMethod: SplitMethod;
    percentages: { [personId: number]: number };
};

type SplitMethod = 'equal' | 'percentage';

type SplitResult = {
    participant: string;
    amount: number;
}[];

export default function ManualSplitPage() {
    const [expenseName, setExpenseName] = useState("");
    const [amount, setAmount] = useState("");
    const [items, setItems] = useState<ExpenseItem []>([
        {
            name: "",
            amount: "",
            personId: null,
            sharedWith: [],
            splitMethod: 'equal',
            percentages: {}
        },
    ]);
    const [percentages, setPercentages] = useState<{ [key: number]: string }>({});
    const [splitResult, setSplitResult] = useState<SplitResult | null>(null);
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState<NavTab>('split');

    const participants = [
        { id: 1, name: "Krittanon" },
        { id: 2, name: "Jutichot" },
        { id: 3, name: "Mark" },
        { id: 4, name: "Nice" },
    ];

    // ฟังก์ชันตรวจสอบว่ามีรายการที่สมบูรณ์อย่างน้อย 1 รายการ
    const hasValidItems = () => {
        return items.some(item => {
            const hasName = item.name.trim() !== "";
            const hasAmount = parseFloat(item.amount || '0') > 0;
            const hasPayer = item.personId !== null;
            const hasParticipants = item.sharedWith.length > 0;
            
            // สำหรับการแบ่งแบบ percentage ต้องมีเปอร์เซ็นต์รวม 100%
            if (item.splitMethod === 'percentage') {
                const totalPercentage = Object.values(item.percentages).reduce((sum, p) => sum + p, 0);
                return hasName && hasAmount && hasPayer && hasParticipants && totalPercentage === 100;
            }
            
            return hasName && hasAmount && hasPayer && hasParticipants;
        });
    };

    const handlePercentageChange = (participantId: number, value: string) => {
        setPercentages(prev => ({ ...prev, [participantId]: value }));
    };

    const handleSubmit = () => {
        // ตรวจสอบว่ามีรายการที่สมบูรณ์หรือไม่
        if (!hasValidItems()) {
            alert("Please add at least one complete item with all required information.");
            return;
        }

        // Calculate split result based on each item's split method
        const participantTotals: { [name: string]: number } = {};

        // Initialize all participants with 0
        participants.forEach(p => {
            participantTotals[p.name] = 0;
        });

        items.forEach(item => {
            const itemAmount = parseFloat(item.amount || '0');
            
            // ข้ามรายการที่ไม่สมบูรณ์
            if (!item.name.trim() || itemAmount <= 0 || !item.personId || item.sharedWith.length === 0) {
                return;
            }

            if (item.splitMethod === 'equal' && item.sharedWith.length > 0) {
                const amountPerPerson = itemAmount / item.sharedWith.length;
                item.sharedWith.forEach(personId => {
                    const person = participants.find(p => p.id === personId);
                    if (person) {
                        participantTotals[person.name] += amountPerPerson;
                    }
                });
            }

            else if (item.splitMethod === 'percentage' && item.sharedWith.length > 0) {
                const totalPercentage = Object.values(item.percentages).reduce((sum, p) => sum + p, 0);
                if (totalPercentage !== 100) {
                    alert(`Percentages must total exactly 100% for item: ${item.name}`);
                    return;
                }

                item.sharedWith.forEach(personId => {
                    const person = participants.find(p => p.id === personId);
                    const percentage = item.percentages[personId] || 0;
                    if (person) {
                        participantTotals[person.name] += (itemAmount * percentage) / 100;
                    }
                });
            }
        });
        
        const result: SplitResult = Object.entries(participantTotals)
            .filter(([_, amount]) => amount > 0)
            .map(([name, amount]) => ({
                participant: name,
                amount: amount
            }));
        setSplitResult(result);
        
        // แสดง Alert สำเร็จ
        alert("Expense successfully recorded!");
        
        console.log({ expenseName, amount, items, result });
    };

    const handleBack = () => {
        navigate(-1);
    };

    const addItem = () => {
        setItems([...items, {
            name: "",
            amount: "",
            personId: null,
            sharedWith: [],
            splitMethod: 'equal',
            percentages: {}
        }]);
    };

    const updateItem = (index: number, field: keyof ExpenseItem , value: any) => {
        const newItems = [...items];
        newItems[index] = { ...newItems[index], [field]: value };
        setItems(newItems);
    };

    const deleteItem = (index: number) => {
        const newItems = items.filter((_, i) => i !== index);
        setItems(newItems);
    };

    const toggleShareWith = (itemIndex: number, personId: number) => {
        const newItems = [...items];
        const currentShareWith = newItems[itemIndex].sharedWith;
        const item = newItems[itemIndex];

        if (currentShareWith.includes(personId)) {
            // Remove person
            newItems[itemIndex].sharedWith = currentShareWith.filter(id => id !== personId);
            // Remove from percentages
            const newPercentages = { ...item.percentages };
            delete newPercentages[personId];
            newItems[itemIndex].percentages = newPercentages;
        } else {
            // Add person
            newItems[itemIndex].sharedWith = [...currentShareWith, personId];
            // Initialize percentage if using percentage method
            if (item.splitMethod === 'percentage') {
                const remainingPeople = currentShareWith.length + 1;
                const defaultPercentage = Math.floor(100 / remainingPeople);
                newItems[itemIndex].percentages = {
                    ...item.percentages,
                    [personId]: defaultPercentage
                };
            }
        }
        setItems(newItems);
    };
    const updatePercentage = (itemIndex: number, personId: number, percentage: number) => {
        const newItems = [...items];
        newItems[itemIndex].percentages = {
            ...newItems[itemIndex].percentages,
            [personId]: percentage
        };
        setItems(newItems);
    };
    const changeSplitMethod = (itemIndex: number, method: SplitMethod) => {
        const newItems = [...items];
        const item = newItems[itemIndex];

        newItems[itemIndex].splitMethod = method;

        if (method === 'percentage' && item.sharedWith.length > 0) {
            // Initialize equal percentages
            const equalPercentage = Math.floor(100 / item.sharedWith.length);
            const percentages: { [key: number]: number } = {};
            item.sharedWith.forEach(personId => {
                percentages[personId] = equalPercentage;
            });
            newItems[itemIndex].percentages = percentages;
        } else if (method !== 'percentage') {
            // Clear percentages for non-percentage methods
            newItems[itemIndex].percentages = {};
        }

        setItems(newItems);
    };
    const getTotalPercentage = (itemIndex: number) => {
        const item = items[itemIndex];
        return Object.values(item.percentages).reduce((sum, percentage) => sum + percentage, 0);
    };
    const getEqualShare = (itemIndex: number) => {
        const item = items[itemIndex];
        const itemAmount = parseFloat(item.amount) || 0;
        const shareCount = item.sharedWith.length;
        return shareCount > 0 ? (itemAmount / shareCount).toFixed(2) : '0.00';
    };

    const getPercentageShare = (itemIndex: number, personId: number) => {
        const item = items[itemIndex];
        const itemAmount = parseFloat(item.amount) || 0;
        const percentage = item.percentages[personId] || 0;
        return ((itemAmount * percentage) / 100).toFixed(2);
    };
    
    return (
        <div className="min-h-screen bg-white flex flex-col">
            <Navbar />

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
                        Method : Manual Split
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
                    placeholder="Enter your expense name (e.g. Tecnol)"
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 
                     focus:outline-none focus:ring-2 focus:ring-blue-500"
                />

                {/* Total Expense Display */}
                <label className="text-gray-700 font-medium">Total Expense</label>
                <div className="w-full p-3 mb-4 mt-2 border-none rounded-xl bg-gray-100 flex items-center gap-5">
                    
                    <span className="text-lg">
                        ฿{items.reduce((acc, item) => acc + parseFloat(item.amount || '0'), 0).toFixed(2)}
                    </span>
                </div>

                {/* Items List */}
                <div className="mb-6">
                    <h3 className="text-gray-700 font-medium mb-3">Items</h3>

                    {items.map((item, index) => (
                        <div key={index} className="mb-4 p-3 border rounded-xl bg-white shadow-sm">

                            {/* Split Method Selection for each item */}
                            <div className="mb-3">
                                <p className="text-sm font-medium text-gray-700 mb-2">Split Method</p>
                                <div className="flex gap-2 flex-wrap">
                                    <button
                                        onClick={() => changeSplitMethod(index, 'equal')}
                                        className={`px-3 py-1 rounded-lg text-sm font-medium transition ${item.splitMethod === 'equal'
                                            ? 'bg-blue-500 text-white'
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                            }`}
                                    >
                                        Equal
                                    </button>
                                    <button
                                        onClick={() => changeSplitMethod(index, 'percentage')}
                                        className={`px-3 py-1 rounded-lg text-sm font-medium transition ${item.splitMethod === 'percentage'
                                            ? 'bg-blue-500 text-white'
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                            }`}
                                    >
                                        Percentage
                                    </button>

                                </div>
                            </div>

                            {/* Amount and Item Name */}
                            <div className="mb-3 flex gap-3">
                                <input
                                    type="number"
                                    value={item.amount}
                                    onChange={(e) => updateItem(index, 'amount', e.target.value)}
                                    placeholder="0.00"
                                    className="w-full p-2 border-none rounded-lg bg-gray-100 
                                        focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <input
                                    type="text"
                                    value={item.name}
                                    onChange={(e) => updateItem(index, 'name', e.target.value)}
                                    placeholder="Enter item name"
                                    className="w-full p-2 border-none rounded-lg bg-gray-100 
                                    focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <button
                                    onClick={() => deleteItem(index)}
                                    className="px-3 py-1 text-red-500 hover:text-red-700"
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
                                        <path fillRule="evenodd" d="M16.5 4.478v.227a48.816 48.816 0 0 1 3.878.512.75.75 0 1 1-.256 1.478l-.209-.035-1.005 13.07a3 3 0 0 1-2.991 2.77H8.084a3 3 0 0 1-2.991-2.77L4.087 6.66l-.209.035a.75.75 0 0 1-.256-1.478A48.567 48.567 0 0 1 7.5 4.705v-.227c0-1.564 1.213-2.9 2.816-2.951a52.662 52.662 0 0 1 3.369 0c1.603.051 2.815 1.387 2.815 2.951Zm-6.136-1.452a51.196 51.196 0 0 1 3.273 0C14.39 3.05 15 3.684 15 4.478v.113a49.488 49.488 0 0 0-6 0v-.113c0-.794.609-1.428 1.364-1.452Zm-.355 5.945a.75.75 0 1 0-1.5.058l.347 9a.75.75 0 1 0 1.499-.058l-.346-9Zm5.48.058a.75.75 0 1 0-1.498-.058l-.347 9a.75.75 0 0 0 1.5.058l.345-9Z" clipRule="evenodd" />
                                    </svg>
                                </button>
                            </div>


                            {/* Shared With Selection - Show for 'equal' and 'percentage' methods */}
                            {(item.splitMethod === 'equal' || item.splitMethod === 'percentage') && (
                                <div className="mb-3 flex gap-3">
                                    <div className="relative w-full">
                                        <select
                                            value={item.personId || ''}
                                            onChange={(e) => updateItem(index, 'personId', e.target.value ? parseInt(e.target.value, 10) : null)}
                                            className="w-full p-2 border-none rounded-lg bg-gray-100 
                                        focus:outline-none focus:ring-1 focus:ring-blue-500"
                                        >
                                            <option value="" disabled>Select Payer</option>
                                            {participants.map(p => (
                                                <option key={p.id} value={p.id}>{p.name}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="relative w-full">
                                        <details className="w-full">
                                            <summary className="flex justify-between items-center cursor-pointer p-2 border rounded-lg bg-gray-100 list-none">
                                                <span className="text-sm">
                                                    {item.sharedWith.length > 0
                                                        ? `Shared with: ${participants
                                                            .filter((p) => item.sharedWith.includes(p.id))
                                                            .map((p) => p.name)
                                                            .join(", ")}`
                                                        : "Participants"}
                                                </span>
                                                <svg
                                                    xmlns="http://www.w3.org/2000/svg"
                                                    className="h-5 w-5 text-gray-500"
                                                    viewBox="0 0 20 20"
                                                    fill="currentColor"
                                                >
                                                    <path
                                                        fillRule="evenodd"
                                                        d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.08 1.04l-4.25 4.25a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z"
                                                        clipRule="evenodd"
                                                    />
                                                </svg>
                                            </summary>
                                            <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2">
                                                {participants.map((p) => (
                                                    <label
                                                        key={p.id}
                                                        className="flex items-center gap-2 px-2 py-1 rounded-lg hover:bg-blue-50 cursor-pointer"
                                                    >
                                                        <input
                                                            type="checkbox"
                                                            checked={item.sharedWith.includes(p.id)}
                                                            onChange={() => toggleShareWith(index, p.id)}
                                                            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                                                        />
                                                        <span className="text-gray-700 text-sm">{p.name}</span>
                                                    </label>
                                                ))}
                                            </div>
                                        </details>
                                    </div>
                                </div>
                            )}

                            {/* Split Details */}
                            {((item.splitMethod === 'equal' || item.splitMethod === 'percentage') && item.sharedWith.length > 0) && (
                                <div className="bg-gray-50 rounded-lg p-3">
                                    <h4 className="text-sm font-medium text-gray-700 mb-2">Split Details</h4>

                                    {item.splitMethod === 'equal' && (
                                        <div className="space-y-1">
                                            {participants
                                                .filter(p => item.sharedWith.includes(p.id))
                                                .map(person => (
                                                    <div key={person.id} className="flex justify-between items-center text-sm">
                                                        <span className="text-gray-600">{person.name}</span>
                                                        <span className="font-medium">฿{getEqualShare(index)}</span>
                                                    </div>
                                                ))}
                                        </div>
                                    )}

                                    {item.splitMethod === 'percentage' && (
                                        <div className="space-y-2">
                                            {participants
                                                .filter(p => item.sharedWith.includes(p.id))
                                                .map(person => (
                                                    <div key={person.id} className="flex items-center gap-2 text-sm">
                                                        <span className="text-gray-600 w-20">{person.name}</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            max="100"
                                                            value={item.percentages[person.id] || 0}
                                                            onChange={(e) => updatePercentage(index, person.id, parseInt(e.target.value) || 0)}
                                                            className="w-16 p-1 text-center border rounded bg-white"
                                                        />
                                                        <span className="text-gray-500">%</span>
                                                        <span className="font-medium ml-auto">฿{getPercentageShare(index, person.id)}</span>
                                                    </div>
                                                ))}
                                            <div className="border-t pt-2 flex justify-between items-center text-sm">
                                                <span className="font-medium">Total:</span>
                                                <span className={`font-medium ${getTotalPercentage(index) === 100 ? 'text-green-600' : 'text-red-500'}`}>
                                                    {getTotalPercentage(index)}%
                                                </span>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}


                        </div>
                    ))}

                    {/* Add Item Button */}
                    <button
                        onClick={addItem}
                        className="w-full py-2 px-4 bg-gray-100 text-blue-500 font-medium rounded-xl 
                                 hover:bg-gray-200 transition flex items-center justify-center gap-2"
                    >
                        <span>+ Add Item</span>
                    </button>
                </div>

                {/* Finish Button */}
                <button
                    onClick={handleSubmit}
                    disabled={!hasValidItems()}
                    className={`w-full font-bold py-3 rounded-xl transition mb-8 ${
                        hasValidItems() 
                            ? 'bg-blue-500 text-white hover:bg-blue-600' 
                            : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                    }`}
                >
                    FINISH
                </button>

                {/* Split Result */}
                {splitResult && (
                    <div className="mb-6">
                        <h3 className="text-gray-700 font-medium mb-3">Split Result</h3>
                        <div className="p-3 border rounded-xl bg-white">
                            {splitResult.map((res, index) => (
                                <div key={index} className="flex justify-between items-center mb-2 last:mb-0">
                                    <span className="font-medium">{res.participant}</span>
                                    <span className="text-lg font-bold text-blue-600">฿{res.amount.toFixed(2)}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
            <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
        </div>
    );
}