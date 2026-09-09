import React, { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Bookmark,
  BookOpen,
  Bot,
  Check,
  CircleAlert,
  CircleCheckBig,
  ChevronRight,
  Clock3,
  Database,
  FileText,
  Hourglass,
  Layers3,
  Loader2,
  LogOut,
  MessageSquareText,
  MoreHorizontal,
  MoreVertical,
  PanelLeft,
  Paperclip,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Sparkles,
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
import { clearLegacyCredentials, loadRememberedUsername, persistRememberedUsername } from "./authStorage";
import "./styles.css";

type AuthMode = "login" | "register";
type WorkspaceTab = "documents" | "chat" | "rag" | "tasks" | "monitor" | "admin";
type DocumentStatusFilter = "ALL" | "UPLOADED" | "PARSING" | "COMPLETED" | "FAILED";

function App() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [booting, setBooting] = useState(true);
  const [authMessage, setAuthMessage] = useState("");

  useEffect(() => clearLegacyCredentials(), []);

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
  const [username, setUsername] = useState(loadRememberedUsername);
  const [rememberUsername, setRememberUsername] = useState(() => username.length > 0);
  const [password, setPassword] = useState("");
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
      persistRememberedUsername(login.username, rememberUsername);
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
        <label className="check-row"><input type="checkbox" checked={rememberUsername} onChange={(event) => setRememberUsername(event.target.checked)} /><span>记住用户名</span></label>
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
  const [chunkSize, setChunkSize] = useState(420);
  const [chunkOverlap, setChunkOverlap] = useState(60);
  const [minBreakSize, setMinBreakSize] = useState(180);
  const [settingsMessage, setSettingsMessage] = useState("");
  const [tab, setTab] = useState<WorkspaceTab>("chat");
  const [kbQuery, setKbQuery] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsMenuOpen, setSettingsMenuOpen] = useState(false);
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false);
  const [sidebarKbMenuOpen, setSidebarKbMenuOpen] = useState(false);
  const [topbarKbMenuOpen, setTopbarKbMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [createKbOpen, setCreateKbOpen] = useState(false);
  const [createKbName, setCreateKbName] = useState("");
  const [createKbDescription, setCreateKbDescription] = useState("");
  const [createKbError, setCreateKbError] = useState("");
  const [sidebarHistory, setSidebarHistory] = useState<QaRecord[]>([]);
  const [requestedHistory, setRequestedHistory] = useState<QaRecord | null>(null);
  const [chatResetKey, setChatResetKey] = useState(0);
  const backupInputRef = useRef<HTMLInputElement | null>(null);
  const selected = useMemo(() => knowledgeBases.find((item) => item.id === selectedId) ?? knowledgeBases[0] ?? null, [knowledgeBases, selectedId]);
  const canAdmin = canAdminKnowledgeBase(selected);
  const filtered = useMemo(() => {
    const keyword = kbQuery.trim().toLowerCase();
    return keyword ? knowledgeBases.filter((item) => item.name.toLowerCase().includes(keyword) || (item.description ?? "").toLowerCase().includes(keyword)) : knowledgeBases;
  }, [knowledgeBases, kbQuery]);
  const visibleSidebarHistory = useMemo(() => sidebarHistory.filter((item, index, items) => items.findIndex((candidate) => candidate.question === item.question) === index).slice(0, 4), [sidebarHistory]);

  useEffect(() => { void refreshKnowledgeBases(); }, []);
  useEffect(() => {
    setName(selected?.name ?? "");
    setDescription(selected?.description ?? "");
    setChunkSize(selected?.chunkSize ?? 420);
    setChunkOverlap(selected?.chunkOverlap ?? 60);
    setMinBreakSize(selected?.minBreakSize ?? 180);
    setSettingsMessage("");
  }, [selected?.id]);
  useEffect(() => {
    if (!selected) { setSidebarHistory([]); return; }
    api.listQaHistory(selected.id).then(setSidebarHistory).catch(() => setSidebarHistory([]));
  }, [selected?.id]);
  useEffect(() => {
    const compactQuery = window.matchMedia("(max-width: 1100px)");
    const syncSidebar = (event: MediaQueryListEvent | MediaQueryList) => setSidebarCollapsed(event.matches);
    syncSidebar(compactQuery);
    compactQuery.addEventListener("change", syncSidebar);
    return () => compactQuery.removeEventListener("change", syncSidebar);
  }, []);
  useEffect(() => {
    if (!createKbOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) setCreateKbOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [createKbOpen, saving]);

  function startNewConversation() {
    setRequestedHistory(null);
    setChatResetKey((value) => value + 1);
    setTab("chat");
  }

  function openSidebarHistory(item: QaRecord) {
    setRequestedHistory(item);
    setTab("chat");
  }

  function selectKnowledgeBase(id: string) {
    setSelectedId(id);
    setRequestedHistory(null);
    setSidebarKbMenuOpen(false);
    setTopbarKbMenuOpen(false);
  }

  function openCreateKnowledgeBase() {
    setCreateKbName("");
    setCreateKbDescription("");
    setCreateKbError("");
    setSidebarKbMenuOpen(false);
    setTopbarKbMenuOpen(false);
    setWorkspaceMenuOpen(false);
    setCreateKbOpen(true);
  }

  async function refreshKnowledgeBases() {
    setLoading(true);
    setError("");
    try {
      const list = await api.listKnowledgeBases();
      setKnowledgeBases(list);
      const currentStillExists = selectedId && list.some((item) => item.id === selectedId);
      if (currentStillExists) return;
      const histories = await Promise.all(list.map(async (item) => {
        try { return { item, history: await api.listQaHistory(item.id) }; }
        catch { return { item, history: [] as QaRecord[] }; }
      }));
      const preferred = histories.sort((left, right) => right.history.length - left.history.length)[0];
      setSelectedId(preferred?.item.id ?? list[0]?.id ?? null);
      if (preferred?.history) setSidebarHistory(preferred.history);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  async function createKnowledgeBase(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedName = createKbName.trim();
    if (trimmedName.length < 2) {
      setCreateKbError("请输入至少 2 个字符的知识库名称");
      return;
    }
    setSaving(true);
    setCreateKbError("");
    try {
      const created = await api.createKnowledgeBase({ name: trimmedName, description: createKbDescription.trim() });
      setKnowledgeBases((items) => [created, ...items]);
      setSelectedId(created.id);
      setCreateKbOpen(false);
      setTab("documents");
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "创建失败，请稍后重试";
      setCreateKbError(message.includes("already exists") ? "已有同名知识库，请换一个名称" : message);
    } finally {
      setSaving(false);
    }
  }

  async function saveSelected(event: FormEvent) {
    event.preventDefault();
    await persistSelected(false);
  }

  async function saveAndRebuild() {
    if (!selected || !canAdmin) return;
    if (!window.confirm("保存切片配置并重新解析当前知识库的全部文档？重建期间检索结果可能暂时不完整。")) return;
    await persistSelected(true);
  }

  async function persistSelected(rebuild: boolean) {
    if (!selected || !canAdmin) return;
    const validationMessage = validateChunkingSettings(chunkSize, chunkOverlap, minBreakSize);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setSaving(true);
    setError("");
    setSettingsMessage("");
    try {
      const updated = await api.updateKnowledgeBase(selected.id, {
        name,
        description,
        chunkSize,
        chunkOverlap,
        minBreakSize
      });
      setKnowledgeBases((items) => items.map((item) => item.id === updated.id ? updated : item));
      if (rebuild) {
        const result = await api.rebuildKnowledgeBase(selected.id);
        setSettingsMessage(`配置已保存，已提交 ${result.submitted} 个文档重建。`);
      } else {
        setSettingsMessage("设置已保存。新配置将在下次解析文档时生效。已有切片需要重建索引后更新。");
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setSaving(false);
    }
  }

  function applyChunkPreset(size: number, overlap: number, minBreak: number) {
    setChunkSize(size);
    setChunkOverlap(overlap);
    setMinBreakSize(minBreak);
    setSettingsMessage("");
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
    <div className={`workspace-shell cobalt-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
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
        <div className="context-heading context-brand">
          <span className="context-brand-mark" aria-hidden="true"><BookOpen size={24} /></span>
          <div><strong>企业知识库</strong></div>
          <button className="sidebar-collapse" type="button" aria-label={sidebarCollapsed ? "展开侧栏" : "收起侧栏"} title={sidebarCollapsed ? "展开侧栏" : "收起侧栏"} onClick={() => setSidebarCollapsed((value) => !value)}><PanelLeft size={19} /></button>
        </div>
        <button className="new-conversation" onClick={startNewConversation} title="开启新对话"><Plus size={18} /><span>开启新对话</span></button>
        <div className="reference-sidebar-label">知识库</div>
        <button className="active-scope" type="button" onClick={() => { setSidebarKbMenuOpen((value) => !value); setWorkspaceMenuOpen(false); }} disabled={!selected} aria-expanded={sidebarKbMenuOpen} title="切换或管理知识库">
          <div className="scope-monogram" aria-hidden="true"><Layers3 size={18} /></div>
          <div className="scope-copy"><strong title={selected?.name}>{selected?.name ?? "尚未选择"}</strong></div>
          <ChevronRight size={14} />
        </button>
        {sidebarKbMenuOpen && <div className="knowledge-switcher-menu sidebar-knowledge-menu"><header><strong>选择知识库</strong><span>{knowledgeBases.length} 个</span></header><div>{knowledgeBases.map((item) => <button type="button" className={item.id === selected?.id ? "active" : ""} key={item.id} onClick={() => selectKnowledgeBase(item.id)}><Layers3 size={16} /><span><strong>{item.name}</strong><small>{roleLabel(item.accessRole)}</small></span>{item.id === selected?.id && <Check size={15} />}</button>)}</div><button type="button" className="knowledge-create-action" onClick={openCreateKnowledgeBase}><Plus size={16} />新建知识库</button></div>}
        <div className="reference-sidebar-label recent-label">最近对话</div>
        <div className="sidebar-conversation-list">
          {visibleSidebarHistory.length === 0 && <span className="sidebar-empty-history">暂无对话记录</span>}
          {visibleSidebarHistory.map((item, index) => <button type="button" key={item.id} className={requestedHistory?.id === item.id || (!requestedHistory && index === 0 && tab === "chat") ? "active" : ""} onClick={() => openSidebarHistory(item)} title={item.question}><MessageSquareText size={16} /><span>{item.question}</span></button>)}
        </div>
      </aside>

      <section className="main-panel">
        <header className="topbar">
          <div className="topbar-title"><button type="button" onClick={() => { setTopbarKbMenuOpen((value) => !value); setWorkspaceMenuOpen(false); }}>当前空间：企业知识库<ChevronRight size={14} /></button><button type="button" className="status-pill" onClick={() => { setTopbarKbMenuOpen((value) => !value); setWorkspaceMenuOpen(false); }} aria-expanded={topbarKbMenuOpen}><i />知识来源范围：{selected?.name ?? "未选择知识库"}<ChevronRight size={14} /></button></div>
          <div className="topbar-actions"><button onClick={() => setTab("chat")} title="最近问答" aria-label="最近问答"><Clock3 size={20} /></button><button onClick={() => setTab("documents")} title="文档库" aria-label="文档库"><Bookmark size={20} /></button><button onClick={() => { setWorkspaceMenuOpen((value) => !value); setTopbarKbMenuOpen(false); }} title="更多功能" aria-label="更多功能"><MoreHorizontal size={22} /></button></div>
          {topbarKbMenuOpen && <div className="knowledge-switcher-menu topbar-knowledge-menu"><header><strong>知识来源范围</strong><span>仅显示你有权限的内容</span></header><div>{knowledgeBases.map((item) => <button type="button" className={item.id === selected?.id ? "active" : ""} key={item.id} onClick={() => selectKnowledgeBase(item.id)}><Layers3 size={16} /><span><strong>{item.name}</strong><small>{roleLabel(item.accessRole)}</small></span>{item.id === selected?.id && <Check size={15} />}</button>)}</div><button type="button" className="knowledge-create-action" onClick={openCreateKnowledgeBase}><Plus size={16} />新建知识库</button></div>}
          {workspaceMenuOpen && <div className="workspace-menu">
            <span className="workspace-menu-label">工作区</span>
            <button onClick={() => { setTab("chat"); setWorkspaceMenuOpen(false); }}><MessageSquareText size={16} />知识问答</button>
            <button onClick={() => { setTab("documents"); setWorkspaceMenuOpen(false); }}><FileText size={16} />文档库</button>
            <button onClick={() => { setTab("tasks"); setWorkspaceMenuOpen(false); }}><Check size={16} />任务中心</button>
            <span className="workspace-menu-label">知识库</span>
            <button onClick={openCreateKnowledgeBase}><Plus size={16} />新建知识库</button>
            <button onClick={() => { setSettingsOpen(true); setWorkspaceMenuOpen(false); }}><Settings size={16} />知识库设置</button>
            {user.role === "ADMIN" && <span className="workspace-menu-label">管理员</span>}
            {user.role === "ADMIN" && <button onClick={() => { setTab("rag"); setWorkspaceMenuOpen(false); }}><Bot size={16} />RAG 配置</button>}
            {user.role === "ADMIN" && <button onClick={() => { setTab("monitor"); setWorkspaceMenuOpen(false); }}><Database size={16} />系统状态</button>}
            {user.role === "ADMIN" && <button onClick={() => { setTab("admin"); setWorkspaceMenuOpen(false); }}><ShieldCheck size={16} />系统管理</button>}
            <span className="workspace-menu-label">账户</span>
            <button onClick={() => { setProfileOpen(true); setWorkspaceMenuOpen(false); }}>个人中心</button>
            <button onClick={onLogout}><LogOut size={16} />退出登录</button>
          </div>}
        </header>
        {error && <div className="inline-error workspace-error">{error}</div>}
        <section className="work-panel">
          {tab === "documents" && <DocumentStage selected={selected} />}
          {tab === "chat" && <ChatStage selected={selected} canDebug={user.role === "ADMIN"} requestedHistory={requestedHistory} resetKey={chatResetKey} onHistoryChanged={setSidebarHistory} />}
          {tab === "rag" && <RagStage selected={selected} />}
          {tab === "tasks" && <TaskCenterStage />}
          {tab === "monitor" && <MonitorStage />}
          {tab === "admin" && <AdminStage />}
        </section>
      </section>

      {createKbOpen && <div className="create-kb-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setCreateKbOpen(false); }}>
        <form className="create-kb-dialog" onSubmit={createKnowledgeBase}>
          <header>
            <span className="create-kb-icon" aria-hidden="true"><Layers3 size={22} /></span>
            <div><strong>新建知识库</strong><p>先命名，再上传文档。创建后可随时调整成员权限。</p></div>
            <button type="button" className="icon-button" aria-label="关闭新建知识库" onClick={() => setCreateKbOpen(false)} disabled={saving}><X size={19} /></button>
          </header>
          <div className="create-kb-fields">
            <label><span>知识库名称 <em>必填</em></span><input autoFocus value={createKbName} onChange={(event) => { setCreateKbName(event.target.value); setCreateKbError(""); }} placeholder="例如：Java 后端面试资料" maxLength={128} /></label>
            <label><span>用途说明 <small>选填</small></span><textarea value={createKbDescription} onChange={(event) => setCreateKbDescription(event.target.value)} placeholder="简要说明收录内容，便于团队成员识别" maxLength={512} /></label>
            <div className="create-kb-next"><FileText size={17} /><span><strong>下一步</strong> 创建后进入文档库上传资料</span></div>
            {createKbError && <div className="inline-error">{createKbError}</div>}
          </div>
          <footer><button type="button" onClick={() => setCreateKbOpen(false)} disabled={saving}>取消</button><button className="primary-action compact" type="submit" disabled={saving || createKbName.trim().length < 2}>{saving ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}创建并上传文档</button></footer>
        </form>
      </div>}

      {settingsOpen && <div className="settings-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setSettingsOpen(false); }}>
        <section className="editor-panel settings-drawer">
          <header><div><span>知识库设置</span><strong>{selected?.name}</strong></div><button type="button" className="icon-button" onClick={() => setSettingsOpen(false)} aria-label="关闭设置"><X size={20} /></button></header>
          <div className="drawer-scroll">
            <form className="drawer-basic-form" onSubmit={saveSelected}>
              <div className="panel-title"><Pencil size={18} /><span>基础信息</span></div>
              <label><span>名称</span><input value={name} onChange={(event) => setName(event.target.value)} disabled={!selected || !canAdmin} maxLength={128} /></label>
              <label><span>描述</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} disabled={!selected || !canAdmin} maxLength={512} /></label>
              <section className="chunk-settings-card">
                <div className="panel-title"><Layers3 size={18} /><span>文档切片</span></div>
                <p>优先按段落和句子边界切分，以下长度作为切片上限。修改后需重建索引才会影响已有文档。</p>
                <div className="chunk-preset-row" aria-label="切片配置预设">
                  <button type="button" className={chunkSize === 300 && chunkOverlap === 40 && minBreakSize === 120 ? "active" : ""} onClick={() => applyChunkPreset(300, 40, 120)} disabled={!canAdmin}>精准引用</button>
                  <button type="button" className={chunkSize === 420 && chunkOverlap === 60 && minBreakSize === 180 ? "active" : ""} onClick={() => applyChunkPreset(420, 60, 180)} disabled={!canAdmin}>均衡模式</button>
                  <button type="button" className={chunkSize === 800 && chunkOverlap === 120 && minBreakSize === 320 ? "active" : ""} onClick={() => applyChunkPreset(800, 120, 320)} disabled={!canAdmin}>长文理解</button>
                </div>
                <div className="chunk-settings-grid">
                  <label><span>切片长度 <small>200–1500</small></span><input type="number" min={200} max={1500} value={chunkSize} onChange={(event) => setChunkSize(Number(event.target.value))} disabled={!selected || !canAdmin} /></label>
                  <label><span>重叠长度 <small>0–300</small></span><input type="number" min={0} max={300} value={chunkOverlap} onChange={(event) => setChunkOverlap(Number(event.target.value))} disabled={!selected || !canAdmin} /></label>
                  <label><span>最小切分位置 <small>50–1200</small></span><input type="number" min={50} max={1200} value={minBreakSize} onChange={(event) => setMinBreakSize(Number(event.target.value))} disabled={!selected || !canAdmin} /></label>
                </div>
                <div className="chunk-settings-summary"><CircleAlert size={16} /><span>当前配置：每片最多 {chunkSize} 字符，前后重叠 {chunkOverlap} 字符。</span></div>
              </section>
              {settingsMessage && <div className="settings-success"><CircleCheckBig size={16} />{settingsMessage}</div>}
              {selected && <div className="permission-summary"><span>{roleLabel(selected.accessRole)}</span><strong>{selected.owned ? "拥有者" : selected.ownerUsername}</strong><small>{selected.memberCount} 个成员</small></div>}
              <div className="editor-actions">
                <button className="primary-action compact" type="submit" disabled={!selected || !canAdmin || saving}>{saving ? <Loader2 className="spin" size={17} /> : <Check size={17} />}保存</button>
                <button className="rebuild-action" type="button" onClick={saveAndRebuild} disabled={!selected || !canAdmin || saving}><RefreshCw size={16} />保存并重建</button>
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
    const targetUsername = (selectedCandidate?.username ?? username).trim();
    if (!targetUsername) {
      setError("请输入要添加的账号");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const added = await api.addKnowledgeBaseMember(selected.id, targetUsername, role);
      setUsername("");
      setSelectedCandidate(null);
      setRole("VIEWER");
      setCandidates([]);
      setCandidateOpen(false);
      setMessage(`已将 ${added.username} 加入「${selected.name}」，对方刷新后即可看到该知识库`);
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
        <button type="submit" disabled={!selected || !canManage || saving || username.trim().length === 0}>添加成员</button>
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
  const [documentQuery, setDocumentQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
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

  const documentTypes = useMemo(() => Array.from(new Set(documents.map((item) => item.fileType?.toUpperCase()).filter(Boolean))), [documents]);
  const filteredDocuments = documents.filter((item) => {
    const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
    const matchesType = typeFilter === "ALL" || item.fileType?.toUpperCase() === typeFilter;
    const keyword = documentQuery.trim().toLowerCase();
    const matchesQuery = !keyword || item.fileName.toLowerCase().includes(keyword) || item.fileType?.toLowerCase().includes(keyword);
    return matchesStatus && matchesType && matchesQuery;
  });
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
    <div className="document-stage document-console">
      <header className="document-console-head">
        <div><h2>文档库</h2></div>
        <div className="document-head-actions">
          <button className="primary-action compact" onClick={() => fileInputRef.current?.click()} disabled={!selected || !canEdit || uploading}>{uploading ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}上传文档</button>
        </div>
      </header>
      <input ref={fileInputRef} type="file" onChange={uploadDocument} hidden />
      <section className="document-summary" aria-label="文档概览">
        <div className="summary-blue"><FileText size={30} /><span><small>全部文档</small><strong>{documentStats.total}</strong></span></div>
        <div className="summary-green"><CircleCheckBig size={30} /><span><small>已就绪</small><strong>{documentStats.completed}</strong></span></div>
        <div className="summary-amber"><Hourglass size={30} /><span><small>处理中</small><strong>{documentStats.parsing}</strong></span></div>
        <div className={"summary-red " + (documentStats.failed > 0 ? "has-error" : "")}><CircleAlert size={30} /><span><small>需处理</small><strong>{documentStats.failed}</strong></span></div>
        <div className="summary-purple"><Layers3 size={30} /><span><small>可检索切片</small><strong>{documentStats.totalChunks}</strong></span></div>
      </section>
      <section className="document-browser-panel">
        <div className="document-toolbar">
          <label className="document-name-search"><input value={documentQuery} onChange={(event) => setDocumentQuery(event.target.value)} placeholder="搜索文档名称、标签或内容" /><Search size={18} /></label>
          <div className="document-toolbar-actions">
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as DocumentStatusFilter)} aria-label="筛选文档状态">{["ALL", "UPLOADED", "PARSING", "COMPLETED", "FAILED"].map((status) => <option key={status} value={status}>{status === "ALL" ? "全部状态" : statusLabel(status as DocumentStatusFilter)}</option>)}</select>
            <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)} aria-label="筛选文档类型"><option value="ALL">全部类型</option>{documentTypes.map((type) => <option key={type} value={type}>{type}</option>)}</select>
            <button className="document-refresh" onClick={() => void loadDocuments()} disabled={!selected || loading} aria-label="刷新文档">{loading ? <Loader2 className="spin" size={18} /> : <RefreshCw size={18} />}</button>
          </div>
        </div>
        {error && <div className="inline-error">{error}</div>}
        <div className="document-table">
        <div className="document-table-head"><span><input type="checkbox" aria-label="选择全部文档" /></span><span>文档名称</span><span>类型</span><span>状态</span><span>可检索切片</span><span>更新时间</span><span>操作</span></div>
        {loading && <div className="muted-document"><Loader2 className="spin" size={18} />正在加载文档...</div>}
        {!loading && filteredDocuments.length === 0 && <div className="document-empty"><FileText size={24} /><strong>还没有符合条件的文档</strong><span>点击右上角“上传文档”开始构建知识库内容。</span></div>}
        {!loading && filteredDocuments.map((doc) => <article key={doc.id} className={"document-row " + (selectedDocumentId === doc.id ? "active" : "")}>
          <input type="checkbox" aria-label={`选择 ${doc.fileName}`} />
          <div className="document-identity"><span className={"document-file-icon file-" + (doc.fileType?.toLowerCase() || "doc")}>{doc.fileType?.slice(0, 1).toUpperCase() || "D"}</span><div><strong title={doc.fileName}>{doc.fileName}</strong>{doc.errorMsg && <small>{doc.errorMsg}</small>}</div></div>
          <span className={"document-type-badge type-" + (doc.fileType?.toLowerCase() || "doc")}>{doc.fileType?.toUpperCase() || "DOC"}</span>
          <em className={"doc-status status-" + doc.status.toLowerCase()}>{statusLabel(doc.status)}</em>
          <span className="document-chunk-count">{doc.chunkCount || "—"}</span>
          <time>{formatDateTime(doc.updatedAt)}</time>
          <details className="document-row-menu"><summary aria-label={`${doc.fileName} 操作`}><MoreVertical size={18} /></summary><div><button onClick={() => loadChunks(doc.id)}>查看切片</button><button onClick={() => parseDocument(doc.id)} disabled={!canEdit || operatingId === doc.id}>重新解析</button><button onClick={() => syncVectors(doc.id)} disabled={!canEdit || operatingId === doc.id}>同步向量</button><button className="danger-action" onClick={() => deleteDocument(doc.id)} disabled={!canEdit || operatingId === doc.id}>删除文档</button></div></details>
        </article>)}
        </div>
      </section>
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
      <details className="document-search-disclosure">
        <summary><span><Search size={17} /><strong>检索预览</strong><small>按需验证文档内容是否能够被找到</small></span><ChevronRight size={17} /></summary>
        <form className="search-panel" onSubmit={searchChunks}><div className="search-box"><input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="输入关键词搜索文档切片" /><button type="submit">搜索</button></div>{searchResults.map((result) => <article className="chunk-card" key={result.chunkId}><strong>{result.documentName} #{result.chunkNo}</strong><p>{renderHighlightedText(result.snippet || result.content, searchQuery)}</p></article>)}</form>
      </details>
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

function ChatStage({ selected, canDebug = false, requestedHistory, resetKey, onHistoryChanged }: { selected: KnowledgeBase | null; canDebug?: boolean; requestedHistory: QaRecord | null; resetKey: number; onHistoryChanged: (items: QaRecord[]) => void }) {
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

  useEffect(() => { setAnswer(null); setQuestion(""); setSessionId(null); setDebug(null); setHistory([]); setActiveCitationId(null); if (selected) void loadHistory(true); }, [selected?.id]);
  useEffect(() => { if (requestedHistory) openHistory(requestedHistory); }, [requestedHistory?.id]);
  useEffect(() => { setAnswer(null); setQuestion(""); setSessionId(null); setDebug(null); setDebugOpen(false); setActiveCitationId(null); }, [resetKey]);
  async function loadHistory(openLatest = false) {
    if (!selected) return;
    try {
      const items = await api.listQaHistory(selected.id);
      setHistory(items);
      onHistoryChanged(items);
      setSessionId((current) => current ?? items.find((item) => item.sessionId)?.sessionId ?? null);
      if (openLatest && items[0]) openHistory(items[0]);
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
      setQuestion("");
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
    setQuestion("");
    setAnswer(item);
    setSessionId(item.sessionId ?? null);
    setDebugOpen(false);
    setActiveCitationId(item.citations[0]?.chunkId ?? null);
  }

  return (
    <div className="chat-stage">
      {error && <div className="inline-error">{error}</div>}
      {answer && (
        <section className="reference-conversation" ref={answerRef}>
          <div className="user-message-row"><div>{answer.question}</div><span>我</span></div>
          <div className="assistant-message-row">
            <span className="assistant-avatar"><Bot size={22} /></span>
            <article className={"answer-card " + (loading ? "streaming" : "")}>
            {loading && <em className="streaming-badge"><Loader2 className="spin" size={14} />生成中</em>}
            {answer.answer ? (
              <div className="answer-body">{renderAnswerBody(answer.answer, answer.citations, jumpToCitation)}</div>
            ) : (
              <div className="streaming-placeholder"><Loader2 className="spin" size={16} />正在组织答案...</div>
            )}
            {answer.citations.length > 0 && <section className="inline-citations"><strong>参考来源</strong>{answer.citations.slice(0, 2).map((citation, index) => <button id={`citation-${citation.chunkId}`} type="button" className={activeCitationId === citation.chunkId ? "active" : ""} key={citation.chunkId} onClick={() => jumpToCitation(citation)}><FileText size={15} /><span><i>{index + 1}</i>{citation.documentName}</span><em>查看</em></button>)}</section>}
            </article>
          </div>
        </section>
      )}
      {!answer && <section className="chat-empty-state"><span className="assistant-avatar"><Bot size={24} /></span><strong>从企业知识中找到可靠答案</strong><p>选择一个推荐问题，或在下方输入你的问题。</p></section>}
      <section className="reference-suggestions" aria-label="推荐问题"><strong>你可能还想问</strong><div>{["缓存穿透和缓存雪崩有什么区别？", "布隆过滤器的误判率如何控制？", "缓存空对象的过期时间怎么设置？", "如何结合限流防止缓存穿透？"].map((item) => <button type="button" key={item} onClick={() => setQuestion(item)}>{item}</button>)}</div></section>
      <form className="reference-composer" onSubmit={ask}>
        <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="输入你的问题，/ 唤起快捷指令" rows={2} />
        <div className="composer-tools"><button type="button" title="添加附件" aria-label="添加附件"><Paperclip size={20} /></button><label title="回答风格"><Sparkles size={20} /><select aria-label="回答风格" value={style} onChange={(event) => setStyle(event.target.value as AnswerStyle)}><option value="STRICT">严谨</option><option value="BRIEF">简洁</option><option value="INTERVIEW">面试</option></select></label>{canDebug && <button type="button" className="composer-debug" onClick={runDebug} disabled={!selected || loading || !question.trim()} title="管理员诊断" aria-label="管理员诊断"><ShieldCheck size={17} /></button>}</div>
        <button className="composer-send" type="submit" disabled={!selected || loading || !question.trim()} title={loading ? "生成中" : "发送问题"}>{loading ? <Loader2 className="spin" size={20} /> : <Send size={21} />}</button>
      </form>
      {canDebug && debug && <section className="debug-panel"><button type="button" onClick={() => setDebugOpen((value) => !value)}>{debugOpen ? "收起管理员诊断" : "展开管理员诊断"}</button>{debugOpen && <div><div className="diagnostic-warning"><ShieldCheck size={16} />以下内容仅对管理员展示，可能包含内部检索参数。</div><h3>{debugSummaryTitle(debug)}</h3><p>{debugSummaryText(debug)}</p>{debug.chunks.map((chunk) => <article className="chunk-card" key={chunk.chunkId}><strong>{chunk.documentName} #{chunk.chunkNo} | 得分 {formatScore(chunk.finalScore)}</strong><p>{debugChunkReason(chunk)}</p><p>{chunk.content}</p></article>)}</div>}</section>}
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
    <div className="rag-stage strategy-stage">
      <header className="workspace-page-head">
        <div><h2>问答策略</h2><p>控制答案如何检索、引用与表达。</p></div>
        <button className="primary-action compact" type="submit" form="rag-preference-form" disabled={saving}>保存配置</button>
      </header>
      {error && <div className="inline-error">{error}</div>}
      {message && <div className="success-banner">{message}</div>}
      <form id="rag-preference-form" className="strategy-grid" onSubmit={savePreference}>
        <section className="strategy-panel">
          <header><h3>检索策略</h3><p>决定从知识库中如何检索最相关的内容。</p></header>
          <div className="strategy-control"><div><strong>检索范围</strong><span>回答仅使用当前有权限的知识库</span></div><em>当前知识库</em></div>
          <label className="strategy-control strategy-range"><div><strong>召回数量</strong><span>提供给回答的候选内容数量</span></div><input type="range" min={1} max={20} value={preference.defaultTopK} onChange={(event) => setPreference({ ...preference, defaultTopK: Number(event.target.value) })} /><em>{preference.defaultTopK} 片段</em></label>
          <label className="strategy-control"><div><strong>相关度要求</strong><span>在覆盖面与准确性之间取得平衡</span></div><select value={preference.vectorWeight} onChange={(event) => setPreference({ ...preference, vectorWeight: Number(event.target.value) })}><option value="0.4">宽松</option><option value="0.6">均衡</option><option value="0.8">严格</option></select></label>
          <div className="strategy-control"><div><strong>无结果时</strong><span>未检索到相关内容时的处理方式</span></div><em>明确告知未找到</em></div>
        </section>
        <section className="strategy-panel">
          <header><h3>回答方式</h3><p>控制答案的表达风格与引用方式。</p></header>
          <div className="strategy-control strategy-style"><div><strong>回答语气</strong><span>选择默认的答案风格</span></div><div>{([['BRIEF','精炼'],['STRICT','标准'],['INTERVIEW','详细']] as const).map(([value,label]) => <button type="button" className={preference.defaultAnswerStyle === value ? "active" : ""} key={value} onClick={() => setPreference({ ...preference, defaultAnswerStyle: value })}>{label}</button>)}</div></div>
          <label className="strategy-control"><div><strong>引用来源</strong><span>在答案中始终保留可核对的依据</span></div><input type="checkbox" checked={preference.enableModel} onChange={(event) => setPreference({ ...preference, enableModel: event.target.checked })} /></label>
          <div className="answer-preview"><span>回答预览</span><p>系统会优先依据当前知识库组织答案，并在关键结论后附上可核对的来源。</p><div><small>[1] 当前知识库文档</small><small>[2] 已授权参考内容</small></div></div>
        </section>
      </form>
      <div className="strategy-disclosures">
        <details><summary><div><strong>高级检索参数（管理员）</strong><span>缓存、模型开关与内部检索行为</span></div><ChevronRight size={17} /></summary><div className="strategy-detail"><label className="check-row"><input type="checkbox" checked={preference.enableCache} onChange={(event) => setPreference({ ...preference, enableCache: event.target.checked })} /><span>启用回答缓存</span></label><button type="button" onClick={clearCache} disabled={!selected}>清理当前知识库缓存</button></div></details>
        <details><summary><div><strong>提示词模板</strong><span>{templates.length} 个已保存模板</span></div><ChevronRight size={17} /></summary><form className="strategy-detail template-editor" onSubmit={saveTemplate}><label><span>模板名称</span><input value={template.name} onChange={(event) => setTemplate({ ...template, name: event.target.value })} /></label><label><span>系统提示词</span><textarea value={template.systemPrompt} onChange={(event) => setTemplate({ ...template, systemPrompt: event.target.value })} /></label><button className="primary-action compact" type="submit" disabled={!selected || saving}>新增模板</button>{templates.map((item) => <article className="template-row" key={item.id}><strong>{item.name}</strong><span>{answerStyleLabel(item.answerStyle)}</span></article>)}</form></details>
        <details><summary><div><strong>调试与评估</strong><span>回答质量、反馈覆盖与异常记录</span></div><ChevronRight size={17} /></summary><QualityPanel overview={qualityOverview} issues={qualityIssues} filter={qualityFilter} loading={qualityLoading} onFilterChange={loadQuality} onRefresh={() => loadQuality(qualityFilter)} /></details>
      </div>
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
      <header className="workspace-page-head"><div><h2>任务中心</h2><p>跟踪文档解析进度，并处理需要人工关注的任务。</p></div><button type="button" onClick={loadTasks} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新任务</button></header>
      {error && <div className="inline-error">{error}</div>}
      <div className="task-summary-grid">
        <TaskMetric label="全部任务" value={summary.total} />
        <TaskMetric label="运行中" value={summary.running} tone="running" />
        <TaskMetric label="等待中" value={summary.pending} tone="pending" />
        <TaskMetric label="失败 / 需处理" value={summary.failed + summary.review} tone="review" />
      </div>
      <section className="task-center-panel">
        <div className="task-center-head">
          <div><h3>文档处理任务</h3><p>默认仅展示可行动的信息，详细日志按需查看。</p></div>
          <div className="task-center-actions">
            <button type="button" onClick={retrySelected} disabled={operatingId === "batch" || selectedIds.length === 0}>{operatingId === "batch" ? <Loader2 className="spin" size={15} /> : null}重试选中</button>
            <button type="button" onClick={loadTasks} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新</button>
          </div>
        </div>
        <div className="task-filter-row">
          {["ALL", "PENDING", "RUNNING", "DONE", "FAILED", "NEEDS_REVIEW"].map((item) => <button type="button" key={item} className={status === item ? "active" : ""} onClick={() => setStatus(item)}>{taskStatusLabel(item)}</button>)}
        </div>
        <div className="task-table">
          <div className="task-table-head"><span>文档</span><span>当前阶段</span><span>状态</span><span>更新时间</span><span>操作</span></div>
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
                  <span>{task.chunkCount > 0 ? `${task.chunkCount} 个切片` : "等待生成切片"}</span>
                  {(task.errorMsg || task.queueErrorMsg) && <p>{task.errorMsg ?? task.queueErrorMsg}</p>}
                </div>
                <span className="task-stage-label">{normalized === "RUNNING" ? "解析与生成向量" : normalized === "DONE" ? "处理完成" : normalized === "PENDING" ? "等待处理" : "需要人工处理"}</span>
                <em className={taskStatusTone(normalized)}>{taskStatusLabel(normalized)}</em>
                <time>{formatDateTime(task.updatedAt)}</time>
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
        <div><span>ADMIN · SYSTEM STATUS</span><h2>系统状态</h2><p>先看结论，需要排查时再展开内部详情。</p></div>
        <div className="monitor-head-actions">
          {health && <div className={"monitor-health-chip " + healthTone(health.status)}><i />{healthStatusLabel(health.status)}{unhealthyCount > 0 ? ` · ${unhealthyCount} 项异常` : ""}</div>}
          <button onClick={loadMonitor} disabled={loading}>{loading ? <Loader2 className="spin" size={16} /> : null}刷新</button>
        </div>
      </header>
      {error && <div className="inline-error">{error}</div>}

      <section className="monitor-summary" aria-label="系统摘要">
        <MonitorSummary label="服务健康" value={health ? `${health.components.length - unhealthyCount}/${health.components.length}` : "-"} hint={unhealthyCount > 0 ? `${unhealthyCount} 项异常` : "全部正常"} tone={unhealthyCount > 0 ? "warning" : "normal"} />
        <MonitorSummary label="待处理任务" value={pendingTaskCount} hint={`共 ${tasks.length} 条任务`} tone={pendingTaskCount > 0 ? "warning" : "normal"} />
        <MonitorSummary label="今日问答" value={metrics?.qaCount ?? 0} hint="已完成请求" />
        <MonitorSummary label="文档覆盖" value={overview?.documentCount ?? 0} hint={`${overview?.knowledgeBaseCount ?? 0} 个知识库`} />
        <MonitorSummary label="异常提醒" value={Number(overview?.failedDocumentCount ?? 0) + unhealthyCount} hint="需要关注" tone={Number(overview?.failedDocumentCount ?? 0) + unhealthyCount > 0 ? "warning" : "normal"} />
        <MonitorSummary label="平均响应" value={formatDuration(metrics?.averageLatencyMs)} hint="今日平均" />
      </section>

      <div className="monitor-disclosures">
        {health && <details className="monitor-disclosure monitor-primary-disclosure" open>
          <summary><div><strong>服务健康</strong><span>数据库、缓存、模型与向量库连接状态</span></div><em className={healthTone(health.status)}>{healthStatusLabel(health.status)}</em><ChevronRight size={18} /></summary>
          <div className="monitor-detail-body health-detail-grid">
            {health.components.map((component) => <article className="monitor-service-row" key={component.name}><i className={healthTone(component.status)} /><div><strong>{componentLabel(component.name)}</strong><span>{componentHealthSummary(component.name, component.status)}</span></div><em>{healthStatusLabel(component.status)}</em></article>)}
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

        <details className="monitor-disclosure monitor-primary-disclosure" open>
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
    const nextPassword = window.prompt(`请输入 ${user.username} 的新密码`);
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
      <header className="workspace-page-head"><div><h2>系统管理</h2><p>管理成员、权限与平台安全。</p></div></header>
      <div className="admin-tabs"><button className="active">成员与权限</button><button disabled>知识库治理</button><button disabled>安全与审计</button></div>
      {error && <div className="inline-error">{error}</div>}
      {overview && <div className="stats-grid"><StatusBar label="成员" value={overview.totalCount} percent={100} tone="synced" /><StatusBar label="管理员" value={overview.adminCount} percent={100} tone="fallback" /><StatusBar label="已启用" value={overview.enabledCount} percent={100} tone="partial" /><StatusBar label="已禁用" value={overview.disabledCount} percent={100} tone="pending" /></div>}
      <section className="admin-panel">
        <div className="admin-panel-head">
          <div><h3>成员管理</h3><p>检索成员、调整账号状态并处理权限。</p></div>
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
          <div className="admin-user-head"><span>成员</span><span>角色</span><span>账号状态</span><span>最后活跃</span><span>操作</span></div>
          {users.map((item) => <article className="admin-user-row" key={item.userId}><div className="admin-user-identity"><i>{item.username.slice(0,1).toUpperCase()}</i><strong>{item.username}</strong></div><em>{appRoleLabel(item.role)}</em><span className={item.enabled ? "enabled" : "disabled"}>{item.enabled ? "已启用" : "已禁用"}</span><time>{item.lastLoginAt ? formatDateTime(item.lastLoginAt) : "-"}</time><div className="admin-row-actions"><button onClick={() => resetPassword(item)} disabled={resettingId === item.userId}>{resettingId === item.userId ? <Loader2 className="spin" size={14} /> : null}重置密码</button><button className={!item.enabled ? "enable-action" : ""} onClick={() => toggleUser(item)}>{item.enabled ? "禁用" : "启用"}</button></div></article>)}
        </div>
      </section>
      <details className="admin-panel admin-audit-disclosure">
        <summary className="admin-panel-head">
          <div><h3>审计日志</h3><p>查看登录尝试与管理员操作记录。</p></div>
          <ChevronRight size={18} />
        </summary>
        <div className="admin-audit-body"><button type="button" onClick={refreshLogs} disabled={loading}>{loading ? <Loader2 className="spin" size={15} /> : null}刷新日志</button>
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
        </div>
      </details>
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

function validateChunkingSettings(chunkSize: number, chunkOverlap: number, minBreakSize: number) {
  if (!Number.isInteger(chunkSize) || chunkSize < 200 || chunkSize > 1500) return "切片长度必须是 200 到 1500 之间的整数。";
  if (!Number.isInteger(chunkOverlap) || chunkOverlap < 0 || chunkOverlap > 300) return "重叠长度必须是 0 到 300 之间的整数。";
  if (chunkOverlap >= chunkSize || chunkOverlap > Math.floor(chunkSize * 0.3)) return "重叠长度必须小于切片长度，且不能超过切片长度的 30%。";
  if (!Number.isInteger(minBreakSize) || minBreakSize < 50 || minBreakSize > 1200 || minBreakSize > chunkSize) return "最小切分位置必须在 50 和切片长度之间。";
  return "";
}
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
    const labelledSection = block.match(/^(结论|说明|依据|建议|总结)[:：]?\s*([\s\S]+)$/);
    if (labelledSection) {
      return <section className="answer-text-section" key={`section-${blockIndex}`}><strong>{labelledSection[1]}</strong><p>{renderAnswerInline(labelledSection[2], citations, onCitationClick)}</p></section>;
    }
    const pointsSection = block.match(/^要点[:：]?\s*([\s\S]+)$/);
    if (pointsSection) {
      const points = pointsSection[1].split(/(?=\d+[.、]\s*)/).map((item) => item.replace(/^\d+[.、]\s*/, "").trim()).filter(Boolean);
      return <section className="answer-text-section answer-points-section" key={`points-${blockIndex}`}><strong>要点</strong><ol className="answer-points">{points.map((point, pointIndex) => <li key={pointIndex}>{renderAnswerInline(point, citations, onCitationClick)}</li>)}</ol></section>;
    }
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
function healthTone(status?: string | null) { const value = normalizeCode(status); return value === "UP" || value === "OK" || value === "SUCCESS" ? "success" : value === "WARN" || value === "WARNING" ? "warning" : "danger"; }
function healthStatusLabel(status?: string | null) { const labels: Record<string, string> = { UP: "正常", OK: "正常", SUCCESS: "成功", DOWN: "异常", ERROR: "错误", FAILED: "失败", WARN: "告警", WARNING: "告警", DISABLED: "未启用", ENABLED: "已启用", MISSING: "缺失", READY: "就绪" }; return status ? labels[normalizeCode(status)] ?? status : "-"; }
function componentLabel(name: string) { const key = normalizeCode(name); const labels: Record<string, string> = { DATABASE: "数据库", MYSQL: "数据库", REDIS: "缓存服务", EMBEDDING: "内容索引", EMBEDDING_MODEL: "内容索引", CHAT: "AI 服务", CHAT_MODEL: "AI 服务", VECTOR: "向量索引", VECTOR_STORE: "向量索引", RAG_CACHE: "回答缓存", RATE_LIMIT: "访问保护" }; return labels[key] ?? name; }
function componentHealthSummary(name: string, status?: string | null) {
  if (healthTone(status) !== "success") return "当前状态异常，建议管理员展开诊断详情。";
  const summaries: Record<string, string> = {
    DATABASE: "连接正常，数据读写可用",
    MYSQL: "连接正常，数据读写可用",
    REDIS: "缓存服务运行稳定",
    EMBEDDING: "文档内容可正常建立索引",
    EMBEDDING_MODEL: "文档内容可正常建立索引",
    CHAT: "知识问答服务可用",
    CHAT_MODEL: "知识问答服务可用",
    VECTOR: "检索索引连接正常",
    VECTOR_STORE: "检索索引连接正常",
    RAG_CACHE: "回答缓存策略运行正常",
    RATE_LIMIT: "访问保护策略已启用"
  };
  return summaries[normalizeCode(name)] ?? "服务运行正常";
}
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
