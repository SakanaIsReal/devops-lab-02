import React, { useState, useEffect } from "react";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import { BottomNav } from "../components/BottomNav";
import CircleBackButton from "../components/CircleBackButton";
import { useAuth } from "../contexts/AuthContext";

import {
  getGroupMembers,
  getUserInformation,
  createBill, // ใช้ /api/expenses ตามที่คุยกัน
} from "../utils/api";

type SplitMethod = "equal" | "percentage";

type ExpenseItem = {
  name: string;
  amount: string;
  sharedWith: number[];
  splitMethod: SplitMethod;
  percentages: { [personId: number]: number };
};

interface Participant {
  id: number;
  name: string;
  imageUrl?: string;
  email?: string;
}

export default function ManualSplitPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { id: routeGroupId } = useParams<{ id: string }>();
  const groupId = routeGroupId ?? (location.state as any)?.groupId;

  const { user } = useAuth();

  const [expenseName, setExpenseName] = useState("");
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [items, setItems] = useState<ExpenseItem[]>([
    { name: "", amount: "", sharedWith: [], splitMethod: "equal", percentages: {} },
  ]);

  useEffect(() => {
    const pickName = (p: any, fallbackId?: number | string) =>
      p?.userName ??
      p?.name ??
      p?.displayName ??
      p?.username ??
      (p?.email ? String(p.email).split("@")[0] : fallbackId != null ? `User #${fallbackId}` : "User");

    const fetchParticipants = async () => {
      if (!groupId) return;
      try {
        const members = await getGroupMembers(groupId);
        // เติมโปรไฟล์รายคน (ถ้ามี API)
        const profiles = await Promise.all(
          members.map((m: any) => getUserInformation(m.id).catch(() => ({})))
        );
        const fetched = members.map((m: any, i: number) => {
          const prof: any = profiles[i] || {};
          return {
            id: Number(m.id),
            name: pickName(prof, m.id),
            imageUrl: prof?.avatarUrl ?? prof?.imageUrl ?? "",
            email: prof?.email ?? "",
          } as Participant;
        });
        setParticipants(fetched);
      } catch (err) {
        console.error("Failed to fetch group members:", err);
      }
    };
    fetchParticipants();
  }, [groupId]);

  // มีรายการที่กรอกครบอย่างน้อย 1 รายการหรือไม่
  const hasValidItems = () =>
    items.some((item) => {
      const hasName = item.name.trim() !== "";
      const hasAmount = parseFloat(item.amount || "0") > 0;
      const hasParticipants = item.sharedWith.length > 0;

      if (item.splitMethod === "percentage") {
        const totalPercentage = Object.values(item.percentages).reduce((s, p) => s + p, 0);
        return hasName && hasAmount && hasParticipants && totalPercentage === 100;
      }
      return hasName && hasAmount && hasParticipants;
    });

  const handleSubmit = async () => {
    if (!hasValidItems()) {
      alert("Please add at least one complete item with all required information.");
      return;
    }
    if (!groupId) {
      alert("ไม่พบ groupId");
      return;
    }

    // รวมยอดต่อคนจากทุก item
    const totalsById: Record<number, number> = {};
    participants.forEach((p) => (totalsById[p.id] = 0));

    for (const item of items) {
      const itemAmount = parseFloat(item.amount || "0");
      if (!item.name.trim() || itemAmount <= 0 || item.sharedWith.length === 0) continue;

      if (item.splitMethod === "equal") {
        const per = itemAmount / item.sharedWith.length;
        item.sharedWith.forEach((id) => {
          totalsById[id] = (totalsById[id] ?? 0) + per;
        });
      } else if (item.splitMethod === "percentage") {
        const totalPct = Object.values(item.percentages).reduce((s, v) => s + v, 0);
        if (totalPct !== 100) {
          alert(`Percentages must total exactly 100% for item: ${item.name}`);
          return;
        }
        item.sharedWith.forEach((id) => {
          const pct = item.percentages[id] || 0;
          totalsById[id] = (totalsById[id] ?? 0) + (itemAmount * pct) / 100;
        });
      }
    }

    const activeIds = Object.keys(totalsById)
      .map(Number)
      .filter((id) => (totalsById[id] ?? 0) > 0);

    if (activeIds.length === 0) {
      alert("ไม่มีผู้ร่วมจ่าย");
      return;
    }

    const totalAmount = Math.round(activeIds.reduce((s, id) => s + totalsById[id], 0) * 100) / 100;

    // ผู้ชำระ: ใช้ user ปัจจุบัน; ถ้าไม่มีให้ใช้คนแรกที่มียอด
    const payerUserId = Number(user?.id ?? activeIds[0]);

    try {
      // สร้าง expense จริง (POST /api/expenses)
      const expense = await createBill({
        groupId,
        payerUserId,
        amount: totalAmount,
        title: expenseName.trim() || "Manual Split",
        type: "CUSTOM",
        status: "SETTLED",
      });

      const billId = expense?.id ?? expense?.expenseId;
      if (!billId) {
        alert("สร้างบิลสำเร็จ แต่ไม่พบ billId");
        return;
      }

      // ส่งรายละเอียดยอดต่อคนไปให้ Bill Detail โชว์ทันที
      const uiMembers = activeIds.map((id) => {
        const p = participants.find((pp) => pp.id === id);
        return {
          id,
          name: p?.name ?? `User #${id}`,
          amount: Math.round((totalsById[id] ?? 0) * 100) / 100,
          imageUrl: p?.imageUrl ?? "",
        };
      });

      navigate(`/bill/${billId}`, {
        state: {
          bill: expense,
          ui: {
            title: expenseName.trim() || "Manual Split",
            amount: totalAmount,
            payerUserId,
            members: uiMembers, // ให้ BillDetail แสดงยอดตามนี้
            createdAt: expense?.createdAt ?? new Date().toISOString(),
          },
        },
      });
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.response?.data?.error || "";
      alert(`สร้างบิลไม่สำเร็จ: ${e?.response?.status ?? "ERR"}${msg ? `\n${msg}` : ""}`);
    }
  };

  const handleBack = () => navigate(-1);

  const addItem = () =>
    setItems((prev) => [
      ...prev,
      { name: "", amount: "", sharedWith: [], splitMethod: "equal", percentages: {} },
    ]);

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
      newItems[itemIndex].sharedWith = currentShareWith.filter((id) => id !== personId);
      const next = { ...item.percentages };
      delete next[personId];
      newItems[itemIndex].percentages = next;
    } else {
      newItems[itemIndex].sharedWith = [...currentShareWith, personId];
      if (item.splitMethod === "percentage") {
        const defaultPct = Math.floor(100 / (currentShareWith.length + 1));
        newItems[itemIndex].percentages = { ...item.percentages, [personId]: defaultPct };
      }
    }
    setItems(newItems);
  };

  const updatePercentage = (itemIndex: number, personId: number, percentage: number) => {
    const newItems = [...items];
    newItems[itemIndex].percentages = { ...newItems[itemIndex].percentages, [personId]: percentage };
    setItems(newItems);
  };

  const changeSplitMethod = (itemIndex: number, method: SplitMethod) => {
    const newItems = [...items];
    const item = newItems[itemIndex];
    newItems[itemIndex].splitMethod = method;

    if (method === "percentage" && item.sharedWith.length > 0) {
      const equalPct = Math.floor(100 / item.sharedWith.length);
      const pct: { [k: number]: number } = {};
      item.sharedWith.forEach((id) => (pct[id] = equalPct));
      newItems[itemIndex].percentages = pct;
    } else {
      newItems[itemIndex].percentages = {};
    }
    setItems(newItems);
  };

  return (
    <div className="h-screen bg-white flex flex-col overflow-hidden">
      <Navbar />
      <div className="flex-1 overflow-y-auto pt-4 mt-5 pb-20 px-4 sm:px-6">
        <CircleBackButton onClick={handleBack} className="border-b border-gray-200" iconClassName="text-blue-600" />

        <div className="flex flex-col justify-center items-start mb-4 mt-4">
          <h1 className="text-xl sm:text-2xl font-bold text-gray-800 mb-1">Expense Management</h1>
          <p className="text-base sm:text-lg font-bold text-gray-700">Method : Manual Split</p>
        </div>

        {/* Expense Name */}
        <label className="block text-gray-700 font-medium mb-2">Expense Name</label>
        <input
          type="text"
          value={expenseName}
          onChange={(e) => setExpenseName(e.target.value)}
          placeholder="Enter your expense name (e.g. Dinner)"
          className="w-full p-3 mb-4 border-none rounded-xl bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />

        {/* Total Expense */}
        <label className="text-gray-700 font-medium">Total Expense</label>
        <div className="w-full p-3 mb-4 mt-2 border-none rounded-xl bg-gray-100 flex items-center gap-5">
          <span className="text-lg">
            ฿{items.reduce((acc, item) => acc + parseFloat(item.amount || "0"), 0).toFixed(2)}
          </span>
        </div>

        {/* Items */}
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
                    className={`px-3 py-1 rounded-lg text-sm font-medium transition ${
                      item.splitMethod === "equal" ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                    }`}
                  >
                    Equal
                  </button>
                  <button
                    onClick={() => changeSplitMethod(index, "percentage")}
                    className={`px-3 py-1 rounded-lg text-sm font-medium transition ${
                      item.splitMethod === "percentage" ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-700 hover:bg-gray-200"
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

              {/* Participants (shared with) */}
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
                            : "Participants"}
                        </span>
                      </summary>
                      <div className="absolute left-0 right-0 mt-2 w-full bg-white border rounded-lg shadow-lg z-10 p-2">
                        {participants
                          .filter((p) => p.id !== Number(user?.id)) // ไม่รวมตัวเอง
                          .map((p) => (
                            <label key={p.id} className="flex items-center gap-2 px-2 py-1 rounded-lg hover:bg-blue-50 cursor-pointer">
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

              {/* Percentage details */}
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
                          onChange={(e) => updatePercentage(index, person.id, parseInt(e.target.value) || 0)}
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

        {/* FINISH */}
        <button
          onClick={handleSubmit}
          disabled={!hasValidItems()}
          className={`w-full font-bold py-3 rounded-xl transition mb-8 ${
            hasValidItems() ? "bg-blue-500 text-white hover:bg-blue-600" : "bg-gray-300 text-gray-500 cursor-not-allowed"
          }`}
        >
          FINISH
        </button>
      </div>
      <BottomNav activeTab={undefined} />
    </div>
  );
}
