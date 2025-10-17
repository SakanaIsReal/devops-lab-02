// src/pages/EqualSplitPage.tsx
import React, { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import { BottomNav } from "../components/BottomNav";
import CircleBackButton from "../components/CircleBackButton";
import { useAuth } from '../contexts/AuthContext';
import { 
    getGroupMembers, 
    fetchUserProfiles, 
    createExpenseApi, // ⬅️ ใช้ API นี้
    createExpenseItem, 
    createExpenseItemShare 
} from "../utils/api";
import type { User } from "../types";

export default function EqualSplitPage() {
    // ฟอร์มพื้นฐาน
    const [expenseName, setExpenseName] = useState("");
    const [amount, setAmount] = useState("");
    const [pickerOpen, setPickerOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const navigate = useNavigate();
    const { user } = useAuth(); // ใช้เป็น payerUserId

    // รับ groupId ได้ทั้งจาก URL / และ state
    const { id: idParam } = useParams<{ id?: string }>();
    const location = useLocation() as {
        state?: { group?: { id?: number | string }; groupId?: number | string };
    };

    // ✅ resolve groupId ให้ชัด และบังคับเป็น number เสมอ
    const groupIdNum: number | undefined = useMemo(() => {
        const fromState = location.state?.group?.id ?? location.state?.groupId;
        const raw = idParam ?? (fromState != null ? String(fromState) : undefined);
        if (raw == null) return undefined;
        const n = Number(raw);
        return Number.isFinite(n) ? n : undefined;
    }, [idParam, location.state]);

    // รายชื่อสมาชิกจาก API
    const [participants, setParticipants] = useState<User[]>([]);
    const [includedIds, setIncludedIds] = useState<number[]>([]);
    const [loadingMembers, setLoadingMembers] = useState<boolean>(false);
    const [membersError, setMembersError] = useState<string | null>(null);

    // โหลดสมาชิก + เติมโปรไฟล์ถ้าชื่อหาย
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
                    // ✅ ค่าเริ่มต้น: เลือก "ทุกคน" เป็นผู้ร่วมจ่าย
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

    // สลับเลือกผู้ร่วมจ่าย
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

    const handleSubmit = async () => {
        if (!groupIdNum) { alert("ไม่พบ groupId"); return; }

        const amountNum = Number(amount);
        if (!Number.isFinite(amountNum) || amountNum <= 0) { alert("ใส่ยอดให้ถูกต้อง"); return; }
        if (!expenseName.trim()) { alert("กรอกชื่อรายการก่อน"); return; }
        // ใน Equal Split, includedIds คือทุกคนที่หารร่วม, ซึ่งรวมคนจ่ายด้วย
        if (includedIds.length === 0) { alert("ต้องมีผู้ร่วมจ่ายอย่างน้อย 1 คน"); return; } 
        if (!user || !Number.isFinite(Number(user.id))) { alert("ข้อมูลผู้ใช้ไม่ถูกต้อง"); return; }


        const payerUserId = Number(user.id);

        setSaving(true);
        try {
            // 1. สร้าง Expense หลัก (ใช้ createExpenseApi แทน createBill)
            const expensePayload = {
                groupId: groupIdNum,
                payerUserId,
                amount: amountNum,
                title: expenseName.trim(),
                type: 'EQUAL' as const, // กำหนด type เป็น 'EQUAL'
                status: 'SETTLED', // สมมติว่า Settled เสมอสำหรับการสร้าง
            };

            const expense = await createExpenseApi(expensePayload);
            const expenseId = expense.id;

            // 2. สร้าง Expense Item
            const ItemName = expense.title;
            const ItemAmount = expense.amount;
            const createdItem = await createExpenseItem(expenseId, ItemName, ItemAmount);
            const itemId = createdItem.id;

            // 3. คำนวณส่วนแบ่งต่อคน (รวมคนจ่ายด้วย)
            const numberOfSharers = includedIds.length;
            const rawShareValue = amountNum / numberOfSharers;
            // ปัดเศษให้มีทศนิยม 2 ตำแหน่ง
            const shareValue = rawShareValue.toFixed(2); 

            // 4. สร้าง Expense Item Share สำหรับทุกคนที่ร่วมจ่าย
            for (const participantId of includedIds) {
                // สำหรับ Equal Split เราจะสร้าง Expense Item Share ให้ทุกคนรวมถึงคนจ่ายด้วย
                // แต่ถ้า logic ของ Backend ต้องการแค่คนอื่นที่ไม่ใช่คนจ่าย ให้กรองออก:
                // if (participantId === payerUserId) continue; // ถ้าไม่ต้องการให้คนจ่ายมี share

                await createExpenseItemShare(
                    expenseId, 
                    itemId, 
                    participantId, 
                    shareValue,
                    undefined // ไม่ใช้ percentage
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

    // ... ส่วน UI (ไม่มีการเปลี่ยนแปลง) ...
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
                                                checked={includedIds.includes(id)} // ✅ ติ๊ก = ร่วมจ่าย
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