import axios from 'axios';
import { User } from '../types';

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

export const getTransactions = async (): Promise<any[]> => {
    const response = await api.get('/transactions');
    return response.data;
};

export const getGroups = async (): Promise<any[]> => {
    const response = await api.get('/groups');
    return response.data;
};

export const getGroupDetails = async (groupId: string): Promise<any> => {
    const response = await api.get(`/groups/${groupId}`);
    return response.data;
};

export const getGroupTransactions = async (groupId: string): Promise<any[]> => {
    const response = await api.get(`/groups/${groupId}/transactions`);
    return response.data;
};

export const searchUsers = async (query: string): Promise<any[]> => {
    const response = await api.get(`/users/search?q=${query}`);
    return response.data;
};

export const createGroup = async (groupName: string, participants: any[]): Promise<any> => {
    const response = await api.post('/groups', { name: groupName, members: participants });
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
