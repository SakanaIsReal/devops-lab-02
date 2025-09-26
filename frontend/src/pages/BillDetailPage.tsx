import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { getBillDetails } from '../utils/api';
import type { BillDetail } from '../types';

const getStatusStyle = (status: 'done' | 'pay' | 'check') => {
  switch (status) {
    case 'done':
      return { backgroundColor: '#52bf52' };
    case 'pay':
      return { backgroundColor: '#0d78f2' };
    case 'check':
      return { backgroundColor: '#efac4e' };
  }
};

type NavState = {
  bill?: any; // object ที่ backend คืนตอน POST /api/expenses
  ui?: {
    title?: string;
    amount?: number;
    payerUserId?: number | string;
    participants?: Array<{ id: number | string; name?: string; email?: string; imageUrl?: string }>;
    createdAt?: string;
  };
};

export const BillDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { billId } = useParams<{ billId: string }>();
  const location = useLocation() as { state?: NavState };

  // แปลง state ที่ส่งมาจากหน้า create → ให้กลายเป็น BillDetail เพื่อโชว์ทันที
  const initialBill: BillDetail | null = useMemo(() => {
    const nav = location.state;
    if (!nav?.bill && !nav?.ui) return null;

    const b = nav.bill ?? {};
    const ui = nav.ui ?? {};
    const title = b.title ?? ui.title ?? 'Expense';
    const amount = Number(b.amount ?? ui.amount ?? 0);
    const createdAt = (b.createdAt ?? ui.createdAt ?? new Date().toISOString()).toString();
    const payerUserId = b.payerUserId ?? ui.payerUserId;

    const participants = ui.participants ?? [];
    const payerName =
      participants.find(p => String(p.id) === String(payerUserId))?.name ??
      `User #${payerUserId ?? ''}`;

    const shareCount = participants.length || 1;
    const perShare = shareCount > 0 ? Math.round((amount / shareCount) * 100) / 100 : 0;

    const members: BillDetail['members'] = participants.map(p => ({
      avatar: p.imageUrl || 'https://placehold.co/80x80?text=User',
      name: p.name || (p.email ? p.email.split('@')[0] : `User #${p.id}`),
      amount: perShare,
      status: String(p.id) === String(payerUserId) ? 'done' : 'pay',
    }));

    return {
      id: b.id ?? billId ?? '',
      storeName: title,
      payer: payerName,
      date: createdAt.slice(0, 10),
      members,
    } as BillDetail;
  }, [location.state, billId]);

  const [bill, setBill] = useState<BillDetail | null>(initialBill);
  const [loading, setLoading] = useState<boolean>(!initialBill);
  const [error, setError] = useState<string | null>(null);

  // รีเฟรชจาก API ด้วย billId (ถ้ามี) เพื่อให้ข้อมูลจริงอัปเดตทีหลัง
  useEffect(() => {
    if (!billId) return;
    let cancelled = false;

    (async () => {
      try {
        const data = await getBillDetails(billId);
        if (!cancelled) {
          setBill(data);
          setError(null);
        }
      } catch (err: any) {
        if (!initialBill) setError(err?.message ?? 'Load failed');
      } finally {
        if (!initialBill && !cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
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
