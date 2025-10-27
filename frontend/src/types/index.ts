export interface User {
    id: string;
    email: string;
    name: string;
    phone: string;
    imageUrl: string;
    qrCodeUrl : string;
}

export interface AuthContextType {
    user: User | null;
    login: (email: string, password: string) => Promise<void>;
    logout: () => void;
    isLoading: boolean;
    updateUser: (user: User) => void;
}

export interface Group {
  id: string;
  name: string;
  participantCount: number;
  imageUrl: string;
  ownerUserId?: number;
  coverImageUrl?: string;
  memberCount?: number;
}

export interface BillMember {
  name: string;
  amount: number;
  status: 'done' | 'pay' | 'check';
  avatar: string;
}

export interface BillDetail {
  id: string;
  storeName: string;
  payer: string;
  date: string;
  members: BillMember[];
}

export interface Bill extends BillDetail {
  groupId: string;
  name: string;
  status: 'pending' | 'completed';
}

export interface UserUpdateForm {
    userName : string ;
    email: string ;
    phone: string ;
    avatar: File | string ;
    qr: File | string ;
}

export interface Expense {
    id: number;
    groupId: number;
    payerUserId: number;
    amount: number;
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
    title: string;
    status: "SETTLED" | "OPEN" | "CANCELED";
    createdAt: string;
}

export interface Transaction {
    id: number;
    groupId: number;
    payerUserId: number;
    amount: number;
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
    title: string;
    status: "SETTLED" | "OPEN" | "CANCELED";
    createdAt: string;
    // For compatibility with existing components
    name?: string;
    payer?: string;
    date?: string;
}

export interface Settlement {
  expenseId: number;
  userId: number;
  owedAmount: number;
  paidAmount: number;
  settled: boolean;
  remaining: number;
}

export interface PaymentDetails {
  transactionId: string;
  payerName: string;
  amountToPay: number;
  qrCodeUrl: string;
  phone?: string;
  // Add settlement-specific fields
  expenseId?: number;
  userId?: number;
  owedAmount?: number;
  paidAmount?: number;
  settled?: boolean;
  remaining?: number;
}

export interface Payment {
  id: number;
  expenseId: number;
  fromUserId: number;
  amount: number;
  status: "PENDING" | "VERIFIED" | "REJECTED";
  createdAt: string;
  verifiedAt: string | null;
  receiptId: number | null;
  receiptFileUrl: string | null;
}

export interface Balance {
  direction: "OWES_YOU" | "YOU_OWE";
  counterpartyUserId: number;
  counterpartyUserName: string;
  counterpartyAvatarUrl: string | null;
  groupId: number;
  groupName: string;
  expenseId: number;
  expenseTitle: string;
  remaining: number;
  status: string;
}