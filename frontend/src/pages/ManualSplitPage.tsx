// src/pages/ManualSplitPage.tsx
import React, { useState, useEffect, useRef } from "react"; 
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

interface OtherRate {
    id: number;
    currency: string;
    rate: string;
}

type ExpenseItem = {
    name: string;
    amount: string;
    sharedWith: number[];
    splitMethod: SplitMethod;
    percentages: { [personId: number]: number };
    currency: string;
    customCurrency: string;
    exchangeRate: string;
    showExchangeRateInput: boolean;
    otherRates: OtherRate[];
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
    const [openCurrencyPicker, setOpenCurrencyPicker] = useState<number | null>(null);
    const [openParticipantPicker, setOpenParticipantPicker] = useState<number | null>(null);

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
            exchangeRate: "",
            showExchangeRateInput: false,
            otherRates: [],
        },
    ]);
    const navigate = useNavigate();

    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploadingItemIndex, setUploadingItemIndex] = useState<number | null>(null);

    const getCurrencySymbol = (currency: string): string => {
        switch (currency.toUpperCase()) {
            case "THB": return "฿";
            case "USD": return "$";
            case "JPY": return "¥";
            default: return currency.toUpperCase();
        }
    };

    const getItemCurrency = (item: ExpenseItem): string => {
        return item.currency === "CUSTOM" && item.customCurrency.trim() !== ""
            ? item.customCurrency.toUpperCase().slice(0, 3)
            : item.currency;
    };

    const handleItemCurrencyChange = (itemIndex: number, value: string) => {
        const newItems = [...items];
        const item = { ...newItems[itemIndex] };
        
        item.currency = value;
        if (value !== "CUSTOM") {
            item.customCurrency = "";
        }

        if (value === "THB") {
            item.exchangeRate = "";
            item.showExchangeRateInput = false;
            item.otherRates = [];
        }

        newItems[itemIndex] = item;
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

    const hasValidItems = () => {
        return items.some((item: ExpenseItem) => {
            const hasName = item.name.trim() !== "";
            const hasAmount = parseFloat(item.amount || "0") > 0;
            const hasParticipants = item.sharedWith.length > 0;
            
            const itemCurrency = getItemCurrency(item);
            let hasValidRate = true;
            if (itemCurrency !== "THB") {
                if (!item.showExchangeRateInput) {
                    hasValidRate = false;
                }
                const rate = parseFloat(item.exchangeRate);
                if (item.showExchangeRateInput && (isNaN(rate) || rate <= 0)) {
                    hasValidRate = false;
                }
            }

            if (item.splitMethod === "percentage") {
                const totalPercentage = Object.values(item.percentages).reduce(
                    (sum: number, p: number) => sum + p,
                    0
                );
                return hasName && hasAmount && hasParticipants && totalPercentage <= 100 && hasValidRate;
            }

            return hasName && hasAmount && hasParticipants && hasValidRate;
        });
    };

    const handleSubmit = async () => {
        if (!hasValidItems()) {
            alert("Please add at least one complete item. If using a foreign currency, you must check 'Set Exchange Rate' and provide a valid rate.");
            return;
        }

        if (!user) {
            alert("User information is missing. Please log in again.");
            return;
        }

        let totalAmountInThb = 0;
        
        // เก็บข้อมูลเรททั้งหมดเพื่อส่ง JSON ก้อนเดียว
        const exchangeRatesMap: { [key: string]: number } = { "THB": 1 };

        // Loop เพื่อคำนวณยอดรวม THB และรวบรวม Rate
        for (const item of items) {
            const amountNum = parseFloat(item.amount || "0");
            const activeCurrency = getItemCurrency(item);
            let itemAmountInThb = amountNum;
            let rateNum: number = 1;

            if (activeCurrency !== "THB") {
                if (!item.showExchangeRateInput) {
                    alert(`Item "${item.name}" uses ${activeCurrency} but 'Set Exchange Rate' is not checked.`);
                    return;
                }
                rateNum = Number(item.exchangeRate);
                if (!Number.isFinite(rateNum) || rateNum <= 0) {
                    alert(`Item "${item.name}" has an invalid exchange rate.`);
                    return;
                }
                itemAmountInThb = amountNum * rateNum;
                
                // เก็บ Rate หลักของ Item นี้
                exchangeRatesMap[activeCurrency] = rateNum;
            }

            // เก็บ Rate ย่อยๆ จาก Rate Manager ของ Item นี้
            item.otherRates.forEach((r: OtherRate) => {
                if (r.currency && r.rate) {
                    exchangeRatesMap[r.currency] = parseFloat(r.rate);
                }
            });
            
            // บวกยอดรวมทั้งหมด (เป็น THB) เพื่อสร้าง Expense Header
            totalAmountInThb += itemAmountInThb;
        }

        try {
            // 1. สร้าง Expense Header (ยอดรวมต้องเป็น THB)
            const expensePayload = {
                groupId: Number(groupId),
                payerUserId: Number(user.id),
                title: expenseName,
                type: "CUSTOM" as const,
                status: "SETTLED" as const,
                amount: totalAmountInThb, 
                ratesJson: exchangeRatesMap, 
            };
            
            console.log("🚀 Creating Expense Payload:", expensePayload);

            const expense = await createExpenseApi(expensePayload);
            const expenseId = expense.id;

            // 2. วนลูปสร้าง Item และ Share
            for (let i = 0; i < items.length; i++) {
                const item = items[i];
                const itemCurrency = getItemCurrency(item);
                const amountNum = parseFloat(item.amount || "0"); // ยอดตามสกุลเงินจริง

                try {
                    // สร้าง Item (เก็บยอด Original + Currency Original)
                    const createdItem = await createExpenseItem(expenseId, item.name, item.amount, itemCurrency);
                    const itemId = createdItem.id;

                    if (item.splitMethod === "equal") {
                        // ✅ แก้ไข: หารเท่า คิดจากยอดเงินดิบ (Original Currency)
                        // จำนวนคน = คนที่ถูกเลือก + คนจ่าย(1)
                        const numberOfSharers = item.sharedWith.length + 1; 
                        
                        // เอา Amount ดิบ หาร จำนวนคน
                        const rawShareValue = amountNum / numberOfSharers;
                        const shareValue = rawShareValue.toFixed(4);

                        console.log(`Item "${item.name}": Equal Split (${itemCurrency}). ${amountNum} / ${numberOfSharers} = ${shareValue}`);

                        for (const participantId of item.sharedWith) {
                            await createExpenseItemShare(
                                expenseId, 
                                itemId, 
                                participantId, 
                                shareValue, // ส่งยอดเงินตามสกุลเงินจริง
                                undefined
                            );
                        }

                    } else if (item.splitMethod === "percentage") {
                        // หารเปอร์เซ็นต์ ส่ง % ไป
                        console.log(`Item "${item.name}": Percentage Split`);
                        
                        for (const participantId of item.sharedWith) {
                            const sharePercent = item.percentages[participantId]?.toString() || "0";
                            
                            await createExpenseItemShare(
                                expenseId,
                                itemId,
                                participantId,
                                undefined, 
                                sharePercent 
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
                exchangeRate: "",
                showExchangeRateInput: false,
                otherRates: [],
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
            newItems[itemIndex].sharedWith = currentShareWith.filter((id: number) => id !== personId);
            const newPercentages = { ...item.percentages };
            delete newPercentages[personId];
            newItems[itemIndex].percentages = newPercentages;
        } else {
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
            item.sharedWith.forEach((personId: number) => {
                percentages[personId] = equalPercentage;
            });
            newItems[itemIndex].percentages = percentages;
        } else if (method !== "percentage") {
            newItems[itemIndex].percentages = {};
        }
        setItems(newItems);
    };

    const handleAddRate = (itemIndex: number) => {
        const newItems = [...items];
        const item = { ...newItems[itemIndex] };
        item.otherRates = [
            ...item.otherRates,
            { id: Date.now(), currency: "", rate: "" }
        ];
        newItems[itemIndex] = item;
        setItems(newItems);
    };

    const handleOtherRateChange = (itemIndex: number, rateId: number, field: 'currency' | 'rate', value: string) => {
        const newItems = [...items];
        const item = { ...newItems[itemIndex] };
        item.otherRates = item.otherRates.map((r: OtherRate) => 
            r.id === rateId
            ? { ...r, [field]: field === 'currency' ? value.toUpperCase().slice(0, 3) : value } 
            : r
        );
        newItems[itemIndex] = item;
        setItems(newItems);
    };

    const handleRemoveRate = (itemIndex: number, rateId: number) => {
        const newItems = [...items];
        const item = { ...newItems[itemIndex] };
        item.otherRates = item.otherRates.filter((r: OtherRate) => r.id !== rateId);
        newItems[itemIndex] = item;
        setItems(newItems);
    };

    const handleDownload = (itemIndex: number) => {
        const item = items[itemIndex];
        const activeCurrency = getItemCurrency(item);
        
        const ratesToDownload: {[key: string]: number} = {};

        if (activeCurrency !== "THB" && item.exchangeRate) {
            ratesToDownload[activeCurrency] = parseFloat(item.exchangeRate);
        }
        item.otherRates.forEach((r: OtherRate) => {
            if (r.currency && r.rate) {
                ratesToDownload[r.currency] = parseFloat(r.rate);
            }
        });

        if (Object.keys(ratesToDownload).length === 0) {
            alert("No rates to download.");
            return;
        }

        const jsonString = JSON.stringify(ratesToDownload, null, 2);
        const blob = new Blob([jsonString], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `item_${itemIndex}_rates.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    const handleUploadClick = (itemIndex: number) => {
        setUploadingItemIndex(itemIndex);
        fileInputRef.current?.click();
    };

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        if (uploadingItemIndex === null) return;
        
        const file = event.target.files?.[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const json = JSON.parse(e.target?.result as string);
                if (typeof json !== 'object' || json === null || Array.isArray(json)) {
                    throw new Error("Invalid JSON format.");
                }

                const newItems = [...items];
                const item = { ...newItems[uploadingItemIndex] };
                const activeCurrency = getItemCurrency(item);
                
                const newOtherRates: OtherRate[] = [];
                let mainRateSet = false;

                Object.keys(json).forEach((key) => {
                    const rate = String(json[key]);
                    const curr = key.toUpperCase();

                    if (curr === activeCurrency) {
                        item.exchangeRate = rate;
                        mainRateSet = true;
                    } else {
                        newOtherRates.push({
                            id: Date.now() + Math.random(),
                            currency: curr,
                            rate: rate
                        });
                    }
                });

                item.otherRates = newOtherRates;
                
                if (mainRateSet || newOtherRates.length > 0) {
                    item.showExchangeRateInput = true;
                }

                if (!mainRateSet && activeCurrency !== "THB") {
                    item.exchangeRate = "";
                }

                newItems[uploadingItemIndex] = item;
                setItems(newItems);
                
            } catch (err: any) {
                alert(`Error reading file: ${err.message}`);
            }
        };
        reader.readAsText(file);
        event.target.value = '';
        setUploadingItemIndex(null);
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
                <label className="text-gray-700 font-medium">Total Expense</label>
                <div className="w-full p-3 mb-4 mt-2 border-none rounded-xl bg-gray-100">
                    {(() => {
                        const currencyTotals = items.reduce((acc: { [key: string]: number }, item: ExpenseItem) => {
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
                                        {total.toFixed(4)}
                                        {Object.keys(currencyTotals).length > 1 && (
                                            <span className="text-sm text-gray-500 ml-1">({currency})</span>
                                        )}
                                    </span>
                                ))}
                            </div>
                        );
                    })()}
                </div>

                <div className="mb-6">
                    <h3 className="text-gray-700 font-medium mb-3">Items</h3>
                    {items.map((item: ExpenseItem, index: number) => (
                        <div key={index} className="mb-4 p-3 border rounded-xl bg-white shadow-sm">
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
                                <div className="relative w-full">
                                    <button
                                        type="button"
                                        onClick={() => setOpenCurrencyPicker(openCurrencyPicker === index ? null : index)}
                                        className="w-full flex justify-between items-center cursor-pointer p-2 border rounded-lg bg-gray-100"
                                    >
                                        <span className="text-sm">
                                            {item.currency === "CUSTOM" && item.customCurrency.trim() !== ""
                                                ? `${item.customCurrency.toUpperCase()}`
                                                : item.currency === "CUSTOM"
                                                ? "Custom"
                                                : `${item.currency} (${getCurrencySymbol(item.currency)})`}
                                        </span>
                                        <span className="text-gray-500 text-xs">{openCurrencyPicker === index ? "▲" : "▼"}</span>
                                    </button>
                                    {openCurrencyPicker === index && (
                                        <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2">
                                            {["THB", "USD", "JPY", "CUSTOM"].map((currency) => (
                                                <label
                                                    key={currency}
                                                    className="flex items-center gap-2 px-2 py-1 rounded-lg hover:bg-blue-50 cursor-pointer"
                                                >
                                                    <input
                                                        type="radio"
                                                        name={`currency-${index}`}
                                                        checked={item.currency === currency}
                                                        onChange={() => {
                                                            handleItemCurrencyChange(index, currency);
                                                            setOpenCurrencyPicker(null);
                                                        }}
                                                        className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300"
                                                    />
                                                    <span className="text-gray-700 text-sm">
                                                        {currency === "CUSTOM"
                                                            ? "Custom"
                                                            : `${currency} (${getCurrencySymbol(currency)})`}
                                                    </span>
                                                </label>
                                            ))}
                                        </div>
                                    )}
                                </div>
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

                            {/* Checkbox & Rate Manager */}
                            {item.currency !== "THB" && (
                                <div className="mb-3">
                                    <label className="flex items-center gap-2 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={item.showExchangeRateInput}
                                            onChange={(e) => {
                                                const isChecked = e.target.checked;
                                                updateItem(index, "showExchangeRateInput", isChecked);
                                                if (!isChecked) {
                                                    updateItem(index, "exchangeRate", "");
                                                }
                                            }}
                                            className="w-4 h-4 text-blue-500 rounded focus:ring-0"
                                        />
                                        <span className="text-gray-700 text-sm font-medium">
                                            Set Exchange Rate
                                        </span>
                                    </label>
                                </div>
                            )}
                
                            {item.showExchangeRateInput && (
                                <div className="p-3 border rounded-lg bg-gray-50 mb-3">
                                    <div className="mb-3">
                                        <label className="block text-gray-700 text-sm font-medium mb-1">
                                            Exchange Rate (1 {getItemCurrency(item)} = ? THB)
                                        </label>
                                        <input
                                            type="number"
                                            value={item.exchangeRate}
                                            onChange={(e) => updateItem(index, "exchangeRate", e.target.value)}
                                            placeholder="Enter rate for main currency"
                                            className="w-full p-2 border-none rounded-lg bg-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                        />
                                    </div>

                                    <hr className="my-3"/>
                                    <h4 className="text-sm font-medium text-gray-800 mb-2">
                                        Rate Manager
                                    </h4>
                                    
                                    <div className="space-y-2 mb-3">
                                        {item.otherRates.map((rateItem: OtherRate) => ( 
                                            <div key={rateItem.id} className="flex items-center gap-2">
                                                <input
                                                    type="text"
                                                    value={rateItem.currency}
                                                    onChange={(e) => handleOtherRateChange(index, rateItem.id, 'currency', e.target.value)}
                                                    placeholder="CUR"
                                                    maxLength={3}
                                                    className="w-1/4 p-2 text-sm border-none rounded-lg bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
                                                />
                                                <input
                                                    type="number"
                                                    value={rateItem.rate}
                                                    onChange={(e) => handleOtherRateChange(index, rateItem.id, 'rate', e.target.value)}
                                                    placeholder="Rate"
                                                    className="w-1/2 p-2 text-sm border-none rounded-lg bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
                                                />
                                                <button
                                                    type="button"
                                                    onClick={() => handleRemoveRate(index, rateItem.id)}
                                                    className="w-1/4 bg-red-500 text-white text-xs py-2 rounded-lg hover:bg-red-600"
                                                >
                                                    Remove
                                                </button>
                                            </div>
                                        ))}
                                    </div>

                                    <button
                                        type="button"
                                        onClick={() => handleAddRate(index)}
                                        className="w-full bg-blue-500 text-white font-medium py-2 text-sm rounded-lg hover:bg-blue-600 mb-2"
                                    >
                                        Add Other Rate
                                    </button>
                                    
                                    <div className="flex gap-2">
                                        <button
                                            type="button"
                                            onClick={() => handleDownload(index)}
                                            className="w-1/2 bg-green-500 text-white font-medium py-2 text-sm rounded-lg hover:bg-green-600"
                                        >
                                            Download
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => handleUploadClick(index)}
                                            className="w-1/2 bg-gray-600 text-white font-medium py-2 text-sm rounded-lg hover:bg-gray-700"
                                        >
                                            Upload
                                        </button>
                                    </div>
                                </div>
                            )}

                            {/* Participants */}
                            <div className="mb-3">
                                <p className="text-sm font-medium text-gray-700 mb-2">Participants</p>
                            {(item.splitMethod === "equal" || item.splitMethod === "percentage") && (
                                <div className="mb-3 flex gap-3">
                                    <div className="relative w-full">
                                        <button
                                            type="button"
                                            onClick={() => setOpenParticipantPicker(openParticipantPicker === index ? null : index)}
                                            className="w-full flex justify-between items-center cursor-pointer p-2 border rounded-lg bg-gray-100"
                                        >
                                            <span className="text-sm">
                                                {item.sharedWith.length > 0
                                                    ? `Shared with: ${participants
                                                        .filter((p: Participant) => item.sharedWith.includes(p.id))
                                                        .map((p: Participant) => p.name)
                                                        .join(", ")}`
                                                    : "Add participants"}
                                            </span>
                                            <span className="text-gray-500 text-xs">{openParticipantPicker === index ? "▲" : "▼"}</span>
                                        </button>
                                        {openParticipantPicker === index && (
                                            <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2 max-h-48 overflow-y-auto">
                                                {participants.filter((p: Participant) => p.id !== Number(user?.id)).map((p: Participant) => (
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
                                        )}
                                    </div>
                                </div>
                            )}
                            </div>
                            {item.splitMethod === "percentage" && item.sharedWith.length > 0 && (
                                <div className="bg-gray-50 rounded-lg p-3">
                                    <h4 className="text-sm font-medium text-gray-700 mb-2">Split Details</h4>
                                    {participants
                                        .filter((p: Participant) => item.sharedWith.includes(p.id))
                                        .map((person: Participant) => (
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
            
            <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileChange}
                accept=".json,application/json"
                className="hidden"
            />
        </div>
    );
}