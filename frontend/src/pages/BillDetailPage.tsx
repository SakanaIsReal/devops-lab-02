import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { getBillDetails, getExpenseSettlements, fetchUserProfiles, getExpensePayments } from '../utils/api';
import { useAuth } from '../contexts/AuthContext';
import type { BillDetail, Payment } from '../types';

const getStatusStyle = (s: 'done' | 'pay' | 'check' | 'pending') =>
  s === 'done' ? { backgroundColor: '#52bf52' } :
    s === 'pay' ? { backgroundColor: '#0d78f2' } :
      s === 'pending' ? { backgroundColor: '#ffc107' } :
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

  const [totalLeft, setTotalLeft] = useState<number>(0);

  const [bill, setBill] = useState<BillDetail | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // ... (useEffect for initial state from navigation)

  // Load data from API
  useEffect(() => {
    if (!expenseId || !user) return;
    let cancelled = false;

    (async () => {
      try {
        const [exp, allSettlements, expensePayments] = await Promise.all([
          getBillDetails(expenseId),
          getExpenseSettlements(expenseId),
          getExpensePayments(Number(expenseId))
        ]);

        if (cancelled) return;

        setPayments(expensePayments);

        const title = exp.title ?? exp.name ?? `Expense #${expenseId}`;
        const dateStr = String(exp.createdAt ?? exp.date ?? '').slice(0, 10);
        const payerUserId = exp.payerUserId ?? exp.payerId ?? exp.payer?.id;

        const isPayer = String(user.id) === String(payerUserId);

        const settlements = isPayer
          ? allSettlements.filter(s => String(s.userId) !== String(payerUserId))
          : allSettlements.filter(s => String(s.userId) === String(user.id));

        const debtorIds = settlements.map(s => Number(s.userId)).filter(n => Number.isFinite(n));
        const ids = Array.from(new Set([...debtorIds, Number(payerUserId)]));
        const profileMap = await fetchUserProfiles(ids);

        const mappedMembers = settlements.map((s, idx) => {
          const uid = Number(s.userId ?? s.memberId ?? s.user?.id ?? idx);
          const pendingPayment = expensePayments.find(p => p.fromUserId === uid && p.status === 'PENDING');

          const owed = Number(s.owedAmount ?? s.owed ?? s.share ?? s.amount ?? 0);
          const paid = Number(s.paidAmount ?? s.paid ?? 0);
          const remIn = s.remaining;
          const remaining = Number(remIn != null ? remIn : (owed - paid));

          const settledFlag =
            (typeof s.settled === 'boolean' ? s.settled : undefined)
            ?? (typeof s.status === 'string' ? s.status.toUpperCase() === 'SETTLED' : undefined)
            ?? (remaining <= 0);

          let status: 'done' | 'pay' | 'check' | 'pending' = settledFlag ? 'done' : 'pay';
          if (isPayer && pendingPayment) {
            status = 'pending';
          }

          const prof = profileMap.get(uid) || {};
          return {
            id: uid,
            name: prof.name || `User #${uid}`,
            avatar: prof.imageUrl || 'https://placehold.co/80x80?text=User',
            amount: Math.max(0, remaining),
            status: status,
            paymentId: pendingPayment?.id
          };
        });

        const payerProfile = payerUserId != null ? profileMap.get(Number(payerUserId)) : undefined;
        const payerName = payerProfile?.name || `User #${payerUserId}`;

        if (cancelled) return;

        setBill({
          id: exp.id ?? expenseId,
          storeName: title,
          payer: payerName,
          date: dateStr,
          members: mappedMembers as unknown as BillDetail['members'],
        });

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
  useEffect(() => {
    if (bill?.members) {
      const count = bill.members.filter((m: any) => m.status !== 'done').length;
      setTotalLeft(count);
    }
  }, [bill]);
  const handlePayClick = (status: string, memberId?: number | string, paymentId?: number) => {
    if (status === 'pay') {
      const paymentUserId = memberId ?? user?.id;
      if (!expenseId) { alert('Missing expense id'); return; }
      if (!paymentUserId) { alert('Missing user id for payment'); return; }
      navigate(`/pay/${expenseId}/${paymentUserId}`);
    } else if (status === 'pending' && paymentId) {
      navigate(`/expense/${expenseId}/payment/${paymentId}/confirm`);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-50 to-gray-100 flex flex-col">
      <Navbar />
      <div className="p-6 flex-grow pb-20">
        <CircleBackButton onClick={() => navigate(-1)} />

        {/* Header Section */}
        <div className="flex items-center justify-between mt-6 mb-6">
          <div>
            <h1 className="text-3xl font-bold text-[#2c4359] tracking-tight">Bill Detail</h1>
            <p className="text-slate-500 text-1xl mt-1">
              Transactions Left:{' '}
              <span className="font-semibold text-[#2c4359]">{totalLeft}</span>
            </p>
          </div>

        </div>

        {loading && (
          <div className="text-center text-slate-500 mt-10 text-lg animate-pulse">
            Loading...
          </div>
        )}

        {error && (
          <p className="text-red-500 text-center font-medium mt-10">Error: {error}</p>
        )}

        {bill && (
          <>
            {/* Bill Info */}
            <div className="bg-white/90 backdrop-blur-sm border border-slate-200 shadow-sm rounded-2xl p-5 mb-6">
              <div className="flex flex-col sm:flex-row sm:items-center  gap-5">
                <p className="text-lg font-semibold text-[#0c0c0c]">
                  Bill Name: <span className="font-normal">{bill.storeName}</span>
                </p>
               
                <p className="text-lg font-semibold text-[#0c0c0c]">
                  Payer: <span className="font-normal">{bill.payer}</span>
                </p>
                 <div className="bg-white shadow-sm rounded-xl px-4 py-2 text-sm font-medium text-slate-600 border border-slate-200">
                  Date : {bill?.date || '—'}
                </div>
              </div>
            </div>

            {/* Members Section */}
            <div className="space-y-4">
              {(bill.members as any[]).map((member: any, idx: number) => (
                <div
                  key={idx}
                  className="flex items-center justify-between bg-white/90 backdrop-blur-sm border border-slate-200
                           rounded-2xl p-4 shadow-sm hover:shadow-md transition duration-200"
                >
                  <div className="flex items-center space-x-4">
                    <div className="relative">
                      <img
                        src={member.avatar}
                        alt={member.name}
                        className="w-12 h-12 rounded-full border-2 border-white shadow-md"
                      />
                      <span
                        className={`absolute bottom-0 right-0 w-3.5 h-3.5 rounded-full border border-white ${member.status === 'done'
                          ? 'bg-green-500'
                          : member.status === 'pending'
                            ? 'bg-yellow-400'
                            : 'bg-blue-500'
                          }`}
                      />
                    </div>
                    <div>
                      <p className="font-semibold text-slate-800">{member.name}</p>
                      <p className="text-sm text-slate-500">
                        Total Left:{' '}
                        <span className="font-medium text-slate-700">
                          {member.amount.toFixed(2)}
                        </span>{' '}
                        Baht
                      </p>
                    </div>
                  </div>

                  <button
                    className={`min-w-[90px] px-4 py-2 font-semibold text-white rounded-xl shadow transition duration-200
                    ${member.status === 'done'
                        ? 'bg-green-500 hover:bg-green-600'
                        : member.status === 'pending'
                          ? 'bg-yellow-400 hover:bg-yellow-500 text-slate-800'
                          : 'bg-blue-500 hover:bg-blue-600'
                      }`}
                    onClick={() =>
                      handlePayClick(member.status, member.id, member.paymentId)
                    }
                  >
                    {member.status === 'done'
                      ? 'Done'
                      : member.status === 'pay'
                        ? 'Pay'
                        : member.status === 'pending'
                          ? 'Verify'
                          : 'Check'}
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      <BottomNav activeTab={undefined} />
    </div>
  );

};

export default BillDetailPage;