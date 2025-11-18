// src/utils/api.ts
import axios from 'axios';
import { Balance, Group, PaymentDetails, Settlement, Transaction, User, UserUpdateForm, Payment } from '../types';

const API_BASE_URL = '/api';

const api = axios.create({
    baseURL: API_BASE_URL,
});

// ============================================================================
// Config & Interceptors
// ============================================================================

// In-memory cache for loaded images (base64 data URLs)
const imageCache = new Map<string, string>();

export const getToken = () => {
    return localStorage.getItem('accessToken'); 
}

api.interceptors.request.use(config => {
    const token = getToken();
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
});

// Remove Content-Type for FormData to let browser handle boundaries
api.interceptors.request.use((cfg) => {
  if (cfg.data instanceof FormData && cfg.headers) {
    delete (cfg.headers as any)["Content-Type"];
    delete (cfg.headers as any)["content-type"];
  }
  return cfg;
});

// ============================================================================
// Helper Functions
// ============================================================================

export async function resolveImageUrl(url: string | undefined): Promise<string> {
    if (!url || url.trim() === '') return '';
    if (url.startsWith('data:')) return url;
    if (imageCache.has(url)) return imageCache.get(url)!;

    try {
        const response = await api.get(url.replace(API_BASE_URL, ''), {
            responseType: 'text'
        });
        const dataUrl = response.data;
        if (typeof dataUrl === 'string' && dataUrl.startsWith('data:')) {
            imageCache.set(url, dataUrl);
            return dataUrl;
        }
        return '';
    } catch (error) {
        console.error('Failed to load image from:', url, error);
        return '';
    }
}

const pickName = (u: any): string => {
  const direct = u.name ?? u.userName ?? u.username ?? u.displayName ?? u.fullName ?? '';
  const fromParts = [u.firstName, u.lastName].filter(Boolean).join(' ');
  const nestedUser = u.user ?? u.profile ?? u.account ?? null;
  const nestedDirect = nestedUser?.name ?? nestedUser?.userName ?? nestedUser?.username ?? '';
  return ((typeof direct === 'string' && direct.trim()) || (fromParts && fromParts.trim()) || (typeof nestedDirect === 'string' && nestedDirect.trim()) || '');
};

// ============================================================================
// Authentication & Users
// ============================================================================

export const loginApi = async (email: string, password: string): Promise<{ user: User; token: string }> => {
    const response = await api.post('/auth/login', { email, password });
    const apiResponse = response.data;
    const avatarUrl = await resolveImageUrl(apiResponse.avatarUrl);
    const qrCodeUrl = await resolveImageUrl(apiResponse.qrCodeUrl);

    return {
        token: apiResponse.accessToken,
        user: {
            id: apiResponse.userId.toString(),
            email: apiResponse.email,
            name: apiResponse.userName,
            phone: apiResponse.phone,
            imageUrl: avatarUrl,
            qrCodeUrl: qrCodeUrl
        }
    };
};

export const signUpApi = async (userName: string, email: string, password: string, phone?: string): Promise<User> => {
    const response = await api.post('/auth/register', {
        userName,
        email,
        password,
        phone: phone || ""
    });
    return response.data;
};

export const getUserInformation = async (userId: string | number,): Promise<any> => {
    const response = await api.get(`/users/${userId}`);
    const userData = response.data;
    if (userData.avatarUrl) userData.avatarUrl = await resolveImageUrl(userData.avatarUrl);
    if (userData.qrCodeUrl) userData.qrCodeUrl = await resolveImageUrl(userData.qrCodeUrl);
    return userData;
};

export const searchUsers = async (query: string): Promise<any[]> => {
    const response = await api.get(`/users/search?q=${query}`);
    const users: any[] = response.data;
    await Promise.all(users.map(async (user) => {
        if (user.avatarUrl) user.avatarUrl = await resolveImageUrl(user.avatarUrl);
    }));
    return users;
};

// ✅ แก้ไขฟังก์ชันนี้: เปลี่ยนจากการยิง Batch (ที่ติด 405) เป็นยิงทีละคน
export const fetchUserProfiles = async (ids: number[]) => {
  // 1. กรอง ID ซ้ำและค่าที่ไม่ใช่ตัวเลขออก
  const uniqIds = Array.from(new Set(ids.filter((n) => Number.isFinite(n))));
  const userMap = new Map<number, any>();

  // 2. ใช้ Promise.all เพื่อยิง API ดึงข้อมูลทุกคนพร้อมกัน
  await Promise.all(
    uniqIds.map(async (id) => {
      try {
        const { data } = await api.get(`/users/${id}`);
        userMap.set(id, data);
      } catch (err) {
        console.warn(`Failed to fetch user ${id}`, err);
      }
    })
  );

  // 3. แปลงข้อมูลกลับเป็น Map ที่ Resolve รูปภาพแล้ว
  const out = new Map<number, any>();
  await Promise.all(
    Array.from(userMap.entries()).map(async ([id, p]) => {
      const imageUrl = await resolveImageUrl(p?.avatarUrl ?? p?.imageUrl ?? '');
      
      // พยายามหาชื่อจากทุกฟิลด์ที่เป็นไปได้
      const name = 
          p?.name || 
          p?.userName || 
          p?.username || 
          p?.displayName || 
          p?.email?.split('@')[0] || 
          `User #${id}`;

      out.set(id, {
        id,
        name: name, 
        email: p?.email ?? '',
        phone: p?.phone ?? '',
        imageUrl: imageUrl,
      });
    })
  );

  return out;
};

export const editUserInformationAcc = async (userId: string | number, formData: UserUpdateForm): Promise<any> => {
    const hasNewFile = (formData.avatar instanceof File) || (formData.qr instanceof File);
    if (hasNewFile) {
        const data = new FormData();
        const userPayload: any = {};
        if (formData.userName !== null) userPayload.userName = formData.userName;
        if (formData.email !== null) userPayload.email = formData.email;
        if (formData.phone !== null) userPayload.phone = formData.phone;

        data.append("user", new Blob([JSON.stringify(userPayload)], { type: "application/json" }), "user.json");
        if (formData.avatar instanceof File) data.append("avatar", formData.avatar);
        if (formData.qr instanceof File) data.append("qr", formData.qr);
        
        const response = await api.put(`/users/${userId}`, data);
        return response.data;
    } else {
        const updatePayload: any = {};
        if (formData.userName !== null) updatePayload.userName = formData.userName;
        if (formData.email !== null) updatePayload.email = formData.email;
        if (formData.phone !== null) updatePayload.phone = formData.phone;
        if (typeof formData.avatar === 'string' || formData.avatar === null) updatePayload.avatarUrl = formData.avatar;
        if (typeof formData.qr === 'string' || formData.qr === null) updatePayload.qrCodeUrl = formData.qr;

        const response = await api.put(`/users/${userId}`, updatePayload);
        return response.data;
    }
}

// ============================================================================
// Expense & Bills (Main Logic)
// ============================================================================

export const createExpenseApi = async (expenseData: {
    groupId: number;
    payerUserId: number;
    amount: number;
    type: "EQUAL" | "PERCENTAGE" | "CUSTOM";
    title: string;
    status?: "SETTLED" | "PENDING";
    participants?: number[];
    exchangeRates?: { [key: string]: number };
}): Promise<any> => {
    const { exchangeRates, ...bodyData } = expenseData;
    const params = {
        currency: "THB", 
        ratesjson: exchangeRates ? JSON.stringify(exchangeRates) : JSON.stringify({ "THB": 1 })
    };

    const response = await api.post("/expenses", bodyData, {
        params: params 
    });
    return response.data;
};

export const createExpenseItem = async (expenseId: number, name: string, amount: string, currency?: string) => {
    const formData = new URLSearchParams();
    formData.append("name", name);
    formData.append("amount", amount);
    if (currency) formData.append("currency", currency);

    const res = await api.post(`/expenses/${expenseId}/items`, formData, {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
    return res.data;
};

export const createExpenseItemShare = async (
    expenseId: number,
    itemId: number,
    participantUserId: number,
    shareValue?: string,
    sharePercent?: string
) => {
    const formData = new URLSearchParams();
    formData.append("participantUserId", participantUserId.toString());
    if (shareValue !== undefined) formData.append("shareValue", shareValue);
    if (sharePercent !== undefined) formData.append("sharePercent", sharePercent);

    const res = await api.post(`/expenses/${expenseId}/items/${itemId}/shares`, formData, {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
    return res.data;
};

export const getBillDetails = async (billId: string): Promise<any> => {
    const response = await api.get(`/expenses/${billId}`);
    return response.data;
};

export const exportExpensePdf = async (expenseId: string): Promise<Blob> => {
    const response = await api.get(`/expenses/${expenseId}/export.pdf`, { responseType: 'blob' });
    return response.data;
};

// ============================================================================
// Groups
// ============================================================================

export const getGroups = async (query?: string): Promise<Group[]> => {
    const endpoint = query ? `/groups?q=${query}` : '/groups/mine';
    const response = await api.get(endpoint);
    const groups: Group[] = response.data;
    await Promise.all(groups.map(async (group: Group) => {
        if (group.coverImageUrl) group.coverImageUrl = await resolveImageUrl(group.coverImageUrl);
    }));
    return groups;
};

export const getGroupDetails = async (groupId: string): Promise<Group> => {
    const response = await api.get(`/groups/${groupId}`);
    const group = response.data;
    if (group.coverImageUrl) group.coverImageUrl = await resolveImageUrl(group.coverImageUrl);
    return group;
};

export const getGroupById = async (groupId: number | string) => {
  const { data } = await api.get(`/groups/${groupId}`);
  if (data.coverImageUrl) data.coverImageUrl = await resolveImageUrl(data.coverImageUrl);
  return data;
};

export const createGroup = async (
  groupName: string,
  participants: { id: number | string }[],
  opts?: { ownerUserId?: number | string; cover?: File }
): Promise<any> => {
  const fd = new FormData();
  const group = {
    id: 0,
    ownerUserId: opts?.ownerUserId != null ? Number(opts.ownerUserId) : undefined,
    name: groupName.trim(),
    coverImageUrl: "",
    memberCount: participants.length,
  };
  fd.append("group", new Blob([JSON.stringify(group)], { type: "application/json" }));
  if (opts?.cover) fd.append("cover", opts.cover);

  const res = await api.post("/groups", fd);
  return res.data;
};

export const updateGroup = async (
  groupId: number | string,
  params: { name: string; ownerUserId?: number | string; userIds?: Array<number | string>; coverFile?: File | null; }
) => {
  const fd = new FormData();
  const group = {
    id: Number(groupId),
    name: params.name.trim(),
    ownerUserId: params.ownerUserId != null ? Number(params.ownerUserId) : undefined,
  };
  fd.append('group', new Blob([JSON.stringify(group)], { type: 'application/json' }));
  if (params.coverFile) fd.append('cover', params.coverFile);
  
  const { data } = await api.put(`/groups/${groupId}`, fd);
  return data;
};

export const deleteGroup = async (groupId: string | number): Promise<any> => {
    const response = await api.delete(`/groups/${groupId}`);
    return response.data;
};

// ============================================================================
// Group Members
// ============================================================================

export const getGroupMembers = async (groupId: number | string): Promise<User[]> => {
  const { data: d } = await api.get(`/groups/${groupId}/members`);
  const raw = Array.isArray(d) ? d : (d?.members || d?.data || []);

  const users: User[] = raw.map((u: any) => {
    const nested = u.user ?? u.profile ?? {};
    return {
      ...u,
      id: u.id ?? u.userId ?? nested.id,
      name: pickName(u),
      email: u.email ?? nested.email ?? '',
      phone: u.phone ?? nested?.phone ?? '',
      imageUrl: u.avatarUrl ?? nested?.avatarUrl ?? '',
    } as User;
  });

  await Promise.all(users.map(async (user) => {
      if (user.imageUrl) user.imageUrl = await resolveImageUrl(user.imageUrl);
  }));

  return users;
};

export const addMember = async (groupId: number | string, userId: number | string) => {
  return api.post(`/groups/${groupId}/members`, {
    groupId: Number(groupId),
    userId: Number(userId),
  }, { headers: { 'Content-Type': 'application/json' }});
};

export const addMembers = async (groupId: number | string, userIds: Array<number | string>) => {
  const existing = await getGroupMembers(groupId);
  const existingIds = new Set(existing.map(m => Number(m.id)));
  for (const uid of userIds) {
    if (existingIds.has(Number(uid))) continue;
    try { await addMember(groupId, uid); } catch (err: any) { if (err?.response?.status !== 409) throw err; }
  }
};

export const setGroupMembers = async (groupId: string | number, userIds: number[]) => {
  const existing = await getGroupMembers(groupId);
  const existSet = new Set(existing.map(m => Number(m.id)));

  for (const uid of userIds) {
    const nuid = Number(uid);
    if (existSet.has(nuid)) continue; 
    try {
      await addMember(groupId, nuid);
    } catch (err: any) {
      if (err?.response?.status !== 409) throw err;
    }
  }
};

export const removeMember = async (groupId: number | string, userId: number | string) => {
  return api.delete(`/groups/${groupId}/members/${userId}`);
};

export const removeMembers = async (groupId: number | string, userIds: Array<number | string>) => {
  for (const uid of userIds) {
    try { await removeMember(groupId, uid); }
    catch (err: any) { if (err?.response?.status !== 404) throw err; }
  }
};

// ============================================================================
// Settlement & Payments
// ============================================================================

export const getBalances = async (): Promise<Balance[]> => {
    const response = await api.get('/me/balances');
    const balances = response.data;
    await Promise.all(balances.map(async (balance: Balance) => {
        if (balance.counterpartyAvatarUrl) balance.counterpartyAvatarUrl = await resolveImageUrl(balance.counterpartyAvatarUrl);
    }));
    return balances;
};

export const getBalanceSummary = async (): Promise<{ youOweTotal: number; youAreOwedTotal: number }> => {
    const response = await api.get('/me/balances/summary');
    return response.data;
};

export const getSettlementDetails = async (expenseId: number, userId: number): Promise<Settlement> => {
    const response = await api.get(`/expenses/${expenseId}/settlement/${userId}`);
    return response.data;
};

export const getExpenseSettlements = async (expenseId: string): Promise<any[]> => {
    const response = await api.get(`/expenses/${expenseId}/settlement`);
    return response.data;
};

export const getExpenseSettlementsUserID = async (expenseId: string, userId : String): Promise<any[]> => {
    const response = await api.get(`/expenses/${expenseId}/settlement/${userId}`);
    if (Array.isArray(response.data)) return response.data;
    return [response.data];
};

export const submitPayment = async (expenseId: number, fromUserId: number, amount: number, receiptFile: File): Promise<any> => {
    const formData = new FormData();
    formData.append('receipt', receiptFile); 
    const response = await api.post(`/expenses/${expenseId}/payments?fromUserId=${fromUserId}&amount=${amount}`, formData);
    return response.data;
};

export const getPaymentDetails = async (expenseId: string, userId: string): Promise<PaymentDetails> => {
    const settlement = await getSettlementDetails(Number(expenseId), Number(userId));
    const expenseDetails = await getBillDetails(expenseId);
    const payerId = expenseDetails.payerUserId;
    let payerName = `User ${payerId}`;
    let qrCodeUrl = '';
    let phone = '';

    try {
        const payerInfo = await getUserInformation(payerId.toString());
        payerName = payerInfo.name || payerInfo.userName || payerName;
        qrCodeUrl = payerInfo.qrCodeUrl || payerInfo.qrCode || '';
        phone = payerInfo.phone || '';
    } catch (error) { console.error("Error fetching payer info:", error); }

    return {
        transactionId: expenseId,
        payerName: payerName,
        amountToPay: settlement.remaining,
        qrCodeUrl: qrCodeUrl,
        phone: phone,
        expenseId: settlement.expenseId,
        userId: settlement.userId,
        owedAmount: settlement.owedAmount,
        paidAmount: settlement.paidAmount,
        settled: settlement.settled,
        remaining: settlement.remaining
    };
};

export const getPayment = async (expenseId: number, paymentId: number): Promise<Payment> => {
    const response = await api.get(`/expenses/${expenseId}/payments/${paymentId}`);
    const payment: Payment = response.data;
    if (payment.receiptFileUrl) payment.receiptFileUrl = await resolveImageUrl(payment.receiptFileUrl);
    return payment;
};

export const getExpensePayments = async (expenseId: number): Promise<Payment[]> => {
  const response = await api.get(`/expenses/${expenseId}/payments`);
  return response.data;
};

export const hasPendingPayment = async (expenseId: number, userId: number): Promise<boolean> => {
  try {
    const payments = await getExpensePayments(expenseId);
    const userPendingPayments = payments.filter(payment => 
      payment.fromUserId === userId && payment.status === "PENDING"
    );
    return userPendingPayments.length > 0;
  } catch (error) {
    console.error("Error checking pending payments:", error);
    return false;
  }
};

export const updatePaymentStatus = async (expenseId: number, paymentId: number, status: 'VERIFIED' | 'REJECTED'): Promise<Payment> => {
    const response = await api.put(`/expenses/${expenseId}/payments/${paymentId}/status?status=${status}`);
    return response.data;
};

// ============================================================================
// Transactions (History)
// ============================================================================

export const getTransactions = async (): Promise<any[]> => {
    const response = await api.get('/transactions');
    return response.data;
};

export const getGroupTransactions = async (groupId: string): Promise<Transaction[]> => {
  const response = await api.get(`/expenses/group/${groupId}`);
  const expenses = response.data;
  const transactions = await Promise.all(
    expenses.map(async (expense: any) => {
      const user_response = await api.get(`/users/${expense.payerUserId}`);
      const username = user_response.data.userName;
      return {
        ...expense,
        name: expense.title,
        payer: `${username}`,
        date: new Date(expense.createdAt).toLocaleDateString(),
        status: expense.status.toLowerCase() as 'pending' | 'completed',
      };
    })
  );
  return transactions;
};