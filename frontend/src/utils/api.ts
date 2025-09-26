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
    const response = await api.get('api/groups');
    return response.data;
};

export const getGroupDetails = async (groupId: string): Promise<any> => {
    const response = await api.get(`api/groups/${groupId}`);
    return response.data;
};

export const getGroupTransactions = async (groupId: string): Promise<any[]> => {
    const response = await api.get(`/groups/${groupId}/transactions`);
    return response.data;
};

export const searchUsers = async (q: string): Promise<User[]> => {
  const response = await api.get("api/users/search", { params: { q } }); // ไม่ต้องต่อ ?q= เอง
  return response.data;
};

export const groupmember = async (groupId: string): Promise<User[]> => {
  const response = await api.post(`/api/groups/${groupId}/members`);
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
