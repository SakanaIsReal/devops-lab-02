import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { getBillDetails } from '../utils/api';
import type { BillDetail } from '../types';

const getStatusStyle = (status: 'done' | 'pay' | 'check') => {
  switch (status) {
    case 'done':  return { backgroundColor: '#52bf52' };
    case 'pay':   return { backgroundColor: '#0d78f2' };
    case 'check': return { backgroundColor: '#efac4e' };
  }
};

// ---------- helper types ----------
type BillMember = BillDetail['members'][number];

type NavState = {
  bill?: any; // response จาก POST /api/expenses
  ui?: {
    title?: string;
    amount?: number;
    payerUserId?: number | string;
    // manual split ส่งยอดต่อคนมา
    members?: Array<{ id: number | string; name?: string; amount: number; imageUrl?: string; email?: string }>;
    // equal split เดิม ส่งรายชื่ออย่างเดียว
    participants?: Array<{ id: number | string; name?: string; email?: string; imageUrl?: string }>;
    createdAt?: string;
  };
};

export const BillDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { billId } = useParams<{ billId: string }>();
  const location = useLocation() as { state?: NavState };

  // สร้างบิลตั้งต้นจาก state ที่ส่งมาหน้า previous (แสดงผลได้ทันที)
  const initialBill: BillDetail | null = useMemo(() => {
    const nav = location.state;
    if (!nav?.bill && !nav?.ui) return null;

    const b  = nav.bill ?? {};
    const ui = nav.ui  ?? {};

    const title     = b.title ?? ui.title ?? 'Expense';
    const amount    = Number(b.amount ?? ui.amount ?? 0);
    const createdAt = String(b.createdAt ?? ui.createdAt ?? new Date().toISOString());
    const payerId   = b.payerUserId ?? ui.payerUserId;

    const uiMembers      = ui.members;
    const uiParticipants = ui.participants;

    // helper หา display name จาก id
    const findNameById = (id?: number | string) => {
      if (id == null) return undefined;
      const hitM = uiMembers?.find(m => String(m.id) === String(id));
      if (hitM) return hitM.name || (hitM.email ? hitM.email.split('@')[0] : `User #${hitM.id}`);
      const hitP = uiParticipants?.find(p => String(p.id) === String(id));
      if (hitP) return hitP.name || (hitP.email ? hitP.email.split('@')[0] : `User #${hitP.id}`);
      return `User #${id}`;
    };

    let members: BillDetail['members'] = [];

    if (uiMembers && uiMembers.length) {
      // ✅ Manual Split: ใช้ยอดที่คำนวณมาแล้ว
      members = uiMembers.map(m => ({
        avatar: m.imageUrl || 'https://placehold.co/80x80?text=User',
        name: m.name || (m.email ? m.email.split('@')[0] : `User #${m.id}`),
        amount: Number(m.amount) || 0,
        status: 'pay',
      }));
    } else if (uiParticipants && uiParticipants.length) {
      // ✅ Equal Split: แบ่งเท่ากัน
      const shareCount = uiParticipants.length || 1;
      const perShare = shareCount > 0 ? Math.round((amount / shareCount) * 100) / 100 : 0;
      members = uiParticipants.map(p => ({
        avatar: p.imageUrl || 'https://placehold.co/80x80?text=User',
        name: p.name || (p.email ? p.email.split('@')[0] : `User #${p.id}`),
        amount: perShare,
        status: 'pay',
      }));
    } else {
      // ไม่มีข้อมูลพอ — รอโหลดจาก API
      return null;
    }

    const payerName = findNameById(payerId);

    return {
      id: b.id ?? billId ?? '',
      storeName: title,
      payer: payerName ?? '',
      date: createdAt.slice(0, 10),
      members,
    } as BillDetail;
  }, [location.state, billId]);

  const [bill, setBill] = useState<BillDetail | null>(initialBill);
  const [loading, setLoading] = useState<boolean>(!initialBill);
  const [error, setError] = useState<string | null>(null);

  // โหลดข้อมูลจริงจาก API แล้ว normalize ให้ status เป็น 'pay' ทุกคน
  useEffect(() => {
    if (!billId) return;
    let cancelled = false;

    (async () => {
      try {
        const data = await getBillDetails(billId);

        const normalized: BillDetail = {
          ...data,
          members: (data.members ?? []).map(
            (m: BillMember) => ({ ...m, status: 'pay' as const })
          ),
        };

        if (!cancelled) {
          setBill(normalized);
          setError(null);
        }
      } catch (err: any) {
        if (!initialBill && !cancelled) setError(err?.message ?? 'Load failed');
      } finally {
        if (!initialBill && !cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [billId, initialBill]);

  const handlePayClick = (status: 'done' | 'pay' | 'check') => {
    if (status === 'pay' && bill) navigate(`/pay/${bill.id}`);
  };

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <Navbar />
      <div className="p-4 flex-grow pb-16">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div className="flex items-center justify-between mt-4 mb-6">
          <h1 className="text-2xl font-bold text-[#2c4359]">Bill Detail</h1>
        </div>

        {loading && <p>Loading...</p>}
        {error && <p className="text-red-500">Error: {error}</p>}

        {bill && (
          <>
            <div className="flex items-center">
              <p className="text-lg font-semibold" style={{ color: '#0c0c0c' }}>
                ร้าน: {bill.storeName}
              </p>
              <p className="text-lg font-semibold ml-8" style={{ color: '#0c0c0c' }}>
                Payer: {bill.payer}
              </p>
            </div>
            <p className="text-lg font-semibold mb-4" style={{ color: '#0c0c0c' }}>
              Date: {bill.date}
            </p>

            {bill.members.map((member, idx) => (
              <div
                key={idx}
                className="bg-white p-3 rounded-lg shadow-lg flex items-center justify-between mb-4"
              >
                <div className="flex items-center">
                  <img
                    src={member.avatar}
                    alt="avatar"
                    className="w-12 h-12 rounded-full mr-3"
                  />
                  <div>
                    <p className="font-semibold">{member.name}</p>
                    <p className="text-sm" style={{ color: '#628fa6' }}>
                      Pay : {member.amount} Bath
                    </p>
                  </div>
                </div>
                <div className="flex justify-center">
                  <button
                    className="w-24 text-center px-4 py-2 rounded-lg text-white font-bold"
                    style={getStatusStyle(member.status)}
                    onClick={() => handlePayClick(member.status)}
                  >
                    {member.status === 'done'
                      ? 'Done'
                      : member.status === 'pay'
                      ? 'Pay'
                      : 'Check'}
                  </button>
                </div>
              </div>
            ))}
          </>
        )}
      </div>
      <BottomNav activeTab={undefined} />
    </div>
  );
};