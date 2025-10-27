import React, { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import { BottomNav } from "../components/BottomNav";
import CircleBackButton from "../components/CircleBackButton";
import { useAuth } from '../contexts/AuthContext';

import {
    createExpenseApi,
    createExpenseItem,
    createExpenseItemShare,
    getGroupMembers,
    getUserInformation,
} from "../utils/api";

type ExpenseItem = {
    name: string;
    amount: string;
    sharedWith: number[];
    splitMethod: SplitMethod;
    percentages: { [personId: number]: number };
    currency: string;
    customCurrency: string;
};

type SplitMethod = "equal" | "percentage";

interface Participant {
    id: number;
    name: string;
}

export default function ManualSplitPage() {
    const [expenseName, setExpenseName] = useState("");
    const location = useLocation();
    const { user } = useAuth();
    const [participants, setParticipants] = useState<Participant[]>([]);

    const groupId = location.state?.groupId;
    const [items, setItems] = useState<ExpenseItem[]>([
        {
            name: "",
            amount: "",
            sharedWith: [],
            splitMethod: "equal",
            percentages: {},
            currency: "THB",
            customCurrency: "",
        },
    ]);
    const navigate = useNavigate();

    // Helper function to get currency symbol
    const getCurrencySymbol = (currency: string): string => {
        switch (currency.toUpperCase()) {
            case "THB": return "฿";
            case "USD": return "$";
            case "JPY": return "¥";
            default: return currency.toUpperCase();
        }
    };

    // Get the active currency for an item (custom or selected)
    const getItemCurrency = (item: ExpenseItem): string => {
        return item.currency === "CUSTOM" && item.customCurrency.trim() !== ""
            ? item.customCurrency.toUpperCase().slice(0, 3)
            : item.currency;
    };

    // Handle currency dropdown change for an item
    const handleItemCurrencyChange = (itemIndex: number, value: string) => {
        const newItems = [...items];
        newItems[itemIndex].currency = value;
        if (value !== "CUSTOM") {
            newItems[itemIndex].customCurrency = "";
        }
        setItems(newItems);
    };

    useEffect(() => {
        const fetchParticipants = async () => {
            if (groupId) {
                try {
                    const members = await getGroupMembers(groupId);
                    const participantPromises = members.map(member => getUserInformation(member.id));
                    const participantDetails = await Promise.all(participantPromises);
                    const fetchedParticipants = participantDetails.map(p => ({ id: p.id, name: p.userName }));
                    setParticipants(fetchedParticipants);
                } catch (error) {
                    console.error("Failed to fetch group members:", error);
                }
            }
        };

        fetchParticipants();
    }, [groupId]);

    // ตรวจสอบว่ามีข้อมูลอย่างน้อย 1 รายการ
    const hasValidItems = () => {
        return items.some((item) => {
            const hasName = item.name.trim() !== "";
            const hasAmount = parseFloat(item.amount || "0") > 0;
            const hasParticipants = item.sharedWith.length > 0;

            if (item.splitMethod === "percentage") {
                const totalPercentage = Object.values(item.percentages).reduce(
                    (sum, p) => sum + p,
                    0
                );
                return hasName && hasAmount && hasParticipants && totalPercentage <= 100;
            }

            return hasName && hasAmount && hasParticipants;
        });
    };

    // Submit และเรียก API
    const handleSubmit = async () => {
        if (!hasValidItems()) {
            alert("Please add at least one complete item with all required information.");
            return;
        }

        try {
            const totalAmount = items.reduce(
                (acc, item) => acc + parseFloat(item.amount || "0"),
                0
            );
            if (!user) {
                alert("User information is missing. Please log in again.");
                return;
            }
            const expensePayload = {
                groupId: Number(groupId),
                payerUserId: Number(user.id),
                title: expenseName,
                type: "CUSTOM" as const,
                status: "SETTLED",
                amount: totalAmount,
            };
            console.log("Expense Payload:", expensePayload);
            console.log("Item Payload:", items);

            const expense = await createExpenseApi(expensePayload);
            const expenseId = expense.id;

            // Step 2: Create Expense Items
            for (const item of items) {
                try {
                    console.log("Processing item:", item);

                    const itemCurrency = getItemCurrency(item);
                    const createdItem = await createExpenseItem(expenseId, item.name, item.amount, itemCurrency);
                    console.log("Created Item:", createdItem);

                    const itemId = createdItem.id;

                    if (item.splitMethod === "equal") {
                        // จำนวนที่ต้องจ่ายต่อคน
                        const shareValue = (
                            parseFloat(item.amount) / (item.sharedWith.length + 1) // +1 = คนจ่ายเอง
                        ).toFixed(2);

                        for (const participantId of item.sharedWith) {
                            try {
                                await createExpenseItemShare(expenseId, itemId, participantId, shareValue);
                            } catch (error: any) {
                                console.error("Backend Error:", error.response?.data || error.message);
                            }
                        }
                    } else if (item.splitMethod === "percentage") {
                        for (const participantId of item.sharedWith) {
                            const sharePercent = item.percentages[participantId]?.toString() || "0";

                            await createExpenseItemShare(
                                expenseId,
                                itemId,
                                participantId,
                                undefined, // ไม่ส่ง value
                                sharePercent // ใช้เปอร์เซ็นต์
                            );
                        }
                    }

                } catch (err) {
                    console.error("Error processing this item:", item, err);
                }
            }


            alert("Expense successfully recorded!");
            const billId = expense?.id ?? expense?.expenseId;
            navigate(`/bill/${billId}`);
        } catch (error) {

            console.error("Failed to save expense", error);
            alert("Failed to save expense. Please try again.");
        }
    };

    const handleBack = () => {
        navigate(-1);
    };

    const addItem = () => {
        setItems([
            ...items,
            {
                name: "",
                amount: "",
                sharedWith: [],
                splitMethod: "equal",
                percentages: {},
                currency: "THB",
                customCurrency: "",
            },
        ]);
    };

    const updateItem = (index: number, field: keyof ExpenseItem, value: any) => {
        const newItems = [...items];
        newItems[index] = { ...newItems[index], [field]: value };
        setItems(newItems);
    };

    const toggleShareWith = (itemIndex: number, personId: number) => {
        const newItems = [...items];
        const currentShareWith = newItems[itemIndex].sharedWith;
        const item = newItems[itemIndex];

        if (currentShareWith.includes(personId)) {
            // remove
            newItems[itemIndex].sharedWith = currentShareWith.filter((id) => id !== personId);
            const newPercentages = { ...item.percentages };
            delete newPercentages[personId];
            newItems[itemIndex].percentages = newPercentages;
        } else {
            // add
            newItems[itemIndex].sharedWith = [...currentShareWith, personId];
            if (item.splitMethod === "percentage") {
                const defaultPercentage = Math.floor(100 / (currentShareWith.length + 1));
                newItems[itemIndex].percentages = {
                    ...item.percentages,
                    [personId]: defaultPercentage,
                };
            }
        }
        setItems(newItems);
    };

    const updatePercentage = (
        itemIndex: number,
        personId: number,
        percentage: number
    ) => {
        const newItems = [...items];
        newItems[itemIndex].percentages = {
            ...newItems[itemIndex].percentages,
            [personId]: percentage,
        };
        setItems(newItems);
    };

    const changeSplitMethod = (itemIndex: number, method: SplitMethod) => {
        const newItems = [...items];
        const item = newItems[itemIndex];
        newItems[itemIndex].splitMethod = method;
        if (method === "percentage" && item.sharedWith.length > 0) {
            const equalPercentage = Math.floor(100 / item.sharedWith.length);
            const percentages: { [key: number]: number } = {};
            item.sharedWith.forEach((personId) => {
                percentages[personId] = equalPercentage;
            });
            newItems[itemIndex].percentages = percentages;
        } else if (method !== "percentage") {
            newItems[itemIndex].percentages = {};
        }
        setItems(newItems);
    };
    return (
        <div className="h-screen bg-white flex flex-col overflow-hidden">
            <Navbar />
            <div className="flex-1 overflow-y-auto pt-4 mt-5 pb-20 px-4 sm:px-6">
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
                    placeholder="Enter your expense name (e.g. Dinner)"
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100
                     focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                {/* Total Expense Display */}
                <label className="text-gray-700 font-medium">Total Expense</label>
                <div className="w-full p-3 mb-4 mt-2 border-none rounded-xl bg-gray-100">
                    {(() => {
                        // Group items by currency
                        const currencyTotals = items.reduce((acc, item) => {
                            const currency = getItemCurrency(item);
                            const amount = parseFloat(item.amount || "0");
                            if (!acc[currency]) {
                                acc[currency] = 0;
                            }
                            acc[currency] += amount;
                            return acc;
                        }, {} as { [key: string]: number });

                        return (
                            <div className="flex flex-col gap-1">
                                {Object.entries(currencyTotals).map(([currency, total]) => (
                                    <span key={currency} className="text-lg">
                                        {getCurrencySymbol(currency)}
                                        {total.toFixed(2)}
                                        {Object.keys(currencyTotals).length > 1 && (
                                            <span className="text-sm text-gray-500 ml-1">({currency})</span>
                                        )}
                                    </span>
                                ))}
                            </div>
                        );
                    })()}
                </div>
                {/* Items List */}
                <div className="mb-6">
                    <h3 className="text-gray-700 font-medium mb-3">Items</h3>
                    {items.map((item, index) => (
                        <div key={index} className="mb-4 p-3 border rounded-xl bg-white shadow-sm">
                            {/* Split Method */}
                            <div className="mb-3">
                                <p className="text-sm font-medium text-gray-700 mb-2">Split Method</p>
                                <div className="flex gap-2 flex-wrap">
                                    <button
                                        onClick={() => changeSplitMethod(index, "equal")}
                                        className={`px-3 py-1 rounded-lg text-sm font-medium transition ${item.splitMethod === "equal"
                                            ? "bg-blue-500 text-white"
                                            : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                                            }`}
                                    >
                                        Equal
                                    </button>
                                    <button
                                        onClick={() => changeSplitMethod(index, "percentage")}
                                        className={`px-3 py-1 rounded-lg text-sm font-medium transition ${item.splitMethod === "percentage"
                                            ? "bg-blue-500 text-white"
                                            : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                                            }`}
                                    >
                                        Percentage
                                    </button>
                                </div>
                            </div>
                            {/* Amount & Name */}
                            <div className="mb-3 flex gap-3">
                                <input
                                    type="number"
                                    value={item.amount}
                                    onChange={(e) => updateItem(index, "amount", e.target.value)}
                                    placeholder="0.00"
                                    className="w-full p-2 border-none rounded-lg bg-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <input
                                    type="text"
                                    value={item.name}
                                    onChange={(e) => updateItem(index, "name", e.target.value)}
                                    placeholder="Enter item name"
                                    className="w-full p-2 border-none rounded-lg bg-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                            </div>

                            {/* Currency Selector */}
                            <div className="mb-3">
                                <p className="text-sm font-medium text-gray-700 mb-2">Currency</p>
                                <select
                                    value={item.currency}
                                    onChange={(e) => handleItemCurrencyChange(index, e.target.value)}
                                    className="w-full p-2 border-none rounded-lg bg-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                >
                                    <option value="THB">THB (฿)</option>
                                    <option value="USD">USD ($)</option>
                                    <option value="JPY">JPY (¥)</option>
                                    <option value="CUSTOM">Custom</option>
                                </select>
                                {/* Custom Currency Input */}
                                {item.currency === "CUSTOM" && (
                                    <input
                                        type="text"
                                        value={item.customCurrency}
                                        onChange={(e) => updateItem(index, "customCurrency", e.target.value.toUpperCase().slice(0, 3))}
                                        placeholder="e.g., EUR, GBP"
                                        maxLength={3}
                                        className="w-full p-2 mt-2 border-none rounded-lg bg-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                    />
                                )}
                            </div>

                            {/* Participants */}
                            <div className="mb-3">
                                <p className="text-sm font-medium text-gray-700 mb-2">Participants</p>
                            {(item.splitMethod === "equal" || item.splitMethod === "percentage") && (
                                <div className="mb-3 flex gap-3">
                                    <div className="relative w-full">
                                        <details className="w-full">
                                            <summary className="flex justify-between items-center cursor-pointer p-2 border rounded-lg bg-gray-100 list-none">
                                                <span className="text-sm">
                                                    {item.sharedWith.length > 0
                                                        ? `Shared with: ${participants
                                                            .filter((p) => item.sharedWith.includes(p.id))
                                                            .map((p) => p.name)
                                                            .join(", ")}`
                                                        : "Add paticipants"}
                                                </span>
                                            </summary>
                                            <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2">
                                                {participants.filter((p) => p.id !== Number(user?.id)).map((p) => (
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
                            </div>

                            {/* Percentage Details */}
                            {item.splitMethod === "percentage" && item.sharedWith.length > 0 && (
                                <div className="bg-gray-50 rounded-lg p-3">
                                    <h4 className="text-sm font-medium text-gray-700 mb-2">Split Details</h4>
                                    {participants
                                        .filter((p) => item.sharedWith.includes(p.id))
                                        .map((person) => (
                                            <div key={person.id} className="flex items-center gap-2 text-sm">
                                                <span className="text-gray-600 w-20">{person.name}</span>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    max="100"
                                                    value={item.percentages[person.id] || 0}
                                                    onChange={(e) =>
                                                        updatePercentage(index, person.id, parseInt(e.target.value) || 0)
                                                    }
                                                    className="w-16 p-1 text-center border rounded bg-white"
                                                />
                                                <span className="text-gray-500">%</span>
                                            </div>
                                        ))}
                                </div>
                            )}
                        </div>
                    ))}
                    <button
                        onClick={addItem}
                        className="w-full py-2 px-4 bg-gray-100 text-blue-500 font-medium rounded-xl hover:bg-gray-200 transition flex items-center justify-center gap-2"
                    >
                        <span>+ Add Item</span>
                    </button>
                </div>
                {/* FINISH Button */}
                <button
                    onClick={handleSubmit}
                    disabled={!hasValidItems()}
                    className={`w-full font-bold py-3 rounded-xl transition mb-8 ${hasValidItems()
                        ? "bg-blue-500 text-white hover:bg-blue-600"
                        : "bg-gray-300 text-gray-500 cursor-not-allowed"
                        }`}
                >
                    FINISH
                </button>
            </div>
            <BottomNav activeTab={undefined} />
        </div>
    );
}
