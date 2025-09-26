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

export interface Transaction {
    id: number;
    type: "owe" | "owed";
    name: string;
    amount: number;
    description: string;
    created_date: string;
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
