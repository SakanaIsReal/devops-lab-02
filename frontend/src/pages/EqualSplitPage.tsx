// src/pages/EqualSplitPage.tsx
import React, { useEffect, useMemo, useState, useRef } from "react";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import { BottomNav } from "../components/BottomNav";
import CircleBackButton from "../components/CircleBackButton";
import { useAuth } from '../contexts/AuthContext';
import { 
    getGroupMembers, 
    fetchUserProfiles, 
    createExpenseApi, 
    createExpenseItem, 
    createExpenseItemShare 
} from "../utils/api";
import type { User } from "../types";

interface OtherRate {
    id: number;
    currency: string;
    rate: string;
}

export default function EqualSplitPage() {
    const [expenseName, setExpenseName] = useState("");
    const [amount, setAmount] = useState("");
    const [pickerOpen, setPickerOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [currencyPickerOpen, setCurrencyPickerOpen] = useState(false);
    const [currency, setCurrency] = useState("THB");
    const [customCurrency, setCustomCurrency] = useState("");
    const [exchangeRate, setExchangeRate] = useState(""); 
    const [showExchangeRateInput, setShowExchangeRateInput] = useState(false); 

    const [otherRates, setOtherRates] = useState<OtherRate[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);
    
    const navigate = useNavigate();
    const { user } = useAuth(); 

    const { id: idParam } = useParams<{ id?: string }>();
    const location = useLocation() as {
        state?: { group?: { id?: number | string }; groupId?: number | string };
    };

    const groupIdNum: number | undefined = useMemo(() => {
        const fromState = location.state?.group?.id ?? location.state?.groupId;
        const raw = idParam ?? (fromState != null ? String(fromState) : undefined);
        if (raw == null) return undefined;
        const n = Number(raw);
        return Number.isFinite(n) ? n : undefined;
    }, [idParam, location.state]);

    const [participants, setParticipants] = useState<User[]>([]);
    const [includedIds, setIncludedIds] = useState<number[]>([]);
    const [loadingMembers, setLoadingMembers] = useState<boolean>(false);
    const [membersError, setMembersError] = useState<string | null>(null);

    const getDisplayName = (u: any): string => {
        if (!u) return "";
        const direct = u.name || u.userName || u.username || u.displayName || u.fullName;
        if (direct && typeof direct === 'string' && direct.trim()) return direct;

        const nested = u.user || u.profile || u.account;
        if (nested) {
            const nestedName = nested.name || nested.userName || nested.username || nested.displayName || nested.fullName;
            if (nestedName && typeof nestedName === 'string' && nestedName.trim()) return nestedName;
        }

        const email = u.email || nested?.email;
        if (email && typeof email === 'string') return email.split("@")[0];

        return "";
    };

    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                setLoadingMembers(true);
                setMembersError(null);

                if (!groupIdNum) {
                    setMembersError("ไม่พบ groupId สำหรับดึงรายชื่อสมาชิก");
                    setParticipants([]);
                    setIncludedIds([]);
                    return;
                }

                const baseMembers = await getGroupMembers(String(groupIdNum));
                if (cancelled) return;

                const needIds = baseMembers
                    .filter((m: any) => !getDisplayName(m))
                    .map((m: any) => Number(m.id))
                    .filter((n) => Number.isFinite(n));

                let finalMembers = baseMembers;

                if (needIds.length > 0) {
                    try {
                        const profMap = await fetchUserProfiles(needIds);
                        if (cancelled) return;

                        finalMembers = baseMembers.map((m: any) => {
                            const id = Number(m.id);
                            const prof = profMap.get(id);
                            const name = getDisplayName(prof) || getDisplayName(m) || `User #${id}`;
                            const email = prof?.email || m.email || "";
                            const imageUrl = prof?.imageUrl || m.imageUrl || "";
                            return { ...m, name, email, imageUrl };
                        });
                    } catch (err) {
                        console.error("Failed to fetch profiles", err);
                        finalMembers = baseMembers.map((m: any) => ({
                            ...m,
                            name: getDisplayName(m) || `User #${m.id}`
                        }));
                    }
                } else {
                    finalMembers = baseMembers.map((m: any) => ({
                        ...m,
                        name: getDisplayName(m) || `User #${m.id}`
                    }));
                }

                if (!cancelled) {
                    setParticipants(finalMembers);
                    setIncludedIds(
                        finalMembers
                            .map((m: any) => Number(m.id))
                            .filter((n: number) => Number.isFinite(n))
                    );
                }
            } catch (e: any) {
                console.error("getGroupMembers failed:", {
                    status: e?.response?.status,
                    data: e?.response?.data,
                    groupId: groupIdNum,
                });
                if (!cancelled) setMembersError("โหลดรายชื่อสมาชิกไม่สำเร็จ");
            } finally {
                if (!cancelled) setLoadingMembers(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [groupIdNum]);

    const toggleInclude = (id: number) => {
        setIncludedIds((prev) =>
            prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
        );
    };

    const labelFor = (p: User) => {
        const name = getDisplayName(p);
        return name || `User #${p.id}`;
    };

    const getCurrencySymbol = (curr: string): string => {
        switch (curr.toUpperCase()) {
            case "THB": return "฿";
            case "USD": return "$";
            case "JPY": return "¥";
            default: return curr.toUpperCase();
        }
    };

    const getActiveCurrency = (): string => {
        if (currency === "CUSTOM" && customCurrency.trim() !== "") {
            return customCurrency.toUpperCase().slice(0, 3);
        }
        return currency;
    };

    const handleSubmit = async () => {
        if (!groupIdNum) { alert("ไม่พบ groupId"); return; }

        const amountNum = Number(amount); 
        if (!Number.isFinite(amountNum) || amountNum <= 0) { alert("ใส่ยอดให้ถูกต้อง"); return; }
        if (!expenseName.trim()) { alert("กรอกชื่อรายการก่อน"); return; }
        if (includedIds.length === 0) { alert("ต้องมีผู้ร่วมจ่ายอย่างน้อย 1 คน"); return; } 
        if (!user || !Number.isFinite(Number(user.id))) { alert("ข้อมูลผู้ใช้ไม่ถูกต้อง"); return; }

        const payerUserId = Number(user.id);
        const activeCurrency = getActiveCurrency(); 

        let amountInThb = amountNum;
        let rateNum: number | undefined = undefined;

        if (activeCurrency !== "THB") {
            if (!showExchangeRateInput) {
                alert("กรุณาติ๊ก 'Set Exchange Rate' เพื่อกำหนดอัตราแลกเปลี่ยน");
                return;
            }
            if (currency === "CUSTOM" && !activeCurrency) {
                 alert("กรุณาระบุรหัสสกุลเงิน (e.g., EUR)");
                 return;
            }
            rateNum = Number(exchangeRate);
            if (!Number.isFinite(rateNum) || rateNum <= 0) {
                alert("กรุณาระบุ Exchange Rate ให้ถูกต้อง (ต้องมากกว่า 0)");
                return; 
            }
            amountInThb = amountNum * rateNum; 
        }

        const exchangeRatesMap: { [key: string]: number } = { "THB": 1 };
        if (activeCurrency !== "THB" && exchangeRate) {
             exchangeRatesMap[activeCurrency] = parseFloat(exchangeRate);
        }
        otherRates.forEach(r => {
            if (r.currency && r.rate) {
                exchangeRatesMap[r.currency] = parseFloat(r.rate);
            }
        });

        // ✅ คำนวณยอดหาร จากยอดเงินต้นทาง (amountNum)
        const numberOfSharers = includedIds.length; 
        // ใช้ amountNum (ยอด Foreign) หารด้วยจำนวนคน
        const rawShareValue = amountNum / numberOfSharers; 
        const shareValue = rawShareValue.toFixed(2); 

        console.group("📊 Split Calculation");
        console.log(`Original Amount (${activeCurrency}): ${amountNum}`);
        console.log(`Total Converted (THB): ${amountInThb}`);
        console.log(`People: ${numberOfSharers}`);
        console.log(`Per Person Share (${activeCurrency}): ${shareValue}`);
        console.groupEnd();

        setSaving(true);
        try {
            const expensePayload = {
                groupId: groupIdNum,
                payerUserId,
                amount: amountInThb, // Header เก็บยอด THB (ถูกต้องแล้วสำหรับระบบรวม)
                title: expenseName.trim(),
                type: 'EQUAL' as const, 
                status: 'SETTLED' as const, 
                ratesJson: exchangeRatesMap, 
            };
            
            console.log("🚀 Creating Expense Payload:", expensePayload);

            const expense = await createExpenseApi(expensePayload);
            const expenseId = expense.id;

            // Item เก็บยอด Original และ Currency Original
            const ItemName = expense.title;
            const ItemAmount = amount; 
            const itemCurrency = activeCurrency; 
            
            const createdItem = await createExpenseItem(expenseId, ItemName, ItemAmount, itemCurrency);
            const itemId = createdItem.id;

            // ✅ ส่งยอดที่หารแล้ว (ตามสกุลเงินต้นทาง) ไปที่ API
            for (const participantId of includedIds) {
                await createExpenseItemShare(
                    expenseId, 
                    itemId, 
                    participantId, 
                    shareValue, // 👈 ส่งยอดหารตามสกุลเงินต้นทาง (e.g., 50 USD)
                    undefined
                );
            }

            const billId = expense?.id ?? expense?.expenseId;
            alert("Expense successfully recorded!");
            
            // UI แสดงผลยอดที่ต้องจ่ายต่อคน (ตามสกุลเงินต้นทาง)
            const displayAmountPerPerson = amountNum / numberOfSharers;

            const uiParticipants = participants
                .filter(p => includedIds.includes(Number(p.id)))
                .map(p => ({
                    id: Number(p.id),
                    name: labelFor(p),
                    email: p.email,
                    imageUrl: p.imageUrl,
                    amountOwed: displayAmountPerPerson 
                }));

            navigate(`/bill/${billId}`, {
                state: {
                    bill: {
                        ...expense,
                        groupId: expense?.groupId ?? groupIdNum,
                    },
                    ui: {
                        billId,
                        groupId: groupIdNum, 
                        title: expenseName.trim(),
                        amount: amountNum, 
                        payerUserId,
                        participants: uiParticipants,
                        createdAt: expense?.createdAt ?? new Date().toISOString(),
                    },
                },
            });
        } catch (e: any) {
            const msg = e?.response?.data?.message || e?.response?.data?.error || '';
            alert(`สร้างบิลไม่สำเร็จ: ${e?.response?.status ?? 'ERR'}${msg ? `\n${msg}` : ''}`);
            console.error(e);
        } finally {
            setSaving(false);
        }
    };

    const handleBack = () => navigate(-1);

    const handleAddRate = () => {
        setOtherRates([
            ...otherRates,
            { id: Date.now(), currency: "", rate: "" }
        ]);
    };

    const handleOtherRateChange = (id: number, field: 'currency' | 'rate', value: string) => {
        setOtherRates(otherRates.map(r => 
            r.id === id 
            ? { ...r, [field]: field === 'currency' ? value.toUpperCase().slice(0, 3) : value } 
            : r
        ));
    };

    const handleRemoveRate = (id: number) => {
        setOtherRates(otherRates.filter(r => r.id !== id));
    };

    const handleDownload = () => {
        const activeCurrency = getActiveCurrency();
        const ratesToDownload: {[key: string]: number} = {};

        if (activeCurrency !== "THB" && exchangeRate) {
            ratesToDownload[activeCurrency] = parseFloat(exchangeRate);
        }

        otherRates.forEach(r => {
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
        a.download = "exchange_rates.json";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    const handleUploadClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const json = JSON.parse(e.target?.result as string);
                if (typeof json !== 'object' || json === null || Array.isArray(json)) {
                    throw new Error("Invalid JSON format.");
                }

                const activeCurrency = getActiveCurrency();
                const newOtherRates: OtherRate[] = [];
                let mainRateSet = false;

                Object.keys(json).forEach((key, index) => {
                    const rate = String(json[key]);
                    const curr = key.toUpperCase();

                    if (curr === activeCurrency) {
                        setExchangeRate(rate);
                        mainRateSet = true;
                    } else {
                        newOtherRates.push({
                            id: Date.now() + index,
                            currency: curr,
                            rate: rate
                        });
                    }
                });

                setOtherRates(newOtherRates);
                if (mainRateSet || newOtherRates.length > 0) {
                    setShowExchangeRateInput(true);
                }
                if (!mainRateSet && activeCurrency !== "THB") {
                    setExchangeRate("");
                }
            } catch (err: any) {
                alert(`Error reading file: ${err.message}`);
            }
        };
        reader.readAsText(file);
        event.target.value = '';
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
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                    className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />

                {/* Currency Selector */}
                <label className="block text-gray-700 font-medium mb-2">
                    Currency
                </label>
                <div className="mb-4">
                    <div className="relative w-full">
                        <button
                            type="button"
                            onClick={() => setCurrencyPickerOpen(!currencyPickerOpen)}
                            className="w-full flex justify-between items-center cursor-pointer p-3 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <span className="text-gray-700">
                                {currency === "CUSTOM" && customCurrency.trim() !== ""
                                    ? `${customCurrency.toUpperCase()}`
                                    : currency === "CUSTOM"
                                    ? "Custom"
                                    : `${currency} (${getCurrencySymbol(currency)})`}
                            </span>
                            <span className="text-gray-500">{currencyPickerOpen ? "▲" : "▼"}</span>
                        </button>
                        {currencyPickerOpen && (
                            <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2">
                                {["THB", "USD", "JPY", "CUSTOM"].map((curr) => (
                                    <label
                                        key={curr}
                                        className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-blue-50 cursor-pointer"
                                    >
                                        <input
                                            type="radio" 
                                            name="currency"
                                            checked={currency === curr}
                                            onChange={() => {
                                                setCurrency(curr);
                                                if (curr !== "CUSTOM") {
                                                    setCustomCurrency("");
                                                }
                                                if (curr === "THB") {
                                                    setExchangeRate("");
                                                    setShowExchangeRateInput(false); 
                                                }
                                                setCurrencyPickerOpen(false);
                                            }}
                                            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300"
                                        />
                                        <span className="text-gray-700 text-sm">
                                            {curr === "CUSTOM"
                                                ? "Custom"
                                                : `${curr} (${getCurrencySymbol(curr)})`}
                                        </span>
                                    </label>
                                ))}
                            </div>
                        )}
                    </div>
                    {currency === "CUSTOM" && (
                        <input
                            type="text"
                            value={customCurrency}
                            onChange={(e) => setCustomCurrency(e.target.value.toUpperCase().slice(0, 3))}
                            placeholder="e.g., EUR, GBP"
                            maxLength={3}
                            className="w-full p-3 mt-2 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                    )}
                </div>

                {/* Checkbox */}
                {currency !== "THB" && (
                    <div className="mb-4">
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={showExchangeRateInput}
                                onChange={(e) => {
                                    setShowExchangeRateInput(e.target.checked);
                                    if (!e.target.checked) {
                                        setExchangeRate("");
                                    }
                                }}
                                className="w-4 h-4 text-blue-500 rounded focus:ring-0"
                            />
                            <span className="text-gray-700 font-medium">
                                Set Exchange Rate
                            </span>
                        </label>
                    </div>
                )}
                
                {/* Rate Manager UI */}
                {showExchangeRateInput && (
                    <div className="p-4 border rounded-xl bg-gray-50 mb-4">
                        {/* Input หลัก */}
                        <div className="mb-4">
                            <label className="block text-gray-700 font-medium mb-2">
                                Exchange Rate (1 {getActiveCurrency()} = ? THB)
                            </label>
                            <input
                                type="number"
                                value={exchangeRate}
                                onChange={(e) => setExchangeRate(e.target.value)}
                                placeholder="Enter rate for main currency"
                                className="w-full p-3 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                        </div>

                        <hr className="my-4"/>

                        <h3 className="text-lg font-medium text-gray-800 mb-3">
                            Rate Manager
                        </h3>
                        
                        {/* รายการ Rate อื่นๆ */}
                        <div className="space-y-3 mb-4">
                            {otherRates.map((item) => (
                                <div key={item.id} className="flex items-center gap-2">
                                    <input
                                        type="text"
                                        value={item.currency}
                                        onChange={(e) => handleOtherRateChange(item.id, 'currency', e.target.value)}
                                        placeholder="CUR"
                                        maxLength={3}
                                        className="w-1/4 p-2 border-none rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                    <input
                                        type="number"
                                        value={item.rate}
                                        onChange={(e) => handleOtherRateChange(item.id, 'rate', e.target.value)}
                                        placeholder="Rate"
                                        className="w-1/2 p-2 border-none rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                    <button
                                        type="button"
                                        onClick={() => handleRemoveRate(item.id)}
                                        className="w-1/4 bg-red-500 text-white text-sm py-2 rounded-lg hover:bg-red-600"
                                    >
                                        Remove
                                    </button>
                                </div>
                            ))}
                        </div>

                        {/* ปุ่ม Add */}
                        <button
                            type="button"
                            onClick={handleAddRate}
                            className="w-full bg-blue-500 text-white font-semibold py-2 rounded-lg hover:bg-blue-600 mb-3"
                        >
                            Add Other Rate
                        </button>
                        
                        {/* ปุ่ม Download / Upload */}
                        <div className="flex gap-3">
                            <button
                                type="button"
                                onClick={handleDownload}
                                className="w-1/2 bg-green-500 text-white font-semibold py-2 rounded-lg hover:bg-green-600"
                            >
                                Download
                            </button>
                            <button
                                type="button"
                                onClick={handleUploadClick}
                                className="w-1/2 bg-gray-600 text-white font-semibold py-2 rounded-lg hover:bg-gray-700"
                            >
                                Upload
                            </button>
                            {/* File Input ที่ซ่อนอยู่ */}
                            <input
                                type="file"
                                ref={fileInputRef}
                                onChange={handleFileChange}
                                accept=".json,application/json"
                                className="hidden"
                            />
                        </div>
                    </div>
                )}

                {/* Select Participants */}
                <div className="mb-6">
                    <button
                        type="button"
                        onClick={() => setPickerOpen(!pickerOpen)}
                        className="flex gap-3 items-center p-3 rounded-lg transition"
                    >
                        <span className="text-gray-700 font-medium">
                            Select Participants
                        </span>
                        <span className="text-gray-500">{pickerOpen ? "▲" : "▼"}</span>
                    </button>

                    {pickerOpen && (
                        <div className="mt-2 border rounded-xl p-3 bg-white shadow-sm">
                            {loadingMembers ? (
                                <p className="text-sm text-gray-500">Loading participants…</p>
                            ) : membersError ? (
                                <p className="text-sm text-red-600">{membersError}</p>
                            ) : participants.length === 0 ? (
                                <p className="text-sm text-gray-500">No participants.</p>
                            ) : (
                                participants.map((p) => {
                                    const id = Number(p.id);
                                    return (
                                        <label
                                            key={String(p.id)}
                                            className="flex items-center gap-2 mb-2 cursor-pointer"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={includedIds.includes(id)}
                                                onChange={() => toggleInclude(id)}
                                                className="w-4 h-4 text-blue-500 rounded focus:ring-0"
                                            />
                                            <span className="text-gray-700">{labelFor(p)}</span>
                                        </label>
                                    );
                                })
                            )}
                        </div>
                    )}
                </div>

                {/* Finish Button */}
                <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={saving}
                    className="w-full bg-blue-500 text-white font-bold py-3 rounded-xl hover:bg-blue-600 transition mb-8 disabled:opacity-60"
                >
                    {saving ? "SAVING…" : "FINISH"}
                </button>
            </div>

            <BottomNav activeTab={undefined} />
        </div>
    );
}