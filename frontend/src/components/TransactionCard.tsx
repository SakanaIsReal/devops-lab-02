// src/components/TransactionCard.tsx
import React from 'react';
import { useNavigate } from 'react-router-dom';

interface TransactionCardProps {
  transaction: {
    id: number | string;               // <-- ต้องมี
    name: string;
    payer?: string;
    date?: string;                     // แนะนำเป็น createdAt ISO ถ้ามี
    amount?: number;
    status?: 'settled' | 'open' | 'canceled' | 'pending' | 'completed' | string;
    type?: 'EQUAL' | 'PERCENTAGE' | 'CUSTOM';
    // ถ้ามี ให้ส่งมาด้วย จะทำให้หน้า BillDetail แสดงเร็วขึ้น
    payerUserId?: number | string;
    createdAt?: string;
    members?: Array<{
      id?: number | string;
      userId?: number | string;
      name?: string;
      email?: string;
      imageUrl?: string;
      avatar?: string;
      amount?: number;
    }>;
    participants?: Array<{
      id: number | string;
      name?: string;
      email?: string;
      imageUrl?: string;
    }>;
    groupId?: number | string;        // ถ้าคุณมี (บาง API ต้องใช้)
  };
}

const statusColors: Record<string, string> = {
  settled: 'bg-green-200 text-green-800',
  open: 'bg-yellow-200 text-yellow-800',
  canceled: 'bg-red-200 text-red-800',
  pending: 'bg-yellow-200 text-yellow-800',
  completed: 'bg-green-200 text-green-800',
};

const TransactionCard: React.FC<TransactionCardProps> = ({ transaction }) => {
  const navigate = useNavigate();

  const displayStatus = (() => {
    switch (transaction.status) {
      case 'SETTLED': return 'completed';
      case 'OPEN': return 'pending';
      case 'CANCELED': return 'canceled';
      default: return (transaction.status as any) ?? 'pending';
    }
  })();

  const handleNavigate = () => {
  if (transaction.id == null) {
    alert('ไม่พบรหัสบิล/ค่าใช้จ่าย');
    return;
  }
  const billId = String(transaction.id);
  console.debug('[TransactionCard] navigate billdetail ->', { billId, tx: transaction });

  navigate(`/bill/${billId}`, {
    state: {
      bill: {
        id: billId,
        title: transaction.name,
        amount: transaction.amount ?? 0,
        createdAt: transaction.createdAt ?? transaction.date ?? '',
        payerUserId: transaction.payerUserId,
        groupId: transaction.groupId,
        members: transaction.members,
        participants: transaction.participants,
      },
      ui: {
        billId,
        title: transaction.name ?? 'Expense',
        amount: transaction.amount ?? 0,
        createdAt: transaction.createdAt ?? transaction.date ?? '',
        payerUserId: transaction.payerUserId,
        groupId: transaction.groupId,
        members: transaction.members?.map(m => ({
          id: m.id ?? m.userId,
          name: m.name,
          email: m.email,
          imageUrl: m.avatar ?? m.imageUrl,
          amount: m.amount,
        })),
        participants: transaction.participants?.map(p => ({
          id: p.id,
          name: p.name,
          email: p.email,
          imageUrl: p.imageUrl,
        })),
      },
      groupId: transaction.groupId,
    },
  });
};

  return (
    <div className="bg-white rounded-lg shadow-sm p-4 flex items-center justify-between">
      <div className="flex-1">
        <h3 className="text-lg font-semibold">{transaction.name}</h3>
        {transaction.payer && <p className="text-gray-600">Paid by {transaction.payer}</p>}
        {transaction.amount != null && (
          <p className="text-gray-500 text-sm">Amount: {transaction.amount} ฿</p>
        )}
        {transaction.date && <p className="text-gray-500 text-sm">{transaction.date}</p>}
      </div>
      <div className="flex items-center space-x-4">
        {displayStatus && (
          <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[displayStatus] || ''}`}>
            {displayStatus}
          </span>
        )}
        <button
          onClick={handleNavigate}
          className="px-4 py-2 bg-gray-900 text-white rounded-md text-sm font-medium"
        >
          Detail
        </button>
      </div>
    </div>
  );
};

export default TransactionCard;
