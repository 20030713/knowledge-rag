const API_BASE = "";
const TOKEN_KEY = "knowledge-rag-token";

export type LoginPayload = {
  username: string;
  password: string;
};

export type LoginResponse = {
  token: string;
  userId: string;
  username: string;
  role: string;
};

export type CurrentUser = {
  userId: string;
  username: string;
  role: string;
};

export type UserLoginLog = {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  message: string | null;
  createdAt: string;
};

export type UserProfile = {
  userId: string;
  username: string;
  role: string;
  createdAt: string;
  updatedAt: string;
  recentLogins: UserLoginLog[];
};

export type AdminUser = {
  userId: string;
  username: string;
  role: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  lastLoginAt: string | null;
  lastLoginSuccess: boolean | null;
};

export type AdminUserOverview = {
  totalCount: number;
  enabledCount: number;
  disabledCount: number;
  adminCount: number;
};

export type AdminLoginLog = {
  id: string;
  userId: string | null;
  username: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  message: string | null;
  createdAt: string;
};

export type AdminOperationLog = {
  id: string;
  adminUserId: string;
  adminUsername: string | null;
  targetUserId: string | null;
  targetUsername: string | null;
  action: string;
  result: string;
  detail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
};

export type KnowledgeBase = {
  id: string;
  ownerUserId: string;
  ownerUsername: string;
  name: string;
  description: string | null;
  visibility: string;
  accessRole: KnowledgeBaseRole;
  owned: boolean;
  memberCount: number;
  chunkSize: number;
  chunkOverlap: number;
  minBreakSize: number;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeBaseRole = "OWNER" | "ADMIN" | "EDITOR" | "VIEWER";

export type KnowledgeBaseMember = {
  id: string | null;
  userId: string;
  username: string;
  role: KnowledgeBaseRole;
  owner: boolean;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeBaseMemberCandidate = {
  userId: string;
  username: string;
};

export type KnowledgeBasePayload = {
  name: string;
  description?: string;
  chunkSize?: number;
  chunkOverlap?: number;
  minBreakSize?: number;
};

export type KnowledgeBaseImportResult = {
  kbId: string;
  name: string;
  documentCount: number;
  chunkCount: number;
  qaRecordCount: number;
  message: string;
};

export type DocumentItem = {
  id: string;
  kbId: string;
  fileName: string;
  fileType: string;
  fileUrl: string;
  fileSize: number;
  status: string;
  errorMsg: string | null;
  retryCount: number;
  chunkCount: number;
  parseDurationMs: number | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentChunk = {
  id: string;
  documentId: string;
  chunkNo: number;
  content: string;
  charCount: number;
  createdAt: string;
};

export type DocumentSearchResult = {
  documentId: string;
  documentName: string;
  chunkId: string;
  chunkNo: number;
  snippet: string;
  content: string;
  charCount: number;
  matchCount: number;
  createdAt: string;
};

export type DocumentQualityReport = {
  kbId: string;
  documentCount: number;
  chunkCount: number;
  duplicateDocumentGroupCount: number;
  duplicateChunkGroupCount: number;
  emptyChunkCount: number;
  oversizedChunkCount: number;
  duplicateDocumentGroups: DuplicateDocumentGroup[];
  duplicateChunkGroups: DuplicateChunkGroup[];
};

export type DuplicateDocumentGroup = {
  fileName: string;
  fileSize: number;
  count: number;
  documents: DuplicateDocumentItem[];
};

export type DuplicateDocumentItem = {
  documentId: string;
  fileName: string;
  status: string;
};

export type DuplicateChunkGroup = {
  fingerprint: string;
  snippet: string;
  charCount: number;
  count: number;
  chunks: DuplicateChunkItem[];
};

export type DuplicateChunkItem = {
  chunkId: string;
  documentId: string;
  documentName: string;
  chunkNo: number;
};

export type DocumentParseResult = {
  documentId: string;
  status: string;
  chunkCount: number;
};

export type DocumentBatchResult = {
  total: number;
  submitted: number;
  deleted: number;
  skipped: number;
  messages: string[];
};

export type DocumentStatusResult = {
  documentId: string;
  status: string;
  errorMsg: string | null;
  retryCount: number;
  chunkCount: number;
  parseDurationMs: number | null;
  progress: DocumentParseProgress | null;
};

export type DocumentParseProgress = {
  documentId: string;
  stage: string;
  percent: number;
  message: string;
  processedChunks: number;
  totalChunks: number;
  updatedAt: string;
};

export type DocumentIndexStatus = {
  documentId: string;
  kbId: string;
  fileName: string;
  documentStatus: string;
  chunkCount: number;
  embeddingCount: number;
  vectorStoreCount: number;
  embeddingModel: string;
  vectorBackend: string;
  status: string;
};

export type KnowledgeBaseIndexStatus = {
  kbId: string;
  documentCount: number;
  chunkCount: number;
  embeddingCount: number;
  vectorStoreCount: number;
  vectorBackend: string;
  status: string;
  documents: DocumentIndexStatus[];
};

export type VectorSyncResult = {
  chunkCount: number;
  syncedCount: number;
  skippedCount: number;
  vectorBackend: string;
  status: string;
};

export type RagCitation = {
  chunkId: string;
  documentId: string;
  documentName: string;
  chunkNo: number;
  content: string;
  score: number;
  vectorScore?: number;
  keywordScore?: number;
};

export type RagAnswer = {
  kbId: string;
  sessionId?: string | null;
  question: string;
  answer: string;
  hitCount: number;
  citations: RagCitation[];
  answerStyle?: string | null;
  answerSource?: string | null;
  modelName?: string | null;
  latencyMs?: number | null;
  cacheHit?: boolean | null;
  fallback?: boolean | null;
  feedbackScore?: number | null;
  feedbackNote?: string | null;
  feedbackAt?: string | null;
};

export type ChatSession = {
  id: string;
  kbId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type ChatMessage = {
  id: string;
  sessionId: string;
  role: "USER" | "ASSISTANT" | string;
  content: string;
  createdAt: string;
};

export type HotQuestion = {
  question: string;
  score: number;
};

export type RagDebugChunk = {
  chunkId: string;
  documentId: string;
  documentName: string;
  chunkNo: number;
  charCount: number;
  vectorScore: number;
  keywordScore: number;
  finalScore: number;
  matchedKeywords: string[];
  content: string;
};

export type RagDebugResult = {
  kbId: string;
  question: string;
  topK: number;
  vectorWeight: number;
  keywordWeight: number;
  cacheHit: boolean;
  answerMode: string;
  vectorBackend: string;
  latencyMs: number;
  queryTerms: string[];
  chunks: RagDebugChunk[];
};

export type AnswerStyle = "STRICT" | "BRIEF" | "INTERVIEW";

export type RagPreference = {
  defaultAnswerStyle: AnswerStyle;
  defaultTopK: number;
  vectorWeight: number;
  keywordWeight: number;
  enableModel: boolean;
  enableCache: boolean;
};

export type PromptTemplate = {
  id: string;
  kbId: string;
  name: string;
  answerStyle: AnswerStyle;
  systemPrompt: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PromptTemplatePayload = {
  name: string;
  answerStyle: AnswerStyle;
  systemPrompt: string;
  enabled: boolean;
};

export type RagEvalCase = {
  id: string;
  kbId: string;
  question: string;
  expectedAnswer: string;
  expectedKeywords: string | null;
  expectedSource: string | null;
  expectNoAnswer: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type RagEvalCasePayload = {
  question: string;
  expectedAnswer: string;
  expectedKeywords?: string | null;
  expectedSource?: string | null;
  expectNoAnswer?: boolean;
  enabled: boolean;
};

export type RagEvalRun = {
  id: string;
  caseId: string;
  question: string;
  answer: string;
  hitCount: number;
  keywordScore: number;
  noAnswerCase: boolean;
  retrievalHit: boolean | null;
  reciprocalRank: number | null;
  citationPrecision: number | null;
  abstentionCorrect: boolean | null;
  passed: boolean;
  latencyMs: number | null;
  createdAt: string;
};

export type RagEvalSummary = {
  kbId: string;
  totalCount: number;
  passedCount: number;
  passRate: number;
  averageKeywordScore: number;
  retrievalHitRate: number;
  meanReciprocalRank: number;
  averageCitationPrecision: number;
  abstentionAccuracy: number;
  averageLatencyMs: number;
  runs: RagEvalRun[];
};

export type QaRecord = RagAnswer & {
  id: string;
  createdAt: string;
};

export type RagStreamHandlers = {
  onCitations?: (citations: RagCitation[]) => void;
  onDelta?: (content: string) => void;
  onComplete?: (answer: RagAnswer) => void;
  onError?: (message: string) => void;
};

export type RecentQuestion = {
  id: string;
  kbId: string;
  question: string;
  hitCount: number;
  createdAt: string;
};

export type DocumentTask = {
  id: string;
  kbId: string;
  fileName: string;
  status: string;
  errorMsg: string | null;
  retryCount: number;
  chunkCount: number;
  parseDurationMs: number | null;
  queueStatus: string | null;
  queueRetryCount: number;
  queueErrorMsg: string | null;
  queueAvailableAt: string | null;
  queueStartedAt: string | null;
  queueFinishedAt: string | null;
  updatedAt: string;
};

export type TaskLog = {
  id: string;
  documentId: string;
  taskType: string;
  status: string;
  message: string;
  durationMs: number | null;
  createdAt: string;
};

export type DashboardOverview = {
  knowledgeBaseCount: number;
  documentCount: number;
  chunkCount: number;
  qaCount: number;
  completedDocumentCount: number;
  failedDocumentCount: number;
  uploadedDocumentCount: number;
  parsingDocumentCount: number;
  feedbackCount: number;
  helpfulFeedbackCount: number;
  unhelpfulFeedbackCount: number;
  recentQuestions: RecentQuestion[];
  recentTasks: DocumentTask[];
};

export type SystemComponentHealth = {
  name: string;
  status: string;
  message: string;
  latencyMs: number | null;
};

export type SystemHealth = {
  status: string;
  checkedAt: string;
  components: SystemComponentHealth[];
};

export type TodayMetrics = {
  qaCount: number;
  modelAnswerCount: number;
  localAnswerCount: number;
  fallbackCount: number;
  highLatencyCount: number;
  averageLatencyMs: number;
  modelSuccessRate: number;
  cacheEnabled: boolean;
  cacheTtlMinutes: number;
  rateLimitEnabled: boolean;
  ragAskLimit: number;
  ragAskWindowSeconds: number;
};

export type RuntimeConfigItem = {
  key: string;
  label: string;
  status: string;
  summary: string;
  details: string[];
};

export type RuntimeConfig = {
  checkedAt: string;
  items: RuntimeConfigItem[];
};

export type DiagnosticItem = {
  key: string;
  label: string;
  status: string;
  message: string;
  latencyMs: number | null;
  details: string[];
};

export type SystemDiagnostics = {
  status: string;
  checkedAt: string;
  items: DiagnosticItem[];
};

export type RagQualityOverview = {
  kbId: string;
  totalCount: number;
  feedbackCount: number;
  helpfulCount: number;
  unhelpfulCount: number;
  noFeedbackCount: number;
  modelAnswerCount: number;
  localAnswerCount: number;
  fallbackCount: number;
  noCitationCount: number;
  highLatencyCount: number;
  averageLatencyMs: number;
  qualityScore: number;
};

export type RagQualityIssueType = "ALL" | "UNHELPFUL" | "FALLBACK" | "NO_CITATION" | "HIGH_LATENCY" | "NO_FEEDBACK";

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
  timestamp: string;
};

export type ApiErrorType = "UNAUTHORIZED" | "FORBIDDEN" | "NETWORK" | "SERVER" | "BUSINESS";

export const AUTH_EXPIRED_EVENT = "knowledge-rag-auth-expired";

export class ApiError extends Error {
  type: ApiErrorType;
  status: number;
  code?: number;
  rawMessage?: string;

  constructor(type: ApiErrorType, message: string, status = 0, code?: number, rawMessage?: string) {
    super(message);
    this.name = "ApiError";
    this.type = type;
    this.status = status;
    this.code = code;
    this.rawMessage = rawMessage;
  }
}

export function getToken() {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) return token;

  const legacyToken = localStorage.getItem(TOKEN_KEY);
  if (legacyToken) {
    sessionStorage.setItem(TOKEN_KEY, legacyToken);
    localStorage.removeItem(TOKEN_KEY);
  }
  return legacyToken;
}

export function setToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token);
  localStorage.removeItem(TOKEN_KEY);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);
}

function emitAuthExpired(message: string) {
  clearToken();
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { message } }));
}

function classifyStatus(status: number, code?: number): ApiErrorType {
  if (status === 401 || code === 401) return "UNAUTHORIZED";
  if (status === 403 || code === 403) return "FORBIDDEN";
  if (status >= 500 || code === 500) return "SERVER";
  return "BUSINESS";
}

function friendlyMessage(type: ApiErrorType, rawMessage?: string) {
  const normalized = (rawMessage ?? "").trim();
  if (type === "UNAUTHORIZED") {
    if (normalized.toLowerCase().includes("disabled")) {
      return "\u8d26\u53f7\u5df2\u88ab\u7ba1\u7406\u5458\u7981\u7528\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002";
    }
    return "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002";
  }
  if (type === "FORBIDDEN") {
    if (normalized.toLowerCase().includes("disabled")) {
      return "\u8d26\u53f7\u5df2\u88ab\u7ba1\u7406\u5458\u7981\u7528\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002";
    }
    return "\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u6743\u9650\u6267\u884c\u6b64\u64cd\u4f5c\u3002";
  }
  if (type === "NETWORK") {
    return "\u65e0\u6cd5\u8fde\u63a5\u540e\u7aef\uff0c\u8bf7\u68c0\u67e5 IDEA \u540e\u7aef\u662f\u5426\u5df2\u542f\u52a8\u3001\u7aef\u53e3\u662f\u5426\u4e3a 8080\u3001\u524d\u7aef\u4ee3\u7406\u662f\u5426\u751f\u6548\u3002";
  }
  if (type === "SERVER") {
    return "\u540e\u7aef\u670d\u52a1\u5f02\u5e38\uff0c\u8bf7\u67e5\u770b IDEA \u63a7\u5236\u53f0\u65e5\u5fd7\u3002";
  }
  if (!normalized || normalized === "系统异常" || normalized === "绯荤粺寮傚父") {
    return "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
  }
  return normalized;
}
function apiError(type: ApiErrorType, status: number, code?: number, rawMessage?: string) {
  const message = friendlyMessage(type, rawMessage);
  if (type === "UNAUTHORIZED") {
    emitAuthExpired(message);
  }
  return new ApiError(type, message, status, code, rawMessage);
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    const raw = await response.text();
    throw apiError(classifyStatus(response.status), response.status, undefined, raw);
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.code !== 0) {
    throw apiError(classifyStatus(response.status, envelope.code), response.status, envelope.code, envelope.message);
  }
  return envelope.data;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers
    });
    return await parseJsonResponse<T>(response);
  } catch (exception) {
    if (exception instanceof ApiError) {
      throw exception;
    }
    throw apiError("NETWORK", 0, undefined, exception instanceof Error ? exception.message : "");
  }
}

async function uploadRequest<T>(path: string, formData: FormData): Promise<T> {
  const token = getToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers,
      body: formData
    });
    return await parseJsonResponse<T>(response);
  } catch (exception) {
    if (exception instanceof ApiError) {
      throw exception;
    }
    throw apiError("NETWORK", 0, undefined, exception instanceof Error ? exception.message : "");
  }
}
async function downloadRequest(path: string) {
  const token = getToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { headers });
  } catch (exception) {
    throw apiError("NETWORK", 0, undefined, exception instanceof Error ? exception.message : "");
  }
  if (!response.ok) {
    const raw = await response.text();
    throw apiError(classifyStatus(response.status), response.status, undefined, raw);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") ?? "";
  const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^"]+)"?/i);
  const fileName = decodeURIComponent(match?.[1] ?? match?.[2] ?? "rag-history.csv");
  return { blob, fileName };
}

function parseSseBlock(block: string) {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim();
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  }
  return {
    event,
    data: dataLines.join("\n")
  };
}

async function streamRequest(path: string, payload: unknown, handlers: RagStreamHandlers) {
  const token = getToken();
  const headers = new Headers();
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "text/event-stream");
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload)
    });
  } catch (exception) {
    throw apiError("NETWORK", 0, undefined, exception instanceof Error ? exception.message : "");
  }
  if (!response.ok || !response.body) {
    throw apiError(classifyStatus(response.status), response.status, undefined, await response.text());
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? "";
    for (const block of blocks) {
      if (!block.trim()) continue;
      const { event, data } = parseSseBlock(block);
      if (!data) continue;
      const parsed = JSON.parse(data);
      if (event === "citations") {
        handlers.onCitations?.(parsed.citations ?? []);
      } else if (event === "delta") {
        handlers.onDelta?.(parsed.content ?? "");
      } else if (event === "complete") {
        handlers.onComplete?.(parsed as RagAnswer);
      } else if (event === "error") {
        handlers.onError?.(friendlyMessage(classifyStatus(parsed.code ?? 400, parsed.code), parsed.message));
      }
    }
  }
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
  logout() {
    return request<void>("/api/auth/logout", {
      method: "POST"
    });
  },
  me() {
    return request<CurrentUser>("/api/auth/me");
  },
  profile() {
    return request<UserProfile>("/api/auth/profile");
  },
  changePassword(oldPassword: string, newPassword: string) {
    return request<void>("/api/auth/password", {
      method: "PUT",
      body: JSON.stringify({ oldPassword, newPassword })
    });
  },
  adminUserOverview() {
    return request<AdminUserOverview>("/api/admin/users/overview");
  },
  listAdminUsers(keyword = "", limit = 50) {
    const params = new URLSearchParams({
      limit: String(limit)
    });
    if (keyword.trim()) {
      params.set("keyword", keyword.trim());
    }
    return request<AdminUser[]>(`/api/admin/users?${params.toString()}`);
  },
  updateAdminUser(userId: string, payload: Partial<Pick<AdminUser, "role" | "enabled">>) {
    return request<AdminUser>(`/api/admin/users/${userId}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  resetAdminUserPassword(userId: string, newPassword: string) {
    return request<void>(`/api/admin/users/${userId}/password`, {
      method: "PUT",
      body: JSON.stringify({ newPassword })
    });
  },
  listAdminLoginLogs(userId?: string | null, success?: boolean | null, limit = 80) {
    const params = new URLSearchParams({
      limit: String(limit)
    });
    if (userId) {
      params.set("userId", userId);
    }
    if (success !== null && success !== undefined) {
      params.set("success", String(success));
    }
    return request<AdminLoginLog[]>(`/api/admin/login-logs?${params.toString()}`);
  },
  listAdminOperationLogs(targetUserId?: string | null, action = "ALL", limit = 80) {
    const params = new URLSearchParams({
      action,
      limit: String(limit)
    });
    if (targetUserId) {
      params.set("targetUserId", targetUserId);
    }
    return request<AdminOperationLog[]>(`/api/admin/operation-logs?${params.toString()}`);
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
  updateKnowledgeBase(id: string, payload: KnowledgeBasePayload) {
    return request<KnowledgeBase>(`/api/kb/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  deleteKnowledgeBase(id: string) {
    return request<void>(`/api/kb/${id}`, {
      method: "DELETE"
    });
  },
  exportKnowledgeBaseBackup(id: string) {
    return downloadRequest(`/api/kb/${id}/backup`);
  },
  importKnowledgeBaseBackup(file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return uploadRequest<KnowledgeBaseImportResult>("/api/kb/backup/import", formData);
  },
  listKnowledgeBaseMembers(id: string) {
    return request<KnowledgeBaseMember[]>(`/api/kb/${id}/members`);
  },
  searchKnowledgeBaseMemberCandidates(id: string, keyword: string, limit = 10) {
    const params = new URLSearchParams({ keyword, limit: String(limit) });
    return request<KnowledgeBaseMemberCandidate[]>(`/api/kb/${id}/member-candidates?${params}`);
  },
  addKnowledgeBaseMember(id: string, username: string, role: KnowledgeBaseRole) {
    return request<KnowledgeBaseMember>(`/api/kb/${id}/members`, {
      method: "POST",
      body: JSON.stringify({ username, role })
    });
  },
  updateKnowledgeBaseMember(id: string, memberId: string, role: KnowledgeBaseRole) {
    return request<KnowledgeBaseMember>(`/api/kb/${id}/members/${memberId}`, {
      method: "PUT",
      body: JSON.stringify({ role })
    });
  },
  removeKnowledgeBaseMember(id: string, memberId: string) {
    return request<void>(`/api/kb/${id}/members/${memberId}`, {
      method: "DELETE"
    });
  },
  listDocuments(kbId: string) {
    return request<DocumentItem[]>(`/api/doc?kbId=${kbId}`);
  },
  uploadDocument(kbId: string, file: File) {
    const formData = new FormData();
    formData.set("kbId", String(kbId));
    formData.set("file", file);
    return uploadRequest<DocumentItem>("/api/doc/upload", formData);
  },
  parseDocument(id: string) {
    return request<DocumentParseResult>(`/api/doc/${id}/parse`, {
      method: "POST"
    });
  },
  batchParseDocuments(ids: string[]) {
    return request<DocumentBatchResult>("/api/doc/batch/parse", {
      method: "POST",
      body: JSON.stringify({ ids })
    });
  },
  batchDeleteDocuments(ids: string[]) {
    return request<DocumentBatchResult>("/api/doc/batch", {
      method: "DELETE",
      body: JSON.stringify({ ids })
    });
  },
  rebuildKnowledgeBase(kbId: string) {
    return request<DocumentBatchResult>(`/api/doc/kb/${kbId}/rebuild`, {
      method: "POST"
    });
  },
  getDocumentStatus(id: string) {
    return request<DocumentStatusResult>(`/api/doc/${id}/status`);
  },
  listDocumentChunks(id: string) {
    return request<DocumentChunk[]>(`/api/doc/${id}/chunks`);
  },
  searchDocumentChunks(kbId: string, keyword: string, limit = 30) {
    const params = new URLSearchParams({
      keyword,
      limit: String(limit)
    });
    return request<DocumentSearchResult[]>(`/api/doc/kb/${kbId}/search?${params}`);
  },
  documentQualityReport(kbId: string) {
    return request<DocumentQualityReport>(`/api/doc/kb/${kbId}/quality`);
  },
  documentIndexStatus(id: string) {
    return request<DocumentIndexStatus>(`/api/doc/${id}/index-status`);
  },
  knowledgeBaseIndexStatus(kbId: string) {
    return request<KnowledgeBaseIndexStatus>(`/api/doc/kb/${kbId}/index-status`);
  },
  syncDocumentVectors(id: string) {
    return request<VectorSyncResult>(`/api/doc/${id}/sync-vector`, {
      method: "POST"
    });
  },
  syncKnowledgeBaseVectors(kbId: string) {
    return request<VectorSyncResult>(`/api/doc/kb/${kbId}/sync-vector`, {
      method: "POST"
    });
  },
  deleteDocument(id: string) {
    return request<void>(`/api/doc/${id}`, {
      method: "DELETE"
    });
  },
  askRag(kbId: string, question: string, answerStyle?: AnswerStyle, sessionId?: string | null) {
    return request<RagAnswer>("/api/rag/ask", {
      method: "POST",
      body: JSON.stringify({ kbId, sessionId, question, answerStyle })
    });
  },
  streamAskRag(kbId: string, question: string, answerStyle: AnswerStyle | undefined, sessionId: string | null, handlers: RagStreamHandlers) {
    return streamRequest("/api/rag/ask/stream", { kbId, sessionId, question, answerStyle }, handlers);
  },
  createChatSession(kbId: string) {
    return request<ChatSession>("/api/rag/sessions", {
      method: "POST",
      body: JSON.stringify({ kbId })
    });
  },
  listChatSessions(kbId: string) {
    return request<ChatSession[]>(`/api/rag/sessions?kbId=${kbId}`);
  },
  listChatMessages(sessionId: string) {
    return request<ChatMessage[]>(`/api/rag/sessions/${sessionId}/messages`);
  },
  deleteChatSession(sessionId: string) {
    return request<void>(`/api/rag/sessions/${sessionId}`, {
      method: "DELETE"
    });
  },
  listHotQuestions(kbId: string, limit = 10) {
    return request<HotQuestion[]>(`/api/rag/hot-questions?kbId=${kbId}&limit=${limit}`);
  },
  debugRag(
    kbId: string,
    question: string,
    answerStyle?: AnswerStyle,
    options: Partial<Pick<RagPreference, "defaultTopK" | "vectorWeight" | "keywordWeight">> = {}
  ) {
    return request<RagDebugResult>("/api/rag/debug", {
      method: "POST",
      body: JSON.stringify({
        kbId,
        question,
        answerStyle,
        topK: options.defaultTopK,
        vectorWeight: options.vectorWeight,
        keywordWeight: options.keywordWeight
      })
    });
  },
  clearRagCache(kbId: string) {
    return request<void>(`/api/rag/cache?kbId=${kbId}`, {
      method: "DELETE"
    });
  },
  listQaHistory(kbId: string) {
    return request<QaRecord[]>(`/api/rag/history?kbId=${kbId}`);
  },
  exportQaHistory(kbId: string, format: "csv" | "md") {
    return downloadRequest(`/api/rag/history/export?kbId=${kbId}&format=${format}`);
  },
  deleteQaHistory(id: string) {
    return request<void>(`/api/rag/history/${id}`, {
      method: "DELETE"
    });
  },
  feedbackQaHistory(id: string, feedbackScore: number) {
    return request<QaRecord>(`/api/rag/history/${id}/feedback`, {
      method: "PUT",
      body: JSON.stringify({ feedbackScore })
    });
  },
  dashboardOverview() {
    return request<DashboardOverview>("/api/dashboard/overview");
  },
  dashboardHealth() {
    return request<SystemHealth>("/api/dashboard/health");
  },
  todayMetrics() {
    return request<TodayMetrics>("/api/dashboard/metrics/today");
  },
  runtimeConfig() {
    return request<RuntimeConfig>("/api/dashboard/runtime-config");
  },
  diagnostics() {
    return request<SystemDiagnostics>("/api/dashboard/diagnostics");
  },
  listDocumentTasks(status = "ALL", limit = 20) {
    const params = new URLSearchParams({
      status,
      limit: String(limit)
    });
    return request<DocumentTask[]>(`/api/dashboard/tasks?${params.toString()}`);
  },
  listTaskLogs(documentId: string) {
    return request<TaskLog[]>(`/api/dashboard/tasks/${documentId}/logs`);
  },
  retryDocumentTask(documentId: string) {
    return request<DocumentParseResult>(`/api/dashboard/tasks/${documentId}/retry`, {
      method: "POST"
    });
  },
  getRagPreference() {
    return request<RagPreference>("/api/preferences/rag");
  },
  updateRagPreference(payload: RagPreference) {
    return request<RagPreference>("/api/preferences/rag", {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  listPromptTemplates(kbId: string) {
    return request<PromptTemplate[]>(`/api/rag/prompt-templates?kbId=${kbId}`);
  },
  createPromptTemplate(kbId: string, payload: PromptTemplatePayload) {
    return request<PromptTemplate>(`/api/rag/prompt-templates?kbId=${kbId}`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  updatePromptTemplate(id: string, payload: PromptTemplatePayload) {
    return request<PromptTemplate>(`/api/rag/prompt-templates/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  deletePromptTemplate(id: string) {
    return request<void>(`/api/rag/prompt-templates/${id}`, {
      method: "DELETE"
    });
  },
  ragQualityOverview(kbId: string) {
    return request<RagQualityOverview>(`/api/rag/quality/overview?kbId=${kbId}`);
  },
  listRagQualityIssues(kbId: string, type: RagQualityIssueType = "ALL", limit = 20) {
    const params = new URLSearchParams({
      kbId,
      type,
      limit: String(limit)
    });
    return request<QaRecord[]>(`/api/rag/quality/issues?${params.toString()}`);
  },
  listRagEvalCases(kbId: string) {
    return request<RagEvalCase[]>(`/api/rag/evals/cases?kbId=${kbId}`);
  },
  createRagEvalCase(kbId: string, payload: RagEvalCasePayload) {
    return request<RagEvalCase>(`/api/rag/evals/cases?kbId=${kbId}`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  updateRagEvalCase(id: string, payload: RagEvalCasePayload) {
    return request<RagEvalCase>(`/api/rag/evals/cases/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  deleteRagEvalCase(id: string) {
    return request<void>(`/api/rag/evals/cases/${id}`, {
      method: "DELETE"
    });
  },
  runRagEval(kbId: string) {
    return request<RagEvalSummary>(`/api/rag/evals/run?kbId=${kbId}`, {
      method: "POST"
    });
  },
  listRagEvalRuns(kbId: string, limit = 20) {
    return request<RagEvalRun[]>(`/api/rag/evals/runs?kbId=${kbId}&limit=${limit}`);
  }
};
