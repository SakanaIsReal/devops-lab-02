import axios from 'axios';
import { Group, Transaction, User } from '../types';
import { UserUpdateForm } from '../types/index'
const API_BASE_URL = 'http://localhost:8081';

const api = axios.create({
    baseURL: API_BASE_URL,
});

// FIX: Consistent token key
export const getToken = () => {
    return localStorage.getItem('accessToken'); // Changed from 'token' to 'accessToken'
}

api.interceptors.request.use(config => {
    const token = getToken();
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
});

// FIX: Correct response handling
export const loginApi = async (email: string, password: string): Promise<{ user: User; token: string }> => {
    const response = await api.post('/api/auth/login', { email, password });

    // Map API response to match your expected structure
    const apiResponse = response.data;
    return {
        token: apiResponse.accessToken, // Map accessToken to token
        user: {
            id: apiResponse.userId.toString(),
            email: apiResponse.email,
            name: apiResponse.userName,
            phone: apiResponse.phone,
            imageUrl: apiResponse.avatarUrl,
            qrCodeUrl: apiResponse.qrCodeUrl
            // Add other properties as needed
        }
    };
};

// In your ../utils/api file, update the signUpApi function:
export const signUpApi = async (
    userName: string,
    email: string,
    password: string,
    phone?: string
): Promise<User> => {
    const response = await api.post('/api/auth/register', {
        userName,
        email,
        password,
        phone: phone || "" // Send empty string if phone is not provided
    });
    return response.data;
};
export const getUserInformation = async (userId: string | number,): Promise<any> => {
    const response = await api.get(`/api/users/${userId}`);
    return response.data;
};


export const getTransactions = async (): Promise<any[]> => {
    const response = await api.get('/transactions');
    return response.data;
};

export const getGroups = async (): Promise<any[]> => {
    const response = await api.get('/api/groups');
    return response.data;
};


export const searchUsers = async (query: string): Promise<any[]> => {
    const response = await api.get(`/users/search?q=${query}`);
    return response.data;
};

export const createGroup = async (groupName: string, participants: any[]): Promise<any> => {
    const response = await api.post('/api/groups', { name: groupName, members: participants });
    return response.data;
};

export const getBillDetails = async (billId: string): Promise<any> => {
    const response = await api.get(`/bills/${billId}`);
    return response.data;
};

export const getPaymentDetails = async (transactionId: string): Promise<any> => {
    const response = await api.get(`/payment-details/${transactionId}`);
    return response.data;
};
// Update your ../utils/api file

export const getGroupDetails = async (groupId: string): Promise<Group> => {
    const response = await api.get(`/api/groups/${groupId}`);
    return response.data;
};

export const getGroupTransactions = async (groupId: string): Promise<Transaction[]> => {
    const response = await api.get(`/api/expenses/group/${groupId}`);

    // Transform the API response to match your frontend needs
    const expenses = response.data;
    return expenses.map((expense: any) => ({
        ...expense,
        // Map API fields to frontend expected fields
        name: expense.title,
        payer: `User ${expense.payerUserId}`, // You might need to fetch actual user names
        date: new Date(expense.createdAt).toLocaleDateString(),
        status: expense.status.toLowerCase() as 'pending' | 'completed' // Map status
    }));
};

export const getGroupMembers = async (groupId: string): Promise<any[]> => {
    const response = await api.get(`/api/groups/${groupId}/members`);
    return response.data;
};

// Add this function for creating expenses
export const createExpense = async (expenseData: {
    groupId: number;
    payerUserId: number;
    amount: number;
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
    title: string;
    // Add other necessary fields
}): Promise<any> => {
    const response = await api.post('/api/expenses', expenseData);
    return response.data;
};
export const editUserInformationAcc = async (
    userId: string | number,
    formData: UserUpdateForm
): Promise<any> => {
    const hasNewFile = (formData.avatar instanceof File) || (formData.qr instanceof File);
    if (hasNewFile) {
        const data = new FormData();
        const userPayload: any = {};
        if (formData.userName !== null) userPayload.userName = formData.userName;
        if (formData.email !== null) userPayload.email = formData.email;
        if (formData.phone !== null) userPayload.phone = formData.phone;

        data.append(
            "user",
            new Blob([JSON.stringify(userPayload)], { type: "application/json" }),
            "user.json"
        );
        if (formData.avatar instanceof File) {
            data.append("avatar", formData.avatar);
        }
        if (formData.qr instanceof File) {
            data.append("qr", formData.qr);
        }
        const response = await api.put(`/api/users/${userId}`, data);
        return response.data;
    } else {
        const updatePayload: any = {};

        // ใส่ข้อมูล Text fields
        if (formData.userName !== null) updatePayload.userName = formData.userName;
        if (formData.email !== null) updatePayload.email = formData.email;
        if (formData.phone !== null) updatePayload.phone = formData.phone;

        if (typeof formData.avatar === 'string' || formData.avatar === null) {
            updatePayload.avatarUrl = formData.avatar;
        }
        if (typeof formData.qr === 'string' || formData.qr === null) {
            updatePayload.qrCodeUrl = formData.qr;
        }

        // ส่ง JSON Request
        const response = await api.put(`/api/users/${userId}`, updatePayload);
        return response.data;
    }
}

// ====================
// 1. Create Expense (ใช้ axios)
// ====================
export const createExpenseApi = async (expenseData: {
    groupId: number;
    payerUserId: number;
    amount: number;
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
    title: string;
}): Promise<any> => {
    const response = await api.post("/api/expenses", expenseData);
    return response.data;
};

// ====================
// 2. Create Expense Item
// ====================
export const createExpenseItem = async (
    expenseId: number,
    name: string,
    amount: string
) => {
    const formData = new URLSearchParams();
    formData.append("name", name);
    formData.append("amount", amount);

    const res = await api.post(
        `/api/expenses/${expenseId}/items`,
        formData,
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
        }
    );

    return res.data; // ✅ axios ใช้ data
};


// ====================
// 3. Create Expense Item Share
// ====================
export const createExpenseItemShare = async (
    expenseId: number,
    itemId: number,
    participantUserId: number,
    shareValue?: string,
    sharePercent?: string
) => {
    const formData = new URLSearchParams();
    formData.append("participantUserId", participantUserId.toString());
    if (shareValue !== undefined) {
        formData.append("shareValue", shareValue);
    }
    if (sharePercent !== undefined) {
        formData.append("sharePercent", sharePercent);
    }

    const res = await api.post(
        `/api/expenses/${expenseId}/items/${itemId}/shares`,
        formData,
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
        }
    );
    return res.data;
};
