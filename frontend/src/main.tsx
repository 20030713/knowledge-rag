import React, { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BookOpen,
  Bot,
  Check,
  ChevronRight,
  Database,
  FileText,
  Loader2,
  LogOut,
  MessageSquareText,
  Pencil,
  Plus,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Trash2,
  UploadCloud,
  X
} from "lucide-react";
import {
  api,
  AdminLoginLog,
  AdminOperationLog,
  AdminUser,
  AdminUserOverview,
  AnswerStyle,
  AUTH_EXPIRED_EVENT,
  clearToken,
  CurrentUser,
  DashboardOverview,
  DocumentChunk,
  DocumentIndexStatus,
  DocumentItem,
  DocumentSearchResult,
  DocumentTask,
  getToken,
  KnowledgeBase,
  KnowledgeBaseIndexStatus,
  KnowledgeBaseMember,
  KnowledgeBaseMemberCandidate,
  KnowledgeBaseRole,
  PromptTemplate,
  PromptTemplatePayload,
  QaRecord,
  RagAnswer,
  RagDebugResult,
  RagPreference,
  RagQualityIssueType,
  RagQualityOverview,
  RuntimeConfig,
  setToken,
  SystemDiagnostics,
  SystemHealth,
  TaskLog,
  TodayMetrics,
  UserProfile
} from "./api";
import "./styles.css";

type AuthMode = "login" | "register";
type WorkspaceTab = "documents" | "chat" | "rag" | "tasks" | "monitor" | "admin";
type DocumentStatusFilter = "ALL" | "UPLOADED" | "PARSING" | "COMPLETED" | "FAILED";

const LAST_USERNAME_KEY = "knowledge-rag-last-username";
const LAST_PASSWORD_KEY = "knowledge-rag-last-password";
const REMEMBER_PASSWORD_KEY = "knowledge-rag-remember-password";

function App() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [booting, setBooting] = useState(true);
  const [authMessage, setAuthMessage] = useState("");

  useEffect(() => {
    function onExpired(event: Event) {
      const detail = event instanceof CustomEvent ? event.detail as { message?: string } : null;
      clearToken();
      setUser(null);
      setAuthMessage(detail?.message ?? "登录已过期，请重新登录。");
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      setBooting(false);
      return;
    }
    api.me().then(setUser).catch((error) => {
      clearToken();
      setAuthMessage(error instanceof Error ? error.message : "登录已过期，请重新登录。");
    }).finally(() => setBooting(false));
  }, []);

  if (booting) return <div className="screen-loader"><Loader2 className="spin" size={28} /></div>;
  if (!user) {
    return <AuthScreen initialError={authMessage} onAuthed={(next) => {
      setAuthMessage("");
      setUser(next);
    }} />;
  }
  return <Workspace user={user} onLogout={async () => {
    try { await api.logout(); } catch { /* local logout still wins */ }
    clearToken();
    setUser(null);
  }} />;
}

function AuthScreen({ initialError = "", onAuthed }: { initialError?: string; onAuthed: (user: CurrentUser) => void }) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState(() => localStorage.getItem(LAST_USERNAME_KEY) ?? "demo_user");
  const [rememberPassword, setRememberPassword] = useState(() => localStorage.getItem(REMEMBER_PASSWORD_KEY) === "true");
  const [password, setPassword] = useState(() => localStorage.getItem(REMEMBER_PASSWORD_KEY) === "true" ? localStorage.getItem(LAST_PASSWORD_KEY) ?? "123456" : "123456");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(initialError);

  useEffect(() => setError(initialError), [initialError]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      if (mode === "register") await api.register({ username, password });
      const login = await api.login({ username, password });
      setToken(login.token);
      localStorage.setItem(LAST_USERNAME_KEY, login.username);
      if (rememberPassword) {
        localStorage.setItem(REMEMBER_PASSWORD_KEY, "true");
        localStorage.setItem(LAST_PASSWORD_KEY, password);
      } else {
        localStorage.removeItem(REMEMBER_PASSWORD_KEY);
        localStorage.removeItem(LAST_PASSWORD_KEY);
      }
      onAuthed({ userId: login.userId, username: login.username, role: login.role });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-copy">
        <div className="product-mark"><Database size={22} /><span>企业知识工作台</span></div>
        <h1>企业知识库问答工作台</h1>
        <p>让团队在清晰的知识边界内提问、核验来源并共同维护可信内容。</p>
        <div className="auth-metrics" aria-label="项目模块">
          <div><strong>来源</strong><span>可信引用</span></div>
          <div><strong>边界</strong><span>权限清晰</span></div>
          <div><strong>协作</strong><span>团队共建</span></div>
        </div>
      </section>
      <form className="auth-panel" onSubmit={submit}>
        <div className="segmented">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>登录</button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>注册</button>
        </div>
        <label><span>用户名</span><input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} maxLength={32} /></label>
        <label><span>密码</span><input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={6} maxLength={64} /></label>
        <label className="check-row"><input type="checkbox" checked={rememberPassword} onChange={(event) => setRememberPassword(event.target.checked)} /><span>记住密码</span></label>
        {error && <div className="inline-error">{error}</div>}
        <button className="primary-action" type="submit" disabled={loading}>{loading ? <Loader2 className="spin" size={18} /> : <ShieldCheck size={18} />}{mode === "login" ? "进入工作台" : "创建账号"}</button>
      </form>
    </main>
  );
}

function Workspace({ user, onLogout }: { user: CurrentUser; onLogout: () => void }) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tab, setTab] = useState<WorkspaceTab>("chat");
  const [kbQuery, setKbQuery] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const backupInputRef = useRef<HTMLInputElement | null>(null);
  const selected = useMemo(() => knowledgeBases.find((item) => item.id === selectedId) ?? knowledgeBases[0] ?? null, [knowledgeBases, selectedId]);
  const canAdmin = canAdminKnowledgeBase(selected);
  const filtered = useMemo(() => {
    const keyword = kbQuery.trim().toLowerCase();
    return keyword ? knowledgeBases.filter((item) => item.name.toLowerCase().includes(keyword) || (item.description ?? "").toLowerCase().includes(keyword)) : knowledgeBases;
  }, [knowledgeBases, kbQuery]);

  useEffect(() => { void refreshKnowledgeBases(); }, []);
  useEffect(() => { setName(selected?.name ?? ""); setDescription(selected?.description ?? ""); }, [selected?.id]);

  async function refreshKnowledgeBases() {
    setLoading(true);
    setError("");
    try {
      const list = await api.listKnowledgeBases();
      setKnowledgeBases(list);
      setSelectedId((current) => current && list.some((item) => item.id === current) ? current : list[0]?.id ?? null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  async function createKnowledgeBase() {
    setSaving(true);
    try {
      const created = await api.createKnowledgeBase({ name: "新的知识库", description: "从工作台创建" });
      setKnowledgeBases((items) => [created, ...items]);
      setSelectedId(created.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setSaving(false);
    }
  }

  async function saveSelected(event: FormEvent) {
    event.preventDefault();
    if (!selected || !canAdmin) return;
    setSaving(true);
    try {
      const updated = await api.updateKnowledgeBase(selected.id, { name, description });
      setKnowledgeBases((items) => items.map((item) => item.id === updated.id ? updated : item));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteSelected() {
    if (!selected || selected.accessRole !== "OWNER" || !window.confirm("确认删除这个知识库吗？")) return;
    setSaving(true);
    try {
      await api.deleteKnowledgeBase(selected.id);
      setKnowledgeBases((items) => items.filter((item) => item.id !== selected.id));
      setSelectedId(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setSaving(false);
    }
  }

  async function exportSelectedBackup() {
    if (!selected) return;
    try {
      const { blob, fileName } = await api.exportKnowledgeBaseBackup(selected.id);
      downloadBlob(blob, fileName);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    }
  }

  async function importBackup(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      await api.importKnowledgeBaseBackup(file);
      await refreshKnowledgeBases();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      event.target.value = "";
    }
  }

  return (
    <div className="workspace-shell cobalt-shell">
      <aside className="app-rail" aria-label="全局导航">
        <div className="rail-brand" title="企业知识库">知</div>
        <nav>
          <button className={tab === "chat" ? "active" : ""} onClick={() => setTab("chat")} title="知识问答"><MessageSquareText size={20} /><span>问答</span></button>
          <button className={tab === "documents" ? "active" : ""} onClick={() => setTab("documents")} title="内容库"><FileText size={20} /><span>内容</span></button>
          <button className={tab === "tasks" ? "active" : ""} onClick={() => setTab("tasks")} title="处理任务"><Check size={20} /><span>任务</span></button>
          {user.role === "ADMIN" && <button className={tab === "monitor" ? "active" : ""} onClick={() => setTab("monitor")} title="系统状态"><Database size={20} /><span>状态</span></button>}
        </nav>
        <div className="rail-bottom">
          {user.role === "ADMIN" && <button className={tab === "rag" ? "active" : ""} onClick={() => setTab("rag")} title="RAG 配置"><Bot size={20} /><span>RAG</span></button>}
          {user.role === "ADMIN" && <button className={tab === "admin" ? "active" : ""} onClick={() => setTab("admin")} title="系统管理"><ShieldCheck size={20} /><span>管理</span></button>}
          <button onClick={() => setProfileOpen(true)} title="个人中心"><span className="rail-avatar">{user.username.slice(0, 1).toUpperCase()}</span></button>
        </div>
      </aside>

      <aside className="sidebar knowledge-context">
        <div className="context-heading">
          <div><span>ENTERPRISE KNOWLEDGE</span><strong>你的知识工作区</strong></div>
        </div>
        <div className="active-scope">
          <div className="scope-monogram" aria-hidden="true">{selected?.name.trim().slice(0, 1).toUpperCase() || "知"}</div>
          <div className="scope-copy"><strong>{selected?.name ?? "尚未选择"}</strong><p>{selected?.description || "选择一个知识库后开始提问。"}</p></div>
          <ChevronRight size={14} />
        </div>
        <button className="new-conversation" onClick={() => setTab("chat")}><Plus size={18} />开始新对话</button>
        <div className="context-section-title"><span>RECENT KNOWLEDGE</span><small>{knowledgeBases.length}</small></div>
        <div className="search-box"><Search size={16} /><input value={kbQuery} onChange={(event) => setKbQuery(event.target.value)} placeholder="搜索知识库" /></div>
        <div className="kb-list">
          {loading && <div className="muted-row"><Loader2 className="spin" size={16} />正在加载...</div>}
          {!loading && knowledgeBases.length === 0 && <button className="empty-kb" onClick={createKnowledgeBase}>创建第一个知识库</button>}
          {filtered.map((item) => <button key={item.id} className={"kb-item " + (selected?.id === item.id ? "active" : "")} onClick={() => setSelectedId(item.id)}><BookOpen size={17} /><span>{item.name}</span><em className={"kb-role role-" + item.accessRole.toLowerCase()}>{roleLabel(item.accessRole)}</em><ChevronRight size={15} /></button>)}
        </div>
        <button className="manage-scope" onClick={() => setSettingsOpen(true)} disabled={!selected}><Settings size={17} /><span><strong>知识空间管理</strong><small>文档、成员与任务状态</small></span><ChevronRight size={15} /></button>
      </aside>

      <section className="main-panel">
        <header className="topbar">
          <div className="topbar-title"><strong>知识 AI</strong><div className="status-pill"><Check size={13} />仅检索你有权限访问的内容</div></div>
          <div className="topbar-actions"><button className="evidence-nav" onClick={() => setTab("chat")}><BookOpen size={16} />证据舱</button><button className="topbar-settings" onClick={() => setSettingsOpen(true)} disabled={!selected}><Settings size={17} />管理</button><button className="topbar-logout" onClick={onLogout} title="退出登录"><LogOut size={17} /></button></div>
        </header>
        {error && <div className="inline-error workspace-error">{error}</div>}
        <section className="work-panel">
          {tab === "documents" && <DocumentStage selected={selected} />}
          {tab === "chat" && <ChatStage selected={selected} canDebug={user.role === "ADMIN"} />}
          {tab === "rag" && <RagStage selected={selected} />}
          {tab === "tasks" && <TaskCenterStage />}
          {tab === "monitor" && <MonitorStage />}
          {tab === "admin" && <AdminStage />}
        </section>
      </section>

      {settingsOpen && <div className="settings-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setSettingsOpen(false); }}>
        <section className="editor-panel settings-drawer">
          <header><div><span>知识库设置</span><strong>{selected?.name}</strong></div><button type="button" className="icon-button" onClick={() => setSettingsOpen(false)} aria-label="关闭设置"><X size={20} /></button></header>
          <div className="drawer-scroll">
            <form className="drawer-basic-form" onSubmit={saveSelected}>
              <div className="panel-title"><Pencil size={18} /><span>基础信息</span></div>
              <label><span>名称</span><input value={name} onChange={(event) => setName(event.target.value)} disabled={!selected || !canAdmin} maxLength={128} /></label>
              <label><span>描述</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} disabled={!selected || !canAdmin} maxLength={512} /></label>
              {selected && <div className="permission-summary"><span>{roleLabel(selected.accessRole)}</span><strong>{selected.owned ? "拥有者" : selected.ownerUsername}</strong><small>{selected.memberCount} 个成员</small></div>}
              <div className="editor-actions">
                <button className="primary-action compact" type="submit" disabled={!selected || !canAdmin || saving}>{saving ? <Loader2 className="spin" size={17} /> : <Check size={17} />}保存</button>
                <button type="button" onClick={exportSelectedBackup} disabled={!selected}>导出</button>
                <button type="button" onClick={() => backupInputRef.current?.click()}>导入</button>
                <input ref={backupInputRef} type="file" accept=".json,application/json" onChange={importBackup} hidden />
                <button className="danger-action" type="button" onClick={deleteSelected} disabled={!selected || selected.accessRole !== "OWNER" || saving}><Trash2 size={17} />删除</button>
              </div>
            </form>
            <SharingPanel selected={selected} onMembersChanged={refreshKnowledgeBases} />
          </div>
        </section>
      </div>}
      {profileOpen && <UserCenter user={user} onClose={() => setProfileOpen(false)} />}
    </div>
  );
}

function SharingPanel({ selected, onMembersChanged }: { selected: KnowledgeBase | null; onMembersChanged: () => Promise<void> }) {
  const [members, setMembers] = useState<KnowledgeBaseMember[]>([]);
  const [candidates, setCandidates] = useState<KnowledgeBaseMemberCandidate[]>([]);
  const [selectedCandidate, setSelectedCandidate] = useState<KnowledgeBaseMemberCandidate | null>(null);
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<KnowledgeBaseRole>("VIEWER");
  const [loading, setLoading] = useState(false);
  const [searchingCandidates, setSearchingCandidates] = useState(false);
  const [candidateOpen, setCandidateOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const canManage = canAdminKnowledgeBase(selected);

  useEffect(() => { if (selected) void loadMembers(selected.id); else setMembers([]); }, [selected?.id]);
  useEffect(() => {
    const keyword = username.trim();
    if (!selected || !canManage || keyword.length < 2) {
      setCandidates([]);
      setSearchingCandidates(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setSearchingCandidates(true);
      api.searchKnowledgeBaseMemberCandidates(selected.id, keyword)
        .then((items) => { if (!cancelled) setCandidates(items); })
        .catch((exception) => { if (!cancelled) setError(exception instanceof Error ? exception.message : "搜索用户失败"); })
        .finally(() => { if (!cancelled) setSearchingCandidates(false); });
    }, 250);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [selected?.id, canManage, username]);

  async function loadMembers(kbId = selected?.id) {
    if (!kbId) return;
    setLoading(true);
    try { setMembers(await api.listKnowledgeBaseMembers(kbId)); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setLoading(false); }
  }
  async function addMember(event: FormEvent) {
    event.preventDefault();
    if (!selected || !canManage) return;
    const targetCandidate = selectedCandidate ?? (candidates.length === 1 ? candidates[0] : null);
    if (!targetCandidate) {
      setError(candidates.length > 1 ? "请先从搜索结果中选择一个用户" : "没有可添加的用户");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const added = await api.addKnowledgeBaseMember(selected.id, targetCandidate.username, role);
      setUsername("");
      setSelectedCandidate(null);
      setRole("VIEWER");
      setCandidates([]);
      setCandidateOpen(false);
      setMessage(`已添加 ${added.username}`);
      await loadMembers(selected.id);
      await onMembersChanged();
    } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setSaving(false); }
  }
  async function updateMember(member: KnowledgeBaseMember, nextRole: KnowledgeBaseRole) {
    if (!selected || !member.id || !canManage) return;
    setSaving(true);
    try { await api.updateKnowledgeBaseMember(selected.id, member.id, nextRole); await loadMembers(selected.id); await onMembersChanged(); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setSaving(false); }
  }
  async function removeMember(member: KnowledgeBaseMember) {
    if (!selected || !member.id || !canManage || !window.confirm("确认移除这个成员吗？")) return;
    setSaving(true);
    try { await api.removeKnowledgeBaseMember(selected.id, member.id); await loadMembers(selected.id); await onMembersChanged(); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setSaving(false); }
  }

  return (
    <section className="sharing-panel">
      <div className="panel-title"><ShieldCheck size={18} /><span>成员管理</span></div>
      {error && <div className="inline-error">{error}</div>}
      {message && <div className="success-banner compact-banner">{message}</div>}
      <form className="member-form" onSubmit={addMember}>
        <div className="member-picker">
          <input
            value={username}
            onChange={(event) => { setUsername(event.target.value); setSelectedCandidate(null); setCandidateOpen(true); setError(""); setMessage(""); }}
            onFocus={() => setCandidateOpen(true)}
            disabled={!selected || !canManage || saving}
            placeholder="搜索已注册用户"
            autoComplete="off"
          />
          {candidateOpen && username.trim().length >= 2 && <div className="member-suggestions" role="listbox" aria-label="可添加成员">
            {searchingCandidates && <span><Loader2 className="spin" size={14} />正在搜索...</span>}
            {!searchingCandidates && candidates.length === 0 && <span>没有可添加的已注册用户</span>}
            {!searchingCandidates && candidates.map((candidate) => <button
              type="button"
              className="member-suggestion"
              key={candidate.userId}
              onClick={() => { setUsername(candidate.username); setSelectedCandidate(candidate); setCandidateOpen(false); setError(""); }}
            >
              <strong>{candidate.username}</strong><small>选择</small>
            </button>)}
          </div>}
        </div>
        <select value={role} onChange={(event) => setRole(event.target.value as KnowledgeBaseRole)} disabled={!selected || !canManage || saving}>{roleOptions(false)}</select>
        <button type="submit" disabled={!selected || !canManage || saving || (!selectedCandidate && candidates.length !== 1)}>添加成员</button>
      </form>
      <div className="member-list">
        {loading && <div className="muted-row"><Loader2 className="spin" size={15} />正在加载成员...</div>}
        {!loading && members.length === 0 && <div className="muted-row">暂无成员</div>}
        {members.map((member) => <div className="member-row" key={member.userId}><div><strong>{member.username}</strong><span>{member.owner ? "拥有者" : roleLabel(member.role)}</span></div>{member.owner ? <em>拥有者</em> : <div className="member-actions"><select value={member.role} onChange={(event) => updateMember(member, event.target.value as KnowledgeBaseRole)} disabled={!canManage || saving}>{roleOptions(false)}</select><button type="button" onClick={() => removeMember(member)} disabled={!canManage || saving}>移除</button></div>}</div>)}
      </div>
    </section>
  );
}

function DocumentStage({ selected }: { selected: KnowledgeBase | null }) {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [chunks, setChunks] = useState<DocumentChunk[]>([]);
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [operatingId, setOperatingId] = useState<string | null>(null);
  const [showAllChunks, setShowAllChunks] = useState(false);
  const [chunkQuery, setChunkQuery] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<DocumentSearchResult[]>([]);
  const [statusFilter, setStatusFilter] = useState<DocumentStatusFilter>("ALL");
  const [indexStatus, setIndexStatus] = useState<KnowledgeBaseIndexStatus | null>(null);
  const [error, setError] = useState("");
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const canEdit = canEditKnowledgeBase(selected);

  useEffect(() => { setDocuments([]); setChunks([]); setSelectedDocumentId(null); if (selected) void loadDocuments(); }, [selected?.id]);
  const parsingDocumentIds = useMemo(
    () => documents
      .filter((document) => normalizeCode(document.status) === "PARSING")
      .map((document) => document.id)
      .join(","),
    [documents]
  );

  useEffect(() => {
    if (!selected || !parsingDocumentIds) return;
    const timer = window.setInterval(() => { void loadDocuments({ silent: true }); }, 2000);
    return () => window.clearInterval(timer);
  }, [selected?.id, parsingDocumentIds]);

  async function loadDocuments(options: { silent?: boolean } = {}) { if (!selected) return; if (!options.silent) setLoading(true); try { setDocuments(await api.listDocuments(selected.id)); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { if (!options.silent) setLoading(false); } }
  async function uploadDocument(event: React.ChangeEvent<HTMLInputElement>) { const file = event.target.files?.[0]; if (!selected || !file) return; setError(""); setUploading(true); try { await api.uploadDocument(selected.id, file); await loadDocuments(); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { event.target.value = ""; setUploading(false); } }
  async function parseDocument(id: string) { setError(""); setOperatingId(id); setSelectedDocumentId(id); try { await api.parseDocument(id); await loadDocuments({ silent: true }); await waitForDocumentParse(id); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setOperatingId(null); } }
  async function waitForDocumentParse(id: string) {
    for (let attempt = 0; attempt < 35; attempt++) {
      await sleep(attempt === 0 ? 600 : 1400);
      const status = await api.getDocumentStatus(id);
      await loadDocuments({ silent: true });
      const code = normalizeCode(status.status);
      if (code === "COMPLETED" || code === "DONE") {
        await loadChunks(id);
        await loadIndexStatus();
        return;
      }
      if (code === "FAILED") {
        setError(status.errorMsg || status.progress?.message || "文档解析失败，请查看任务日志。");
        return;
      }
    }
    setError("解析任务仍在后台执行，可稍后点击刷新或进入任务中心查看进度。");
  }
  async function deleteDocument(id: string) { if (!window.confirm("确认删除这个文档吗？")) return; setOperatingId(id); try { await api.deleteDocument(id); if (selectedDocumentId === id) { setSelectedDocumentId(null); setChunks([]); } await loadDocuments(); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setOperatingId(null); } }
  async function loadChunks(id: string) { setError(""); setSelectedDocumentId(id); setShowAllChunks(false); setChunkQuery(""); try { setChunks(await api.listDocumentChunks(id)); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } }
  async function searchChunks(event: FormEvent) { event.preventDefault(); if (!selected || !searchQuery.trim()) return; try { setSearchResults(await api.searchDocumentChunks(selected.id, searchQuery.trim(), 20)); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } }
  async function loadIndexStatus() { if (!selected) return; try { setIndexStatus(await api.knowledgeBaseIndexStatus(selected.id)); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } }
  async function syncVectors(documentId?: string) { if (!selected) return; setOperatingId(documentId ?? "kb"); try { if (documentId) await api.syncDocumentVectors(documentId); else await api.syncKnowledgeBaseVectors(selected.id); await loadIndexStatus(); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setOperatingId(null); } }

  const filteredDocuments = documents.filter((item) => statusFilter === "ALL" || item.status === statusFilter);
  const visibleChunks = chunks.filter((chunk) => !chunkQuery.trim() || chunk.content.toLowerCase().includes(chunkQuery.trim().toLowerCase()));
  const chunkSlice = showAllChunks ? visibleChunks : visibleChunks.slice(0, 5);
  const documentStats = useMemo(() => {
    const totalChunks = documents.reduce((sum, item) => sum + item.chunkCount, 0);
    return {
      total: documents.length,
      completed: documents.filter((item) => item.status === "COMPLETED").length,
      parsing: documents.filter((item) => item.status === "PARSING").length,
      failed: documents.filter((item) => item.status === "FAILED").length,
      totalChunks
    };
  }, [documents]);

  return (
    <div className="document-stage">
      <section className="stage-overview">
        <div>
          <span>文档总数</span>
          <strong>{documentStats.total}</strong>
        </div>
        <div>
          <span>已完成</span>
          <strong>{documentStats.completed}</strong>
        </div>
        <div>
          <span>解析中</span>
          <strong>{documentStats.parsing}</strong>
        </div>
        <div>
          <span>失败</span>
          <strong>{documentStats.failed}</strong>
        </div>
        <div>
          <span>切片总数</span>
          <strong>{documentStats.totalChunks}</strong>
        </div>
      </section>
      <div className="stage-actions">
        <input ref={fileInputRef} type="file" onChange={uploadDocument} hidden />
        <button className="primary-action compact" onClick={() => fileInputRef.current?.click()} disabled={!selected || !canEdit || uploading}>{uploading ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}上传文档</button>
        <button onClick={loadDocuments} disabled={!selected || loading}>刷新</button>
        <button onClick={loadIndexStatus} disabled={!selected}>索引状态</button>
        <button onClick={() => syncVectors()} disabled={!selected || !canEdit || operatingId === "kb"}>同步知识库向量</button>
      </div>
      {error && <div className="inline-error">{error}</div>}
      <div className="filter-row"><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as DocumentStatusFilter)}>{["ALL", "UPLOADED", "PARSING", "COMPLETED", "FAILED"].map((status) => <option key={status} value={status}>{statusLabel(status as DocumentStatusFilter)}</option>)}</select></div>
      <div className="document-list">{loading && <div className="muted-document"><Loader2 className="spin" size={18} />正在加载文档...</div>}{!loading && filteredDocuments.length === 0 && <div className="muted-document">暂无文档</div>}{filteredDocuments.map((doc) => <article key={doc.id} className={"document-card " + (selectedDocumentId === doc.id ? "active" : "")}><div><strong>{doc.fileName}</strong><span>{doc.fileType} | {formatFileSize(doc.fileSize)} | {doc.chunkCount} 个切片</span><small>{doc.errorMsg}</small></div><em className={"doc-status status-" + doc.status.toLowerCase()}>{statusLabel(doc.status)}</em><div className="document-actions"><button onClick={() => loadChunks(doc.id)}>切片</button><button onClick={() => parseDocument(doc.id)} disabled={!canEdit || operatingId === doc.id}>解析</button><button onClick={() => syncVectors(doc.id)} disabled={!canEdit || operatingId === doc.id}>向量</button><button className="danger-action" onClick={() => deleteDocument(doc.id)} disabled={!canEdit || operatingId === doc.id}>删除</button></div></article>)}</div>
      {indexStatus && <VectorIndexPanel status={indexStatus} syncingId={operatingId} canSync={Boolean(selected && canEdit)} onSyncDocument={syncVectors} onSyncAll={() => syncVectors()} />}
      {selectedDocumentId && <section className="chunk-panel">
        <div className="chunk-panel-head">
          <div className="panel-title"><FileText size={18} /><span>切片预览</span><em>{visibleChunks.length} 条</em></div>
          <button className="chunk-toggle" type="button" onClick={() => setShowAllChunks((value) => !value)} disabled={visibleChunks.length <= 5}>
            {visibleChunks.length <= 5 ? "已展示全部" : showAllChunks ? "收起切片" : `展开全部 ${visibleChunks.length} 条`}
          </button>
        </div>
        <div className="search-box"><Search size={16} /><input value={chunkQuery} onChange={(event) => setChunkQuery(event.target.value)} placeholder="筛选切片" /></div>
        {chunkSlice.length === 0 && <div className="chunk-empty">暂无切片。解析完成后会自动刷新，也可以稍后点击“切片”查看。</div>}
        {chunkSlice.map((chunk) => <article className={"chunk-card " + (showAllChunks ? "expanded" : "")} key={chunk.id}><header><strong>#{chunk.chunkNo}</strong><span>{chunk.charCount} 字符</span></header><p>{renderHighlightedText(chunk.content, chunkQuery)}</p></article>)}
        {visibleChunks.length > 5 && <button className="chunk-more" type="button" onClick={() => setShowAllChunks((value) => !value)}>{showAllChunks ? "收起到前 5 条" : `继续展开 ${visibleChunks.length - chunkSlice.length} 条切片`}</button>}
      </section>}
      <form className="search-panel" onSubmit={searchChunks}><div className="panel-title"><Search size={18} /><span>检索预览</span></div><div className="search-box"><input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="搜索文档内容" /><button type="submit">搜索</button></div>{searchResults.map((result) => <article className="chunk-card" key={result.chunkId}><strong>{result.documentName} #{result.chunkNo}</strong><p>{renderHighlightedText(result.snippet || result.content, searchQuery)}</p></article>)}</form>
    </div>
  );
}

function VectorIndexPanel({
  status,
  syncingId,
  canSync,
  onSyncDocument,
  onSyncAll
}: {
  status: KnowledgeBaseIndexStatus;
  syncingId: string | null;
  canSync: boolean;
  onSyncDocument: (documentId: string) => void;
  onSyncAll: () => void;
}) {
  const embeddingPercent = percent(status.embeddingCount, status.chunkCount);
  const storePercent = percent(status.vectorStoreCount, status.chunkCount);
  const backendReady = status.vectorBackend !== "local-fallback";

  return (
    <section className="index-panel vector-index-panel">
      <div className="index-panel-head">
        <div>
          <h3>向量索引</h3>
          <p>{indexStatusDescription(status.status, status.vectorBackend)}</p>
        </div>
        <button type="button" onClick={onSyncAll} disabled={!canSync || syncingId === "kb"}>
          {syncingId === "kb" ? <Loader2 className="spin" size={15} /> : null}
          全量同步
        </button>
      </div>

      <div className="index-summary-grid">
        <VectorMetric label="向量后端" value={status.vectorBackend} tone={backendReady ? "synced" : "fallback"} />
        <VectorMetric label="文档数" value={String(status.documentCount)} tone="partial" />
        <VectorMetric label="Embedding" value={`${status.embeddingCount}/${status.chunkCount}`} percent={embeddingPercent} tone={embeddingPercent === 100 ? "synced" : "partial"} />
        <VectorMetric label="向量库" value={`${status.vectorStoreCount}/${status.chunkCount}`} percent={storePercent} tone={storePercent === 100 ? "synced" : backendReady ? "partial" : "fallback"} />
      </div>

      {!backendReady && (
        <div className="index-warning">
          当前 pgvector 不可用，检索会临时回退到本地向量索引。启动 pgvector 容器后，解析文档并点击“全量同步”即可写入向量库。
        </div>
      )}

      <div className="index-document-list">
        {status.documents.map((doc) => {
          const docStorePercent = percent(doc.vectorStoreCount, doc.chunkCount);
          const docEmbeddingPercent = percent(doc.embeddingCount, doc.chunkCount);
          const syncing = syncingId === doc.documentId;
          return (
            <article className="index-document-row" key={doc.documentId}>
              <div className="index-document-main">
                <strong>{doc.fileName}</strong>
                <span>{statusLabel(doc.documentStatus)} | 模型 {doc.embeddingModel || "-"} | 后端 {doc.vectorBackend}</span>
              </div>
              <div className="index-progress-group">
                <VectorProgress label="Embedding" value={docEmbeddingPercent} text={`${doc.embeddingCount}/${doc.chunkCount}`} />
                <VectorProgress label="入库" value={docStorePercent} text={`${doc.vectorStoreCount}/${doc.chunkCount}`} />
              </div>
              <em className={"index-status " + indexStatusTone(doc.status)}>{indexStatusLabel(doc.status)}</em>
              <button type="button" onClick={() => onSyncDocument(doc.documentId)} disabled={!canSync || syncing || doc.chunkCount === 0}>
                {syncing ? <Loader2 className="spin" size={14} /> : null}
                同步
              </button>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function VectorMetric({ label, value, percent: metricPercent, tone }: { label: string; value: string; percent?: number; tone: string }) {
  return (
    <article className="vector-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      {metricPercent !== undefined && <VectorProgress label="" value={metricPercent} text={`${metricPercent}%`} tone={tone} />}
    </article>
  );
}

function VectorProgress({ label, value, text, tone = value === 100 ? "synced" : "partial" }: { label: string; value: number; text: string; tone?: string }) {
  return (
    <div className="vector-progress">
      {label && <span>{label}</span>}
      <div className="vector-progress-track"><i className={tone} style={{ width: `${value}%` }} /></div>
      <small>{text}</small>
    </div>
  );
}

function ChatStage({ selected, canDebug = false }: { selected: KnowledgeBase | null; canDebug?: boolean }) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<RagAnswer | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [history, setHistory] = useState<QaRecord[]>([]);
  const [style, setStyle] = useState<AnswerStyle>("STRICT");
  const [loading, setLoading] = useState(false);
  const [debug, setDebug] = useState<RagDebugResult | null>(null);
  const [debugOpen, setDebugOpen] = useState(false);
  const [activeCitationId, setActiveCitationId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const answerRef = useRef<HTMLElement | null>(null);

  useEffect(() => { setAnswer(null); setSessionId(null); setDebug(null); setHistory([]); setActiveCitationId(null); if (selected) void loadHistory(); }, [selected?.id]);
  async function loadHistory() {
    if (!selected) return;
    try {
      const items = await api.listQaHistory(selected.id);
      setHistory(items);
      setSessionId((current) => current ?? items.find((item) => item.sessionId)?.sessionId ?? null);
    } catch { /* optional */ }
  }
  async function ask(event: FormEvent) {
    event.preventDefault();
    if (!selected || !question.trim()) return;
    const asked = question.trim();
    const streamingAnswer: RagAnswer = {
      kbId: selected.id,
      question: asked,
      answer: "",
      hitCount: 0,
      citations: [],
      answerStyle: style,
      answerSource: "MODEL",
      latencyMs: null,
      cacheHit: false,
      fallback: false
    };
    setLoading(true);
    setError("");
    setDebugOpen(false);
    setDebug(null);
    setActiveCitationId(null);
    setAnswer(streamingAnswer);
    try {
      await api.streamAskRag(selected.id, asked, style, sessionId, {
        onCitations: (citations) => setAnswer((current) => current ? { ...current, citations, hitCount: citations.length } : current),
        onDelta: (content) => setAnswer((current) => current ? { ...current, answer: current.answer + content } : current),
        onComplete: (result) => {
          setAnswer(result);
          setSessionId(result.sessionId ?? null);
          setActiveCitationId(result.citations[0]?.chunkId ?? null);
        },
        onError: (message) => setError(message)
      });
      await loadHistory();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }
  async function runDebug() { if (!selected || !question.trim()) return; setLoading(true); setError(""); try { setDebug(await api.debugRag(selected.id, question.trim(), style)); setDebugOpen(true); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setLoading(false); } }

  function jumpToCitation(citation: { chunkId: string }) {
    setActiveCitationId(citation.chunkId);
    const target = document.getElementById(`citation-${citation.chunkId}`);
    const container = target?.closest(".citation-list");
    if (target && container) container.scrollTo({ top: Math.max(0, (target as HTMLElement).offsetTop - 160), behavior: "smooth" });
  }

  function openHistory(item: QaRecord) {
    setQuestion(item.question);
    setAnswer(item);
    setSessionId(item.sessionId ?? null);
    setDebugOpen(false);
    setActiveCitationId(item.citations[0]?.chunkId ?? null);
  }

  return (
    <div className="chat-stage">
      <form className={"ask-box " + (answer ? "follow-up" : "first-question")} onSubmit={ask}>
        {!answer && <div className="ask-head">
          <div>
            <strong>{selected ? selected.name : "未选择知识库"}</strong>
            <span>流式回答会实时输出，并保留引用定位与历史回看。</span>
          </div>
          <select value={style} onChange={(event) => setStyle(event.target.value as AnswerStyle)}>
            <option value="STRICT">严谨模式</option>
            <option value="BRIEF">简洁模式</option>
            <option value="INTERVIEW">面试模式</option>
          </select>
        </div>}
        <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={answer ? "继续追问，或输入新的企业知识问题..." : "输入你的问题"} rows={answer ? 1 : 4} />
        <div className="editor-actions">
          <button className="primary-action compact" type="submit" disabled={!selected || loading || !question.trim()} title={loading ? "生成中" : "发送问题"}>{loading ? <Loader2 className="spin" size={17} /> : <Send size={18} />}<span>{loading ? "生成中" : answer ? "发送" : "开始提问"}</span></button>
          {canDebug && !answer && <button type="button" className="admin-debug-action" onClick={runDebug} disabled={!selected || loading || !question.trim()}><ShieldCheck size={16} />管理员诊断</button>}
        </div>
      </form>
      {error && <div className="inline-error">{error}</div>}
      {answer && (
        <section className="answer-layout" ref={answerRef}>
          <article className={"answer-card " + (loading ? "streaming" : "")}>
            <div className="answer-kicker"><i />ANSWER CANVAS · {selected?.name.toUpperCase()}</div>
            <div className="answer-head">
              <div>
                <strong>{answer.question}</strong>
                <span>{answerStyleLabel(answer.answerStyle ?? style)}</span>
              </div>
              {loading && <em className="streaming-badge"><Loader2 className="spin" size={14} />流式生成中</em>}
            </div>
            <div className="answer-meta">
              <span>{answerStyleLabel(answer.answerStyle ?? style)}</span>
              <span>基于 {answer.hitCount || answer.citations.length} 份资料</span>
            </div>
            {answer.answer ? (
              <div className="answer-body">{renderAnswerBody(answer.answer, answer.citations, jumpToCitation)}</div>
            ) : (
              <div className="streaming-placeholder"><Loader2 className="spin" size={16} />正在组织答案...</div>
            )}
          </article>
          <section className="citation-list">
            <div className="evidence-kicker">TRACEABLE EVIDENCE</div>
            <div className="evidence-heading"><strong>证据舱</strong><p>显示可读来源与定位，不暴露内部检索实现。</p></div>
            <div className="document-list-head">
              <strong>引用来源</strong>
              <span>{answer.citations.length} 份资料</span>
            </div>
            {answer.citations.length === 0 && <div className="muted-document">暂无可展示的引用资料</div>}
            {answer.citations.map((citation, index) => (
              <article id={`citation-${citation.chunkId}`} className={"citation-card " + (activeCitationId === citation.chunkId ? "active" : "")} key={citation.chunkId}>
                <header>
                  <span className="source-index">{String(index + 1).padStart(2, "0")}</span>
                  <strong>{citation.documentName}</strong>
                </header>
                <div className="source-label"><ShieldCheck size={14} />已核对的知识库来源</div>
                <p>{renderQuestionHighlights(citation.content, answer.question)}</p>
                <button className="citation-open" type="button" onClick={() => jumpToCitation(citation)}>查看原文 →</button>
              </article>
            ))}
          </section>
        </section>
      )}
      {canDebug && debug && <section className="debug-panel"><button type="button" onClick={() => setDebugOpen((value) => !value)}>{debugOpen ? "收起管理员诊断" : "展开管理员诊断"}</button>{debugOpen && <div><div className="diagnostic-warning"><ShieldCheck size={16} />以下内容仅对管理员展示，可能包含内部检索参数。</div><h3>{debugSummaryTitle(debug)}</h3><p>{debugSummaryText(debug)}</p>{debug.chunks.map((chunk) => <article className="chunk-card" key={chunk.chunkId}><strong>{chunk.documentName} #{chunk.chunkNo} | 得分 {formatScore(chunk.finalScore)}</strong><p>{debugChunkReason(chunk)}</p><p>{chunk.content}</p></article>)}</div>}</section>}
      <section className="history-list">
        <div className="document-list-head"><strong>最近问答</strong><span>{history.length} 条</span></div>
        {history.length === 0 && <div className="muted-document">暂无问答记录</div>}
        {history.slice(0, 10).map((item) => <article key={item.id} className="history-row"><button type="button" className="history-content" onClick={() => openHistory(item)}><strong>{item.question}</strong><span>{formatDateTime(item.createdAt)} · {item.hitCount} 份参考资料</span></button></article>)}
      </section>
    </div>
  );
}

function RagStage({ selected }: { selected: KnowledgeBase | null }) {
  const [preference, setPreference] = useState<RagPreference>(defaultRagPreference());
  const [templates, setTemplates] = useState<PromptTemplate[]>([]);
  const [template, setTemplate] = useState<PromptTemplatePayload>(defaultPromptTemplate());
  const [qualityOverview, setQualityOverview] = useState<RagQualityOverview | null>(null);
  const [qualityIssues, setQualityIssues] = useState<QaRecord[]>([]);
  const [qualityFilter, setQualityFilter] = useState<RagQualityIssueType>("ALL");
  const [qualityLoading, setQualityLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => { api.getRagPreference().then(setPreference).catch(() => setPreference(defaultRagPreference())); }, []);
  useEffect(() => {
    setTemplates([]);
    setQualityOverview(null);
    setQualityIssues([]);
    setQualityFilter("ALL");
    if (selected) {
      setTemplate(defaultPromptTemplate(selected.name));
      api.listPromptTemplates(selected.id).then(setTemplates).catch(() => undefined);
      void loadQuality("ALL");
    }
  }, [selected?.id]);

  async function savePreference(event: FormEvent) { event.preventDefault(); setSaving(true); try { setPreference(await api.updateRagPreference(preference)); setMessage("偏好设置已保存"); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setSaving(false); } }
  async function saveTemplate(event: FormEvent) { event.preventDefault(); if (!selected) return; setSaving(true); try { await api.createPromptTemplate(selected.id, template); setTemplate(defaultPromptTemplate(selected.name)); setTemplates(await api.listPromptTemplates(selected.id)); setMessage("模板已保存"); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } finally { setSaving(false); } }
  async function clearCache() { if (!selected) return; await api.clearRagCache(selected.id); setMessage("缓存已清理"); }

  async function loadQuality(nextFilter = qualityFilter) {
    if (!selected) return;
    setQualityLoading(true);
    setError("");
    try {
      const [overview, issues] = await Promise.all([
        api.ragQualityOverview(selected.id),
        api.listRagQualityIssues(selected.id, nextFilter, 20)
      ]);
      setQualityOverview(overview);
      setQualityIssues(issues);
      setQualityFilter(nextFilter);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setQualityLoading(false);
    }
  }

  return (
    <div className="rag-stage">
      {error && <div className="inline-error">{error}</div>}
      {message && <div className="success-banner">{message}</div>}
      <form className="settings-panel" onSubmit={savePreference}>
        <div className="panel-title"><Bot size={18} /><span>RAG 偏好设置</span></div>
        <label><span>默认回答风格</span><select value={preference.defaultAnswerStyle} onChange={(event) => setPreference({ ...preference, defaultAnswerStyle: event.target.value as AnswerStyle })}><option value="STRICT">严谨模式</option><option value="BRIEF">简洁模式</option><option value="INTERVIEW">面试模式</option></select></label>
        <label><span>召回数量 Top K</span><input type="number" min={1} max={20} value={preference.defaultTopK} onChange={(event) => setPreference({ ...preference, defaultTopK: Number(event.target.value) })} /></label>
        <label><span>向量权重</span><input type="number" step="0.05" min={0} max={1} value={preference.vectorWeight} onChange={(event) => setPreference({ ...preference, vectorWeight: Number(event.target.value) })} /></label>
        <label className="check-row"><input type="checkbox" checked={preference.enableModel} onChange={(event) => setPreference({ ...preference, enableModel: event.target.checked })} /><span>启用大模型回答</span></label>
        <label className="check-row"><input type="checkbox" checked={preference.enableCache} onChange={(event) => setPreference({ ...preference, enableCache: event.target.checked })} /><span>启用 Redis 缓存</span></label>
        <div className="editor-actions"><button className="primary-action compact" type="submit" disabled={saving}>保存设置</button><button type="button" onClick={clearCache} disabled={!selected}>清理缓存</button></div>
      </form>
      <form className="settings-panel" onSubmit={saveTemplate}>
        <div className="panel-title"><FileText size={18} /><span>Prompt 模板</span></div>
        <label><span>模板名称</span><input value={template.name} onChange={(event) => setTemplate({ ...template, name: event.target.value })} /></label>
        <label><span>系统提示词</span><textarea value={template.systemPrompt} onChange={(event) => setTemplate({ ...template, systemPrompt: event.target.value })} /></label>
        <button className="primary-action compact" type="submit" disabled={!selected || saving}>新增模板</button>
        {templates.map((item) => <article className="template-row" key={item.id}><strong>{item.name}</strong><span>{answerStyleLabel(item.answerStyle)}</span></article>)}
      </form>
      <QualityPanel
        overview={qualityOverview}
        issues={qualityIssues}
        filter={qualityFilter}
        loading={qualityLoading}
        onFilterChange={loadQuality}
        onRefresh={() => loadQuality(qualityFilter)}
      />
    </div>
  );
}

function QualityPanel({
  overview,
  issues,
  filter,
  loading,
  onFilterChange,
  onRefresh
}: {
  overview: RagQualityOverview | null;
  issues: QaRecord[];
  filter: RagQualityIssueType;
  loading: boolean;
  onFilterChange: (filter: RagQualityIssueType) => void;
  onRefresh: () => void;
}) {
  const filters: RagQualityIssueType[] = ["ALL", "UNHELPFUL", "FALLBACK", "NO_CITATION", "HIGH_LATENCY", "NO_FEEDBACK"];

  return (
    <section className="quality-panel">
      <div className="quality-head">
        <div>
          <h3>RAG 质量评估</h3>
          <p>跟踪回答质量、反馈覆盖率、兜底回答、引用缺失与慢响应。</p>
        </div>
        <button type="button" onClick={onRefresh} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新</button>
      </div>
      {overview ? (
        <>
          <div className="quality-score-card">
            <strong>{overview.qualityScore}</strong>
            <span>质量评分</span>
            <VectorProgress label="" value={overview.qualityScore} text={`${overview.qualityScore}/100`} tone={overview.qualityScore >= 80 ? "synced" : overview.qualityScore >= 60 ? "partial" : "fallback"} />
          </div>
          <div className="quality-metrics-grid">
            <QualityMetric label="回答总数" value={overview.totalCount} />
            <QualityMetric label="反馈数" value={overview.feedbackCount} detail={`${percent(overview.feedbackCount, overview.totalCount)}% 覆盖`} />
            <QualityMetric label="有帮助" value={overview.helpfulCount} detail={`${percent(overview.helpfulCount, overview.feedbackCount)}% 反馈占比`} />
            <QualityMetric label="无帮助" value={overview.unhelpfulCount} tone="danger" />
            <QualityMetric label="兜底回答" value={overview.fallbackCount} tone={overview.fallbackCount > 0 ? "warning" : "normal"} />
            <QualityMetric label="缺少引用" value={overview.noCitationCount} tone={overview.noCitationCount > 0 ? "warning" : "normal"} />
            <QualityMetric label="高延迟" value={overview.highLatencyCount} tone={overview.highLatencyCount > 0 ? "warning" : "normal"} />
            <QualityMetric label="平均耗时" value={Math.round(overview.averageLatencyMs)} detail="ms" />
          </div>
        </>
      ) : (
        <div className="muted-document">请选择知识库查看质量指标</div>
      )}
      <div className="quality-filter-row">
        {filters.map((item) => <button key={item} type="button" className={filter === item ? "active" : ""} onClick={() => onFilterChange(item)} disabled={loading}>{qualityFilterLabel(item)}</button>)}
      </div>
      <div className="quality-issue-list">
        {issues.length === 0 && <div className="muted-document">当前视图暂无质量问题</div>}
        {issues.map((item) => (
          <article className="quality-issue-row" key={item.id}>
            <div>
              <strong>{item.question}</strong>
              <span>{qualityIssueLabel(item)} | {answerSourceLabel(item.answerSource, item.cacheHit, item.fallback)} | {formatDuration(item.latencyMs)} | {formatDateTime(item.createdAt)}</span>
            </div>
            <em className={qualityIssueTone(item)}>{qualityIssueTag(item)}</em>
          </article>
        ))}
      </div>
    </section>
  );
}

function QualityMetric({ label, value, detail, tone = "normal" }: { label: string; value: number; detail?: string; tone?: string }) {
  return (
    <article className={"quality-metric " + tone}>
      <span>{label}</span>
      <strong>{value}</strong>
      {detail && <small>{detail}</small>}
    </article>
  );
}

function TaskCenterStage() {
  const [tasks, setTasks] = useState<DocumentTask[]>([]);
  const [logs, setLogs] = useState<TaskLog[]>([]);
  const [status, setStatus] = useState("ALL");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [activeTask, setActiveTask] = useState<DocumentTask | null>(null);
  const [loading, setLoading] = useState(false);
  const [operatingId, setOperatingId] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => { void loadTasks(); }, [status]);

  async function loadTasks() {
    setLoading(true);
    setError("");
    try {
      const nextTasks = await api.listDocumentTasks(status, 100);
      setTasks(nextTasks);
      setSelectedIds((current) => current.filter((id) => nextTasks.some((task) => task.id === id)));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  async function openLogs(task: DocumentTask) {
    setActiveTask(task);
    setLogs([]);
    try {
      setLogs(await api.listTaskLogs(task.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    }
  }

  async function retryTask(task: DocumentTask) {
    if (!canRetryTask(task)) return;
    setOperatingId(task.id);
    setError("");
    try {
      await api.retryDocumentTask(task.id);
      await loadTasks();
      if (activeTask?.id === task.id) await openLogs(task);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setOperatingId(null);
    }
  }

  async function retrySelected() {
    const retryable = tasks.filter((task) => selectedIds.includes(task.id) && canRetryTask(task));
    if (retryable.length === 0) return;
    setOperatingId("batch");
    setError("");
    try {
      await api.batchParseDocuments(retryable.map((task) => task.id));
      await loadTasks();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setOperatingId(null);
    }
  }

  function toggleSelected(taskId: string) {
    setSelectedIds((current) => current.includes(taskId) ? current.filter((id) => id !== taskId) : [...current, taskId]);
  }

  const summary = useMemo(() => {
    const counts = { total: tasks.length, pending: 0, running: 0, done: 0, failed: 0, review: 0 };
    tasks.forEach((task) => {
      const normalized = taskCenterStatus(task);
      if (normalized === "PENDING") counts.pending++;
      if (normalized === "RUNNING") counts.running++;
      if (normalized === "DONE") counts.done++;
      if (normalized === "FAILED") counts.failed++;
      if (normalized === "NEEDS_REVIEW") counts.review++;
    });
    return counts;
  }, [tasks]);

  return (
    <div className="task-center-stage">
      {error && <div className="inline-error">{error}</div>}
      <div className="task-summary-grid">
        <TaskMetric label="任务总数" value={summary.total} />
        <TaskMetric label="等待中" value={summary.pending} tone="pending" />
        <TaskMetric label="运行中" value={summary.running} tone="running" />
        <TaskMetric label="已完成" value={summary.done} tone="done" />
        <TaskMetric label="需处理" value={summary.review} tone="review" />
      </div>
      <section className="task-center-panel">
        <div className="task-center-head">
          <div><h3>任务中心</h3><p>跟踪文档解析、队列状态、失败重试与任务日志。</p></div>
          <div className="task-center-actions">
            <button type="button" onClick={retrySelected} disabled={operatingId === "batch" || selectedIds.length === 0}>{operatingId === "batch" ? <Loader2 className="spin" size={15} /> : null}重试选中</button>
            <button type="button" onClick={loadTasks} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新</button>
          </div>
        </div>
        <div className="task-filter-row">
          {["ALL", "PENDING", "RUNNING", "DONE", "FAILED", "NEEDS_REVIEW"].map((item) => <button type="button" key={item} className={status === item ? "active" : ""} onClick={() => setStatus(item)}>{taskStatusLabel(item)}</button>)}
        </div>
        <div className="task-table">
          {loading && <div className="muted-document"><Loader2 className="spin" size={18} />正在加载任务...</div>}
          {!loading && tasks.length === 0 && <div className="muted-document">暂无任务</div>}
          {tasks.map((task) => {
            const normalized = taskCenterStatus(task);
            const retryable = canRetryTask(task);
            return (
              <article className={"task-center-row " + normalized.toLowerCase()} key={task.id}>
                <label className="task-check"><input type="checkbox" checked={selectedIds.includes(task.id)} onChange={() => toggleSelected(task.id)} disabled={!retryable} /></label>
                <div className="task-main">
                  <strong>{task.fileName}</strong>
                  <span>{taskStatusLabel(normalized)} | {task.chunkCount} 个切片 | 更新于 {formatDateTime(task.updatedAt)}</span>
                  <small>{task.queueStatus ? `队列 ${queueStatusLabel(task.queueStatus)} | 重试 ${task.queueRetryCount ?? 0} | 可执行时间 ${formatDateTime(task.queueAvailableAt)}` : "暂未进入队列"}</small>
                  {(task.errorMsg || task.queueErrorMsg) && <p>{task.errorMsg ?? task.queueErrorMsg}</p>}
                </div>
                <em className={taskStatusTone(normalized)}>{taskStatusLabel(normalized)}</em>
                <div className="task-row-actions">
                  <button type="button" onClick={() => openLogs(task)}>日志</button>
                  <button type="button" onClick={() => retryTask(task)} disabled={!retryable || operatingId === task.id}>{operatingId === task.id ? <Loader2 className="spin" size={14} /> : null}重试</button>
                </div>
              </article>
            );
          })}
        </div>
      </section>
      {activeTask && <section className="task-log-panel task-center-logs"><div className="document-list-head"><div><strong>{activeTask.fileName}</strong><span>任务日志</span></div><button onClick={() => setActiveTask(null)}>关闭</button></div>{logs.length === 0 && <div className="muted-document">暂无日志</div>}{logs.map((log) => <article className="task-log-row" key={log.id}><header><em className={taskLogTone(log.status)}>{taskStatusLabel(log.status)}</em><span>{log.taskType} | {formatDuration(log.durationMs)} | {formatDateTime(log.createdAt)}</span></header><p>{log.message}</p></article>)}</section>}
    </div>
  );
}

function TaskMetric({ label, value, tone = "normal" }: { label: string; value: number; tone?: string }) {
  return <article className={"task-metric " + tone}><span>{label}</span><strong>{value}</strong></article>;
}

function MonitorStage() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [metrics, setMetrics] = useState<TodayMetrics | null>(null);
  const [runtime, setRuntime] = useState<RuntimeConfig | null>(null);
  const [diagnostics, setDiagnostics] = useState<SystemDiagnostics | null>(null);
  const [tasks, setTasks] = useState<DocumentTask[]>([]);
  const [logs, setLogs] = useState<TaskLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => { void loadMonitor(); }, []);
  async function loadMonitor() {
    setLoading(true);
    setError("");
    try {
      const [a, b, c, d, e, f] = await Promise.all([api.dashboardOverview(), api.dashboardHealth(), api.todayMetrics(), api.runtimeConfig(), api.diagnostics(), api.listDocumentTasks("ALL", 30)]);
      setOverview(a); setHealth(b); setMetrics(c); setRuntime(d); setDiagnostics(e); setTasks(f);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "监控数据加载失败");
    } finally {
      setLoading(false);
    }
  }
  async function showLogs(documentId: string) {
    try { setLogs(await api.listTaskLogs(documentId)); } catch (exception) { setError(exception instanceof Error ? exception.message : "任务日志加载失败"); }
  }
  const unhealthyCount = health?.components.filter((item) => healthTone(item.status) !== "success").length ?? 0;
  const pendingTaskCount = tasks.filter((task) => !["COMPLETED", "DONE", "SUCCESS"].includes(normalizeCode(task.status))).length;
  return (
    <div className="monitor-stage monitor-console">
      <header className="monitor-console-head">
        <div><span>ADMIN · SYSTEM STATUS</span><h2>运行监控</h2><p>先看结论，需要排查时再展开内部详情。</p></div>
        <div className="monitor-head-actions">
          {health && <div className={"monitor-health-chip " + healthTone(health.status)}><i />{healthStatusLabel(health.status)}{unhealthyCount > 0 ? ` · ${unhealthyCount} 项异常` : ""}</div>}
          <button onClick={loadMonitor} disabled={loading}>{loading ? <Loader2 className="spin" size={16} /> : null}刷新</button>
        </div>
      </header>
      {error && <div className="inline-error">{error}</div>}

      <section className="monitor-summary" aria-label="系统摘要">
        <MonitorSummary label="知识库" value={overview?.knowledgeBaseCount ?? 0} hint="可用知识空间" />
        <MonitorSummary label="文档" value={overview?.documentCount ?? 0} hint={`${overview?.failedDocumentCount ?? 0} 份需关注`} tone={(overview?.failedDocumentCount ?? 0) > 0 ? "warning" : "normal"} />
        <MonitorSummary label="问答" value={metrics?.qaCount ?? 0} hint="今日请求" />
        <MonitorSummary label="平均响应" value={formatDuration(metrics?.averageLatencyMs)} hint="今日平均" />
        <MonitorSummary label="模型成功率" value={metrics ? `${Math.round(metrics.modelSuccessRate)}%` : "-"} hint="今日模型调用" />
        <MonitorSummary label="待处理任务" value={pendingTaskCount} hint={`共 ${tasks.length} 条任务`} tone={pendingTaskCount > 0 ? "warning" : "normal"} />
      </section>

      <div className="monitor-disclosures">
        {health && <details className="monitor-disclosure">
          <summary><div><strong>服务健康</strong><span>数据库、缓存、模型与向量库连接状态</span></div><em className={healthTone(health.status)}>{healthStatusLabel(health.status)}</em><ChevronRight size={18} /></summary>
          <div className="monitor-detail-body health-detail-grid">
            {health.components.map((component) => <article className="monitor-service-row" key={component.name}><i className={healthTone(component.status)} /><div><strong>{componentLabel(component.name)}</strong><span>{component.message}</span></div><em>{component.latencyMs === null ? healthStatusLabel(component.status) : formatDuration(component.latencyMs)}</em></article>)}
          </div>
        </details>}

        {runtime && <details className="monitor-disclosure">
          <summary><div><strong>运行配置</strong><span>模型、缓存、限流和向量后端的内部配置</span></div><em>{runtime.items.length} 项</em><ChevronRight size={18} /></summary>
          <div className="monitor-detail-body config-detail-list">
            {runtime.items.map((item) => <article className="monitor-config-row" key={item.key}><div><strong>{item.label}</strong><span>{item.summary}</span></div><em className={healthTone(item.status)}>{healthStatusLabel(item.status)}</em>{item.details.length > 0 && <ul>{item.details.map((detail, index) => <li key={index}>{detail}</li>)}</ul>}</article>)}
          </div>
        </details>}

        {diagnostics && <details className="monitor-disclosure">
          <summary><div><strong>系统诊断</strong><span>仅在排障时查看的连通性与内部检测结果</span></div><em className={healthTone(diagnostics.status)}>{healthStatusLabel(diagnostics.status)}</em><ChevronRight size={18} /></summary>
          <div className="monitor-detail-body config-detail-list">
            {diagnostics.items.map((item) => <article className="monitor-config-row" key={item.key}><div><strong>{item.label}</strong><span>{item.message}</span></div><em className={healthTone(item.status)}>{item.latencyMs === null ? healthStatusLabel(item.status) : formatDuration(item.latencyMs)}</em>{item.details.length > 0 && <ul>{item.details.map((detail, index) => <li key={index}>{detail}</li>)}</ul>}</article>)}
          </div>
        </details>}

        <details className="monitor-disclosure">
          <summary><div><strong>文档任务</strong><span>解析进度、失败任务和重试日志</span></div><em className={pendingTaskCount > 0 ? "warning" : "success"}>{pendingTaskCount > 0 ? `${pendingTaskCount} 条待处理` : "全部完成"}</em><ChevronRight size={18} /></summary>
          <div className="monitor-detail-body monitor-task-list">
            {tasks.length === 0 && <div className="monitor-empty">暂无文档任务</div>}
            {tasks.map((task) => <article className="monitor-task-row" key={task.id}><div><strong>{task.fileName}</strong><span>{statusLabel(task.status)} · {task.chunkCount} 个切片 · {formatDateTime(task.updatedAt)}</span>{task.queueErrorMsg && <small>{task.queueErrorMsg}</small>}</div><button onClick={() => showLogs(task.id)}>查看日志</button></article>)}
            {logs.length > 0 && <div className="monitor-log-list"><div className="monitor-log-title"><strong>任务日志</strong><button onClick={() => setLogs([])}>关闭</button></div>{logs.map((log) => <article key={log.id}><div><strong>{taskTypeLabel(log.taskType)}</strong><span>{log.message}</span></div><em>{taskStatusLabel(log.status)} · {formatDuration(log.durationMs)}</em></article>)}</div>}
          </div>
        </details>
      </div>
    </div>
  );
}

function MonitorSummary({ label, value, hint, tone = "normal" }: { label: string; value: string | number; hint: string; tone?: "normal" | "warning" }) {
  return <article className={"monitor-summary-item " + tone}><span>{label}</span><strong>{value}</strong><small>{hint}</small></article>;
}

function AdminStage() {
  const [overview, setOverview] = useState<AdminUserOverview | null>(null);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loginLogs, setLoginLogs] = useState<AdminLoginLog[]>([]);
  const [operationLogs, setOperationLogs] = useState<AdminOperationLog[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loginSuccess, setLoginSuccess] = useState<"ALL" | "SUCCESS" | "FAILED">("ALL");
  const [operationAction, setOperationAction] = useState("ALL");
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resettingId, setResettingId] = useState<string | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { void loadAdmin(); }, []);

  async function loadAdmin() {
    setLoading(true);
    setError("");
    try {
      const [nextOverview, nextUsers] = await Promise.all([
        api.adminUserOverview(),
        api.listAdminUsers(keyword, 50)
      ]);
      setOverview(nextOverview);
      setUsers(nextUsers);
      await loadLogs();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadLogs() {
    const success = loginSuccess === "ALL" ? null : loginSuccess === "SUCCESS";
    const [nextLoginLogs, nextOperationLogs] = await Promise.all([
      api.listAdminLoginLogs(selectedUserId, success, 80),
      api.listAdminOperationLogs(selectedUserId, operationAction, 80)
    ]);
    setLoginLogs(nextLoginLogs);
    setOperationLogs(nextOperationLogs);
  }

  async function refreshLogs() {
    setLoading(true);
    setError("");
    try {
      await loadLogs();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  async function toggleUser(user: AdminUser) {
    await api.updateAdminUser(user.userId, { enabled: !user.enabled });
    await loadAdmin();
  }

  async function resetPassword(user: AdminUser) {
    const nextPassword = window.prompt(`请输入 ${user.username} 的新密码`, "123456");
    if (!nextPassword) return;
    setResettingId(user.userId);
    setError("");
    try {
      await api.resetAdminUserPassword(user.userId, nextPassword);
      await loadAdmin();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setResettingId(null);
    }
  }

  return (
    <div className="admin-stage">
      {error && <div className="inline-error">{error}</div>}
      {overview && <div className="stats-grid"><StatusBar label="用户总数" value={overview.totalCount} percent={100} tone="synced" /><StatusBar label="已启用" value={overview.enabledCount} percent={100} tone="partial" /><StatusBar label="已禁用" value={overview.disabledCount} percent={100} tone="pending" /><StatusBar label="管理员" value={overview.adminCount} percent={100} tone="fallback" /></div>}
      <section className="admin-panel">
        <div className="admin-panel-head">
          <div><h3>用户管理</h3><p>检索用户、禁用风险账号并重置密码。</p></div>
          <button type="button" onClick={loadAdmin} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新</button>
        </div>
        <div className="admin-toolbar">
          <div className="search-box"><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索用户" /><button onClick={loadAdmin}>搜索</button></div>
          <select value={selectedUserId ?? ""} onChange={(event) => setSelectedUserId(event.target.value || null)}>
            <option value="">全部用户</option>
            {users.map((item) => <option key={item.userId} value={item.userId}>{item.username}</option>)}
          </select>
        </div>
        <div className="admin-user-list">
          {users.map((item) => <article className="admin-user-row" key={item.userId}><div><strong>{item.username}</strong><span>{appRoleLabel(item.role)} | {item.enabled ? "已启用" : "已禁用"} | 最近登录 {item.lastLoginAt ? formatDateTime(item.lastLoginAt) : "-"}</span></div><div className="admin-row-actions"><button onClick={() => resetPassword(item)} disabled={resettingId === item.userId}>{resettingId === item.userId ? <Loader2 className="spin" size={14} /> : null}重置密码</button><button onClick={() => toggleUser(item)}>{item.enabled ? "禁用" : "启用"}</button></div></article>)}
        </div>
      </section>
      <section className="admin-panel">
        <div className="admin-panel-head">
          <div><h3>审计日志</h3><p>查看登录尝试与管理员操作记录。</p></div>
          <button type="button" onClick={refreshLogs} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新日志</button>
        </div>
        <div className="admin-toolbar">
          <select value={loginSuccess} onChange={(event) => setLoginSuccess(event.target.value as "ALL" | "SUCCESS" | "FAILED")}>
            <option value="ALL">全部登录</option>
            <option value="SUCCESS">登录成功</option>
            <option value="FAILED">登录失败</option>
          </select>
          <select value={operationAction} onChange={(event) => setOperationAction(event.target.value)}>
            <option value="ALL">全部操作</option>
            <option value="UPDATE_USER">更新用户</option>
            <option value="RESET_PASSWORD">重置密码</option>
            <option value="DISABLE_USER">禁用用户</option>
            <option value="ENABLE_USER">启用用户</option>
          </select>
          <button type="button" onClick={refreshLogs}>应用筛选</button>
        </div>
        <div className="audit-grid">
          <div className="audit-list">
            <h4>登录日志</h4>
            {loginLogs.length === 0 && <div className="muted-document">暂无登录日志</div>}
            {loginLogs.map((item) => <article className="audit-row" key={item.id}><div><strong>{item.username ?? "-"}</strong><span>{item.ipAddress ?? "-"} | {item.userAgent ?? "-"} | {formatDateTime(item.createdAt)}</span>{item.message && <small>{item.message}</small>}</div><em className={item.success ? "success" : "danger"}>{item.success ? "成功" : "失败"}</em></article>)}
          </div>
          <div className="audit-list">
            <h4>操作日志</h4>
            {operationLogs.length === 0 && <div className="muted-document">暂无操作日志</div>}
            {operationLogs.map((item) => <article className="audit-row" key={item.id}><div><strong>{adminActionLabel(item.action)}</strong><span>{item.adminUsername ?? "-"} {"->"} {item.targetUsername ?? "-"} | {formatDateTime(item.createdAt)}</span>{item.detail && <small>{item.detail}</small>}</div><em>{resultLabel(item.result)}</em></article>)}
          </div>
        </div>
      </section>
    </div>
  );
}

function UserCenter({ user, onClose }: { user: CurrentUser; onClose: () => void }) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  useEffect(() => { api.profile().then(setProfile).catch((exception) => setError(exception instanceof Error ? exception.message : "操作失败")); }, []);
  async function changePassword(event: FormEvent) { event.preventDefault(); setError(""); try { await api.changePassword(oldPassword, newPassword); setOldPassword(""); setNewPassword(""); setMessage("密码已更新"); } catch (exception) { setError(exception instanceof Error ? exception.message : "操作失败"); } }
  return <div className="modal-backdrop"><section className="user-center"><header><div><strong>{profile?.username ?? user.username}</strong><span>{appRoleLabel(profile?.role ?? user.role)}</span></div><button onClick={onClose}>关闭</button></header>{error && <div className="inline-error">{error}</div>}{message && <div className="success-banner">{message}</div>}<form onSubmit={changePassword}><label><span>原密码</span><input type="password" value={oldPassword} onChange={(event) => setOldPassword(event.target.value)} /></label><label><span>新密码</span><input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label><button className="primary-action compact" type="submit">修改密码</button></form><section><h3>最近登录</h3>{profile?.recentLogins.map((item) => <article className="login-log-row" key={item.id}><div><strong>{item.ipAddress ?? "-"}</strong><span>{formatDateTime(item.createdAt)}</span></div><em className={item.success ? "success" : "danger"}>{item.success ? "成功" : "失败"}</em></article>)}</section></section></div>;
}

function StatusBar({ label, value, percent, tone }: { label: string; value: number; percent: number; tone: string }) {
  return <article className="stat-card"><span>{label}</span><strong>{value}</strong><div className="status-track"><i className={tone} style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} /></div></article>;
}

function roleOptions(includeOwner: boolean) { return <>{includeOwner && <option value="OWNER">拥有者</option>}<option value="ADMIN">管理员</option><option value="EDITOR">编辑者</option><option value="VIEWER">只读成员</option></>; }
function normalizeCode(value?: string | null) { return (value ?? "").trim().replace(/[\s-]+/g, "_").toUpperCase(); }
function sleep(ms: number) { return new Promise((resolve) => window.setTimeout(resolve, ms)); }
function roleLevel(role?: KnowledgeBaseRole | null) { const value = normalizeCode(role); if (value === "OWNER") return 4; if (value === "ADMIN") return 3; if (value === "EDITOR") return 2; if (value === "VIEWER") return 1; return 0; }
function canAdminKnowledgeBase(kb: KnowledgeBase | null) { return roleLevel(kb?.accessRole) >= roleLevel("ADMIN"); }
function canEditKnowledgeBase(kb: KnowledgeBase | null) { return roleLevel(kb?.accessRole) >= roleLevel("EDITOR"); }
function roleLabel(role?: string | null) { const value = normalizeCode(role); return value === "OWNER" ? "拥有者" : value === "ADMIN" ? "管理员" : value === "EDITOR" ? "编辑者" : value === "VIEWER" ? "只读" : "-"; }
function appRoleLabel(role?: string | null) { const value = normalizeCode(role); return value === "ADMIN" ? "管理员" : value === "USER" ? "普通用户" : value === "OWNER" ? "拥有者" : value === "EDITOR" ? "编辑者" : value === "VIEWER" ? "只读" : role ?? "-"; }
function statusLabel(status: string) { const labels: Record<string, string> = { ALL: "全部", UPLOADED: "已上传", PARSING: "解析中", COMPLETED: "已完成", DONE: "已完成", FAILED: "失败", PENDING: "等待中", RUNNING: "运行中" }; return labels[normalizeCode(status)] ?? status; }
function queueStatusLabel(status?: string | null) { const labels: Record<string, string> = { PENDING: "等待中", RUNNING: "运行中", DONE: "已完成", COMPLETED: "已完成", FAILED: "失败", SUCCESS: "成功", ERROR: "错误" }; return status ? labels[normalizeCode(status)] ?? status : "-"; }
function taskCenterStatus(task: DocumentTask) {
  const queueStatus = normalizeCode(task.queueStatus);
  const documentStatus = normalizeCode(task.status);
  if (documentStatus === "FAILED" && Math.max(task.retryCount ?? 0, task.queueRetryCount ?? 0) >= 3) return "NEEDS_REVIEW";
  if (queueStatus === "PENDING" || documentStatus === "UPLOADED") return "PENDING";
  if (queueStatus === "RUNNING" || documentStatus === "PARSING") return "RUNNING";
  if (queueStatus === "FAILED" || documentStatus === "FAILED") return "FAILED";
  if (queueStatus === "DONE" || documentStatus === "COMPLETED" || documentStatus === "DONE") return "DONE";
  return documentStatus || task.status;
}
function taskStatusLabel(status: string) {
  const labels: Record<string, string> = { ALL: "全部", PENDING: "等待中", RUNNING: "运行中", DONE: "已完成", FAILED: "失败", NEEDS_REVIEW: "需处理", UPLOADED: "已上传", PARSING: "解析中", COMPLETED: "已完成", SUCCESS: "成功", ERROR: "错误" };
  return labels[normalizeCode(status)] ?? status;
}
function taskStatusTone(status: string) { const value = normalizeCode(status); if (value === "DONE" || value === "COMPLETED" || value === "SUCCESS") return "success"; if (value === "RUNNING" || value === "PARSING" || value === "PENDING") return "warning"; if (value === "FAILED" || value === "NEEDS_REVIEW" || value === "ERROR") return "danger"; return "neutral"; }
function taskLogTone(status: string) { const value = normalizeCode(status); if (value === "SUCCESS" || value === "DONE" || value === "COMPLETED") return "success"; if (value === "FAILED" || value === "ERROR") return "danger"; return "warning"; }
function canRetryTask(task: DocumentTask) { const status = taskCenterStatus(task); return status === "FAILED" || status === "DONE" || status === "PENDING"; }
function indexStatusLabel(status: string) { const labels: Record<string, string> = { SYNCED: "已同步", FALLBACK: "本地回退", PARTIAL: "部分同步", EMBEDDING_ONLY: "仅有 Embedding", PENDING: "待同步" }; return labels[normalizeCode(status)] ?? status; }
function indexStatusDescription(status: string, backend: string) { const value = normalizeCode(status); if (value === "SYNCED") return `所有切片已同步到 ${backend}。`; if (value === "FALLBACK") return "向量库不可用，当前使用本地回退索引检索。"; if (value === "PARTIAL") return "仍有部分切片需要同步到向量库。"; if (value === "EMBEDDING_ONLY") return "Embedding 已生成，但 pgvector 记录尚未完整。"; return "请先解析文档，再同步向量。"; }
function indexStatusTone(status: string) { const value = normalizeCode(status); if (value === "SYNCED") return "synced"; if (value === "FALLBACK") return "fallback"; if (value === "PARTIAL" || value === "EMBEDDING_ONLY") return "partial"; return "pending"; }
function answerSourceLabel(source?: string | null, cacheHit?: boolean | null, fallback?: boolean | null) { const value = normalizeCode(source); if (cacheHit) return "缓存命中"; if (fallback) return "兜底回答"; if (value === "MODEL") return "模型回答"; if (value === "LOCAL") return "本地回答"; return source ?? "回答"; }
function answerStyleLabel(style?: string | null) { const value = normalizeCode(style); if (value === "STRICT") return "严谨模式"; if (value === "BRIEF") return "简洁模式"; if (value === "INTERVIEW") return "面试模式"; return "默认模式"; }
function qualityFilterLabel(filter: RagQualityIssueType) {
  const labels: Record<RagQualityIssueType, string> = {
    ALL: "全部",
    UNHELPFUL: "无帮助",
    FALLBACK: "兜底回答",
    NO_CITATION: "缺少引用",
    HIGH_LATENCY: "高延迟",
    NO_FEEDBACK: "无反馈"
  };
  return labels[filter];
}
function qualityIssueLabel(record: QaRecord) {
  if (record.feedbackScore !== null && record.feedbackScore !== undefined && record.feedbackScore <= 0) return "用户标记无帮助";
  if (record.fallback) return "兜底回答";
  if (record.hitCount === 0) return "缺少引用";
  if ((record.latencyMs ?? 0) >= 5000) return "高延迟";
  if (record.feedbackScore === null || record.feedbackScore === undefined) return "暂无反馈";
  return "正常";
}
function qualityIssueTag(record: QaRecord) {
  if (record.feedbackScore !== null && record.feedbackScore !== undefined && record.feedbackScore <= 0) return "无帮助";
  if (record.fallback) return "兜底";
  if (record.hitCount === 0) return "缺引用";
  if ((record.latencyMs ?? 0) >= 5000) return "慢响应";
  if (record.feedbackScore === null || record.feedbackScore === undefined) return "无反馈";
  return "正常";
}
function qualityIssueTone(record: QaRecord) {
  if (record.feedbackScore !== null && record.feedbackScore !== undefined && record.feedbackScore <= 0) return "danger";
  if (record.fallback || record.hitCount === 0 || (record.latencyMs ?? 0) >= 5000) return "warning";
  if (record.feedbackScore === null || record.feedbackScore === undefined) return "neutral";
  return "success";
}
function defaultRagPreference(): RagPreference { return { defaultAnswerStyle: "STRICT", defaultTopK: 5, vectorWeight: 0.7, keywordWeight: 0.3, enableModel: true, enableCache: true }; }
function defaultPromptTemplate(kbName = "知识库"): PromptTemplatePayload { return { name: `${kbName} 默认提示词`, answerStyle: "STRICT", enabled: true, systemPrompt: "请仅基于检索到的上下文回答问题，尽可能标注引用来源。如果资料中没有答案，请明确说明无法从当前知识库确认。" }; }
function debugSummaryTitle(result: RagDebugResult) { if (result.chunks.length === 0) return "未命中检索结果"; if (result.cacheHit) return "缓存命中"; const best = result.chunks[0]; if (best.finalScore >= 0.75) return "强相关检索结果"; if (best.finalScore >= 0.45) return "可用检索结果"; return "弱相关检索结果"; }
function debugSummaryText(result: RagDebugResult) { if (result.chunks.length === 0) return "没有切片匹配这个问题。"; const best = result.chunks[0]; const keywordHits = result.chunks.filter((chunk) => chunk.matchedKeywords.length > 0).length; return `最高得分 ${formatScore(best.finalScore)}，来源于 ${best.documentName}。${keywordHits} 个切片命中关键词。向量后端：${result.vectorBackend}。`; }
function debugChunkReason(chunk: { matchedKeywords: string[]; vectorScore: number; keywordScore: number }) { if (chunk.matchedKeywords.length > 0) return `关键词命中：${chunk.matchedKeywords.join(", ")}。向量分 ${formatScore(chunk.vectorScore)}，关键词分 ${formatScore(chunk.keywordScore)}。`; return `向量分 ${formatScore(chunk.vectorScore)}，关键词分 ${formatScore(chunk.keywordScore)}。`; }
function renderHighlightedText(text: string, query: string) { const keyword = query.trim(); if (!keyword) return text; const parts = text.split(new RegExp(`(${escapeRegExp(keyword)})`, "ig")); return <>{parts.map((part, index) => part.toLowerCase() === keyword.toLowerCase() ? <mark key={index}>{part}</mark> : <React.Fragment key={index}>{part}</React.Fragment>)}</>; }
function renderAnswerBody(answer: string, citations: Array<{ chunkId: string }>, onCitationClick: (citation: { chunkId: string }) => void) {
  const blocks = answer.split(/\n{2,}/).map((item) => item.trim()).filter(Boolean);
  if (blocks.length === 0) return null;
  return blocks.map((block, blockIndex) => {
    const lines = block.split(/\n/).map((line) => line.trim()).filter(Boolean);
    if (lines.length > 1 && lines.every((line) => /^\d+[.、]\s*/.test(line))) {
      return <ol className="answer-points" key={`ol-${blockIndex}`}>{lines.map((line, lineIndex) => <li key={lineIndex}>{renderAnswerInline(line.replace(/^\d+[.、]\s*/, ""), citations, onCitationClick)}</li>)}</ol>;
    }
    if (lines.length === 1 && /^(结论|要点|说明|依据|建议|总结)[:：]?$/.test(lines[0])) {
      return <h4 key={`h-${blockIndex}`}>{lines[0].replace(/[:：]$/, "")}</h4>;
    }
    return <p key={`p-${blockIndex}`}>{renderAnswerInline(block, citations, onCitationClick)}</p>;
  });
}
function renderAnswerInline(text: string, citations: Array<{ chunkId: string }>, onCitationClick: (citation: { chunkId: string }) => void) {
  const parts = text.split(/(\[\d+\]|【\d+】|\*\*[^*]+\*\*)/g).filter((part) => part.length > 0);
  return <>{parts.map((part, index) => {
    if (/^\*\*[^*]+\*\*$/.test(part)) return <strong key={index}>{part.slice(2, -2)}</strong>;
    const match = part.match(/[\[【](\d+)[\]】]/);
    if (!match) return <React.Fragment key={index}>{part}</React.Fragment>;
    const citationIndex = Number(match[1]) - 1;
    const citation = citations[citationIndex];
    if (!citation) return <React.Fragment key={index}>{part}</React.Fragment>;
    return <button className="citation-ref" type="button" key={index} onClick={() => onCitationClick(citation)}>{part}</button>;
  })}</>;
}
function renderQuestionHighlights(text: string, question: string) {
  const keywords = extractHighlightKeywords(question);
  if (keywords.length === 0) return text;
  const pattern = new RegExp(`(${keywords.map(escapeRegExp).join("|")})`, "ig");
  const parts = text.split(pattern);
  return <>{parts.map((part, index) => keywords.some((keyword) => keyword.toLowerCase() === part.toLowerCase()) ? <mark className="question-hit" key={index}>{part}</mark> : <React.Fragment key={index}>{part}</React.Fragment>)}</>;
}
function extractHighlightKeywords(question: string) {
  const normalized = question.replace(/[，。！？、；：,.!?;:()[\]{}"'“”‘’]/g, " ");
  const tokens = normalized.split(/\s+/).map((item) => item.trim()).filter((item) => item.length >= 2 && item.length <= 18);
  if (tokens.length > 0) return Array.from(new Set(tokens)).slice(0, 8);
  const compact = normalized.replace(/\s+/g, "");
  const chunks: string[] = [];
  for (let index = 0; index < compact.length - 1 && chunks.length < 8; index += 2) chunks.push(compact.slice(index, index + 2));
  return Array.from(new Set(chunks));
}
function escapeRegExp(value: string) { return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }
function percent(value: number, total: number) { if (total <= 0) return 0; return Math.max(0, Math.min(100, Math.round((value / total) * 100))); }
function formatScore(value: number | undefined) { return typeof value === "number" ? value.toFixed(3) : "-"; }
function healthTone(status: string) { const value = normalizeCode(status); return value === "UP" || value === "OK" || value === "SUCCESS" ? "success" : value === "WARN" || value === "WARNING" ? "warning" : "danger"; }
function healthStatusLabel(status?: string | null) { const labels: Record<string, string> = { UP: "正常", OK: "正常", SUCCESS: "成功", DOWN: "异常", ERROR: "错误", FAILED: "失败", WARN: "告警", WARNING: "告警", DISABLED: "未启用", ENABLED: "已启用", MISSING: "缺失", READY: "就绪" }; return status ? labels[normalizeCode(status)] ?? status : "-"; }
function componentLabel(name: string) { const labels: Record<string, string> = { database: "数据库", redis: "Redis", embedding: "Embedding 模型", chat: "对话模型", vector: "向量库" }; return labels[name] ?? name; }
function adminActionLabel(action: string) {
  const labels: Record<string, string> = { UPDATE_USER: "更新用户", RESET_PASSWORD: "重置密码", DISABLE_USER: "禁用用户", ENABLE_USER: "启用用户" };
  return labels[normalizeCode(action)] ?? action;
}
function resultLabel(result?: string | null) { const labels: Record<string, string> = { SUCCESS: "成功", FAILED: "失败", ERROR: "错误", OK: "成功" }; return result ? labels[normalizeCode(result)] ?? result : "-"; }
function taskTypeLabel(type?: string | null) { const labels: Record<string, string> = { PARSE: "文档解析", PARSE_DOCUMENT: "文档解析", DOCUMENT_PARSE: "文档解析", VECTOR_SYNC: "向量同步", SYNC_VECTOR: "向量同步", EMBEDDING: "Embedding 生成" }; return type ? labels[normalizeCode(type)] ?? type : "-"; }
function formatFileSize(size: number) { if (size < 1024) return `${size} B`; if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`; return `${(size / 1024 / 1024).toFixed(1)} MB`; }
function formatDuration(value?: number | null) { if (value === null || value === undefined) return "-"; if (value < 1000) return `${value} ms`; return `${(value / 1000).toFixed(2)} s`; }
function formatDateTime(value: string | null | undefined) { if (!value) return "-"; const date = new Date(value); if (Number.isNaN(date.getTime())) return value; return date.toLocaleString(); }
function downloadBlob(blob: Blob, fileName: string) { const url = URL.createObjectURL(blob); const anchor = document.createElement("a"); anchor.href = url; anchor.download = fileName; anchor.click(); URL.revokeObjectURL(url); }

createRoot(document.getElementById("root")!).render(<App />);
