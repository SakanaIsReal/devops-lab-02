// src/service/registerApi.ts
const API_BASE_URL =
  (import.meta as any)?.env?.VITE_API_BASE_URL ??
  (process as any)?.env?.REACT_APP_API_BASE_URL ??
  "http://localhost:8080";

export type RegisterPayload = {
  email: string;
  password: string;
  userName: string;
  phone: string;
};

async function toJson(res: Response) {
  const text = await res.text();
  try { return text ? JSON.parse(text) : {}; } catch { return { message: text }; }
}

export async function registerApi(payload: RegisterPayload) {
  const res = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Accept": "application/json" },
    body: JSON.stringify(payload),
  });
  const data = await toJson(res);
  if (!res.ok) throw new Error(data?.message || "Register failed");
  return data; 
}
