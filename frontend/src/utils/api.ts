import axios from 'axios';
import { Balance, Group, PaymentDetails, Settlement, Transaction, User, UserUpdateForm } from '../types';

export const getBalances = async (): Promise<Balance[]> => {
    const response = await api.get('/api/me/balances');
    return response.data;
};
const API_BASE_URL = 'http://localhost:8081';

const api = axios.create({
    baseURL: API_BASE_URL,
});

// utils/api.ts
export const getPaymentDetail = async (billId: string, userId: string) => {
  const response = await api.get(`/api/payments/${billId}/${userId}`);
  return response.data;
};


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
    const response = await api.get('/api/transactions');
    return response.data;
};

export const getGroups = async (): Promise<Group[]> => {
    const response = await api.get('/api/groups/mine');
    return response.data;
};


export const searchUsers = async (query: string): Promise<any[]> => {
    const response = await api.get(`/api/users/search?q=${query}`);
    return response.data;
};



export const getBillDetails = async (billId: string): Promise<any> => {
    const response = await api.get(`/api/expenses/${billId}`);
    return response.data;
};

export const getExpenseSettlements = async (expenseId: string): Promise<any[]> => {
    const response = await api.get(`/api/expenses/${expenseId}/settlement`);
    return response.data;
};
export const getExpenseSettlementsUserID = async (expenseId: string, userId : String): Promise<any[]> => {
    const response = await api.get(`/api/expenses/${expenseId}/settlement/${userId}`);
    if (Array.isArray(response.data)) {
        return response.data;
    }
    return [response.data];
};
// Update your ../utils/api file

export const getGroupDetails = async (groupId: string): Promise<Group> => {
    const response = await api.get(`/api/groups/${groupId}`);
    return response.data;
};

export const getGroupTransactions = async (groupId: string): Promise<Transaction[]> => {
  const response = await api.get(`/api/expenses/group/${groupId}`);
  const expenses = response.data;

  const transactions = await Promise.all(
    expenses.map(async (expense: any) => {
      const user_response = await api.get(`/api/users/${expense.payerUserId}`);
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
    participants?: number[];
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

export const getGroupById = async (groupId: number | string) => {
  const { data } = await api.get(`/api/groups/${groupId}`);
  return data; // คาดว่า { id, name, ownerUserId, members: [...], coverImageUrl, ... }
};


// อัปเดตกลุ่ม (multipart/form-data + ฟิลด์ 'group')
export const updateGroup = async (
  groupId: number | string,
  params: {
    name: string;
    ownerUserId?: number | string;
    // ส่งเฉพาะรายการ id ของสมาชิก (ถ้า BE ต้องการ endpoint แยก เดี๋ยวค่อยเรียกอีกตัว)
    userIds?: Array<number | string>;
    coverFile?: File | null;        // ถ้าเปลี่ยนปก
  }
) => {
  const fd = new FormData();

  const group = {
    id: Number(groupId),                           // สำคัญ: ใส่ id กลุ่ม
    name: params.name.trim(),
    ownerUserId: params.ownerUserId != null ? Number(params.ownerUserId) : undefined,
    // ถ้า BE ต้องการ field อื่น เช่น coverImageUrl/memberCount ใส่เพิ่มได้
  };

  // แนบ object group เป็น JSON (อย่าตั้ง Content-Type เอง)
  fd.append('group', new Blob([JSON.stringify(group)], { type: 'application/json' }));

  if (params.coverFile) {
    fd.append('cover', params.coverFile);
  }
  const { data } = await api.put(`/api/groups/${groupId}`, fd);
  return data;
};



delete (api.defaults.headers as any).common?.["Content-Type"];
delete (api.defaults.headers as any).post?.["Content-Type"];
delete (api.defaults.headers as any).put?.["Content-Type"];

// ถ้า body เป็น FormData ให้ลบ Content-Type ออก เพื่อให้ browser ใส่ boundary เอง
api.interceptors.request.use((cfg) => {
  if (cfg.data instanceof FormData && cfg.headers) {
    delete (cfg.headers as any)["Content-Type"];
    delete (cfg.headers as any)["content-type"];
  }
  return cfg;
});



// utils/api.ts
export const createGroup = async (
  groupName: string,
  participants: { id: number | string }[],
  opts?: { ownerUserId?: number | string; cover?: File }
): Promise<any> => {

  const fd = new FormData();

  // object ที่ Swagger แสดงว่าควรอยู่ในฟิลด์ `group`
  const group = {
    id: 0, // สำหรับสร้างใหม่ มักใส่ 0 หรือไม่ส่งก็ได้ ถ้า BE ไม่บังคับ
    ownerUserId: opts?.ownerUserId != null ? Number(opts.ownerUserId) : undefined,
    name: groupName.trim(),
    coverImageUrl: "",                 // ถ้ายังไม่มี URL ใส่ค่าว่างหรือตัดทิ้งได้หาก BE ไม่บังคับ
    memberCount: participants.length,  // จำนวนสมาชิกเริ่มต้น
  };

  // ส่ง `group` เป็น JSON ใน multipart (อย่าตั้ง Content-Type เอง)
  fd.append("group", new Blob([JSON.stringify(group)], { type: "application/json" }));

  // แนบไฟล์ปกถ้ามี
  if (opts?.cover) {
    fd.append("cover", opts.cover);
  }

  // ใช้ path ที่มี / ข้างหน้าเสมอ
  const res = await api.post("/api/groups", fd);
  return res.data;
};


// utils/api.ts (แนะนำทำที่นี่ จะได้ใช้ซ้ำทุกหน้า)
const pickName = (u: any): string => {
  const direct =
    u.name ?? u.userName ?? u.username ?? u.displayName ?? u.fullName ?? '';
  const fromParts = [u.firstName, u.lastName].filter(Boolean).join(' ');
  const nestedUser = u.user ?? u.profile ?? u.account ?? null;

  const nestedDirect =
    nestedUser?.name ??
    nestedUser?.userName ??
    nestedUser?.username ??
    nestedUser?.displayName ??
    nestedUser?.fullName ?? '';

  const nestedFromParts = [nestedUser?.firstName, nestedUser?.lastName]
    .filter(Boolean)
    .join(' ');

  return (
    (typeof direct === 'string' && direct.trim()) ||
    (fromParts && fromParts.trim()) ||
    (typeof nestedDirect === 'string' && nestedDirect.trim()) ||
    (nestedFromParts && nestedFromParts.trim()) ||
    ''
  );
};

export const getGroupMembers = async (groupId: number | string): Promise<User[]> => {
  const { data: d } = await api.get(`/api/groups/${groupId}/members`);

  const raw = Array.isArray(d) ? d
           : Array.isArray(d?.members) ? d.members
           : Array.isArray(d?.items) ? d.items
           : Array.isArray(d?.data) ? d.data
           : Array.isArray(d?.content) ? d.content
           : [];

  return raw.map((u: any) => {
    const nested = u.user ?? u.profile ?? u.account ?? {};
    return {
      ...u,
      id: u.id ?? u.userId ?? u.memberId ?? nested.id ?? nested.userId,
      name: pickName(u),
      email: u.email ?? nested.email ?? '',
      phone: u.phone ?? u.mobile ?? nested?.phone ?? '',
      imageUrl: u.avatarUrl ?? u.imageUrl ?? nested?.avatarUrl ?? nested?.imageUrl ?? '',
    } as User;
  });
};




// เพิ่มสมาชิก 1 คน (ตามสเปค Swagger: body = { groupId, userId })
export const addMember = async (groupId: number | string, userId: number | string) => {
  return api.post(`/api/groups/${groupId}/members`, {
    groupId: Number(groupId),
    userId: Number(userId),
  }, { headers: { 'Content-Type': 'application/json' }});
};

// เพิ่มหลายคน: ข้ามคนที่อยู่แล้ว + ข้าม 409 แบบนุ่มนวล
export const addMembers = async (groupId: number | string, userIds: Array<number | string>) => {
  // อ่านรายชื่อที่มีอยู่ก่อน
  const existing = await getGroupMembers(groupId);
  const existingIds = new Set(existing.map(m => Number(m.id)));

  // เพิ่มเฉพาะคนที่ยังไม่อยู่
  for (const uid of userIds) {
    const nuid = Number(uid);
    if (existingIds.has(nuid)) continue;
    try {
      await addMember(groupId, nuid);
    } catch (err: any) {
      if (err?.response?.status === 409) {
        // มีอยู่แล้วระหว่าง race condition -> ข้าม
        continue;
      }
      throw err;
    }
  }
};


export const setGroupMembers = async (groupId: string | number, userIds: number[]) => {
  const existing = await getGroupMembers(groupId);
  const existSet = new Set(existing.map(m => Number(m.id)));

  for (const uid of userIds) {
    const nuid = Number(uid);
    if (existSet.has(nuid)) continue; // มีแล้ว ข้าม
    try {
      await addMember(groupId, nuid);
    } catch (err: any) {
      if (err?.response?.status === 409) {
        // ซ้ำระหว่างแข่งกัน เพิ่มแล้ว ข้ามไป
        continue;
      }
      throw err;
    }
  }
};
// ช่วยประกอบชื่อ
const deriveName = (u: any): string => {
  const direct = u?.name ?? u?.userName ?? u?.username ?? u?.displayName ?? u?.fullName ?? '';
  const parts = [u?.firstName, u?.lastName].filter(Boolean).join(' ');
  return (direct || parts || '').toString();
};

// ดึงโปรไฟล์ users ตาม ids (พยายามหลายรูปแบบ เผื่อ BE แต่ละทรง)
export const fetchUserProfiles = async (ids: number[]) => {
  const uniq = Array.from(new Set(ids.filter((n) => Number.isFinite(n))));
  const byId = new Map<number, any>();

  // 1) POST /api/users/batch  { ids: [...] }
  try {
    const { data } = await api.post('/api/users/batch', { ids: uniq });
    const arr = Array.isArray(data) ? data
      : Array.isArray(data?.users) ? data.users
      : Array.isArray(data?.items) ? data.items
      : Array.isArray(data?.data)  ? data.data
      : [];
    arr.forEach((p: any) => {
      const id = Number(p?.id ?? p?.userId);
      if (Number.isFinite(id)) byId.set(id, p);
    });
  } catch (_) {}

  // 2) GET /api/users?ids=1,2,3
  if (byId.size < uniq.length) {
    try {
      const { data } = await api.get('/api/users', { params: { ids: uniq.join(',') } });
      const arr = Array.isArray(data) ? data
        : Array.isArray(data?.users) ? data.users
        : Array.isArray(data?.items) ? data.items
        : Array.isArray(data?.data)  ? data.data
        : [];
      arr.forEach((p: any) => {
        const id = Number(p?.id ?? p?.userId);
        if (Number.isFinite(id) && !byId.has(id)) byId.set(id, p);
      });
    } catch (_) {}
  }

  // 3) Fallback: GET /api/users/:id ทีละตัว
  if (byId.size < uniq.length) {
    for (const id of uniq) {
      if (byId.has(id)) continue;
      try {
        const { data } = await api.get(`/api/users/${id}`);
        byId.set(id, data);
      } catch (_) {}
    }
  }

  // สร้างแผนที่ id -> โปรไฟล์ normalize แล้ว
  const out = new Map<number, any>();
  byId.forEach((p, id) => {
    out.set(id, {
      id,
      name: deriveName(p),
      email: p?.email ?? '',
      phone: p?.phone ?? '',
      imageUrl: p?.avatarUrl ?? p?.imageUrl ?? '',
    });
  });
  return out;
};
// utils/api.ts
export const getBalanceSummary = async (): Promise<{ youOweTotal: number; youAreOwedTotal: number }> => {
    const response = await api.get('/api/me/balances/summary');
    return response.data;
};

export const removeMember = async (groupId: number | string, userId: number | string) => {
  return api.delete(`/api/groups/${groupId}/members/${userId}`);
};

export const removeMembers = async (groupId: number | string, userIds: Array<number | string>) => {
  for (const uid of userIds) {
    try { await removeMember(groupId, uid); }
    catch (err: any) {
      if (err?.response?.status === 404) continue; // ไม่มีอยู่แล้ว ข้าม
      throw err;
    }
  }
};

// utils/api.ts
export const createBill = async (p: {
  groupId: number | string;
  payerUserId: number | string;   // ผู้จ่าย
  amount: number | string;
  title: string;                  // ชื่อบิล
  type?: 'EQUAL' | 'CUSTOM';
  status?: 'PENDING' | 'SETTLED';
}) => {
  const body = {
    groupId: Number(p.groupId),
    payerUserId: Number(p.payerUserId),
    amount: Number(p.amount),
    type: p.type ?? 'EQUAL',
    title: p.title,
    status: p.status ?? 'SETTLED',     // จาก Swagger ในรูป
  };

  // กันพลาด: validate ง่าย ๆ ก่อนส่ง
  if (!Number.isFinite(body.groupId) || !Number.isFinite(body.payerUserId) || !Number.isFinite(body.amount)) {
    throw new Error('Invalid payload: groupId/payerUserId/amount must be numbers');
  }

  const { data } = await api.post('/api/expenses', body, {
    headers: { 'Content-Type': 'application/json' },
  });
  return data; // ควรได้ { id, ... }
};



// Add these functions to your api.ts file

// Get settlement details for a specific expense and user
export const getSettlementDetails = async (expenseId: number, userId: number): Promise<Settlement> => {
    const response = await api.get(`/api/expenses/${expenseId}/settlement/${userId}`);
    return response.data;
};

// Submit payment for a settlement
// Replace the submitPayment function in api.ts with this corrected version:
export const submitPayment = async (
    expenseId: number, 
    fromUserId: number,
    amount: number,
    receiptFile: File // Now required
): Promise<any> => {
    const formData = new FormData();
    
    // Add receipt file (required)
    formData.append('recelpt', receiptFile); // Note: API expects 'recelpt' (typo in API)
    
    const response = await api.post(
        `/api/expenses/${expenseId}/payments?fromUserId=${fromUserId}&amount=${amount}`,
        formData
    );
    return response.data;
};

// Update the existing getPaymentDetails function to use the real API
export const getPaymentDetails = async (expenseId: string, userId: string): Promise<PaymentDetails> => {
    // First, get the settlement details for the user who needs to pay
    const settlement = await getSettlementDetails(Number(expenseId), Number(userId));

    // Then, get the expense details to find the payer
    const expenseDetails = await getBillDetails(expenseId);
    const payerId = expenseDetails.payerUserId;
    
    // Then, get user information of the payer to populate payer name and QR code
    let payerName = `User ${payerId}`;
    let qrCodeUrl = '';
    
    try {
        const payerInfo = await getUserInformation(payerId.toString());
        payerName = payerInfo.name || payerInfo.userName || payerName;
        qrCodeUrl = payerInfo.qrCodeUrl || payerInfo.qrCode || '';
    } catch (error) {
        console.error("Error fetching payer info:", error);
        // Use default values if user info fetch fails
        qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=payment-${expenseId}-${userId}`;
    }
    
    return {
        transactionId: expenseId,
        payerName: payerName,
        amountToPay: settlement.remaining, // Use the remaining amount
        qrCodeUrl: qrCodeUrl,
        // Include settlement details for reference
        expenseId: settlement.expenseId,
        userId: settlement.userId,
        owedAmount: settlement.owedAmount,
        paidAmount: settlement.paidAmount,
        settled: settlement.settled,
        remaining: settlement.remaining
    };
};

export const deleteGroup = async (groupId: string | number): Promise<any> => {
    const response = await api.delete(`/api/groups/${groupId}`);
    return response.data;
};

// Add this interface to your types file
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

// Add this function to your api.ts file
export const getExpensePayments = async (expenseId: number): Promise<Payment[]> => {
  const response = await api.get(`/api/expenses/${expenseId}/payments`);
  return response.data;
};

// Add this helper function to check if user has pending payment
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
