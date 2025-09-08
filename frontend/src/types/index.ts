export interface User {
    id: string;
    email: string;
    name: string;
    imageUrl?: string;
    phone?: string;
}

export interface AuthContextType {
    user: User | null;
    login: (email: string, password: string) => Promise<void>;
    logout: () => void;
    isLoading: boolean;
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