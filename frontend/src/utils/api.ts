// src/utils/api.ts
import axios from 'axios';
import {
  Balance,
  Group,
  PaymentDetails,
  Settlement,
  Transaction,
  User,
  UserUpdateForm,
  Payment,
} from '../types';

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
};

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    (config.headers as any)['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

// Remove Content-Type for FormData to let browser handle boundaries
api.interceptors.request.use((cfg) => {
  if (cfg.data instanceof FormData && cfg.headers) {
    delete (cfg.headers as any)['Content-Type'];
    delete (cfg.headers as any)['content-type'];
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
    // If URL includes API_BASE_URL prefix, strip it for relative fetching
    const fetchUrl = url.replace(API_BASE_URL, '');
    const response = await api.get(fetchUrl, {
      responseType: 'text',
    });
    const dataUrl = response.data;
    if (typeof dataUrl === 'string' && dataUrl.startsWith('data:')) {
      imageCache.set(url, dataUrl);
      return dataUrl;
    }
    // If backend returned a plain url, return it (but avoid caching non-data URLs unless desired)
    if (typeof dataUrl === 'string') {
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
  const direct = u?.name ?? u?.userName ?? u?.username ?? u?.displayName ?? u?.fullName ?? '';
  const fromParts = [u?.firstName, u?.lastName].filter(Boolean).join(' ');
  const nestedUser = u?.user ?? u?.profile ?? u?.account ?? null;
  const nestedDirect = nestedUser?.name ?? nestedUser?.userName ?? nestedUser?.username ?? '';
  const candidate =
    (typeof direct === 'string' && direct.trim()) ||
    (fromParts && fromParts.trim()) ||
    (typeof nestedDirect === 'string' && nestedDirect.trim()) ||
    '';
  return candidate;
};

const deriveName = (u: any): string => {
  const direct = u?.name ?? u?.userName ?? u?.username ?? u?.displayName ?? u?.fullName ?? '';
  const parts = [u?.firstName, u?.lastName].filter(Boolean).join(' ');
  return (direct || parts || '').toString();
};

// ============================================================================
// Authentication & Users
// ============================================================================

export const loginApi = async (
  email: string,
  password: string
): Promise<{ user: User; token: string }> => {
  const response = await api.post('/auth/login', { email, password });
  const apiResponse = response.data;
  const avatarUrl = await resolveImageUrl(apiResponse.avatarUrl);
  const qrCodeUrl = await resolveImageUrl(apiResponse.qrCodeUrl);

  return {
    token: apiResponse.accessToken,
    user: {
      id: apiResponse.userId?.toString?.() ?? String(apiResponse.userId ?? ''),
      email: apiResponse.email ?? '',
      name: apiResponse.userName ?? apiResponse.name ?? '',
      phone: apiResponse.phone ?? '',
      imageUrl: avatarUrl,
      qrCodeUrl: qrCodeUrl,
      firstName: apiResponse.firstName ?? '',
      lastName: apiResponse.lastName ?? '',
    } as User,
  };
};

export const signUpApi = async (
  userName: string,
  email: string,
  password: string,
  phone?: string,
  firstName?: string,
  lastName?: string
): Promise<User> => {
  const response = await api.post('/auth/register', {
    userName,
    email,
    password,
    phone: phone || '',
    firstName: firstName || '',
    lastName: lastName || '',
  });
  return response.data;
};

export const getUserInformation = async (userId: string | number): Promise<any> => {
  const response = await api.get(`/users/${userId}`);
  const userData = response.data;

  // Resolve avatar and QR code images (if present)
  if (userData?.avatarUrl) {
    userData.avatarUrl = await resolveImageUrl(userData.avatarUrl);
  }
  if (userData?.qrCodeUrl) {
    userData.qrCodeUrl = await resolveImageUrl(userData.qrCodeUrl);
  }

  // Ensure firstName/lastName exist (avoid undefined)
  userData.firstName = userData.firstName ?? '';
  userData.lastName = userData.lastName ?? '';

  return userData;
};

export const searchUsers = async (query: string): Promise<any[]> => {
  const response = await api.get(`/users/search?q=${encodeURIComponent(query)}`);
  const users: any[] = Array.isArray(response.data) ? response.data : response.data?.users ?? [];
  await Promise.all(
    users.map(async (user: any) => {
      const avatarUrl = user.avatarUrl ?? user.imageUrl ?? '';
      if (avatarUrl) {
        const resolved = await resolveImageUrl(avatarUrl);
        user.avatarUrl = resolved || avatarUrl;
        user.imageUrl = user.avatarUrl;
      }
    })
  );
  return users;
};

// ============================================================================
// fetchUserProfiles (single robust implementation)
// ============================================================================

/**
 * Fetch user profiles for a list of ids.
 * Tries multiple backend shapes:
 *  1) POST /users/batch { ids: [...] }
 *  2) GET /users?ids=1,2,3
 *  3) Fallback GET /users/:id for each id
 *
 * Returns a Map<id, normalizedProfile>
 */
export const fetchUserProfiles = async (ids: number[]) => {
  const uniq = Array.from(new Set(ids.filter((n) => Number.isFinite(n) && n != null)));
  const byId = new Map<number, any>();

  // 1) POST /users/batch
  try {
    const { data } = await api.post('/users/batch', { ids: uniq });
    const arr = Array.isArray(data)
      ? data
      : Array.isArray(data?.users)
      ? data.users
      : Array.isArray(data?.items)
      ? data.items
      : Array.isArray(data?.data)
      ? data.data
      : [];
    arr.forEach((p: any) => {
      const id = Number(p?.id ?? p?.userId);
      if (Number.isFinite(id)) byId.set(id, p);
    });
  } catch (e) {
    // ignore and try next
  }

  // 2) GET /users?ids=1,2,3
  if (byId.size < uniq.length) {
    try {
      const { data } = await api.get('/users', { params: { ids: uniq.join(',') } });
      const arr = Array.isArray(data)
        ? data
        : Array.isArray(data?.users)
        ? data.users
        : Array.isArray(data?.items)
        ? data.items
        : Array.isArray(data?.data)
        ? data.data
        : [];
      arr.forEach((p: any) => {
        const id = Number(p?.id ?? p?.userId);
        if (Number.isFinite(id) && !byId.has(id)) byId.set(id, p);
      });
    } catch (e) {
      // ignore and try next
    }
  }

  // 3) Fallback: GET /users/:id individually
  if (byId.size < uniq.length) {
    for (const id of uniq) {
      if (byId.has(id)) continue;
      try {
        const { data } = await api.get(`/users/${id}`);
        byId.set(id, data);
      } catch (e) {
        // skip failures
      }
    }
  }

  // Normalize and resolve images in parallel
  const out = new Map<number, any>();
  const resolvePromises: Promise<void>[] = [];

  byId.forEach((p, id) => {
    const promise = (async () => {
      const imageUrl = p?.avatarUrl ?? p?.imageUrl ?? '';
      const resolvedImageUrl = imageUrl ? await resolveImageUrl(imageUrl) : '';
      out.set(id, {
        id,
        name: deriveName(p),
        email: p?.email ?? '',
        phone: p?.phone ?? '',
        imageUrl: resolvedImageUrl ?? '',
        raw: p,
      });
    })();
    resolvePromises.push(promise);
  });

  await Promise.all(resolvePromises);
  return out;
};

// ============================================================================
// editUserInformationAcc
// ============================================================================

export const editUserInformationAcc = async (
  userId: string | number,
  formData: UserUpdateForm
): Promise<any> => {
  const hasNewFile = formData?.avatar instanceof File || formData?.qr instanceof File;
  if (hasNewFile) {
    const data = new FormData();
    const userPayload: any = {};
    if (formData.userName !== null && formData.userName !== undefined) userPayload.userName = formData.userName;
    if (formData.email !== null && formData.email !== undefined) userPayload.email = formData.email;
    if (formData.phone !== null && formData.phone !== undefined) userPayload.phone = formData.phone;

    // Only include firstName/lastName if non-empty
    if (formData.firstName && typeof formData.firstName === 'string' && formData.firstName.trim() !== '') {
      userPayload.firstName = formData.firstName.trim();
    }
    if (formData.lastName && typeof formData.lastName === 'string' && formData.lastName.trim() !== '') {
      userPayload.lastName = formData.lastName.trim();
    }

    data.append('user', new Blob([JSON.stringify(userPayload)], { type: 'application/json' }), 'user.json');
    if (formData.avatar instanceof File) data.append('avatar', formData.avatar);
    if (formData.qr instanceof File) data.append('qr', formData.qr);

    const response = await api.put(`/users/${userId}`, data);
    return response.data;
  } else {
    const updatePayload: any = {};
    if (formData.userName !== null && formData.userName !== undefined) updatePayload.userName = formData.userName;
    if (formData.email !== null && formData.email !== undefined) updatePayload.email = formData.email;
    if (formData.phone !== null && formData.phone !== undefined) updatePayload.phone = formData.phone;

    // If avatar/qr are strings (URLs) include them but avoid data: URLs
    if (typeof formData.avatar === 'string' && formData.avatar !== null) {
      if (!formData.avatar.startsWith('data:')) {
        updatePayload.avatarUrl = formData.avatar;
      }
    } else if (formData.avatar === null) {
      updatePayload.avatarUrl = null;
    }

    if (typeof formData.qr === 'string' && formData.qr !== null) {
      if (!formData.qr.startsWith('data:')) {
        updatePayload.qrCodeUrl = formData.qr;
      }
    } else if (formData.qr === null) {
      updatePayload.qrCodeUrl = null;
    }

    // Only include firstName/lastName when non-empty
    if (formData.firstName && typeof formData.firstName === 'string' && formData.firstName.trim() !== '') {
      updatePayload.firstName = formData.firstName.trim();
    }
    if (formData.lastName && typeof formData.lastName === 'string' && formData.lastName.trim() !== '') {
      updatePayload.lastName = formData.lastName.trim();
    }

    const response = await api.put(`/users/${userId}`, updatePayload);
    return response.data;
  }
};

// ============================================================================
// Expense & Bills (Main Logic)
// ============================================================================

export const createExpenseApi = async (expenseData: {
  groupId: number;
  payerUserId: number;
  amount: number;
  type: 'EQUAL' | 'PERCENTAGE' | 'CUSTOM';
  title: string;
  status?: 'SETTLED' | 'PENDING';
  participants?: number[];
  ratesJson?: { [key: string]: number };
}): Promise<any> => {
  // 1. Separate ratesJson
  const { ratesJson, ...bodyData } = expenseData;

  // 2. Stringify for query param
  const ratesJsonString = ratesJson ? JSON.stringify(ratesJson) : JSON.stringify({ THB: 1 });

  const params = {
    currency: 'THB',
    ratesJson: ratesJsonString,
  };

  // 3. Body includes both object & string (defensive)
  const finalBody = {
    ...bodyData,
    ratesJson: ratesJson,
    ratesjson: ratesJsonString,
  };

  console.log('🚀 Sending API Request:', { params, body: finalBody });

  const response = await api.post('/expenses', finalBody, {
    params,
  });
  return response.data;
};

export const createExpenseItem = async (expenseId: number, name: string, amount: string, currency?: string) => {
  const formData = new URLSearchParams();
  formData.append('name', name);
  formData.append('amount', amount);
  if (currency) formData.append('currency', currency);

  const res = await api.post(`/expenses/${expenseId}/items`, formData, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
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
  formData.append('participantUserId', participantUserId.toString());
  if (shareValue !== undefined) formData.append('shareValue', shareValue);
  if (sharePercent !== undefined) formData.append('sharePercent', sharePercent);

  const res = await api.post(`/expenses/${expenseId}/items/${itemId}/shares`, formData, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
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
  const endpoint = query ? `/groups?q=${encodeURIComponent(query)}` : '/groups/mine';
  const response = await api.get(endpoint);
  const groups: Group[] = response.data;
  await Promise.all(
    groups.map(async (group: Group) => {
      if (group.coverImageUrl) group.coverImageUrl = await resolveImageUrl(group.coverImageUrl);
    })
  );
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
    coverImageUrl: '',
    memberCount: participants.length,
  };
  fd.append('group', new Blob([JSON.stringify(group)], { type: 'application/json' }));
  if (opts?.cover) fd.append('cover', opts.cover);

  const res = await api.post('/groups', fd);
  return res.data;
};

export const updateGroup = async (
  groupId: number | string,
  params: { name: string; ownerUserId?: number | string; userIds?: Array<number | string>; coverFile?: File | null }
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
  const raw =
    Array.isArray(d) ? d : Array.isArray(d?.members) ? d.members : Array.isArray(d?.data) ? d.data : Array.isArray(d?.items) ? d.items : Array.isArray(d?.content) ? d.content : [];

  const members: User[] = raw.map((u: any) => {
    const nested = u.user ?? u.profile ?? u.account ?? {};
    return {
      ...u,
      id: u.id ?? u.userId ?? nested.id,
      name: pickName(u),
      email: u.email ?? nested.email ?? '',
      phone: u.phone ?? nested?.phone ?? '',
      imageUrl: u.avatarUrl ?? nested?.avatarUrl ?? '',
    } as User;
  });

  // Resolve all member image URLs in parallel
  await Promise.all(
    members.map(async (member: User) => {
      if (member.imageUrl) {
        member.imageUrl = await resolveImageUrl(member.imageUrl);
      }
    })
  );

  return members;
};

export const addMember = async (groupId: number | string, userId: number | string) => {
  return api.post(
    `/groups/${groupId}/members`,
    {
      groupId: Number(groupId),
      userId: Number(userId),
    },
    { headers: { 'Content-Type': 'application/json' } }
  );
};

export const addMembers = async (groupId: number | string, userIds: Array<number | string>) => {
  const existing = await getGroupMembers(groupId);
  const existingIds = new Set(existing.map((m) => Number(m.id)));
  for (const uid of userIds) {
    if (existingIds.has(Number(uid))) continue;
    try {
      await addMember(groupId, uid);
    } catch (err: any) {
      if (err?.response?.status !== 409) throw err;
    }
  }
};

export const setGroupMembers = async (groupId: string | number, userIds: number[]) => {
  const existing = await getGroupMembers(groupId);
  const existSet = new Set(existing.map((m) => Number(m.id)));

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
    try {
      await removeMember(groupId, uid);
    } catch (err: any) {
      if (err?.response?.status !== 404) throw err;
    }
  }
};

// ============================================================================
// Settlement & Payments
// ============================================================================

export const getBalances = async (): Promise<Balance[]> => {
  const response = await api.get('/me/balances');
  const balances: Balance[] = response.data;
  await Promise.all(
    balances.map(async (balance: Balance) => {
      if (balance.counterpartyAvatarUrl) balance.counterpartyAvatarUrl = await resolveImageUrl(balance.counterpartyAvatarUrl);
    })
  );
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

export const getExpenseSettlementsUserID = async (expenseId: string, userId: String): Promise<any[]> => {
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
    if (payerInfo.firstName && payerInfo.lastName) {
      payerName = `${payerInfo.firstName} ${payerInfo.lastName}`;
    } else if (payerInfo.firstName) {
      payerName = payerInfo.firstName;
    } else if (payerInfo.lastName) {
      payerName = payerInfo.lastName;
    } else {
      payerName = payerInfo.name || payerInfo.userName || payerName;
    }
    qrCodeUrl = payerInfo.qrCodeUrl || payerInfo.qrCode || '';
    phone = payerInfo.phone || '';
  } catch (error) {
    console.error('Error fetching payer info:', error);
  }

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
    remaining: settlement.remaining,
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
    const userPendingPayments = payments.filter((payment) => payment.fromUserId === userId && payment.status === 'PENDING');
    return userPendingPayments.length > 0;
  } catch (error) {
    console.error('Error checking pending payments:', error);
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
  const expenses = Array.isArray(response.data) ? response.data : [];
  const transactions = await Promise.all(
    expenses.map(async (expense: any) => {
      // Fetch payer username safely; fallback gracefully
      let username = '';
      try {
        const user_response = await api.get(`/users/${expense.payerUserId}`);
        username = user_response.data?.userName ?? user_response.data?.name ?? '';
      } catch (e) {
        username = '';
      }
      return {
        ...expense,
        name: expense.title,
        payer: `${username}`,
        date: expense.createdAt ? new Date(expense.createdAt).toLocaleDateString() : '',
        status: (expense.status ?? '').toString().toLowerCase() as 'pending' | 'completed',
      } as Transaction;
    })
  );
  return transactions;
};
