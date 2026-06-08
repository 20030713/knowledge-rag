import React, { FormEvent, useEffect, useMemo, useState } from "react";
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
  ShieldCheck,
  Trash2,
  UploadCloud
} from "lucide-react";
import {
  api,
  clearToken,
  CurrentUser,
  getToken,
  KnowledgeBase,
  setToken
} from "./api";
import "./styles.css";

type AuthMode = "login" | "register";
type WorkspaceTab = "documents" | "chat" | "settings";

function App() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [booting, setBooting] = useState(true);

  useEffect(() => {
    if (!getToken()) {
      setBooting(false);
      return;
    }
    api
      .me()
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setBooting(false));
  }, []);

  if (booting) {
    return (
      <div className="screen-loader">
        <Loader2 className="spin" size={28} />
      </div>
    );
  }

  if (!user) {
    return <AuthScreen onAuthed={setUser} />;
  }

  return <Workspace user={user} onLogout={() => {
    clearToken();
    setUser(null);
  }} />;
}

function AuthScreen({ onAuthed }: { onAuthed: (user: CurrentUser) => void }) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("demo_user");
  const [password, setPassword] = useState("123456");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      if (mode === "register") {
        await api.register({ username, password });
      }
      const login = await api.login({ username, password });
      setToken(login.token);
      onAuthed({
        userId: login.userId,
        username: login.username,
        role: login.role
      });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-copy">
        <div className="product-mark">
          <Database size={22} />
          <span>Knowledge RAG</span>
        </div>
        <h1>企业知识库问答工作台</h1>
        <p>围绕私有文档建立知识库，向后端 RAG 流程提供清晰、可演示的操作入口。</p>
        <div className="auth-metrics" aria-label="project modules">
          <div><strong>JWT</strong><span>身份鉴权</span></div>
          <div><strong>KB</strong><span>知识边界</span></div>
          <div><strong>RAG</strong><span>引用溯源</span></div>
        </div>
      </section>

      <form className="auth-panel" onSubmit={submit}>
        <div className="segmented">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
            登录
          </button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>
            注册
          </button>
        </div>
        <label>
          <span>用户名</span>
          <input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} maxLength={32} />
        </label>
        <label>
          <span>密码</span>
          <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={6} maxLength={64} />
        </label>
        {error && <div className="inline-error">{error}</div>}
        <button className="primary-action" type="submit" disabled={loading}>
          {loading ? <Loader2 className="spin" size={18} /> : <ShieldCheck size={18} />}
          {mode === "login" ? "进入工作台" : "创建账号并进入"}
        </button>
      </form>
    </main>
  );
}

function Workspace({ user, onLogout }: { user: CurrentUser; onLogout: () => void }) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tab, setTab] = useState<WorkspaceTab>("documents");

  const selected = useMemo(
    () => knowledgeBases.find((item) => item.id === selectedId) ?? knowledgeBases[0] ?? null,
    [knowledgeBases, selectedId]
  );

  useEffect(() => {
    refreshKnowledgeBases();
  }, []);

  useEffect(() => {
    if (!selected) {
      setName("");
      setDescription("");
      return;
    }
    setName(selected.name);
    setDescription(selected.description ?? "");
    setSelectedId(selected.id);
  }, [selected?.id]);

  async function refreshKnowledgeBases() {
    setLoading(true);
    setError("");
    try {
      const list = await api.listKnowledgeBases();
      setKnowledgeBases(list);
      setSelectedId((current) => current ?? list[0]?.id ?? null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function createKnowledgeBase() {
    setSaving(true);
    setError("");
    try {
      const created = await api.createKnowledgeBase({
        name: "新的知识库",
        description: "待整理"
      });
      setKnowledgeBases((items) => [created, ...items]);
      setSelectedId(created.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "创建失败");
    } finally {
      setSaving(false);
    }
  }

  async function saveSelected(event: FormEvent) {
    event.preventDefault();
    if (!selected) return;
    setSaving(true);
    setError("");
    try {
      const updated = await api.updateKnowledgeBase(selected.id, { name, description });
      setKnowledgeBases((items) => items.map((item) => (item.id === updated.id ? updated : item)));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteSelected() {
    if (!selected) return;
    setSaving(true);
    setError("");
    try {
      await api.deleteKnowledgeBase(selected.id);
      setKnowledgeBases((items) => items.filter((item) => item.id !== selected.id));
      setSelectedId(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "删除失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="workspace">
      <aside className="sidebar">
        <div className="brand-row">
          <Database size={21} />
          <span>Knowledge RAG</span>
        </div>
        <button className="new-kb" onClick={createKnowledgeBase} disabled={saving}>
          <Plus size={17} />
          新建知识库
        </button>
        <div className="search-box">
          <Search size={16} />
          <input placeholder="搜索知识库" />
        </div>
        <div className="kb-list">
          {loading && <div className="muted-row"><Loader2 className="spin" size={16} />加载中</div>}
          {!loading && knowledgeBases.length === 0 && (
            <button className="empty-kb" onClick={createKnowledgeBase}>创建第一个知识库</button>
          )}
          {knowledgeBases.map((item) => (
            <button
              key={item.id}
              className={`kb-item ${selected?.id === item.id ? "active" : ""}`}
              onClick={() => setSelectedId(item.id)}
            >
              <BookOpen size={17} />
              <span>{item.name}</span>
              <ChevronRight size={15} />
            </button>
          ))}
        </div>
        <div className="account-row">
          <div>
            <strong>{user.username}</strong>
            <span>{user.role}</span>
          </div>
          <button className="icon-button" onClick={onLogout} title="退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      <section className="main-panel">
        <header className="topbar">
          <div>
            <p>知识库</p>
            <h2>{selected ? selected.name : "未选择"}</h2>
          </div>
          <div className="status-pill">
            <Check size={16} />
            已连接后端
          </div>
        </header>

        {error && <div className="toast-error">{error}</div>}

        <div className="content-grid">
          <form className="editor-panel" onSubmit={saveSelected}>
            <div className="panel-title">
              <Pencil size={18} />
              <span>基础信息</span>
            </div>
            <label>
              <span>名称</span>
              <input value={name} onChange={(event) => setName(event.target.value)} disabled={!selected} maxLength={128} />
            </label>
            <label>
              <span>描述</span>
              <textarea value={description} onChange={(event) => setDescription(event.target.value)} disabled={!selected} maxLength={512} />
            </label>
            <div className="editor-actions">
              <button className="primary-action compact" type="submit" disabled={!selected || saving}>
                {saving ? <Loader2 className="spin" size={17} /> : <Check size={17} />}
                保存
              </button>
              <button className="danger-action" type="button" onClick={deleteSelected} disabled={!selected || saving}>
                <Trash2 size={17} />
                删除
              </button>
            </div>
          </form>

          <section className="work-panel">
            <div className="tabs">
              <button className={tab === "documents" ? "active" : ""} onClick={() => setTab("documents")}><FileText size={17} />文档</button>
              <button className={tab === "chat" ? "active" : ""} onClick={() => setTab("chat")}><MessageSquareText size={17} />问答</button>
              <button className={tab === "settings" ? "active" : ""} onClick={() => setTab("settings")}><Bot size={17} />RAG</button>
            </div>
            {tab === "documents" && <DocumentStage />}
            {tab === "chat" && <ChatStage selected={selected} />}
            {tab === "settings" && <RagStage />}
          </section>
        </div>
      </section>
    </main>
  );
}

function DocumentStage() {
  return (
    <div className="stage-surface">
      <UploadCloud size={34} />
      <h3>文档入口</h3>
      <p>上传、解析状态、失败重试会在文档模块接入后启用。</p>
      <div className="pipeline">
        <span>UPLOADED</span>
        <span>PARSING</span>
        <span>EMBEDDING</span>
        <span>COMPLETED</span>
      </div>
    </div>
  );
}

function ChatStage({ selected }: { selected: KnowledgeBase | null }) {
  return (
    <div className="chat-mock">
      <div className="message user">这份知识库里的核心结论是什么？</div>
      <div className="message assistant">
        {selected ? `等待 ${selected.name} 的文档切片与 RAG 问答接口接入。` : "请先创建知识库。"}
      </div>
    </div>
  );
}

function RagStage() {
  return (
    <div className="rag-grid">
      <div><strong>TopK</strong><span>5</span></div>
      <div><strong>Cache</strong><span>Redis</span></div>
      <div><strong>Scope</strong><span>kb_id</span></div>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
