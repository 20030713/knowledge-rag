const API_BASE = "";
const TOKEN_KEY = "knowledge-rag-token";

export type LoginPayload = {
  username: string;
  password: string;
};

export type LoginResponse = {
  token: string;
  userId: number;
  username: string;
  role: string;
};

export type CurrentUser = {
  userId: number;
  username: string;
  role: string;
};

export type KnowledgeBase = {
  id: number;
  name: string;
  description: string | null;
  visibility: string;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeBasePayload = {
  name: string;
  description?: string;
};

export type DocumentItem = {
  id: number;
  kbId: number;
  fileName: string;
  fileType: string;
  fileUrl: string;
  fileSize: number;
  status: string;
  errorMsg: string | null;
  createdAt: string;
  updatedAt: string;
};

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
  timestamp: string;
};

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    const message = await response.text();
    throw new Error(message || "请求失败，请检查后端服务或跨域配置");
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.code !== 0) {
    throw new Error(envelope.message || "请求失败");
  }
  return envelope.data;
}

async function uploadRequest<T>(path: string, formData: FormData): Promise<T> {
  const token = getToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers,
    body: formData
  });

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    const message = await response.text();
    throw new Error(message || "请求失败，请检查后端服务或跨域配置");
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.code !== 0) {
    throw new Error(envelope.message || "请求失败");
  }
  return envelope.data;
}

export const api = {
  register(payload: LoginPayload) {
    return request<CurrentUser>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  login(payload: LoginPayload) {
    return request<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  me() {
    return request<CurrentUser>("/api/auth/me");
  },
  listKnowledgeBases() {
    return request<KnowledgeBase[]>("/api/kb");
  },
  createKnowledgeBase(payload: KnowledgeBasePayload) {
    return request<KnowledgeBase>("/api/kb", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  updateKnowledgeBase(id: number, payload: KnowledgeBasePayload) {
    return request<KnowledgeBase>(`/api/kb/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  deleteKnowledgeBase(id: number) {
    return request<void>(`/api/kb/${id}`, {
      method: "DELETE"
    });
  },
  listDocuments(kbId: number) {
    return request<DocumentItem[]>(`/api/doc?kbId=${kbId}`);
  },
  uploadDocument(kbId: number, file: File) {
    const formData = new FormData();
    formData.set("kbId", String(kbId));
    formData.set("file", file);
    return uploadRequest<DocumentItem>("/api/doc/upload", formData);
  }
};
