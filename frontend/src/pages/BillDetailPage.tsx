import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { getBillDetails, getExpenseSettlements, fetchUserProfiles } from '../utils/api';
import { useAuth } from '../contexts/AuthContext';
import type { BillDetail } from '../types';

const getStatusStyle = (s: 'done' | 'pay' | 'check') =>
  s === 'done' ? { backgroundColor: '#52bf52' } :
    s === 'pay' ? { backgroundColor: '#0d78f2' } :
      { backgroundColor: '#efac4e' };

type NavState = {
  bill?: any;
  ui?: {
    title?: string; amount?: number; payerUserId?: number | string;
    members?: Array<{ id: number | string; name?: string; amount?: number; imageUrl?: string; email?: string }>;
    participants?: Array<{ id: number | string; name?: string; email?: string; imageUrl?: string }>;
    createdAt?: string; billId?: string | number; groupId?: string | number;
  };
  groupId?: string | number;
};

const displayName = (o: { name?: string; email?: string; id?: string | number } = {}) =>
  o.name?.trim() || (o.email ? o.email.split('@')[0] : undefined) || (o.id != null ? `User #${o.id}` : 'User');

const sliceDate = (d?: string) => (d ? String(d).slice(0, 10) : '');

export const BillDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { billId: billIdFromUrl } = useParams<{ billId: string }>();
  const location = useLocation() as { state?: NavState };
  const { user } = useAuth();

  const expenseId = useMemo(() => {
    const id = location.state?.bill?.id ?? location.state?.ui?.billId ?? billIdFromUrl ?? '';
    return String(id).trim();
  }, [location.state, billIdFromUrl]);

  const [bill, setBill] = useState<BillDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // แสดงทันทีถ้ามี state ส่งมาจากหน้าก่อน
  useEffect(() => {
    const nav = location.state;
    if (!nav?.bill && !nav?.ui) { setBill(null); setLoading(true); return; }

    const b = nav.bill ?? {};
    const ui = nav.ui ?? {};
    const title = b.title ?? ui.title ?? 'Expense';
    const amount = Number(b.amount ?? ui.amount ?? 0);
    const createdAt = String(b.createdAt ?? ui.createdAt ?? '');
    const payerUserId = b.payerUserId ?? ui.payerUserId;

    const uiMembers = ui.members;
    const uiParticipants = ui.participants;

    let membersUI: any[] = [];
    if (uiMembers && uiMembers.length) {
      membersUI = uiMembers.map(m => ({
        id: (m as any).id,
        avatar: (m as any).imageUrl || 'https://placehold.co/80x80?text=User',
        name: displayName({ name: (m as any).name, email: (m as any).email, id: (m as any).id }),
        amount: Number((m as any).amount ?? 0) || 0,
        status: 'pay' as const,
      }));
    } else if (uiParticipants && uiParticipants.length) {
      const n = uiParticipants.length || 1;
      const perShare = n > 0 ? Math.round((amount / n) * 100) / 100 : 0;
      membersUI = uiParticipants.map(p => ({
        id: p.id,
        avatar: p.imageUrl || 'https://placehold.co/80x80?text=User',
        name: displayName({ name: p.name, email: p.email, id: p.id }),
        amount: perShare,
        status: 'pay' as const,
      }));
    }

    if (membersUI.length) {
      const payerName =
        displayName(membersUI.find(m => String(m.id) === String(payerUserId)) || { id: payerUserId });

      setBill({
        id: b.id ?? expenseId ?? '',
        storeName: title,
        payer: payerName,
        date: sliceDate(createdAt),
        members: membersUI as unknown as BillDetail['members'],
      });
      setLoading(false);
    } else {
      setBill(null);
      setLoading(true);
    }
  }, [location.state, expenseId]);

  // โหลดจริงจาก API: expense + settlements + โปรไฟล์ → map เป็น members
  useEffect(() => {
    if (!expenseId || !user) return;
    let cancelled = false;

    (async () => {
      try {
        const exp: any = await getBillDetails(expenseId); // /api/expenses/:id
        const title = exp.title ?? exp.name ?? `Expense #${expenseId}`;
        const dateStr = String(exp.createdAt ?? exp.date ?? '').slice(0, 10);
        const payerUserId = exp.payerUserId ?? exp.payerId ?? exp.payer?.id;

        const isPayer = String(user.id) === String(payerUserId);

        const allSettlements: any[] = await getExpenseSettlements(String(expenseId));

        const settlements = isPayer
            ? allSettlements.filter(s => String(s.userId) !== String(payerUserId)) // show others if payer
            : allSettlements.filter(s => String(s.userId) === String(user.id)); // show self if not payer

        const debtorIds = settlements.map(s => Number(s.userId)).filter(n => Number.isFinite(n));
        const ids = Array.from(new Set([...debtorIds, Number(payerUserId)]));
        const profileMap = await fetchUserProfiles(ids);

        const mappedMembers = settlements.map((s, idx) => {
          const uid = Number(s.userId ?? s.memberId ?? s.user?.id ?? idx);

          const owed = Number(s.owedAmount ?? s.owed ?? s.share ?? s.amount ?? 0);
          const paid = Number(s.paidAmount ?? s.paid ?? 0);
          const remIn = s.remaining;
          const remaining = Number(remIn != null ? remIn : (owed - paid));

          const settledFlag =
            (typeof s.settled === 'boolean' ? s.settled : undefined)
            ?? (typeof s.status === 'string' ? s.status.toUpperCase() === 'SETTLED' : undefined)
            ?? (remaining <= 0);

          const prof = profileMap.get(uid) || {};
          return {
            id: uid,
            name: prof.name || `User #${uid}`,
            avatar: prof.imageUrl || 'https://placehold.co/80x80?text=User',
            amount: Math.max(0, remaining),
            status: settledFlag ? ('done' as const) : ('pay' as const),
          };
        });


        const payerProfile = payerUserId != null ? profileMap.get(Number(payerUserId)) : undefined;
        const payerName =
          payerProfile?.name
          || mappedMembers.find(m => String(m.id) === String(payerUserId))?.name
          || (payerUserId != null ? `User #${payerUserId}` : '');

        if (cancelled) return;

        setBill(prev => ({
          id: exp.id ?? prev?.id ?? expenseId,
          storeName: title || prev?.storeName || '',
          payer: payerName || prev?.payer || '',
          date: dateStr || prev?.date || '',
          members: (mappedMembers.length
            ? (mappedMembers as unknown as BillDetail['members'])
            : (prev?.members as BillDetail['members']) || []),
        }));

        setError(null);
        setLoading(false);
      } catch (err: any) {
        if (!cancelled) {
          setError(err?.message ?? 'Load failed');
          setLoading(false);
        }
      }
    })();

    return () => { cancelled = true; };
  }, [expenseId, user]);

  const handlePayClick = (status: 'done' | 'pay' | 'check', memberId?: number | string) => {
    if (status !== 'pay') return;
    const paymentUserId = memberId ?? user?.id;
    if (!expenseId) { alert('ไม่พบ bill/expense id'); return; }
    if (!paymentUserId) { alert('ไม่พบ user id สำหรับการชำระ'); return; }
    navigate(`/pay/${expenseId}/${paymentUserId}`);
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
              <p className="text-lg font-semibold text-[#0c0c0c]">ร้าน: {bill.storeName}</p>
              <p className="text-lg font-semibold ml-8 text-[#0c0c0c]">Payer: {bill.payer}</p>
            </div>
            <p className="text-lg font-semibold mb-4 text-[#0c0c0c]">Date: {bill.date}</p>

            {(bill.members as any[]).map((member: any, idx: number) => (
              <div key={idx} className="bg-white p-3 rounded-lg shadow-lg flex items-center justify-between mb-4">
                <div className="flex items-center">
                  <img src={member.avatar} alt="avatar" className="w-12 h-12 rounded-full mr-3" />
                  <div>
                    <p className="font-semibold">{member.name}</p>
                    <p className="text-sm text-[#628fa6]">Pay : {member.amount} Bath</p>
                  </div>
                </div>
                <div className="flex justify-center">
                  <button
                    className="w-24 text-center px-4 py-2 rounded-lg text-white font-bold"
                    style={getStatusStyle(member.status)}
                    onClick={() => handlePayClick(member.status, member.id)}
                  >
                    {member.status === 'done' ? 'Done' : member.status === 'pay' ? 'Pay' : 'Check'}
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

export default BillDetailPage;