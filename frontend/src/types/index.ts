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
}

export interface PaymentDetails {
  transactionId: string;
  payerName: string;
  amountToPay: number;
  qrCodeUrl: string;
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

// Update your types file (../types.ts)

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

export interface Group {
  id: string;
  name: string;
  participantCount: number;
  imageUrl: string;
  ownerUserId?: number;
  coverImageUrl?: string;
  memberCount?: number;
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

export interface BillDetail {
  id: string;
  storeName: string;
  payer: string;
  date: string;
  members: BillMember[];
}

export interface UserUpdateForm {
    userName : string ;
    email: string ;
    phone: string ;
    avatar: File | string ;
    qr: File | string ;
}

// Update your types file (../types.ts)

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

export interface Group {
  id: string;
  name: string;
  participantCount: number;
  imageUrl: string;
  ownerUserId?: number;
  coverImageUrl?: string;
  memberCount?: number;
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

export interface BillDetail {
  id: string;
  storeName: string;
  payer: string;
  date: string;
  members: BillMember[];
}