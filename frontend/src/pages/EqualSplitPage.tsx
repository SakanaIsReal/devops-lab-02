// src/pages/EqualSplitPage.tsx
import React, { useEffect, useMemo, useState, useRef } from "react"; // 1. เพิ่ม useRef
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

// ✅ 2. สร้าง Interface สำหรับรายการ Rate ที่เพิ่ม
interface OtherRate {
    id: number;
    currency: string;
    rate: string;
}

export default function EqualSplitPage() {
    // ... (State เดิม)
    const [expenseName, setExpenseName] = useState("");
    const [amount, setAmount] = useState("");
    const [pickerOpen, setPickerOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [currencyPickerOpen, setCurrencyPickerOpen] = useState(false);
    const [currency, setCurrency] = useState("THB");
    const [customCurrency, setCustomCurrency] = useState("");
    const [exchangeRate, setExchangeRate] = useState(""); 
    const [showExchangeRateInput, setShowExchangeRateInput] = useState(false); 

    // ✅ 3. เพิ่ม State ใหม่สำหรับฟีเจอร์นี้
    const [otherRates, setOtherRates] = useState<OtherRate[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);
    
    const navigate = useNavigate();
    const { user } = useAuth(); 

    // ... (ส่วน Logic เดิม: groupIdNum, participants, useEffect, toggleInclude, labelFor, getCurrencySymbol) ...
    // ... (คัดลอกส่วนนี้มาแปะได้เลย) ...
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

                const base = await getGroupMembers(String(groupIdNum));
                if (cancelled) return;

                const needIds = base
                    .filter((m) => !(m.name && `${m.name}`.trim()))
                    .map((m) => Number(m.id))
                    .filter((n) => Number.isFinite(n));

                let members: User[] = base;
                if (needIds.length) {
                    try {
                        const profMap = await fetchUserProfiles(needIds);
                        if (cancelled) return;
                        members = base.map((m: any) => {
                            const id = Number(m.id);
                            const prof = profMap.get(id);
                            return {
                                ...m,
                                name:
                                    prof?.name ||
                                    m.name ||
                                    (m.email?.split("@")[0] ?? `User #${id}`),
                                email: prof?.email || m.email || "",
                                imageUrl: prof?.imageUrl || m.imageUrl || "",
                            };
                        });
                    } catch {
                        members = base.map((m: any) => ({
                            ...m,
                            name: m.name || (m.email?.split("@")[0] ?? `User #${m.id}`),
                        }));
                    }
                }

                if (!cancelled) {
                    setParticipants(members);
                    setIncludedIds(
                        members
                            .map((m: any) => Number(m.id))
                            .filter((n) => Number.isFinite(n))
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

    const labelFor = (p: User) =>
        (p.name && p.name.trim()) ||
        (p as any).username ||
        (p as any).userName ||
        (p.email ? p.email.split("@")[0] : "") ||
        `User #${p.id}`;

    const getCurrencySymbol = (curr: string): string => {
        switch (curr.toUpperCase()) {
            case "THB": return "฿";
            case "USD": return "$";
            case "JPY": return "¥";
            default: return curr.toUpperCase();
        }
    };
    
    // ... (สิ้นสุดส่วน Logic เดิม) ...

    const getActiveCurrency = (): string => {
        if (currency === "CUSTOM" && customCurrency.trim() !== "") {
            return customCurrency.toUpperCase().slice(0, 3);
        }
        return currency;
    };

    // ... (handleSubmit ไม่เปลี่ยนแปลง) ...
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

        // --- Logic ตรวจสอบและคำนวณเรท ---
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
        // --- สิ้นสุด Logic ใหม่ ---

        setSaving(true);
        try {
            // ... (ส่วนที่เหลือของ handleSubmit เหมือนเดิม) ...
            const expensePayload = {
                groupId: groupIdNum,
                payerUserId,
                amount: amountInThb, 
                title: expenseName.trim(),
                type: 'EQUAL' as const, 
                status: 'SETTLED', 
                ...(rateNum !== undefined && { exchangeRate: rateNum }), 
            };
            const expense = await createExpenseApi(expensePayload);
            const expenseId = expense.id;
            const ItemName = expense.title;
            const ItemAmount = amount; 
            const itemCurrency = activeCurrency; 
            const createdItem = await createExpenseItem(expenseId, ItemName, ItemAmount, itemCurrency);
            const itemId = createdItem.id;
            const numberOfSharers = includedIds.length;
            const rawShareValue = amountInThb / numberOfSharers; 
            const shareValue = rawShareValue.toFixed(2); 
            for (const participantId of includedIds) {
                await createExpenseItemShare(
                    expenseId, 
                    itemId, 
                    participantId, 
                    shareValue,
                    undefined
                );
            }
            const billId = expense?.id ?? expense?.expenseId;
            alert("Expense successfully recorded!");
            const uiParticipants = participants
                .filter(p => includedIds.includes(Number(p.id)))
                .map(p => ({
                    id: Number(p.id),
                    name: labelFor(p),
                    email: p.email,
                    imageUrl: p.imageUrl,
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

    // --- ✅ 4. เพิ่ม Handlers ใหม่ทั้งหมด ---

    // 4.1. เพิ่มแถวใหม่
    const handleAddRate = () => {
        setOtherRates([
            ...otherRates,
            { id: Date.now(), currency: "", rate: "" }
        ]);
    };

    // 4.2. อัปเดตแถว
    const handleOtherRateChange = (id: number, field: 'currency' | 'rate', value: string) => {
        setOtherRates(otherRates.map(r => 
            r.id === id 
            ? { ...r, [field]: field === 'currency' ? value.toUpperCase().slice(0, 3) : value } 
            : r
        ));
    };

    // 4.3. ลบแถว
    const handleRemoveRate = (id: number) => {
        setOtherRates(otherRates.filter(r => r.id !== id));
    };

    // 4.4. ดาวน์โหลด
    const handleDownload = () => {
        const activeCurrency = getActiveCurrency();
        
        // เราจะใช้ Format {"USD": 36.5, "JPY": 0.25} ซึ่งเป็น JSON Standard
        const ratesToDownload: {[key: string]: number} = {};

        // เพิ่มเรทหลัก (ถ้ามี)
        if (activeCurrency !== "THB" && exchangeRate) {
            ratesToDownload[activeCurrency] = parseFloat(exchangeRate);
        }

        // เพิ่มเรทอื่นๆ
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

    // 4.5. สั่งคลิกปุ่ม Upload
    const handleUploadClick = () => {
        fileInputRef.current?.click();
    };

    // 4.6. เมื่อไฟล์ถูกเลือก
    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const json = JSON.parse(e.target?.result as string);
                
                // ตรวจสอบว่าไฟล์เป็น Object (เช่น {"USD": 36.5})
                if (typeof json !== 'object' || json === null || Array.isArray(json)) {
                    throw new Error("Invalid JSON format. Must be an object like {\"USD\": 36.5}");
                }

                const activeCurrency = getActiveCurrency();
                const newOtherRates: OtherRate[] = [];
                let mainRateSet = false;

                Object.keys(json).forEach((key, index) => {
                    const rate = String(json[key]);
                    const curr = key.toUpperCase();

                    // ถ้าสกุลเงินในไฟล์ ตรงกับสกุลเงินหลักที่เลือก -> ใส่ในช่องหลัก
                    if (curr === activeCurrency) {
                        setExchangeRate(rate);
                        mainRateSet = true;
                    } else {
                    // ถ้าไม่ตรง -> ใส่ใน "Other Rates"
                        newOtherRates.push({
                            id: Date.now() + index,
                            currency: curr,
                            rate: rate
                        });
                    }
                });

                setOtherRates(newOtherRates);
                
                // ถ้ามีเรทในไฟล์ (ไม่ว่าจะหลักหรือรอง) ให้ติ๊ก Checkbox อัตโนมัติ
                if (mainRateSet || newOtherRates.length > 0) {
                    setShowExchangeRateInput(true);
                }

                // ถ้าสกุลเงินหลัก (เช่น USD) ไม่ได้อยู่ในไฟล์ JSON ให้เคลียร์ช่องหลัก
                if (!mainRateSet && activeCurrency !== "THB") {
                    setExchangeRate("");
                }

            } catch (err: any) {
                alert(`Error reading file: ${err.message}`);
            }
        };
        reader.readAsText(file);

        // เคลียร์ค่า input เพื่อให้อัปโหลดไฟล์ชื่อเดิมซ้ำได้
        event.target.value = '';
    };

    // --- ✅ 5. อัปเดต UI (JSX) ---
    return (
        <div className="min-h-screen bg-white flex flex-col">
            {/* ... (Navbar, CircleBack, Headers, Expense Name, Total Amount) ... */}
            {/* ... (คัดลอกส่วนนี้มาแปะได้เลย) ... */}
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
            {/* ... (สิ้นสุดส่วนที่คัดลอก) ... */}

                {/* --- 🔽 UI Currency (เหมือนเดิม) 🔽 --- */}
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
                {/* --- 🔼 สิ้นสุด UI Currency 🔼 --- */}

                {/* --- 🔽 Checkbox (เหมือนเดิม) 🔽 --- */}
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
                
                {/* --- 🔽 Exchange Rate Input และ UI ใหม่ 🔽 --- */}
                {/* โชว์เมื่อ Checkbox ถูกติ๊ก */}
                {showExchangeRateInput && (
                    <div className="p-4 border rounded-xl bg-gray-50 mb-4">
                        {/* 1. Input หลัก */}
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
                        
                        {/* 2. รายการ Rate อื่นๆ */}
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

                        {/* 3. ปุ่ม Add */}
                        <button
                            type="button"
                            onClick={handleAddRate}
                            className="w-full bg-blue-500 text-white font-semibold py-2 rounded-lg hover:bg-blue-600 mb-3"
                        >
                            Add Other Rate
                        </button>
                        
                        {/* 4. ปุ่ม Download / Upload */}
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
                            {/* 5. File Input ที่ซ่อนอยู่ */}
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
                {/* --- 🔼 สิ้นสุด Exchange Rate Input 🔼 --- */}


                {/* Select Participants (เหมือนเดิม) */}
                <div className="mb-6">
                    {/* ... (Code เดิม) ... */}
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
                            ) : participants.length === 0 ?
(
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

                {/* Finish Button (เหมือนเดิม) */}
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