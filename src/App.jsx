import { Client } from "@stomp/stompjs";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import {
  Activity,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  BadgeCheck,
  Bell,
  Bolt,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  CircleHelp,
  ClipboardList,
  Clock3,
  Command,
  Copy,
  FilePlus2,
  Gift,
  Globe2,
  Grid2X2,
  LayoutTemplate,
  LogOut,
  MapPin,
  Menu,
  Monitor,
  MoreHorizontal,
  Pencil,
  Play,
  Plus,
  QrCode,
  Radio,
  RefreshCw,
  Search,
  Send,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Ticket,
  Timer,
  Trophy,
  Users,
  Volume2,
  X,
} from "lucide-react";
import {
  ApiError,
  api,
  clearSession,
  createIdempotencyKey,
  getAccessToken,
  getParticipantToken,
  getScreenSession,
  getStoredIdentity,
  login,
  logout,
  refreshSession,
  setParticipantToken,
  setScreenSession,
} from "./api";
import {
  activeVenueCode,
  buildRegistrationPayload,
  registrationFieldKey,
  splitRegistrationOptions,
} from "./registration";

const AuthContext = createContext(null);
const navItems = [
  { id: "overview", label: "活动总览", icon: Grid2X2 },
  { id: "activities", label: "活动管理", icon: CalendarDays },
  { id: "control", label: "实时控场", icon: Radio },
  { id: "questions", label: "题库与组卷", icon: CircleHelp },
  { id: "participants", label: "参与者", icon: Users },
  { id: "rewards", label: "奖品与核销", icon: Gift },
  { id: "screens", label: "大屏管理", icon: Monitor },
  { id: "settings", label: "站点与权限", icon: Settings2 },
];

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/app/*"
            element={
              <RequireStaff>
                <StaffApp />
              </RequireStaff>
            }
          />
          <Route path="/join/demo" element={<ParticipantEntryRedirect />} />
          <Route path="/join/:activityId" element={<ParticipantPortal />} />
          <Route
            path="/lottery/:activityId"
            element={<ParticipantPortal lotteryMode />}
          />
          <Route path="/screen/:activityId" element={<PublicScreen />} />
          <Route path="*" element={<Navigate to="/app/overview" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredIdentity);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let mounted = true;
    if (!getAccessToken()) {
      setReady(true);
      return undefined;
    }
    refreshSession()
      .then((session) => mounted && setUser(session.user))
      .catch(() => {
        clearSession();
        mounted && setUser(null);
      })
      .finally(() => mounted && setReady(true));
    return () => {
      mounted = false;
    };
  }, []);
  const value = useMemo(
    () => ({
      user,
      ready,
      signIn: async (email, password) => {
        const session = await login(email, password);
        setUser(session.user);
        return session.user;
      },
      signOut: async () => {
        await logout();
        setUser(null);
      },
    }),
    [user, ready],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function useAuth() {
  return useContext(AuthContext);
}

function RequireStaff({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return <LoadingPage label="正在验证工作台权限" />;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function ParticipantEntryRedirect() {
  const navigate = useNavigate();
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;
    api.activities()
      .then((activities) => {
        const target =
          activities.find((activity) => activity.status === "LIVE") ||
          activities[0];
        if (mounted && target?.id) navigate(`/join/${target.id}`, { replace: true });
        else if (mounted) setError("当前没有可参加的活动");
      })
      .catch((cause) => mounted && setError(cause.message));
    return () => {
      mounted = false;
    };
  }, [navigate]);

  if (error) {
    return <BackendProblem message={error} onRetry={() => window.location.reload()} />;
  }
  return <LoadingPage label="正在查找进行中的活动" />;
}

function LoadingPage({ label = "正在加载活动数据" }) {
  return (
    <div className="loading-page">
      <div className="loading-mark">
        <span />
        <span />
        <span />
      </div>
      <p>{label}</p>
    </div>
  );
}

function LoginPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  if (user) return <Navigate to="/app/overview" replace />;
  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await signIn(email, password);
      navigate("/app/overview");
    } catch (cause) {
      setError(cause.message || "无法登录，请检查账号和密码");
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <main className="login-page">
      <section className="login-story">
        <Link className="product-logo product-logo--light" to="/login">
          <Mark />
          <span>矩阵现场</span>
        </Link>
        <div className="login-story__copy">
          <span className="eyebrow">LIVE EVENT OPERATIONS</span>
          <h1>
            让每一次
            <br />
            现场参与都有回响
          </h1>
          <p>把登记、竞赛、抽奖和大屏控制汇聚到一套有秩序的实时系统。</p>
        </div>
        <div className="story-tile story-tile--signal">
          <span>在线终端</span>
          <strong>3,827</strong>
          <i>
            <Activity size={18} />
          </i>
        </div>
        <div className="story-tile story-tile--event">
          <span>
            <i /> 活动正在进行
          </span>
          <strong>信号跃迁 · 上海</strong>
        </div>
        <p className="login-copyright">Matrix Live · Secure workspace</p>
      </section>
      <section className="login-form-side">
        <div className="login-card">
          <span className="eyebrow">STAFF SIGN IN</span>
          <h2>进入活动工作台</h2>
          <p>使用受授权的工作人员账户登录。</p>
          <form onSubmit={submit}>
            <label>
              工作账号
              <input
                type="text"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="输入工作账号"
                autoComplete="username"
                required
              />
            </label>
            <label>
              密码
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="输入密码"
                autoComplete="current-password"
                required
              />
            </label>
            {error && (
              <p className="form-error">
                <CircleAlert size={16} />
                {error}
              </p>
            )}
            <button
              className="primary-button"
              type="submit"
              disabled={submitting}
            >
              {submitting ? "正在验证" : "安全登录"}
              <ArrowRight size={17} />
            </button>
          </form>
          <div className="login-hint">
            <ShieldCheck size={17} />
            <span>登录行为将被记录并按活动范围授权</span>
          </div>
        </div>
        <Link className="public-link" to="/join/demo">
          <QrCode size={17} />
          参与者入口
        </Link>
      </section>
    </main>
  );
}

function StaffApp() {
  const { user, signOut } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activities, setActivities] = useState([]);
  const [activityId, setActivityId] = useState(
    localStorage.getItem("matrix.activity-id") || "",
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const reloadActivities = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const list = await api.activities();
      setActivities(list);
      setActivityId((current) =>
        current && list.some((item) => item.id === current)
          ? current
          : list[0]?.id || "",
      );
    } catch (cause) {
      setError(cause.message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    reloadActivities();
  }, [reloadActivities]);
  useEffect(() => {
    if (activityId) localStorage.setItem("matrix.activity-id", activityId);
  }, [activityId]);
  useEffect(() => {
    document.body.classList.toggle("staff-menu-open", sidebarOpen);
    return () => document.body.classList.remove("staff-menu-open");
  }, [sidebarOpen]);
  if (loading) return <LoadingPage />;
  if (error)
    return <BackendProblem message={error} onRetry={reloadActivities} />;
  return (
    <div className="staff-shell">
      <button
        className={`staff-nav-backdrop ${sidebarOpen ? "is-open" : ""}`}
        type="button"
        aria-label="关闭导航菜单"
        aria-hidden={!sidebarOpen}
        onClick={() => setSidebarOpen(false)}
      />
      <StaffSidebar
        user={user}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onSignOut={signOut}
      />
      <main className="staff-main">
        <StaffTopbar
          activities={activities}
          activityId={activityId}
          setActivityId={setActivityId}
          onMenuToggle={() => setSidebarOpen(true)}
        />
        <Routes>
          <Route
            path="overview"
            element={
              <OverviewPage activityId={activityId} activities={activities} />
            }
          />
          <Route
            path="activities"
            element={
              <ActivitiesPage
                activities={activities}
                reload={reloadActivities}
                user={user}
                setActivityId={setActivityId}
              />
            }
          />
          <Route
            path="control"
            element={<ControlPage activityId={activityId} />}
          />
          <Route
            path="questions"
            element={<QuestionsPage activityId={activityId} />}
          />
          <Route
            path="participants"
            element={<ParticipantsPage activityId={activityId} />}
          />
          <Route
            path="rewards"
            element={<RewardsPage activityId={activityId} />}
          />
          <Route
            path="screens"
            element={<ScreensPage activityId={activityId} />}
          />
          <Route
            path="settings"
            element={
              <SettingsPage
                user={user}
                activityId={activityId}
                activity={activities.find((item) => item.id === activityId)}
                reloadActivities={reloadActivities}
              />
            }
          />
          <Route path="*" element={<Navigate to="overview" replace />} />
        </Routes>
      </main>
    </div>
  );
}

function StaffSidebar({ user, open, onClose, onSignOut }) {
  const location = useLocation();
  const navigate = useNavigate();
  const active = location.pathname.split("/").pop();
  const go = (path) => {
    navigate(path);
    onClose();
  };
  return (
    <aside className={`staff-sidebar ${open ? "is-open" : ""}`}>
      <div className="staff-sidebar__header">
        <Link to="/app/overview" className="product-logo product-logo--sidebar" onClick={onClose}>
        <Mark />
        <span>矩阵现场</span>
        </Link>
        <button
          className="staff-sidebar__close"
          type="button"
          aria-label="关闭导航菜单"
          onClick={onClose}
        >
          <X size={19} />
        </button>
      </div>
      <div className="sidebar-context">
        <span>运营工作区</span>
        <strong>{user?.organization || "Matrix Live"}</strong>
        <small>
          <i />
          安全连接
        </small>
      </div>
      <nav>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              type="button"
              className={active === item.id ? "is-active" : ""}
              key={item.id}
              onClick={() => go(`/app/${item.id}`)}
            >
              <Icon size={18} />
              <span>{item.label}</span>
              {item.id === "control" && <em>LIVE</em>}
            </button>
          );
        })}
      </nav>
      <div className="sidebar-footer">
        <button type="button" onClick={() => go("/app/settings")}>
          <CircleHelp size={18} />
          帮助与支持
        </button>
        <div className="staff-profile">
          <Avatar name={user?.displayName || user?.name || "管"} />
          <div>
            <strong>{user?.displayName || user?.name || "管理员"}</strong>
            <span>{roleLabel(user?.role)}</span>
          </div>
          <button type="button" title="退出登录" onClick={onSignOut}>
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
}

function StaffTopbar({ activities, activityId, setActivityId, onMenuToggle }) {
  const location = useLocation();
  const current = activities.find((item) => item.id === activityId);
  const currentPage = navItems.find(
    (item) => item.id === location.pathname.split("/").pop(),
  );
  return (
    <header className="staff-topbar">
      <button
        className="staff-menu-toggle"
        type="button"
        aria-label="打开导航菜单"
        onClick={onMenuToggle}
      >
        <Menu size={19} />
      </button>
      <div className="crumb">
        <span>活动运营</span>
        <ChevronRight size={14} />
        <strong>{current?.name || "未选择活动"}</strong>
      </div>
      <div className="staff-topbar__mobile-title">
        <strong>{currentPage?.label || "活动总览"}</strong>
        <span>{current?.name || "未选择活动"}</span>
      </div>
      <div className="topbar-actions">
        <div className="activity-select">
          <MapPin size={15} />
          <select
            value={activityId}
            onChange={(event) => setActivityId(event.target.value)}
          >
            {activities.map((activity) => (
              <option key={activity.id} value={activity.id}>
                {activity.name} · {activity.city}
              </option>
            ))}
          </select>
          <ChevronDown size={15} />
        </div>
        <button className="toolbar-icon" type="button" aria-label="通知">
          <Bell size={19} />
          <i />
        </button>
      </div>
    </header>
  );
}

function PageHeader({ eyebrow, title, description, action }) {
  return (
    <div className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && (
          <p className="page-header__description">{description}</p>
        )}
      </div>
      {action}
    </div>
  );
}

function OverviewPage({ activityId, activities }) {
  const [participants, setParticipants] = useState([]);
  const [scores, setScores] = useState([]);
  const [control, setControl] = useState(null);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [people, board, state] = await Promise.all([
        api.participants(activityId),
        api.scoreboard(activityId),
        api.controlState(activityId),
      ]);
      setParticipants(people);
      setScores(board);
      setControl(state);
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId]);
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, load);
  const activity = activities.find((item) => item.id === activityId);
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="ACTIVITY PULSE"
        title="活动总览"
        description="实时读取当前活动、参与者和控场服务的状态。"
        action={
          <Link className="secondary-button" to="/app/control">
            <Radio size={16} />
            进入控场
          </Link>
        }
      />
      {error && <InlineError text={error} onRetry={load} />}
      <section className="metric-grid">
        <Metric
          icon={Users}
          label="报名参与者"
          value={participants.length}
          sub="按活动范围统计"
          tone="teal"
        />
        <Metric
          icon={Activity}
          label="当前阶段"
          value={stageLabel(control?.stage || "LOBBY")}
          sub={
            control?.seconds ? `剩余 ${control.seconds} 秒` : "等待工作人员开场"
          }
          tone="violet"
        />
        <Metric
          icon={Trophy}
          label="已产生积分"
          value={scores.reduce((sum, item) => sum + (item.score || 0), 0)}
          sub="来源于得分流水"
          tone="orange"
        />
        <Metric
          icon={Monitor}
          label="已连接大屏"
          value="--"
          sub="由设备服务实时上报"
          tone="rose"
        />
      </section>
      <section className="overview-grid">
        <article className="live-activity-panel">
          <div className="panel-top">
            <div>
              <p className="eyebrow">LIVE ACTIVITY</p>
              <h2>{activity?.name || "尚未创建活动"}</h2>
            </div>
            <span
              className={`stage-tag stage-tag--${String(control?.stage || "lobby").toLowerCase()}`}
            >
              {stageLabel(control?.stage || "LOBBY")}
            </span>
          </div>
          <div className="activity-hero">
            <div className="activity-hero__text">
              <span>
                <i />
                上海 · 主会场
              </span>
              <strong>
                {control?.stage === "QUESTION_OPEN"
                  ? "问题已向所有终端开放"
                  : "现场即将开始"}
              </strong>
              <small>
                {control?.seconds
                  ? `现场倒计时 ${formatSeconds(control.seconds)}`
                  : "工作人员可从实时控场推进流程"}
              </small>
            </div>
            <div className="activity-hero__shape" />
          </div>
          <div className="panel-bottom">
            <span>
              <Radio size={15} />
              WebSocket 事件按活动隔离
            </span>
            <Link to={`/screen/${activityId}`} target="_blank">
              打开大屏
              <ArrowRight size={15} />
            </Link>
          </div>
        </article>
        <article className="scoreboard-panel">
          <div className="panel-top">
            <div>
              <p className="eyebrow">SCOREBOARD</p>
              <h2>实时积分榜</h2>
            </div>
            <Link to="/app/participants">
              全部
              <ChevronRight size={16} />
            </Link>
          </div>
          <ScoreList items={scores.slice(0, 5)} />
        </article>
      </section>
    </div>
  );
}

function ActivitiesPage({ activities, reload, user, setActivityId }) {
  const emptyActivity = () => ({
    name: "",
    city: "",
    startsAt: "",
    endsAt: "",
    description: "",
    clientDisplayName: "",
    clientThemeColor: "#168F7C",
    clientHeroImageUrl: "",
    clientBackgroundImageUrl: "",
    parentActivityId: "",
    activityType: "EVENT",
  });
  const [dialog, setDialog] = useState(null);
  const [form, setForm] = useState(emptyActivity);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const openCreate = () => {
    setForm(emptyActivity());
    setError("");
    setDialog("create");
  };
  const openEdit = (activity) => {
    setForm({
      ...emptyActivity(),
      ...activity,
      startsAt: toDateTimeInput(activity.startsAt),
      endsAt: toDateTimeInput(activity.endsAt),
    });
    setError("");
    setDialog(activity);
  };
  const payload = () => ({
    ...form,
    startsAt: form.startsAt ? new Date(form.startsAt).toISOString() : null,
    endsAt: form.endsAt ? new Date(form.endsAt).toISOString() : null,
    description: form.description,
    clientDisplayName: form.clientDisplayName,
    clientThemeColor: form.clientThemeColor,
    clientHeroImageUrl: form.clientHeroImageUrl,
    clientBackgroundImageUrl: form.clientBackgroundImageUrl,
    parentActivityId: form.parentActivityId || null,
    activityType: form.activityType || "EVENT",
  });
  const submit = async (event) => {
    event.preventDefault();
    setBusy("activity");
    setError("");
    try {
      const saved =
        dialog === "create"
          ? await api.createActivity(payload())
          : await api.updateActivity(dialog.id, payload());
      await reload();
      setActivityId(saved.id);
      setDialog(null);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const terminate = async (activity) => {
    if (
      !window.confirm(
        `确定终止“${activity.name}”吗？终止后参与者不能再报名或答题，历史数据将保留。`,
      )
    )
      return;
    setBusy(`terminate-${activity.id}`);
    setError("");
    try {
      await api.terminateActivity(activity.id);
      await reload();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="ACTIVITY DIRECTORY"
        title="活动管理"
        description="完整维护活动时间、说明与参与端品牌；创建者会自动成为该活动管理员。"
        action={
          <button className="primary-button" type="button" onClick={openCreate}>
            <Plus size={17} />
            新建活动
          </button>
        }
      />
      <InlineError text={error} onRetry={reload} />
      <section className="activity-list">
        {activities.map((activity) => (
          <article
            key={activity.id}
            className="activity-list-row activity-list-row--detailed"
          >
            <div className="activity-list-icon">
              <CalendarDays size={21} />
            </div>
            <div>
              <strong>{activity.name}</strong>
              <span>
                <MapPin size={13} />
                {activity.city} · {formatDate(activity.startsAt)}
                {activity.endsAt ? ` 至 ${formatDate(activity.endsAt)}` : ""}
              </span>
              <small>
                {activity.parentActivityId
                  ? `${activities.find((item) => item.id === activity.parentActivityId)?.name || "父活动"} · `
                  : "顶层活动 · "}
                {activityTypeLabel(activity.activityType)} · {activity.description || "尚未填写活动说明"} · 管理员：
                {"创建者与活动成员"}
              </small>
            </div>
            <span
              className={`status-pill status-pill--${activity.status?.toLowerCase()}`}
            >
              {activity.status === "LIVE"
                ? "进行中"
                : activity.status === "FINISHED"
                  ? "已结束"
                  : activity.status === "CANCELLED"
                    ? "已终止"
                    : "草稿"}
            </span>
            <div className="activity-row-actions">
              <button
                className="toolbar-icon"
                type="button"
                title="编辑活动"
                onClick={() => openEdit(activity)}
              >
                <Pencil size={16} />
              </button>
              {activity.status !== "CANCELLED" && (
                <button
                  className="text-button"
                  type="button"
                  disabled={busy === `terminate-${activity.id}`}
                  onClick={() => terminate(activity)}
                >
                  {busy === `terminate-${activity.id}` ? "处理中" : "终止"}
                </button>
              )}
              <Link
                to="/app/overview"
                className="row-action"
                onClick={() => setActivityId(activity.id)}
              >
                进入
                <ChevronRight size={16} />
              </Link>
            </div>
          </article>
        ))}
      </section>
      {dialog && (
        <Dialog
          title={
            dialog === "create" ? "创建活动项目" : `编辑活动 · ${dialog.name}`
          }
          onClose={() => setDialog(null)}
        >
          <form className="dialog-form" onSubmit={submit}>
            <label>
              活动名称
              <input
                value={form.name}
                required
                onChange={(event) =>
                  setForm({ ...form, name: event.target.value })
                }
                placeholder="例如：2025 品牌知识挑战赛"
              />
            </label>
            <label>
              活动城市
              <input
                value={form.city}
                required
                onChange={(event) =>
                  setForm({ ...form, city: event.target.value })
                }
                placeholder="例如：上海"
              />
            </label>
            <div className="form-grid">
              <label>
                活动类型
                <select
                  value={form.activityType || "EVENT"}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      activityType: event.target.value,
                      parentActivityId:
                        event.target.value === "EVENT" ? "" : form.parentActivityId,
                    })
                  }
                >
                  <option value="EVENT">顶层活动</option>
                  <option value="QUIZ">答题活动</option>
                  <option value="LOTTERY">抽奖活动</option>
                  <option value="OTHER">其他活动</option>
                </select>
              </label>
              {form.activityType && form.activityType !== "EVENT" && (
                <label>
                  所属父活动
                  <select
                    required
                    value={form.parentActivityId || ""}
                    onChange={(event) =>
                      setForm({ ...form, parentActivityId: event.target.value })
                    }
                  >
                    <option value="">选择父活动</option>
                    {activities
                      .filter((item) => !item.parentActivityId)
                      .map((item) => (
                        <option key={item.id} value={item.id}>
                          {item.city} · {item.name}
                        </option>
                      ))}
                  </select>
                </label>
              )}
            </div>
            <div className="form-grid">
              <label>
                开始时间
                <input
                  type="datetime-local"
                  value={form.startsAt}
                  onChange={(event) =>
                    setForm({ ...form, startsAt: event.target.value })
                  }
                />
              </label>
              <label>
                结束时间
                <input
                  type="datetime-local"
                  value={form.endsAt}
                  onChange={(event) =>
                    setForm({ ...form, endsAt: event.target.value })
                  }
                />
              </label>
            </div>
            <label>
              活动简介
              <textarea
                maxLength="5000"
                value={form.description}
                onChange={(event) =>
                  setForm({ ...form, description: event.target.value })
                }
                placeholder="说明活动目标、议程或参与须知"
              />
            </label>
            <fieldset className="brand-fieldset">
              <legend>参与端品牌</legend>
              <label>
                显示名称
                <input
                  value={form.clientDisplayName}
                  onChange={(event) =>
                    setForm({ ...form, clientDisplayName: event.target.value })
                  }
                  placeholder="默认使用活动名称"
                />
              </label>
              <label>
                主题色
                <input
                  type="color"
                  value={form.clientThemeColor || "#168F7C"}
                  onChange={(event) =>
                    setForm({ ...form, clientThemeColor: event.target.value })
                  }
                />
              </label>
              <label>
                头图地址
                <input
                  type="url"
                  value={form.clientHeroImageUrl}
                  onChange={(event) =>
                    setForm({ ...form, clientHeroImageUrl: event.target.value })
                  }
                  placeholder="https://..."
                />
              </label>
              <label>
                背景图地址
                <input
                  type="url"
                  value={form.clientBackgroundImageUrl}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      clientBackgroundImageUrl: event.target.value,
                    })
                  }
                  placeholder="https://..."
                />
              </label>
            </fieldset>
            <div className="member-readonly">
              <ShieldCheck size={16} />
              <span>
                活动管理员：
                {dialog === "create"
                  ? `${user?.displayName || user?.username || "当前登录账号"}（创建后自动加入）`
                  : "可在“站点与权限”查看和维护成员。"}
              </span>
            </div>
            {error && (
              <p className="form-error">
                <CircleAlert size={16} />
                {error}
              </p>
            )}
            <button className="primary-button" disabled={busy === "activity"}>
              {busy === "activity"
                ? "正在保存"
                : dialog === "create"
                  ? "创建并进入配置"
                  : "保存活动"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}

function ControlPage({ activityId }) {
  const [questions, setQuestions] = useState([]);
  const [state, setState] = useState(null);
  const [responseStats, setResponseStats] = useState(null);
  const [selectedQuestionId, setSelectedQuestionId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const loadStats = useCallback(
    async (questionId) => {
      if (!activityId || !questionId) {
        setResponseStats(null);
        return;
      }
      try {
        setResponseStats(await api.questionStats(activityId, questionId));
      } catch (cause) {
        setError(cause.message);
      }
    },
    [activityId],
  );
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [allQuestions, control] = await Promise.all([
        api.questionsControl(activityId),
        api.controlState(activityId),
      ]);
      setQuestions(allQuestions);
      setState(control);
      const nextQuestionId = control.questionId || allQuestions[0]?.id || "";
      setSelectedQuestionId((current) =>
        control.questionId ||
        (allQuestions.some((item) => item.id === current)
          ? current
          : nextQuestionId),
      );
      await loadStats(nextQuestionId);
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId, loadStats]);
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, load);
  const controlledQuestionId = ["QUESTION_OPEN", "ANSWER_REVEALED"].includes(
    state?.stage,
  )
    ? state?.questionId
    : selectedQuestionId;
  const current =
    questions.find((question) => question.id === controlledQuestionId) ||
    questions[0];
  const enabledQuestions = questions
    .filter((item) => item.enabled)
    .sort((left, right) => (left.displayOrder ?? 0) - (right.displayOrder ?? 0));
  const currentIndex = enabledQuestions.findIndex((item) => item.id === current?.id);
  const nextQuestion = currentIndex >= 0 ? enabledQuestions[currentIndex + 1] : enabledQuestions[0];
  const selectQuestion = (questionId) => {
    setSelectedQuestionId(questionId);
    loadStats(questionId);
  };
  const update = async (stage, overrides = {}) => {
    if (["QUESTION_OPEN", "ANSWER_REVEALED"].includes(stage) && !current)
      return;
    setBusy(true);
    try {
      const questionId =
        overrides.questionId ??
        (["QUESTION_OPEN", "ANSWER_REVEALED"].includes(stage)
          ? current?.id
          : state?.questionId || null);
      const next = await api.control(activityId, {
        stage,
        questionId,
        seconds:
          overrides.seconds ??
          (stage === "QUESTION_OPEN" ? 30 : state?.seconds || 0),
      });
      setState(next);
      await loadStats(questionId);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="LIVE CONSOLE"
        title="实时控场"
        description="选择题目、设定倒计时，并将流程同步给参与者和受控大屏。"
      />
      <InlineError text={error} onRetry={load} />
      <div className="control-layout-new">
        <section>
          <div className="control-connection">
            <span>
              <i />
              LIVE
            </span>
            <b>{stageLabel(state?.stage || "LOBBY")}</b>
            <span>活动事件已隔离广播</span>
          </div>
          <article className="control-question">
            <div className="question-top">
              <span>待发布题目</span>
              <select
                aria-label="选择控场题目"
                value={current?.id || ""}
                onChange={(event) => selectQuestion(event.target.value)}
              >
                <option value="">请选择题目</option>
                {enabledQuestions
                  .map((item, index) => (
                    <option key={item.id} value={item.id}>
                      第 {index + 1} 题 · {item.title}
                    </option>
                  ))}
              </select>
              <Link className="text-button" to="/app/questions">
                <Pencil size={15} />
                题库
              </Link>
            </div>
            <h2>{current?.title || "请先在题库中创建并启用题目"}</h2>
            <ScreenMedia
              src={current?.mediaUrl}
              className="control-question-media"
            />
            <div className="control-option-grid">
              {asOptions(current).map((option, index) => (
                <div key={`${option}-${index}`}>
                  <b>{String.fromCharCode(65 + index)}</b>
                  <span>{option}</span>
                </div>
              ))}
              {current?.type === "TEXT" && (
                <div className="control-text-note">
                  <strong>{textMatchLabel(current.textMatchMode)}</strong>
                  {textAcceptedAnswers(current).length ? (
                    <span>
                      正确答案：{textAcceptedAnswers(current).join("、")}
                    </span>
                  ) : (
                    <span>未配置标准答案，回答将进入人工评分。</span>
                  )}
                </div>
              )}
            </div>
            <div className="control-question-footer">
              <span>
                <Clock3 size={16} />
                {state?.seconds ? formatSeconds(state.seconds) : "未开始计时"}
              </span>
              <span>
                <Users size={16} />
                {responseStats
                  ? `已答 ${responseStats.submittedCount}/${responseStats.eligibleParticipantCount} 人`
                  : "等待答题数据"}
              </span>
            </div>
            <section className="control-response-overview" aria-live="polite">
              <div className="control-response-metrics">
                <span>
                  <b>{responseStats?.unansweredCount || 0}</b>
                  待答
                </span>
                <span className="is-review">
                  <b>{responseStats?.pendingReviewCount || 0}</b>
                  待评分
                </span>
                <span className="is-correct">
                  <b>{responseStats?.correctCount || 0}</b>
                  正确
                </span>
                <span className="is-partial">
                  <b>{responseStats?.partialCount || 0}</b>
                  部分正确
                </span>
                <span className="is-incorrect">
                  <b>{responseStats?.incorrectCount || 0}</b>
                  不正确
                </span>
              </div>
              <div className="control-response-feed">
                <span>最近提交</span>
                {responseStats?.submissions?.map((submission) => (
                  <div key={submission.participantId}>
                    <strong>{submission.participantName}</strong>
                    <small>
                      回答：{submission.answers?.join("、") || "未填写"} · {submission.awardedPoints || 0} 分
                      <br />
                      第 {submission.responseRank} 位 · {submissionStatusLabel(submission.status)} · {formatDate(submission.submittedAt)}
                    </small>
                  </div>
                ))}
                {!responseStats?.submissions?.length && <small>尚无人提交本题</small>}
              </div>
            </section>
          </article>
          <div className="control-actions">
            <button
              className="secondary-button"
              disabled={busy || !current}
              onClick={() => update("QUESTION_OPEN", { seconds: 30 })}
            >
              <Play size={17} />
              开题并计时
            </button>
            <button
              className="secondary-button"
              disabled={busy || !nextQuestion}
              onClick={() => {
                if (!nextQuestion) return;
                setSelectedQuestionId(nextQuestion.id);
                update("QUESTION_OPEN", { questionId: nextQuestion.id, seconds: 30 });
              }}
            >
              <ArrowRight size={17} />
              下一题并开始
            </button>
            <button
              className="accent-button"
              disabled={busy || !state?.questionId}
              onClick={() => update("ANSWER_REVEALED", { seconds: 0 })}
            >
              <BadgeCheck size={17} />
              公布答案
            </button>
            <button
              className="primary-button"
              disabled={busy}
              onClick={() => update("SCOREBOARD", { seconds: 0 })}
            >
              发布积分榜
              <ArrowRight size={17} />
            </button>
            <button
              className="secondary-button"
              disabled={busy}
              onClick={() => update("WINNERS", { seconds: 0 })}
            >
              <Trophy size={17} />
              确认获奖并上屏
            </button>
          </div>
        </section>
        <aside className="control-aside">
          <ControlTimer
            state={state}
            onRestart={() => update("QUESTION_OPEN", { seconds: 30 })}
          />
          <article className="control-guide">
            <p className="eyebrow">RUN OF SHOW</p>
            <h3>现场流程</h3>
            <ol>
              <li
                className={state?.stage === "QUESTION_OPEN" ? "is-current" : ""}
              >
                选择题目、开放答题与计时
              </li>
              <li
                className={
                  state?.stage === "ANSWER_REVEALED" ? "is-current" : ""
                }
              >
                公布本题结果
              </li>
              <li className={state?.stage === "SCOREBOARD" ? "is-current" : ""}>
                展示积分排行
              </li>
              <li className={state?.stage === "WINNERS" ? "is-current" : ""}>
                确认获奖并生成核销任务
              </li>
            </ol>
          </article>
        </aside>
      </div>
    </div>
  );
}

function QuestionsPage({ activityId }) {
  const emptyQuestion = (questionType = "SINGLE") => ({
    type: questionType,
    title: "",
    options: "选项 A\n选项 B\n选项 C\n选项 D",
    answers: "B",
    fullScore: 100,
    partialCreditPercent: 40,
    textAcceptedAnswers: "",
    textMatchMode: "FUZZY",
    mediaUrl: "",
    enabled: true,
  });
  const [questions, setQuestions] = useState([]);
  const [questionSets, setQuestionSets] = useState([]);
  const [participants, setParticipants] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [dialog, setDialog] = useState(null);
  const [questionSetDialog, setQuestionSetDialog] = useState(null);
  const [questionSetForm, setQuestionSetForm] = useState({ name: "", description: "", questionIds: [], active: false });
  const [form, setForm] = useState(emptyQuestion());
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [questionList, setList, people] = await Promise.all([
        api.questionsAdmin(activityId),
        api.questionSets(activityId),
        api.participants(activityId),
      ]);
      setQuestions(questionList);
      setQuestionSets(setList);
      setParticipants(people);
      const allSubmissions = await Promise.all(
        people.map((person) => api.submissions(activityId, person.id)),
      );
      setSubmissions(allSubmissions.flat());
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId]);
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, load);
  const openCreate = () => {
    setError("");
    setForm(emptyQuestion());
    setDialog("create");
  };
  const openEdit = (question) => {
    setError("");
    setForm({
      ...emptyQuestion(question.type),
      ...question,
      options: asOptions(question).join("\n"),
      answers: answerSet(question).size
        ? [...answerSet(question)].join(", ")
        : "",
      textAcceptedAnswers: textAcceptedAnswers(question).join("\n"),
      textMatchMode: question.textMatchMode || "MANUAL",
    });
    setDialog(question);
  };
  const openSetCreate = () => {
    setError("");
    setQuestionSetForm({ name: "", description: "", questionIds: [], active: false });
    setQuestionSetDialog("create");
  };
  const openSetEdit = (set) => {
    setError("");
    setQuestionSetForm({
      name: set.name,
      description: set.description || "",
      questionIds: (set.items || []).map((item) => item.questionId),
      active: Boolean(set.active),
    });
    setQuestionSetDialog(set);
  };
  const toggleSetQuestion = (questionId) => {
    setQuestionSetForm((current) => ({
      ...current,
      questionIds: current.questionIds.includes(questionId)
        ? current.questionIds.filter((id) => id !== questionId)
        : [...current.questionIds, questionId],
    }));
  };
  const moveSetQuestion = (index, direction) => {
    setQuestionSetForm((current) => {
      const next = [...current.questionIds];
      const target = index + direction;
      if (target < 0 || target >= next.length) return current;
      [next[index], next[target]] = [next[target], next[index]];
      return { ...current, questionIds: next };
    });
  };
  const saveSet = async (event) => {
    event.preventDefault();
    if (!questionSetForm.questionIds.length) {
      setError("题组至少需要选择一道题目");
      return;
    }
    setBusy("question-set");
    setError("");
    try {
      const payload = {
        name: questionSetForm.name,
        description: questionSetForm.description,
        questionIds: questionSetForm.questionIds,
        active: Boolean(questionSetForm.active),
      };
      if (questionSetDialog === "create") await api.createQuestionSet(activityId, payload);
      else await api.updateQuestionSet(activityId, questionSetDialog.id, payload);
      setQuestionSetDialog(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const activateSet = async (set) => {
    setBusy(`activate-set-${set.id}`);
    setError("");
    try {
      await api.activateQuestionSet(activityId, set.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const deleteSet = async (set) => {
    if (!window.confirm(`确认删除题目组“${set.name}”吗？`)) return;
    setBusy(`delete-set-${set.id}`);
    setError("");
    try {
      await api.deleteQuestionSet(activityId, set.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const updateType = (type) =>
    setForm((current) => ({
      ...current,
      type,
      options:
        type === "TEXT"
          ? ""
          : current.options || "选项 A\n选项 B\n选项 C\n选项 D",
      answers: type === "TEXT" ? "" : current.answers || "B",
      textMatchMode:
        type === "TEXT" ? current.textMatchMode || "FUZZY" : "FUZZY",
    }));
  const uploadMedia = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy("question-media");
    setError("");
    try {
      const uploaded = await api.uploadMedia(activityId, file, "questions");
      setForm((current) => ({ ...current, mediaUrl: uploaded.url }));
    } catch (cause) {
      setError(cause.message);
    } finally {
      event.target.value = "";
      setBusy("");
    }
  };
  const submit = async (event) => {
    event.preventDefault();
    setBusy("question-form");
    setError("");
    const options =
      form.type === "TEXT"
        ? []
        : form.options
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean);
    const answers =
      form.type === "TEXT"
        ? []
        : form.answers
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean)
            .map((answer) => {
              const index =
                answer.length === 1
                  ? answer.toUpperCase().charCodeAt(0) - 65
                  : -1;
              return index >= 0 && index < options.length
                ? options[index]
                : answer;
            });
    const textAcceptedAnswers =
      form.type === "TEXT"
        ? form.textAcceptedAnswers
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean)
        : [];
    const payload = {
      ...form,
      options,
      answers,
      fullScore: Number(form.fullScore),
      partialCreditPercent: Number(form.partialCreditPercent),
      textAcceptedAnswers,
      textMatchMode: form.type === "TEXT" ? form.textMatchMode : null,
      // Empty string is the explicit "remove media" value; null remains the
      // server-side convention for a partial update that leaves it unchanged.
      mediaUrl: form.mediaUrl || "",
    };
    try {
      if (dialog === "create") await api.createQuestion(activityId, payload);
      else await api.updateQuestion(activityId, dialog.id, payload);
      setDialog(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const deleteQuestion = async (question) => {
    if (
      !window.confirm(
        `确认删除题目“${question.title}”吗？已有答题记录的题目不能删除。`,
      )
    )
      return;
    setBusy(`delete-${question.id}`);
    setError("");
    try {
      await api.deleteQuestion(activityId, question.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const grade = async (event) => {
    event.preventDefault();
    const gradeForm = new FormData(event.currentTarget);
    const award = Number(gradeForm.get("points"));
    const feedback = String(gradeForm.get("feedback") || "");
    setBusy(`grade-${dialog.id}`);
    setError("");
    try {
      await api.gradeSubmission(activityId, dialog.id, {
        awardedPoints: award,
        feedback,
      });
      setDialog(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const filtered = questions.filter((question) =>
    `${question.title} ${question.type}`
      .toLowerCase()
      .includes(query.trim().toLowerCase()),
  );
  const pendingText = submissions.filter(
    (submission) =>
      questions.find((question) => question.id === submission.questionId)
        ?.type === "TEXT",
  );
  const personById = new Map(participants.map((person) => [person.id, person]));
  const questionById = new Map(
    questions.map((question) => [question.id, question]),
  );
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="QUESTION LIBRARY"
        title="题库与组卷"
        description="维护题型、媒体和计分规则；文本题可自动匹配，未命中时进入人工评分队列。"
        action={
          <button className="primary-button" onClick={openCreate}>
            <FilePlus2 size={17} />
            新建题目
          </button>
        }
      />
      <InlineError text={error} onRetry={load} />
      <section className="data-panel">
        <div className="data-panel__toolbar">
          <div className="search-input">
            <Search size={17} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索题目或题型"
            />
          </div>
          <span>{questions.length} 道题目</span>
        </div>
        <div className="question-rows">
          {filtered.map((question, index) => (
            <article className="question-row" key={question.id}>
              <span className="question-sequence">
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <strong>{question.title}</strong>
                <small>
                  {typeLabel(question.type)} · {question.fullScore || 100} 分 ·{" "}
                  {question.type === "TEXT"
                    ? `${textMatchLabel(question.textMatchMode)} · ${textAcceptedAnswers(question).length} 项标准答案`
                    : `${asOptions(question).length} 个选项`}
                  {question.mediaUrl ? " · 含媒体" : ""}
                </small>
              </div>
              <span className="type-chip">
                {question.enabled ? "已启用" : "已停用"}
              </span>
              <div className="row-icon-actions">
                <button
                  className="toolbar-icon"
                  type="button"
                  title="编辑题目"
                  onClick={() => openEdit(question)}
                >
                  <Pencil size={16} />
                </button>
                <button
                  className="toolbar-icon"
                  type="button"
                  title="删除题目"
                  disabled={busy === `delete-${question.id}`}
                  onClick={() => deleteQuestion(question)}
                >
                  <X size={16} />
                </button>
              </div>
            </article>
          ))}
          {!filtered.length && (
            <EmptyState
              icon={CircleHelp}
              title={questions.length ? "没有匹配的题目" : "尚未创建题目"}
              description="新建题目后即可在实时控场中发布。"
            />
          )}
        </div>
      </section>
      <section className="data-panel question-set-panel">
        <div className="data-panel__toolbar">
          <div>
            <p className="eyebrow">QUESTION SETS</p>
            <h2>组卷与下发</h2>
          </div>
          <button className="secondary-button" type="button" onClick={openSetCreate}>
            <Plus size={16} />
            新建题组
          </button>
        </div>
        <div className="question-set-rows">
          {questionSets.map((set) => (
            <article className="question-set-row" key={set.id}>
              <div className="question-set-row__mark">
                <ClipboardList size={17} />
              </div>
              <div>
                <strong>{set.name}</strong>
                <small>
                  {set.items?.length || 0} 道题目{set.description ? ` · ${set.description}` : ""}
                </small>
              </div>
              <span className={set.active ? "online-status" : "offline-status"}>
                <i />
                {set.active ? "当前下发" : "未下发"}
              </span>
              <button className="toolbar-icon" type="button" title="编辑题组" onClick={() => openSetEdit(set)}>
                <Pencil size={16} />
              </button>
              {!set.active && (
                <button
                  className="secondary-button"
                  type="button"
                  disabled={busy === `activate-set-${set.id}`}
                  onClick={() => activateSet(set)}
                >
                  下发
                </button>
              )}
              <button
                className="toolbar-icon"
                type="button"
                title="删除题组"
                disabled={busy === `delete-set-${set.id}`}
                onClick={() => deleteSet(set)}
              >
                <X size={16} />
              </button>
            </article>
          ))}
          {!questionSets.length && (
            <EmptyState
              icon={ClipboardList}
              title="尚未创建题组"
              description="选择题库中的题目组成答题活动，并通过下发按钮让参与端使用该题组。"
            />
          )}
        </div>
      </section>
      <section className="data-panel review-panel">
        <div className="data-panel__toolbar">
          <div>
            <p className="eyebrow">TEXT REVIEW</p>
            <h2>文本题人工评分</h2>
          </div>
          <span>
            {
              pendingText.filter((item) => item.status === "PENDING_REVIEW")
                .length
            }{" "}
            份待处理
          </span>
        </div>
        <div className="submission-rows">
          {pendingText.map((submission) => {
            const person = personById.get(submission.participantId);
            const question = questionById.get(submission.questionId);
            const pending = submission.status === "PENDING_REVIEW";
            return (
              <article key={submission.id}>
                <div>
                  <strong>{person?.name || "参与者"}</strong>
                  <small>
                    {question?.title || "文本题"} ·{" "}
                    {formatDate(submission.submittedAt)}
                  </small>
                </div>
                <p>{submission.answers?.join("、") || "未填写内容"}</p>
                <span
                  className={`award-status award-status--${pending ? "pending" : "redeemed"}`}
                >
                  {pending ? "待评分" : `${submission.awardedPoints} 分`}
                </span>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => setDialog(submission)}
                >
                  {pending ? "评分" : "查看评分"}
                </button>
              </article>
            );
          })}
          {!pendingText.length && (
            <EmptyState
              icon={ClipboardList}
              title="暂无文本回答"
              description="参与者提交文本题后会显示在这里，评分结果将实时反馈给本人。"
            />
          )}
        </div>
      </section>
      {dialog && (dialog === "create" || dialog.type) && (
        <Dialog
          title={
            dialog === "create"
              ? "新建活动题目"
              : `编辑题目 · ${typeLabel(form.type)}`
          }
          onClose={() => setDialog(null)}
        >
          <form className="dialog-form" onSubmit={submit}>
            <fieldset className="segmented">
              <legend>题目类型</legend>
              {[
                ["SINGLE", "单选"],
                ["MULTIPLE", "多选"],
                ["TEXT", "文本"],
              ].map(([value, label]) => (
                <button
                  type="button"
                  className={form.type === value ? "is-selected" : ""}
                  key={value}
                  onClick={() => updateType(value)}
                >
                  {label}
                </button>
              ))}
            </fieldset>
            <label>
              题干
              <textarea
                required
                value={form.title}
                onChange={(event) =>
                  setForm({ ...form, title: event.target.value })
                }
                placeholder="输入题目内容"
              />
            </label>
            {form.type !== "TEXT" && (
              <>
                <label>
                  选项（每行一项）
                  <textarea
                    required
                    value={form.options}
                    onChange={(event) =>
                      setForm({ ...form, options: event.target.value })
                    }
                  />
                </label>
                <label>
                  正确答案{form.type === "MULTIPLE" && "（逗号分隔）"}
                  <input
                    required
                    value={form.answers}
                    onChange={(event) =>
                      setForm({ ...form, answers: event.target.value })
                    }
                  />
                </label>
              </>
            )}
            {form.type === "TEXT" && (
              <>
                <fieldset className="segmented text-match-mode">
                  <legend>给分方式</legend>
                  {[
                    ["FUZZY", "模糊匹配"],
                    ["REGEX", "正则匹配"],
                    ["MANUAL", "人工评分"],
                  ].map(([value, label]) => (
                    <button
                      type="button"
                      className={
                        form.textMatchMode === value ? "is-selected" : ""
                      }
                      key={value}
                      onClick={() =>
                        setForm({ ...form, textMatchMode: value })
                      }
                    >
                      {label}
                    </button>
                  ))}
                </fieldset>
                <label>
                  正确答案选项（每行一项）
                  <textarea
                    required={form.textMatchMode !== "MANUAL"}
                    value={form.textAcceptedAnswers}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        textAcceptedAnswers: event.target.value,
                      })
                    }
                    placeholder={
                      form.textMatchMode === "REGEX"
                        ? "例如：^(北京|beijing)$"
                        : "输入可接受的正确回答"
                    }
                  />
                </label>
              </>
            )}
            <div className="form-grid">
              <label>
                满分
                <input
                  required
                  type="number"
                  min="1"
                  value={form.fullScore}
                  onChange={(event) =>
                    setForm({ ...form, fullScore: Number(event.target.value) })
                  }
                />
              </label>
              {form.type === "MULTIPLE" && (
                <label>
                  部分得分 %
                  <input
                    required
                    type="number"
                    min="0"
                    max="100"
                    value={form.partialCreditPercent}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        partialCreditPercent: Number(event.target.value),
                      })
                    }
                  />
                </label>
              )}
            </div>
            <label>
              媒体地址（图片、音频或视频）
              <input
                type="url"
                value={form.mediaUrl || ""}
                onChange={(event) =>
                  setForm({ ...form, mediaUrl: event.target.value })
                }
                placeholder="https://..."
              />
            </label>
            <div className="media-upload-control">
              <label className="secondary-button">
                <FilePlus2 size={16} />
                {busy === "question-media" ? "正在上传" : "上传媒体"}
                <input
                  type="file"
                  accept="image/*,audio/*,video/*"
                  disabled={busy === "question-media"}
                  onChange={uploadMedia}
                />
              </label>
              {form.mediaUrl && (
                <button
                  className="toolbar-icon"
                  type="button"
                  title="移除题目媒体"
                  onClick={() => setForm({ ...form, mediaUrl: "" })}
                >
                  <X size={16} />
                </button>
              )}
              <span>{form.mediaUrl ? "媒体已关联到本题" : "支持图片、音频和视频"}</span>
            </div>
            <ScreenMedia src={form.mediaUrl} className="question-editor-media" />
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={Boolean(form.enabled)}
                onChange={(event) =>
                  setForm({ ...form, enabled: event.target.checked })
                }
              />
              允许在控场中发布
            </label>
            <button
              className="primary-button"
              disabled={busy === "question-form"}
            >
              {busy === "question-form" ? "正在保存" : "保存题目"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {questionSetDialog && (
        <Dialog
          title={questionSetDialog === "create" ? "新建题组" : "编辑题组"}
          onClose={() => setQuestionSetDialog(null)}
        >
          <form className="dialog-form" onSubmit={saveSet}>
            <label>
              题组名称
              <input
                required
                maxLength="180"
                value={questionSetForm.name}
                onChange={(event) => setQuestionSetForm({ ...questionSetForm, name: event.target.value })}
                placeholder="例如：决赛第一轮"
              />
            </label>
            <label>
              说明
              <input
                maxLength="1000"
                value={questionSetForm.description}
                onChange={(event) => setQuestionSetForm({ ...questionSetForm, description: event.target.value })}
                placeholder="用于现场工作人员识别题组"
              />
            </label>
            <fieldset className="question-set-picker">
              <legend>题目顺序</legend>
              <div className="question-set-picker__selected">
                {questionSetForm.questionIds.map((questionId, index) => {
                  const question = questions.find((item) => item.id === questionId);
                  return (
                    <div key={questionId}>
                      <span>{String(index + 1).padStart(2, "0")}</span>
                      <strong>{question?.title || "题目已删除"}</strong>
                      <button
                        className="toolbar-icon"
                        type="button"
                        title="上移"
                        disabled={index === 0}
                        onClick={() => moveSetQuestion(index, -1)}
                      >
                        <ArrowUp size={15} />
                      </button>
                      <button
                        className="toolbar-icon"
                        type="button"
                        title="下移"
                        disabled={index === questionSetForm.questionIds.length - 1}
                        onClick={() => moveSetQuestion(index, 1)}
                      >
                        <ArrowDown size={15} />
                      </button>
                    </div>
                  );
                })}
                {!questionSetForm.questionIds.length && <small>请从下方选择题目</small>}
              </div>
              <div className="question-set-picker__options">
                {questions.map((question) => (
                  <label key={question.id}>
                    <input
                      type="checkbox"
                      checked={questionSetForm.questionIds.includes(question.id)}
                      onChange={() => toggleSetQuestion(question.id)}
                    />
                    <span>{question.title}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={Boolean(questionSetForm.active)}
                onChange={(event) => setQuestionSetForm({ ...questionSetForm, active: event.target.checked })}
              />
              保存后立即下发到当前活动
            </label>
            <button className="primary-button" disabled={busy === "question-set"}>
              {busy === "question-set" ? "正在保存" : "保存题组"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {dialog && dialog.questionId && (
        <Dialog title="文本回答评分" onClose={() => setDialog(null)}>
          <form className="dialog-form" onSubmit={grade}>
            <div className="review-answer">
              <span>
                {personById.get(dialog.participantId)?.name || "参与者"} ·{" "}
                {questionById.get(dialog.questionId)?.title}
              </span>
              <p>{dialog.answers?.join("、") || "未填写内容"}</p>
            </div>
            {textAcceptedAnswers(questionById.get(dialog.questionId)).length >
              0 && (
              <div className="review-reference">
                <span>
                  正确答案选项 · {textMatchLabel(questionById.get(dialog.questionId)?.textMatchMode)}
                </span>
                <div>
                  {textAcceptedAnswers(questionById.get(dialog.questionId)).map(
                    (answer) => (
                      <code key={answer}>{answer}</code>
                    ),
                  )}
                </div>
              </div>
            )}
            <label>
              得分
              <input
                name="points"
                required
                type="number"
                min="0"
                max={questionById.get(dialog.questionId)?.fullScore || 100}
                defaultValue={dialog.awardedPoints || 0}
              />
            </label>
            <label>
              反馈给参与者
              <textarea
                name="feedback"
                maxLength="1000"
                defaultValue={dialog.feedback || ""}
                placeholder="例如：观点完整，继续保持。"
              />
            </label>
            <button
              className="primary-button"
              disabled={busy === `grade-${dialog.id}`}
            >
              {busy === `grade-${dialog.id}` ? "正在提交" : "保存评分并反馈"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}

function ParticipantsPage({ activityId }) {
  const [participants, setParticipants] = useState([]);
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState(null);
  const [venues, setVenues] = useState([]);
  const [form, setForm] = useState(null);
  const [scoreDelta, setScoreDelta] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      setParticipants(await api.participants(activityId));
      setError("");
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId]);
  const refreshParticipants = useCallback(
    (event) => {
      if (
        event.type?.startsWith("participant") ||
        event.type?.startsWith("answer") ||
        event.type?.startsWith("score") ||
        event.type?.startsWith("lottery") ||
        event.type?.startsWith("award")
      )
        load();
    },
    [load],
  );
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, refreshParticipants);
  const openDetail = async (person) => {
    setBusy(`detail-${person.id}`);
    setError("");
    try {
      const [nextDetail, venueList] = await Promise.all([
        api.participant(activityId, person.id),
        api.venues(activityId),
      ]);
      setDetail(nextDetail);
      setForm({ ...nextDetail, customFields: nextDetail.customFields || {} });
      setVenues(venueList);
      setScoreDelta("");
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const save = async (event) => {
    event.preventDefault();
    if (!detail || !form) return;
    setBusy("save-participant");
    setError("");
    try {
      await api.updateParticipant(activityId, detail.id, {
        name: form.name,
        contact: form.contact,
        organization: form.organization || null,
        venue: form.venue,
        status: form.status,
        customFields: form.customFields || {},
      });
      const points = Number(scoreDelta || 0);
      if (points)
        await api.adjustScore(activityId, {
          participantId: detail.id,
          points,
          note: "工作人员人工调整",
        });
      const nextDetail = await api.participant(activityId, detail.id);
      setDetail(nextDetail);
      setForm({ ...nextDetail, customFields: nextDetail.customFields || {} });
      setScoreDelta("");
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const normalizedQuery = query.trim().toLowerCase();
  const visible = participants.filter(
    (person) =>
      !normalizedQuery ||
      `${person.name} ${person.contact || ""} ${person.id}`
        .toLowerCase()
        .includes(normalizedQuery),
  );
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="PARTICIPANT DIRECTORY"
        title="参与者"
        description="搜索、编辑和核对活动内参与者的身份、积分、奖品与抽奖机会。"
        action={
          <button className="secondary-button" onClick={load}>
            <RefreshCw size={16} />
            刷新数据
          </button>
        }
      />
      <InlineError text={error} onRetry={load} />
      <section className="data-panel">
        <div className="data-panel__toolbar">
          <div className="search-input">
            <Search size={17} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索姓名、ID 或联系方式"
            />
          </div>
          <span>
            显示 {visible.length} / {participants.length} 人
          </span>
        </div>
        <div className="participant-table">
          <div className="table-header">
            <span>参与者</span>
            <span>会场</span>
            <span>积分</span>
            <span>登记时间</span>
            <span />
          </div>
          {visible.map((person) => (
            <div className="participant-row" key={person.id}>
              <div className="person-cell">
                <Avatar name={person.name} />
                <div>
                  <strong>{person.name}</strong>
                  <small>
                    {person.contact || person.organization || "个人参与者"} ·{" "}
                    {shortId(person.id)}
                  </small>
                </div>
              </div>
              <span>{person.venue}</span>
              <strong>
                {person.score || 0}
                <small> 分</small>
              </strong>
              <span>{formatDate(person.registeredAt)}</span>
              <button
                className="toolbar-icon"
                type="button"
                title="查看并编辑参与者"
                disabled={busy === `detail-${person.id}`}
                onClick={() => openDetail(person)}
              >
                <ChevronRight size={18} />
              </button>
            </div>
          ))}
          {!visible.length && (
            <EmptyState
              icon={Users}
              title={participants.length ? "没有匹配的参与者" : "暂无参与者"}
              description={
                participants.length
                  ? "可按姓名、完整或部分 ID、联系方式继续检索。"
                  : "用户通过活动入口登记后会实时显示在这里。"
              }
            />
          )}
        </div>
      </section>
      {detail && form && (
        <Dialog
          title={`参与者档案 · ${detail.name}`}
          onClose={() => setDetail(null)}
        >
          <form className="dialog-form" onSubmit={save}>
            <div className="participant-detail-summary">
              <Avatar name={detail.name} />
              <div>
                <strong>{detail.name}</strong>
                <span>
                  {shortId(detail.id)} · 登记于{" "}
                  {formatDate(detail.registeredAt)}
                </span>
              </div>
              <b>{detail.score} 分</b>
            </div>
            <div className="form-grid">
              <label>
                姓名
                <input
                  required
                  value={form.name || ""}
                  onChange={(event) =>
                    setForm({ ...form, name: event.target.value })
                  }
                />
              </label>
              <label>
                联系方式
                <input
                  required
                  value={form.contact || ""}
                  onChange={(event) =>
                    setForm({ ...form, contact: event.target.value })
                  }
                />
              </label>
            </div>
            <label>
              所属组织
              <input
                value={form.organization || ""}
                onChange={(event) =>
                  setForm({ ...form, organization: event.target.value })
                }
              />
            </label>
            <div className="form-grid">
              <label>
                会场
                <select
                  value={form.venue || ""}
                  onChange={(event) =>
                    setForm({ ...form, venue: event.target.value })
                  }
                >
                  {venues.map((venue) => (
                    <option key={venue.id} value={venue.code}>
                      {venue.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                状态
                <select
                  value={form.status || "ACTIVE"}
                  onChange={(event) =>
                    setForm({ ...form, status: event.target.value })
                  }
                >
                  <option value="ACTIVE">正常</option>
                  <option value="DISABLED">已停用</option>
                </select>
              </label>
            </div>
            <label>
              积分调整（可正可负）
              <input
                type="number"
                value={scoreDelta}
                onChange={(event) => setScoreDelta(event.target.value)}
                placeholder="例如：20 或 -10"
              />
            </label>
            <div className="participant-detail-grid">
              <div>
                <p className="eyebrow">REWARDS</p>
                <strong>已获奖品</strong>
                {detail.awards?.length ? (
                  detail.awards.map((award) => (
                    <span key={award.id}>
                      {award.prizeName} ·{" "}
                      {award.status === "REDEEMED"
                        ? "已核销"
                        : award.status === "VOID"
                          ? "已作废"
                          : "待核销"}
                    </span>
                  ))
                ) : (
                  <span>暂无奖品</span>
                )}
              </div>
              <div>
                <p className="eyebrow">LOTTERY</p>
                <strong>抽奖机会</strong>
                <b>{detail.lotteryChance?.remainingDraws || 0} 次剩余</b>
                <span>
                  累计发放 {detail.lotteryChance?.grantedDraws || 0} 次
                </span>
              </div>
            </div>
            {Object.entries(form.customFields || {}).length > 0 && (
              <div className="custom-fields-readonly">
                <p className="eyebrow">REGISTRATION FIELDS</p>
                {Object.entries(form.customFields).map(([key, value]) => (
                  <span key={key}>
                    <b>{key}</b>
                    {value}
                  </span>
                ))}
              </div>
            )}
            {error && (
              <p className="form-error">
                <CircleAlert size={16} />
                {error}
              </p>
            )}
            <button
              className="primary-button"
              disabled={busy === "save-participant"}
            >
              {busy === "save-participant" ? "正在保存" : "保存参与者信息"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}

function RewardsPage({ activityId }) {
  const emptyPool = () => ({
    code: "",
    name: "",
    purpose: "MANUAL",
    deliveryType: "DIGITAL",
    description: "",
    redemptionUrl: "",
    totalQuantity: 1,
    minScore: 0,
    drawWeight: 1,
    rankFrom: 1,
    rankTo: 3,
    enabled: true,
  });
  const [pools, setPools] = useState([]);
  const [participants, setParticipants] = useState([]);
  const [venues, setVenues] = useState([]);
  const [awards, setAwards] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [poolDialog, setPoolDialog] = useState(null);
  const [poolForm, setPoolForm] = useState(emptyPool);
  const [redemptionOpen, setRedemptionOpen] = useState(false);
  const [issueOpen, setIssueOpen] = useState(false);
  const [lotteryDialog, setLotteryDialog] = useState(null);
  const [lotteryVenue, setLotteryVenue] = useState("");
  const [busy, setBusy] = useState("");
  const [awardForm, setAwardForm] = useState({
    participantId: "",
    prizePoolId: "",
    fulfillmentNote: "",
  });
  const [chanceForm, setChanceForm] = useState({
    participantId: "",
    draws: 1,
    reason: "",
  });
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [nextPools, nextParticipants, nextAwards, nextVenues] = await Promise.all([
        api.prizePools(activityId),
        api.participants(activityId),
        api.awardsAdmin(activityId),
        api.venues(activityId),
      ]);
      setPools(nextPools);
      setParticipants(nextParticipants);
      setAwards(nextAwards);
      setVenues(nextVenues || []);
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId]);
  useEffect(() => {
    load();
  }, [load]);
  const openCreate = () => {
    setError("");
    setNotice("");
    setPoolForm(emptyPool());
    setPoolDialog("create");
  };
  const openEdit = (pool) => {
    setError("");
    setNotice("");
    setPoolForm({ ...emptyPool(), ...pool });
    setPoolDialog(pool);
  };
  const closePoolDialog = () => {
    setPoolDialog(null);
    setPoolForm(emptyPool());
  };
  const run = async (key, action, successMessage) => {
    setBusy(key);
    setError("");
    setNotice("");
    try {
      await action();
      await load();
      if (successMessage) setNotice(successMessage);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const submitPool = async (event) => {
    event.preventDefault();
    const isRanking = poolForm.purpose === "RANKING";
    const payload = {
      ...poolForm,
      totalQuantity: Number(poolForm.totalQuantity),
      minScore: Number(poolForm.minScore),
      drawWeight: Number(poolForm.drawWeight),
      rankFrom: isRanking ? Number(poolForm.rankFrom) : null,
      rankTo: isRanking ? Number(poolForm.rankTo) : null,
    };
    await run(
      "pool-form",
      async () => {
        if (poolDialog === "create")
          await api.createPrizePool(activityId, payload);
        else await api.updatePrizePool(activityId, poolDialog.id, payload);
        closePoolDialog();
      },
      poolDialog === "create"
        ? "奖池已创建，可继续配置发奖或抽奖。"
        : "奖池配置已保存。",
    );
  };
  const deletePool = (pool) => {
    if (
      !window.confirm(
        `确认删除奖池“${pool.name}”吗？未发奖的奖池可以安全删除。`,
      )
    )
      return;
    run(
      `delete-${pool.id}`,
      () => api.deletePrizePool(activityId, pool.id),
      "奖池已删除。",
    );
  };
  const issueRanking = (pool) =>
    run(
      `ranking-${pool.id}`,
      () => api.issueRankingAwards(activityId, pool.id),
      "已按当前积分排行生成待核销奖项。",
    );
  const submitIssue = async (event) => {
    event.preventDefault();
    await run(
      "issue-award",
      async () => {
        await api.issueAward(activityId, awardForm);
        setAwardForm({
          participantId: "",
          prizePoolId: "",
          fulfillmentNote: "",
        });
        setIssueOpen(false);
      },
      "奖品已发放，等待现场核销。",
    );
  };
  const redeemPending = (award) =>
    run(
      `redeem-${award.id}`,
      () => api.redeem(activityId, award.id),
      "奖品已核销。",
    );
  const reverseAward = (award) =>
    run(
      `reverse-${award.id}`,
      () => api.reverseRedemption(activityId, award.id),
      "核销已撤回，奖品重新进入待核销列表。",
    );
  const voidPending = (award) => {
    if (!window.confirm(`确认作废“${award.prizeName}”吗？库存将自动释放。`))
      return;
    run(
      `void-${award.id}`,
      () => api.voidAward(activityId, award.id, { note: "工作人员作废" }),
      "奖品已作废，库存已释放。",
    );
  };
  const redeemAll = () =>
    run(
      "redeem-all",
      () =>
        Promise.all(
          awards
            .filter((award) => award.status === "PENDING")
            .map((award) => api.redeem(activityId, award.id)),
        ),
      "所有待核销奖品已处理。",
    );
  const openLottery = (pool) => {
    setChanceForm({ participantId: "", draws: 1, reason: "" });
    setLotteryVenue((venues.find((venue) => venue.enabled) || venues[0])?.code || "");
    setLotteryDialog(pool);
  };
  const grantLotteryChances = async (event) => {
    event.preventDefault();
    await run(
      "grant-chances",
      () =>
        api.grantLotteryChances(activityId, chanceForm.participantId, {
          draws: Number(chanceForm.draws),
          reason: chanceForm.reason,
        }),
      "抽奖机会已授予，参与者可通过专属入口抽奖。",
    );
    setLotteryDialog(null);
  };
  const pendingAwards = awards.filter((award) => award.status === "PENDING");
  const issueablePools = pools.filter(
    (pool) =>
      pool.enabled && pool.purpose !== "RANKING" && pool.remainingQuantity > 0,
  );
  const lotteryUrl = lotteryDialog
    ? `${window.location.origin}/lottery/${activityId}?pool=${encodeURIComponent(lotteryDialog.id)}${lotteryVenue ? `&venue=${encodeURIComponent(lotteryVenue)}` : ""}`
    : "";
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="PRIZE OPERATIONS"
        title="奖品与核销"
        description="为排名、抽奖和人工发放配置独立奖池，并在现场完成可审计的奖品核销。"
        action={
          <button className="primary-button" type="button" onClick={openCreate}>
            <Plus size={17} />
            创建奖池
          </button>
        }
      />
      <InlineError text={error} onRetry={load} />
      {notice && (
        <div className="inline-notice" role="status">
          <BadgeCheck size={17} />
          <span>{notice}</span>
        </div>
      )}
      <section className="prize-grid">
        {pools.map((pool) => (
          <article className="prize-pool-card" key={pool.id}>
            <div className="prize-pool-card__icon">
              <Gift size={21} />
            </div>
            <span className="type-chip">
              {pool.purpose === "RANKING"
                ? "排名奖励"
                : pool.purpose === "LOTTERY"
                  ? "抽奖奖池"
                  : "人工发放"}
            </span>
            <h2>{pool.name}</h2>
            <p>
              {pool.description ||
                `${pool.deliveryType === "PHYSICAL" ? "实物" : "数字"}奖品 · ${pool.code}`}
            </p>
            <div>
              <span>
                剩余库存 <strong>{pool.remainingQuantity}</strong>
              </span>
              <span>
                已发放 <strong>{pool.claimedQuantity}</strong>
              </span>
              <span>
                最低积分 <strong>{pool.minScore}</strong>
              </span>
            </div>
            {pool.purpose === "RANKING" && (
              <small className="pool-rule">
                排名 {pool.rankFrom} - {pool.rankTo} 名
              </small>
            )}
            <div className="pool-card-actions">
              <button
                className="secondary-button"
                type="button"
                onClick={() => openEdit(pool)}
              >
                配置
                <ChevronRight size={16} />
              </button>
              {pool.purpose === "RANKING" && (
                <button
                  className="accent-button"
                  type="button"
                  disabled={
                    busy === `ranking-${pool.id}` ||
                    !pool.enabled ||
                    !pool.remainingQuantity
                  }
                  onClick={() => issueRanking(pool)}
                >
                  {busy === `ranking-${pool.id}` ? "正在生成" : "按排行发奖"}
                </button>
              )}
              {pool.purpose === "LOTTERY" && (
                <button
                  className="accent-button"
                  type="button"
                  disabled={!pool.enabled || !pool.remainingQuantity}
                  onClick={() => openLottery(pool)}
                >
                  <Ticket size={16} />
                  抽奖入口
                </button>
              )}
              <button
                className="text-button"
                type="button"
                disabled={busy === `delete-${pool.id}`}
                onClick={() => deletePool(pool)}
              >
                删除
              </button>
            </div>
          </article>
        ))}
        {!pools.length && (
          <EmptyState
            icon={Gift}
            title="尚未配置奖池"
            description="创建奖池后，可为排名、抽奖或人工发放配置对应库存。"
          />
        )}
      </section>
      <section className="reward-operations">
        <article className="redemption-callout">
          <QrCode size={24} />
          <div>
            <p className="eyebrow">ON-SITE REDEMPTION</p>
            <h2>现场扫码核销</h2>
            <span>
              {pendingAwards.length}{" "}
              份待核销奖品，可在工作人员端逐项或批量完成核销。
            </span>
          </div>
          <button
            className="primary-button"
            type="button"
            onClick={() => {
              setRedemptionOpen(true);
              load();
            }}
          >
            <QrCode size={17} />
            打开核销器
          </button>
        </article>
        <div className="reward-operations__actions">
          <button
            className="secondary-button"
            type="button"
            onClick={() => {
              setIssueOpen(true);
              setRedemptionOpen(true);
            }}
            disabled={!participants.length || !issueablePools.length}
          >
            <Gift size={16} />
            人工发奖
          </button>
          <button
            className="secondary-button"
            type="button"
            onClick={redeemAll}
            disabled={!pendingAwards.length || busy === "redeem-all"}
          >
            <ClipboardList size={16} />
            {busy === "redeem-all" ? "正在批量核销" : "批量核销待领奖品"}
          </button>
        </div>
      </section>
      {redemptionOpen && (
        <section className="redemption-workspace">
          <div className="redemption-workspace__heading">
            <div>
              <p className="eyebrow">REDEMPTION LEDGER</p>
              <h2>奖品核销台</h2>
            </div>
            <button
              className="toolbar-icon"
              type="button"
              aria-label="关闭核销器"
              onClick={() => setRedemptionOpen(false)}
            >
              <X size={18} />
            </button>
          </div>
          <div className="award-table">
            <div className="award-table__header">
              <span>获奖人</span>
              <span>奖品</span>
              <span>状态</span>
              <span>领取凭证</span>
              <span>操作</span>
            </div>
            {awards.map((award) => {
              const participant = participants.find(
                (item) => item.id === award.participantId,
              );
              return (
                <article key={award.id}>
                  <div>
                    <strong>
                      {award.participantName ||
                        participant?.name ||
                        "已删除参与者"}
                    </strong>
                    <small>
                      {award.participantContact ||
                        participant?.venue ||
                        "活动参与者"}
                    </small>
                  </div>
                  <div>
                    <strong>{award.prizeName}</strong>
                    <small>
                      {award.deliveryType === "PHYSICAL"
                        ? "现场实物领取"
                        : award.deliveryType === "VOUCHER"
                          ? "兑换券"
                          : "数字奖品"}
                    </small>
                  </div>
                  <span
                    className={`award-status award-status--${String(award.status || "").toLowerCase()}`}
                  >
                    {award.status === "PENDING"
                      ? "待核销"
                      : award.status === "REDEEMED"
                        ? "已核销"
                        : "已作废"}
                  </span>
                  <code>
                    {award.redemptionCode || award.redemptionUrl || "现场领取"}
                  </code>
                  <div className="award-actions">
                    {award.status === "PENDING" && (
                      <>
                        <button
                          className="secondary-button"
                          type="button"
                          disabled={busy === `redeem-${award.id}`}
                          onClick={() => redeemPending(award)}
                        >
                          {busy === `redeem-${award.id}` ? "处理中" : "核销"}
                        </button>
                        <button
                          className="text-button"
                          type="button"
                          disabled={busy === `void-${award.id}`}
                          onClick={() => voidPending(award)}
                        >
                          作废
                        </button>
                      </>
                    )}
                    {award.status === "REDEEMED" && (
                      <button
                        className="text-button"
                        type="button"
                        disabled={busy === `reverse-${award.id}`}
                        onClick={() => reverseAward(award)}
                      >
                        反核销
                      </button>
                    )}
                  </div>
                </article>
              );
            })}
            {!awards.length && (
              <EmptyState
                icon={ClipboardList}
                title="暂无待处理奖品"
                description="按排行发奖、人工发奖或抽奖成功后，奖品会在此处生成。"
              />
            )}
          </div>
        </section>
      )}
      {poolDialog && (
        <Dialog
          title={
            poolDialog === "create"
              ? "创建活动奖池"
              : `配置奖池 · ${poolDialog.name}`
          }
          onClose={closePoolDialog}
        >
          <form className="dialog-form" onSubmit={submitPool}>
            <label>
              奖池编码
              <input
                required
                disabled={poolDialog !== "create"}
                value={poolForm.code}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, code: event.target.value })
                }
                placeholder="例如：rank-top-3"
              />
            </label>
            <label>
              奖池名称
              <input
                required
                value={poolForm.name}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, name: event.target.value })
                }
                placeholder="例如：冠军礼盒"
              />
            </label>
            <label>
              触发场景
              <select
                disabled={poolDialog !== "create"}
                value={poolForm.purpose}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, purpose: event.target.value })
                }
              >
                <option value="MANUAL">人工发放</option>
                <option value="RANKING">积分排名</option>
                <option value="LOTTERY">抽奖转盘</option>
              </select>
            </label>
            <label>
              交付方式
              <select
                value={poolForm.deliveryType}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, deliveryType: event.target.value })
                }
              >
                <option value="DIGITAL">数字兑换码</option>
                <option value="VOUCHER">兑换券</option>
                <option value="PHYSICAL">实物领取</option>
              </select>
            </label>
            <label>
              奖品说明
              <textarea
                value={poolForm.description || ""}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, description: event.target.value })
                }
                placeholder="说明奖品内容或领取要求"
              />
            </label>
            {poolForm.deliveryType !== "PHYSICAL" && (
              <label>
                兑换链接（可选）
                <input
                  type="url"
                  value={poolForm.redemptionUrl || ""}
                  onChange={(event) =>
                    setPoolForm({
                      ...poolForm,
                      redemptionUrl: event.target.value,
                    })
                  }
                  placeholder="https://..."
                />
              </label>
            )}
            <div className="form-grid">
              <label>
                总库存
                <input
                  required
                  type="number"
                  min="1"
                  value={poolForm.totalQuantity}
                  onChange={(event) =>
                    setPoolForm({
                      ...poolForm,
                      totalQuantity: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                最低积分
                <input
                  required
                  type="number"
                  min="0"
                  value={poolForm.minScore}
                  onChange={(event) =>
                    setPoolForm({ ...poolForm, minScore: event.target.value })
                  }
                />
              </label>
            </div>
            {poolForm.purpose === "LOTTERY" && (
              <label>
                抽奖权重
                <input
                  required
                  type="number"
                  min="1"
                  value={poolForm.drawWeight}
                  onChange={(event) =>
                    setPoolForm({ ...poolForm, drawWeight: event.target.value })
                  }
                />
              </label>
            )}
            {poolForm.purpose === "RANKING" && (
              <div className="form-grid">
                <label>
                  起始排名
                  <input
                    required
                    type="number"
                    min="1"
                    value={poolForm.rankFrom || ""}
                    onChange={(event) =>
                      setPoolForm({ ...poolForm, rankFrom: event.target.value })
                    }
                  />
                </label>
                <label>
                  结束排名
                  <input
                    required
                    type="number"
                    min="1"
                    value={poolForm.rankTo || ""}
                    onChange={(event) =>
                      setPoolForm({ ...poolForm, rankTo: event.target.value })
                    }
                  />
                </label>
              </div>
            )}
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={Boolean(poolForm.enabled)}
                onChange={(event) =>
                  setPoolForm({ ...poolForm, enabled: event.target.checked })
                }
              />
              启用此奖池
            </label>
            <button className="primary-button" disabled={busy === "pool-form"}>
              {busy === "pool-form"
                ? "正在保存"
                : poolDialog === "create"
                  ? "创建奖池"
                  : "保存配置"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {issueOpen && (
        <Dialog title="人工发放奖品" onClose={() => setIssueOpen(false)}>
          <form className="dialog-form" onSubmit={submitIssue}>
            <label>
              参与者
              <select
                required
                value={awardForm.participantId}
                onChange={(event) =>
                  setAwardForm({
                    ...awardForm,
                    participantId: event.target.value,
                  })
                }
              >
                <option value="">选择获奖参与者</option>
                {participants.map((participant) => (
                  <option key={participant.id} value={participant.id}>
                    {participant.name} · {participant.score} 分 ·{" "}
                    {participant.venue}
                  </option>
                ))}
              </select>
            </label>
            <label>
              奖池
              <select
                required
                value={awardForm.prizePoolId}
                onChange={(event) =>
                  setAwardForm({
                    ...awardForm,
                    prizePoolId: event.target.value,
                  })
                }
              >
                <option value="">选择可发放奖池</option>
                {issueablePools.map((pool) => (
                  <option key={pool.id} value={pool.id}>
                    {pool.name} · 剩余 {pool.remainingQuantity}
                  </option>
                ))}
              </select>
            </label>
            <label>
              发奖备注（可选）
              <textarea
                value={awardForm.fulfillmentNote}
                onChange={(event) =>
                  setAwardForm({
                    ...awardForm,
                    fulfillmentNote: event.target.value,
                  })
                }
                placeholder="例如：主持人确认的现场奖励"
              />
            </label>
            <button
              className="primary-button"
              disabled={busy === "issue-award"}
            >
              {busy === "issue-award" ? "正在发放" : "生成待核销奖品"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {lotteryDialog && (
        <Dialog
          title={`抽奖入口 · ${lotteryDialog.name}`}
          onClose={() => setLotteryDialog(null)}
        >
          <div className="lottery-entry">
            <QRCodeSVG
              value={lotteryUrl}
              size={132}
              level="M"
              includeMargin
            />
            <div>
              <p className="eyebrow">LOTTERY LINK</p>
              <strong>{lotteryDialog.name}</strong>
              <a
                href={lotteryUrl}
                target="_blank"
                rel="noreferrer"
              >
                打开参与者专属抽奖入口
                <ArrowRight size={15} />
              </a>
            </div>
          </div>
          <label>
            抽奖会场
            <select
              required
              value={lotteryVenue}
              onChange={(event) => setLotteryVenue(event.target.value)}
            >
              <option value="">选择入口会场</option>
              {venues.filter((venue) => venue.enabled).map((venue) => (
                <option key={venue.id} value={venue.code}>{venue.name}</option>
              ))}
            </select>
          </label>
          <form className="dialog-form" onSubmit={grantLotteryChances}>
            <label>
              授予参与者
              <select
                required
                value={chanceForm.participantId}
                onChange={(event) =>
                  setChanceForm({
                    ...chanceForm,
                    participantId: event.target.value,
                  })
                }
              >
                <option value="">选择参与者</option>
                {participants.map((participant) => (
                  <option key={participant.id} value={participant.id}>
                    {participant.name} · {participant.score} 分 ·{" "}
                    {participant.venue}
                  </option>
                ))}
              </select>
            </label>
            <div className="form-grid">
              <label>
                抽奖次数
                <input
                  required
                  type="number"
                  min="1"
                  value={chanceForm.draws}
                  onChange={(event) =>
                    setChanceForm({ ...chanceForm, draws: event.target.value })
                  }
                />
              </label>
              <label>
                授予原因
                <input
                  value={chanceForm.reason}
                  onChange={(event) =>
                    setChanceForm({ ...chanceForm, reason: event.target.value })
                  }
                  placeholder="例如：完成问答"
                />
              </label>
            </div>
            <button
              className="primary-button"
              disabled={busy === "grant-chances"}
            >
              {busy === "grant-chances" ? "正在授予" : "授予抽奖机会"}
              <Ticket size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}

function ScreensPage({ activityId }) {
  const emptyTemplate = () => ({
    name: "",
    description: "",
    headline: "",
    background: "#163449",
    backgroundImage: "",
    imageUrl: "",
    fileUrl: "",
    includeActivityQr: true,
    includeRegistrationQr: false,
  });
  const [templates, setTemplates] = useState([]);
  const [devices, setDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [error, setError] = useState("");
  const [registering, setRegistering] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(false);
  const [editingTemplateId, setEditingTemplateId] = useState(null);
  const [pairing, setPairing] = useState(null);
  const [renamingDevice, setRenamingDevice] = useState(null);
  const [deviceForm, setDeviceForm] = useState({
    name: "",
    viewportWidth: 1920,
    viewportHeight: 1080,
  });
  const [deviceNameForm, setDeviceNameForm] = useState({ name: "" });
  const [templateForm, setTemplateForm] = useState(emptyTemplate);
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [templateList, deviceList] = await Promise.all([
        api.templates(activityId),
        api.devices(activityId),
      ]);
      setTemplates(templateList);
      setDevices(deviceList);
      setSelectedDeviceId((current) =>
        current && deviceList.some((item) => item.id === current)
          ? current
          : deviceList[0]?.id || "",
      );
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId]);
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, load);
  const selected = devices.find((item) => item.id === selectedDeviceId);
  const pairingUrl = pairing
    ? window.location.origin +
      "/screen/" +
      activityId +
      "?device=" +
      encodeURIComponent(pairing.device.id) +
      "&pairing=" +
      encodeURIComponent(pairing.pairingToken)
    : "";
  const registerDevice = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const next = await api.registerScreen(activityId, {
        ...deviceForm,
        viewportWidth: Number(deviceForm.viewportWidth),
        viewportHeight: Number(deviceForm.viewportHeight),
      });
      setPairing(next);
      setRegistering(false);
      setDeviceForm({ name: "", viewportWidth: 1920, viewportHeight: 1080 });
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const templateToForm = (template) => {
    const form = emptyTemplate();
    for (const component of template?.components || []) {
      const config = component.config || {};
      if (component.type === "BACKGROUND") {
        form.background = config.color || form.background;
        form.backgroundImage = config.imageUrl || "";
      } else if (component.type === "TEXT") {
        form.headline = config.text || config.label || "";
      } else if (component.type === "IMAGE") {
        form.imageUrl = config.url || "";
      } else if (component.type === "FILE") {
        form.fileUrl = config.url || "";
      } else if (component.type === "ACTIVITY_QR") {
        form.includeActivityQr = true;
      } else if (component.type === "REGISTRATION_QR") {
        form.includeRegistrationQr = true;
      }
    }
    return {
      ...form,
      name: template?.name || "",
      description: template?.description || "",
    };
  };
  const openTemplateEditor = (template = null) => {
    setError("");
    setEditingTemplateId(template?.id || null);
    setTemplateForm(template ? templateToForm(template) : emptyTemplate());
    setEditingTemplate(true);
  };
  const saveTemplate = async (event) => {
    event.preventDefault();
    const components = [
      {
        id: "background",
        type: "BACKGROUND",
        config: templateForm.backgroundImage
          ? { imageUrl: templateForm.backgroundImage }
          : { color: templateForm.background },
      },
      { id: "headline", type: "TEXT", config: { text: templateForm.headline } },
    ];
    if (templateForm.includeActivityQr)
      components.push({
        id: "activity-qr",
        type: "ACTIVITY_QR",
        config: { label: "扫描进入当前活动" },
      });
    if (templateForm.includeRegistrationQr)
      components.push({
        id: "registration-qr",
        type: "REGISTRATION_QR",
        config: { label: "扫描登记信息" },
      });
    if (templateForm.imageUrl)
      components.push({
        id: "image",
        type: "IMAGE",
        config: { url: templateForm.imageUrl, alt: "活动图片" },
      });
    if (templateForm.fileUrl)
      components.push({
        id: "file",
        type: "FILE",
        config: { url: templateForm.fileUrl, label: "活动文件" },
      });
    try {
      const payload = {
        name: templateForm.name,
        description: templateForm.description,
        components,
      };
      if (editingTemplateId) {
        await api.updateTemplate(activityId, editingTemplateId, payload);
      } else {
        await api.createTemplate(activityId, payload);
      }
      setEditingTemplate(false);
      setEditingTemplateId(null);
      setTemplateForm(emptyTemplate());
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const apply = async (templateId) => {
    if (!selectedDeviceId) {
      setError("请先选择一个受控大屏设备");
      return;
    }
    setError("");
    try {
      await api.applyTemplate(activityId, templateId, {
        deviceIds: [selectedDeviceId],
        overrides: {},
      });
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const updateSettings = async (patch) => {
    if (!selected) return;
    try {
      await api.updateScreenSettings(activityId, selected.id, patch);
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const rename = async (event) => {
    event.preventDefault();
    if (!renamingDevice) return;
    try {
      await api.renameScreen(activityId, renamingDevice.id, deviceNameForm);
      setRenamingDevice(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const rotatePairing = async () => {
    if (!selected) return;
    try {
      const next = await api.rotateScreenPairing(activityId, selected.id);
      setPairing(next);
    } catch (cause) {
      setError(cause.message);
    }
  };
  const copyPairing = async () => {
    if (!pairingUrl) return;
    try {
      await navigator.clipboard.writeText(pairingUrl);
    } catch {
      setError("无法自动复制，请扫描二维码或在受控设备上打开直达链接");
    }
  };
  const deleteTemplate = async (template) => {
    if (
      template.preset ||
      !window.confirm("确认删除模板“" + template.name + "”吗？")
    )
      return;
    try {
      await api.deleteTemplate(activityId, template.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="PUBLIC SCREEN SYSTEM"
        title="大屏管理"
        description="每块屏幕均需一次性安全配对；内容、字号、音量和滚动位置会通过专属实时通道同步。"
        action={
          <button
            className="primary-button"
            type="button"
            onClick={() => setRegistering(true)}
          >
            <Plus size={17} />
            注册大屏
          </button>
        }
      />
      <InlineError text={error} onRetry={load} />
      {pairing && (
        <section className="pairing-callout">
          <div className="pairing-callout__qr">
            <QRCodeSVG value={pairingUrl} size={86} level="M" includeMargin />
          </div>
          <ShieldCheck size={22} />
          <div>
            <p className="eyebrow">ONE-TIME PAIRING LINK</p>
            <strong>{pairing.device.name} 已创建</strong>
            <span>扫描二维码或打开直达链接，令牌只可使用一次。</span>
          </div>
          <div className="pairing-callout__actions">
            <a
              className="secondary-button"
              href={pairingUrl}
              target="_blank"
              rel="noreferrer"
            >
              <Monitor size={16} />
              打开受控大屏
            </a>
            <button
              className="secondary-button"
              type="button"
              onClick={copyPairing}
            >
              <Copy size={16} />
              复制链接
            </button>
          </div>
        </section>
      )}
      <div className="screens-admin-grid">
        <section className="data-panel">
          <div className="panel-section-title">
            <div>
              <p className="eyebrow">CONNECTED DEVICES</p>
              <h2>受控现场屏幕</h2>
            </div>
            <span>{devices.length} 台设备</span>
          </div>
          <div className="device-admin-list">
            {devices.map((device) => (
              <article
                key={device.id}
                className={
                  "device-admin-row " +
                  (device.id === selectedDeviceId ? "is-selected" : "")
                }
                onClick={() => setSelectedDeviceId(device.id)}
              >
                <div className="device-preview">
                  <Monitor size={19} />
                  <span>LIVE</span>
                </div>
                <div>
                  <strong>{device.name}</strong>
                  <small>
                    {device.viewportWidth || 1920} ×{" "}
                    {device.viewportHeight || 1080} · {device.displayMode}
                  </small>
                </div>
                <span
                  className={
                    device.status === "ONLINE"
                      ? "online-status"
                      : "offline-status"
                  }
                >
                  <i />
                  {device.status === "ONLINE" ? "在线" : "离线"}
                </span>
                <button
                  className="row-action"
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    setRenamingDevice(device);
                    setDeviceNameForm({ name: device.name });
                  }}
                >
                  管理
                  <ChevronRight size={15} />
                </button>
              </article>
            ))}
            {!devices.length && (
              <EmptyState
                icon={Monitor}
                title="尚未注册大屏"
                description="由工作人员注册后，在目标屏幕打开一次性配对链接。"
              />
            )}
          </div>
          {selected && (
            <div className="screen-device-controls">
              <div>
                <strong>{selected.name}</strong>
                <span>
                  {selected.fontScale}% 字号 · {selected.volume}% 音量 · 滚动{" "}
                  {selected.scrollPosition}px
                </span>
              </div>
              <label>
                字号
                <input
                  type="range"
                  min="50"
                  max="200"
                  value={selected.fontScale}
                  onChange={(event) =>
                    updateSettings({ fontScale: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                音量
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={selected.volume}
                  onChange={(event) =>
                    updateSettings({ volume: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                滚动位置
                <input
                  type="range"
                  min="0"
                  max="100000"
                  step="10"
                  value={selected.scrollPosition}
                  onChange={(event) =>
                    updateSettings({
                      scrollPosition: Number(event.target.value),
                    })
                  }
                />
              </label>
              <label className="toggle-control">
                <input
                  type="checkbox"
                  checked={selected.autoScroll}
                  onChange={(event) =>
                    updateSettings({ autoScroll: event.target.checked })
                  }
                />
                自动滚动
              </label>
              <button
                className="text-button"
                type="button"
                onClick={rotatePairing}
              >
                重新生成配对链接
              </button>
            </div>
          )}
        </section>
        <section className="data-panel">
          <div className="panel-section-title">
            <div>
              <p className="eyebrow">TEMPLATE LIBRARY</p>
              <h2>大屏内容模板</h2>
            </div>
            <button
              className="toolbar-icon"
              type="button"
              title="新建模板"
              aria-label="新建模板"
              onClick={() => openTemplateEditor()}
            >
              <Plus size={18} />
            </button>
          </div>
          <div className="template-list">
            {templates.map((template) => (
              <article key={template.id} className="screen-template-row">
                <div className="template-mini">
                  <LayoutTemplate size={19} />
                </div>
                <div>
                  <strong>{template.name}</strong>
                  <small>
                    {template.components?.length || 0} 个组件 ·{" "}
                    {template.updatedAt
                      ? formatDate(template.updatedAt)
                      : "预置模板"}
                  </small>
                </div>
                <button
                  className="row-action"
                  type="button"
                  onClick={() => apply(template.id)}
                >
                  下发
                  <Send size={15} />
                </button>
                {!template.preset && (
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => openTemplateEditor(template)}
                  >
                    <Pencil size={14} />
                    编辑
                  </button>
                )}
                <button
                  className="text-button"
                  type="button"
                  onClick={() => deleteTemplate(template)}
                  disabled={template.preset}
                >
                  删除
                </button>
              </article>
            ))}
            {!templates.length && (
              <EmptyState
                icon={LayoutTemplate}
                title="尚未创建模板"
                description="模板可组合二维码、文字、图片、背景和文件组件。"
              />
            )}
          </div>
        </section>
      </div>
      {registering && (
        <Dialog title="注册受控大屏" onClose={() => setRegistering(false)}>
          <form className="dialog-form" onSubmit={registerDevice}>
            <label>
              设备名称
              <input
                required
                value={deviceForm.name}
                onChange={(event) =>
                  setDeviceForm({ ...deviceForm, name: event.target.value })
                }
                placeholder="例如：主舞台 LED 屏"
              />
            </label>
            <div className="form-grid">
              <label>
                宽度
                <input
                  required
                  type="number"
                  min="1"
                  value={deviceForm.viewportWidth}
                  onChange={(event) =>
                    setDeviceForm({
                      ...deviceForm,
                      viewportWidth: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                高度
                <input
                  required
                  type="number"
                  min="1"
                  value={deviceForm.viewportHeight}
                  onChange={(event) =>
                    setDeviceForm({
                      ...deviceForm,
                      viewportHeight: event.target.value,
                    })
                  }
                />
              </label>
            </div>
            <button className="primary-button">
              创建一次性配对链接
              <ArrowRight size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {editingTemplate && (
        <Dialog
          title={editingTemplateId ? "编辑大屏模板" : "创建大屏模板"}
          onClose={() => {
            setEditingTemplate(false);
            setEditingTemplateId(null);
          }}
        >
          <form className="dialog-form" onSubmit={saveTemplate}>
            <label>
              模板名称
              <input
                required
                value={templateForm.name}
                onChange={(event) =>
                  setTemplateForm({ ...templateForm, name: event.target.value })
                }
              />
            </label>
            <label>
              说明
              <input
                value={templateForm.description}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    description: event.target.value,
                  })
                }
              />
            </label>
            <label>
              主标题
              <textarea
                required
                value={templateForm.headline}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    headline: event.target.value,
                  })
                }
              />
            </label>
            <label>
              背景色
              <input
                type="color"
                value={templateForm.background}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    background: event.target.value,
                  })
                }
              />
            </label>
            <label>
              背景图片地址（可选）
              <input
                type="url"
                value={templateForm.backgroundImage}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    backgroundImage: event.target.value,
                  })
                }
                placeholder="https://..."
              />
            </label>
            <label>
              图片地址（可选）
              <input
                type="url"
                value={templateForm.imageUrl}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    imageUrl: event.target.value,
                  })
                }
                placeholder="https://..."
              />
            </label>
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={templateForm.includeActivityQr}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    includeActivityQr: event.target.checked,
                  })
                }
              />
              加入活动二维码
            </label>
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={templateForm.includeRegistrationQr}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    includeRegistrationQr: event.target.checked,
                  })
                }
              />
              加入信息登记二维码
            </label>
            <label>
              文件地址（可选）
              <input
                type="url"
                value={templateForm.fileUrl}
                onChange={(event) =>
                  setTemplateForm({
                    ...templateForm,
                    fileUrl: event.target.value,
                  })
                }
                placeholder="PDF 或 Word 文件地址"
              />
            </label>
            <button className="primary-button" type="submit">
              {editingTemplateId ? "更新模板" : "保存模板"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {renamingDevice && (
        <Dialog title="管理大屏设备" onClose={() => setRenamingDevice(null)}>
          <form className="dialog-form" onSubmit={rename}>
            <label>
              设备名称
              <input
                required
                value={deviceNameForm.name}
                onChange={(event) =>
                  setDeviceNameForm({ name: event.target.value })
                }
              />
            </label>
            <button className="primary-button">
              保存设备名称
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}
function SettingsPage({ user, activityId, activity, reloadActivities }) {
  const emptyVenue = () => ({
    code: "",
    name: "",
    capacity: "",
    enabled: true,
  });
  const emptyRegistrationField = (displayOrder = 0) => ({
    fieldKey: "",
    label: "",
    type: "TEXT",
    options: "",
    required: false,
    enabled: true,
    displayOrder,
  });
  const [members, setMembers] = useState([]);
  const [users, setUsers] = useState([]);
  const [venues, setVenues] = useState([]);
  const [registrationFields, setRegistrationFields] = useState([]);
  const [siteSettings, setSiteSettings] = useState(null);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [editingAccount, setEditingAccount] = useState(null);
  const [accountEditForm, setAccountEditForm] = useState({
    username: "",
    displayName: "",
    password: "",
  });
  const [configDialog, setConfigDialog] = useState(null);
  const [busy, setBusy] = useState("");
  const [form, setForm] = useState({ userId: "", role: "STAFF" });
  const [account, setAccount] = useState({
    username: "",
    displayName: "",
    password: "",
    systemRole: "STAFF",
  });
  const [venueForm, setVenueForm] = useState(emptyVenue);
  const [fieldForm, setFieldForm] = useState(emptyRegistrationField);
  const [siteForm, setSiteForm] = useState({
    domain: "",
    siteName: "",
    logoUrl: "",
    footerCode: "",
    storageEnabled: false,
    storageEndpoint: "",
    storageRegion: "us-east-1",
    storageBucket: "",
    storageAccessKey: "",
    storageSecretKey: "",
    storageSessionToken: "",
    storagePublicBaseUrl: "",
    storageAddressingStyle: "AUTO",
    clearStorageCredentials: false,
  });
  const [brandForm, setBrandForm] = useState({
    clientDisplayName: "",
    clientThemeColor: "#168F7C",
    clientHeroImageUrl: "",
    clientBackgroundImageUrl: "",
  });
  const isSystemAdmin = user?.role === "SYSTEM_ADMIN";
  const isActivityAdmin = members.some(
    (member) => member.userId === user?.id && member.role === "ACTIVITY_ADMIN",
  );
  const canManageUsers = isSystemAdmin || isActivityAdmin;
  const load = useCallback(async () => {
    if (!activityId) return;
    try {
      const [memberList, venueList, fieldList, nextSiteSettings] = await Promise.all([
        api.memberships(activityId),
        api.venues(activityId),
        api.registrationFields(activityId),
        isSystemAdmin ? api.adminSiteSettings() : Promise.resolve(null),
      ]);
      const accountList = isSystemAdmin
        ? await api.users()
        : memberList.some(
              (member) => member.userId === user?.id && member.role === "ACTIVITY_ADMIN",
            )
          ? await api.membershipUsers(activityId)
          : [];
      setMembers(memberList);
      setVenues(venueList);
      setRegistrationFields(fieldList);
      setUsers(accountList);
      setSiteSettings(nextSiteSettings);
      if (nextSiteSettings) {
        setSiteForm({
          domain: nextSiteSettings.domain || "",
          siteName: nextSiteSettings.siteName || "",
          logoUrl: nextSiteSettings.logoUrl || "",
          footerCode: nextSiteSettings.footerCode || "",
          storageEnabled: Boolean(nextSiteSettings.storageEnabled),
          storageEndpoint: nextSiteSettings.storageEndpoint || "",
          storageRegion: nextSiteSettings.storageRegion || "us-east-1",
          storageBucket: nextSiteSettings.storageBucket || "",
          storageAccessKey: nextSiteSettings.storageAccessKey || "",
          storageSecretKey: "",
          storageSessionToken: "",
          storagePublicBaseUrl: nextSiteSettings.storagePublicBaseUrl || "",
          storageAddressingStyle: nextSiteSettings.storageAddressingStyle || "AUTO",
          clearStorageCredentials: false,
        });
      }
      setError("");
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId, isSystemAdmin, user?.id]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    setBrandForm({
      clientDisplayName: activity?.clientDisplayName || "",
      clientThemeColor: activity?.clientThemeColor || "#168F7C",
      clientHeroImageUrl: activity?.clientHeroImageUrl || "",
      clientBackgroundImageUrl: activity?.clientBackgroundImageUrl || "",
    });
  }, [
    activity?.id,
    activity?.clientDisplayName,
    activity?.clientThemeColor,
    activity?.clientHeroImageUrl,
    activity?.clientBackgroundImageUrl,
  ]);
  const grant = async (event) => {
    event.preventDefault();
    try {
      await api.upsertMembership(activityId, form);
      setForm({ userId: "", role: "STAFF" });
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const createUser = async (event) => {
    event.preventDefault();
    try {
      let created;
      if (account.systemRole === "SYSTEM_ADMIN") {
        created = await api.createUser({ ...account, systemRole: "SYSTEM_ADMIN" });
      } else {
        if (isSystemAdmin) {
          created = await api.createUser({ ...account, systemRole: null });
          await api.upsertMembership(activityId, {
            userId: created.id,
            role: account.systemRole,
          });
        } else {
          await api.createMembershipUser(activityId, {
            username: account.username,
            displayName: account.displayName,
            password: account.password,
            role: account.systemRole,
          });
        }
      }
      setCreating(false);
      setAccount({
        username: "",
        displayName: "",
        password: "",
        systemRole: "STAFF",
      });
      await load();
    } catch (cause) {
      setError(cause.message);
    }
  };
  const openAccountEdit = (item) => {
    setError("");
    setEditingAccount(item);
    setAccountEditForm({
      username: item.username || "",
      displayName: item.displayName || "",
      password: "",
    });
  };
  const updateAccount = async (event) => {
    event.preventDefault();
    setBusy("account");
    setError("");
    try {
      const payload = {
        username: accountEditForm.username,
        displayName: accountEditForm.displayName,
        password: accountEditForm.password || null,
      };
      if (isSystemAdmin) {
        await api.updateUser(editingAccount.id, payload);
      } else {
        await api.updateMembershipUser(activityId, editingAccount.id, payload);
      }
      setEditingAccount(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const openVenue = (venue) => {
    setVenueForm(
      venue ? { ...venue, capacity: venue.capacity ?? "" } : emptyVenue(),
    );
    setConfigDialog({ type: "venue", value: venue || null });
    setError("");
  };
  const openField = (field) => {
    setFieldForm(
      field
        ? { ...field, options: (field.options || []).join("\n") }
        : emptyRegistrationField(registrationFields.length),
    );
    setConfigDialog({ type: "field", value: field || null });
    setError("");
  };
  const saveVenue = async (event) => {
    event.preventDefault();
    setBusy("venue");
    setError("");
    try {
      const payload = {
        ...venueForm,
        capacity: venueForm.capacity === "" ? null : Number(venueForm.capacity),
      };
      if (configDialog.value)
        await api.updateVenue(activityId, configDialog.value.id, {
          name: payload.name,
          capacity: payload.capacity,
          enabled: payload.enabled,
        });
      else await api.createVenue(activityId, payload);
      setConfigDialog(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const saveField = async (event) => {
    event.preventDefault();
    setBusy("field");
    setError("");
    try {
      const payload = {
        ...fieldForm,
        options: splitRegistrationOptions(fieldForm.options),
        displayOrder: Number(fieldForm.displayOrder),
      };
      if (configDialog.value)
        await api.updateRegistrationField(activityId, configDialog.value.id, {
          label: payload.label,
          type: payload.type,
          options: payload.options,
          required: payload.required,
          enabled: payload.enabled,
          displayOrder: payload.displayOrder,
        });
      else await api.createRegistrationField(activityId, payload);
      setConfigDialog(null);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const deleteVenue = async (venue) => {
    if (
      !window.confirm(
        `确认删除会场“${venue.name}”吗？已有参与者的会场请改为停用。`,
      )
    )
      return;
    setBusy(`venue-${venue.id}`);
    setError("");
    try {
      await api.deleteVenue(activityId, venue.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const deleteField = async (field) => {
    if (
      !window.confirm(
        `确认删除登记字段“${field.label}”吗？既有参与者的历史登记信息不会被删除。`,
      )
    )
      return;
    setBusy(`field-${field.id}`);
    setError("");
    try {
      await api.deleteRegistrationField(activityId, field.id);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const saveSite = async (event) => {
    event.preventDefault();
    setBusy("site");
    setError("");
    try {
      const saved = await api.updateSiteSettings(siteForm);
      setSiteSettings(saved);
      setSiteForm({
        ...saved,
        storageSecretKey: "",
        storageSessionToken: "",
        clearStorageCredentials: false,
      });
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const saveBrand = async (event) => {
    event.preventDefault();
    if (!activityId) return;
    setBusy("brand");
    setError("");
    try {
      await api.updateActivity(activityId, {
        clientDisplayName: brandForm.clientDisplayName,
        clientThemeColor: brandForm.clientThemeColor,
        clientHeroImageUrl: brandForm.clientHeroImageUrl,
        clientBackgroundImageUrl: brandForm.clientBackgroundImageUrl,
      });
      await reloadActivities?.();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy("");
    }
  };
  const choiceField = ["SELECT", "RADIO", "CHECKBOX"].includes(fieldForm.type);
  return (
    <div className="page-content">
      <PageHeader
        eyebrow="PLATFORM & ACCESS"
        title="站点与权限"
        description="维护当前活动的登记会场、报名字段和工作人员访问权限。"
        action={
            canManageUsers ? (
              <button
                className="primary-button"
                onClick={() => setCreating(true)}
              >
                <Plus size={17} />
                新增用户
              </button>
          ) : null
        }
      />
      <InlineError text={error} onRetry={load} />
      {isSystemAdmin && (
        <section className="settings-form-section">
          <div className="settings-form-section__heading">
            <div>
              <p className="eyebrow">SITE BASICS</p>
              <h2>站点基础设置</h2>
              <span>
                域名、名称、Logo
                与页脚内容会持久化保存，并在参与端入口实际使用。
              </span>
            </div>
            <Globe2 size={22} />
          </div>
          <form className="settings-inline-form" onSubmit={saveSite}>
            <label>
              站点域名
              <input
                required
                value={siteForm.domain}
                onChange={(event) =>
                  setSiteForm({ ...siteForm, domain: event.target.value })
                }
                placeholder="event.example.com"
              />
            </label>
            <label>
              站点名称
              <input
                required
                value={siteForm.siteName}
                onChange={(event) =>
                  setSiteForm({ ...siteForm, siteName: event.target.value })
                }
              />
            </label>
            <label>
              Logo 地址
              <input
                type="url"
                value={siteForm.logoUrl || ""}
                onChange={(event) =>
                  setSiteForm({ ...siteForm, logoUrl: event.target.value })
                }
                placeholder="https://..."
              />
            </label>
            <label>
              页脚内容
              <textarea
                value={siteForm.footerCode || ""}
                onChange={(event) =>
                  setSiteForm({ ...siteForm, footerCode: event.target.value })
                }
                placeholder="Copyright 2026"
              />
            </label>
            <button className="primary-button" disabled={busy === "site"}>
              {busy === "site" ? "正在保存" : "保存站点设置"}
              <Check size={16} />
            </button>
          </form>
          <div className="settings-storage-section">
            <div>
              <p className="eyebrow">OBJECT STORAGE</p>
              <h3>S3 静态文件存储</h3>
              <span>
                填写标准 S3 连接信息后，题库媒体会直传到指定桶；密钥只在提交时写入，不会再次显示。
              </span>
            </div>
            <form className="settings-inline-form settings-inline-form--storage" onSubmit={saveSite}>
              <label className="storage-toggle">
                <span>S3 上传</span>
                <input
                  type="checkbox"
                  checked={siteForm.storageEnabled}
                  onChange={(event) => setSiteForm({ ...siteForm, storageEnabled: event.target.checked })}
                />
              </label>
              <label>
                S3 接入点
                <input
                  type="url"
                  value={siteForm.storageEndpoint || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageEndpoint: event.target.value })}
                  placeholder="留空使用 AWS 区域端点；兼容服务填 https://..."
                />
              </label>
              <label>
                区域
                <input
                  required={siteForm.storageEnabled}
                  value={siteForm.storageRegion || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageRegion: event.target.value })}
                  placeholder="us-east-1"
                />
              </label>
              <label>
                S3 静态文件桶
                <input
                  required={siteForm.storageEnabled}
                  value={siteForm.storageBucket || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageBucket: event.target.value })}
                  placeholder="event-media"
                />
              </label>
              <label>
                Access key
                <input
                  value={siteForm.storageAccessKey || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageAccessKey: event.target.value })}
                  placeholder="Access key ID"
                />
              </label>
              <label>
                Secret key
                <input
                  type="password"
                  value={siteForm.storageSecretKey || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageSecretKey: event.target.value })}
                  placeholder={siteSettings?.storageSecretConfigured ? "已保存，留空不修改" : "Secret access key"}
                />
              </label>
              <label>
                会话令牌（可选）
                <input
                  type="password"
                  value={siteForm.storageSessionToken || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storageSessionToken: event.target.value })}
                  placeholder={siteSettings?.storageSessionTokenConfigured ? "已保存，留空不修改" : "STS session token"}
                />
              </label>
              <label>
                公共访问地址（可选）
                <input
                  type="url"
                  value={siteForm.storagePublicBaseUrl || ""}
                  onChange={(event) => setSiteForm({ ...siteForm, storagePublicBaseUrl: event.target.value })}
                  placeholder="https://cdn.example.com"
                />
              </label>
              <label>
                寻址方式
                <select
                  value={siteForm.storageAddressingStyle || "AUTO"}
                  onChange={(event) => setSiteForm({ ...siteForm, storageAddressingStyle: event.target.value })}
                >
                  <option value="AUTO">自动（推荐）</option>
                  <option value="VIRTUAL">虚拟主机式（bucket.endpoint）</option>
                  <option value="PATH">路径式（endpoint/bucket）</option>
                </select>
              </label>
              <label className="storage-toggle storage-toggle--clear">
                <span>清除已保存密钥</span>
                <input
                  type="checkbox"
                  checked={siteForm.clearStorageCredentials}
                  onChange={(event) => setSiteForm({ ...siteForm, clearStorageCredentials: event.target.checked })}
                />
              </label>
              <button className="primary-button" disabled={busy === "site"}>
                {busy === "site" ? "正在保存" : "保存存储设置"}
                <Check size={16} />
              </button>
            </form>
          </div>
        </section>
      )}
      <section className="settings-form-section settings-form-section--brand">
        <div className="settings-form-section__heading">
          <div>
            <p className="eyebrow">CLIENT BRANDING</p>
            <h2>参与端活动品牌</h2>
            <span>
              {activity?.name || "当前活动"}{" "}
              的显示名称、主题色、头图和背景图会直接呈现在参与者入口。
            </span>
          </div>
          <Sparkles size={22} />
        </div>
        <form className="settings-inline-form" onSubmit={saveBrand}>
          <label>
            显示名称
            <input
              value={brandForm.clientDisplayName}
              onChange={(event) =>
                setBrandForm({
                  ...brandForm,
                  clientDisplayName: event.target.value,
                })
              }
              placeholder={activity?.name || "活动名称"}
            />
          </label>
          <label>
            主题色
            <input
              type="color"
              value={brandForm.clientThemeColor || "#168F7C"}
              onChange={(event) =>
                setBrandForm({
                  ...brandForm,
                  clientThemeColor: event.target.value,
                })
              }
            />
          </label>
          <label>
            头图地址
            <input
              type="url"
              value={brandForm.clientHeroImageUrl || ""}
              onChange={(event) =>
                setBrandForm({
                  ...brandForm,
                  clientHeroImageUrl: event.target.value,
                })
              }
              placeholder="https://..."
            />
          </label>
          <label>
            背景图地址
            <input
              type="url"
              value={brandForm.clientBackgroundImageUrl || ""}
              onChange={(event) =>
                setBrandForm({
                  ...brandForm,
                  clientBackgroundImageUrl: event.target.value,
                })
              }
              placeholder="https://..."
            />
          </label>
          <button className="primary-button" disabled={busy === "brand"}>
            {busy === "brand" ? "正在保存" : "保存参与端品牌"}
            <Check size={16} />
          </button>
        </form>
      </section>
      <div className="settings-grid">
        <article className="settings-panel">
          <div className="settings-panel__icon">
            <MapPin size={21} />
          </div>
          <h2>会场登记</h2>
          <p>
            参与者必须选择启用会场。联系方式在每个活动、每个会场内独立且唯一。
          </p>
          <span className="settings-caption">
            {venues.filter((venue) => venue.enabled).length} 个启用会场 ·{" "}
            {venues.length} 个已配置
          </span>
        </article>
        <article className="settings-panel">
          <div className="settings-panel__icon settings-panel__icon--violet">
            <ClipboardList size={21} />
          </div>
          <h2>登记字段</h2>
          <p>
            可配置文本、联系方式、选择项与必填规则，并按显示顺序下发参与者入口。
          </p>
          <span className="settings-caption">
            {registrationFields.filter((field) => field.enabled).length}{" "}
            个启用字段 · {registrationFields.length} 个已配置
          </span>
        </article>
        <article className="settings-panel">
          <div className="settings-panel__icon settings-panel__icon--orange">
            <ShieldCheck size={21} />
          </div>
          <h2>当前身份</h2>
          <p>
            {roleLabel(user?.role)} · {user?.displayName || user?.username}
          </p>
          <span className="settings-caption">活动访问权限独立于系统角色</span>
        </article>
      </div>
      <section className="registration-admin-grid">
        <article className="access-workspace">
          <div className="workspace-heading">
            <div>
              <p className="eyebrow">REGISTRATION VENUES</p>
              <h2>会场管理</h2>
            </div>
            <button
              className="toolbar-icon"
              type="button"
              aria-label="新增会场"
              title="新增会场"
              onClick={() => openVenue(null)}
            >
              <Plus size={18} />
            </button>
          </div>
          <div className="configuration-list">
            {venues.map((venue) => (
              <article key={venue.id}>
                <div className="configuration-list__icon configuration-list__icon--venue">
                  <MapPin size={17} />
                </div>
                <div>
                  <strong>{venue.name}</strong>
                  <small>
                    {venue.code}
                    {venue.capacity
                      ? ` · 容量 ${venue.capacity}`
                      : " · 不限容量"}
                  </small>
                </div>
                <span
                  className={venue.enabled ? "online-status" : "offline-status"}
                >
                  <i />
                  {venue.enabled ? "启用" : "停用"}
                </span>
                <button
                  className="toolbar-icon"
                  type="button"
                  title="编辑会场"
                  onClick={() => openVenue(venue)}
                >
                  <Pencil size={16} />
                </button>
                <button
                  className="text-button"
                  type="button"
                  disabled={busy === `venue-${venue.id}`}
                  onClick={() => deleteVenue(venue)}
                >
                  删除
                </button>
              </article>
            ))}
            {!venues.length && (
              <EmptyState
                icon={MapPin}
                title="尚未配置会场"
                description="新增会场后，参与者才能在登记入口选择其所在会场。"
              />
            )}
          </div>
        </article>
        <article className="access-workspace">
          <div className="workspace-heading">
            <div>
              <p className="eyebrow">REGISTRATION SCHEMA</p>
              <h2>报名字段模板</h2>
            </div>
            <button
              className="toolbar-icon"
              type="button"
              aria-label="新增报名字段"
              title="新增报名字段"
              onClick={() => openField(null)}
            >
              <Plus size={18} />
            </button>
          </div>
          <div className="configuration-list">
            {registrationFields.map((field) => (
              <article key={field.id}>
                <div className="configuration-list__icon configuration-list__icon--field">
                  <FilePlus2 size={17} />
                </div>
                <div>
                  <strong>
                    {field.label}
                    {field.required && <em>必填</em>}
                  </strong>
                  <small>
                    {field.fieldKey} · {field.type}
                    {field.options?.length
                      ? ` · ${field.options.length} 个选项`
                      : ""}
                  </small>
                </div>
                <span
                  className={field.enabled ? "online-status" : "offline-status"}
                >
                  <i />
                  {field.enabled ? "启用" : "停用"}
                </span>
                <button
                  className="toolbar-icon"
                  type="button"
                  title="编辑报名字段"
                  onClick={() => openField(field)}
                >
                  <Pencil size={16} />
                </button>
                <button
                  className="text-button"
                  type="button"
                  disabled={busy === `field-${field.id}`}
                  onClick={() => deleteField(field)}
                >
                  删除
                </button>
              </article>
            ))}
            {!registrationFields.length && (
              <EmptyState
                icon={ClipboardList}
                title="暂无扩展字段"
                description="姓名、联系方式和所属组织为系统固定字段；可在此增加活动专属登记信息。"
              />
            )}
          </div>
        </article>
      </section>
      <section className="access-workspace">
        <div>
          <p className="eyebrow">ACTIVITY MEMBERS</p>
          <h2>当前活动成员</h2>
        </div>
        <form className="access-grant" onSubmit={grant}>
          <select
            required
            value={form.userId}
            onChange={(event) =>
              setForm({ ...form, userId: event.target.value })
            }
          >
            <option value="">选择账户</option>
            {users.map((item) => (
              <option key={item.id} value={item.id}>
                {item.displayName} · {item.username}
              </option>
            ))}
          </select>
          <select
            value={form.role}
            onChange={(event) => setForm({ ...form, role: event.target.value })}
          >
            <option value="ACTIVITY_ADMIN">活动管理员</option>
            <option value="STAFF">活动工作人员</option>
          </select>
          <button className="secondary-button" disabled={!form.userId}>
            <Plus size={16} />
            授予
          </button>
        </form>
        <div className="access-members">
          {members.map((member) => (
            <article key={member.id}>
              <Avatar name={member.displayName} />
              <div>
                <strong>{member.displayName}</strong>
                <span>
                  {member.username} · {roleLabel(member.role)}
                </span>
              </div>
              <button
                className="toolbar-icon"
                type="button"
                title="移除活动权限"
                onClick={async () => {
                  try {
                    await api.deleteMembership(activityId, member.userId);
                    await load();
                  } catch (cause) {
                    setError(cause.message);
                  }
                }}
              >
                <X size={16} />
              </button>
            </article>
          ))}
          {!members.length && (
            <EmptyState
              icon={Users}
              title="暂无活动成员"
              description="账户授权后才可访问当前活动的数据与控制功能。"
            />
          )}
        </div>
      </section>
      {canManageUsers && (
        <section className="access-workspace">
          <div className="workspace-heading">
            <div>
              <p className="eyebrow">ACCOUNT DIRECTORY</p>
              <h2>用户账户</h2>
            </div>
            <span className="settings-caption">可编辑登录名、显示名称和密码</span>
          </div>
          <div className="access-members account-directory">
            {users.filter((item) => isSystemAdmin || members.some((member) => member.userId === item.id)).map((item) => {
              const member = members.find((entry) => entry.userId === item.id);
              const role = item.systemRole || member?.role || "STAFF";
              return (
                <article key={item.id}>
                  <Avatar name={item.displayName} />
                  <div>
                    <strong>{item.displayName}</strong>
                    <span>
                      {item.username} · {roleLabel(role)}
                      {!item.enabled && " · 已停用"}
                    </span>
                  </div>
                  <button
                    className="toolbar-icon"
                    type="button"
                    title="编辑账户"
                    aria-label={`编辑账户 ${item.displayName}`}
                    onClick={() => openAccountEdit(item)}
                  >
                    <Pencil size={16} />
                  </button>
                </article>
              );
            })}
            {!users.length && (
              <EmptyState
                icon={Users}
                title="暂无可管理账户"
                description="创建账户后，可在此维护登录凭据与显示名称。"
              />
            )}
          </div>
        </section>
      )}
      {creating && (
        <Dialog
          title={isSystemAdmin ? "创建账户" : "新增活动成员"}
          onClose={() => setCreating(false)}
        >
          <form className="dialog-form" onSubmit={createUser}>
            <label>
              登录名
              <input
                required
                value={account.username}
                onChange={(event) =>
                  setAccount({ ...account, username: event.target.value })
                }
              />
            </label>
            <label>
              显示名称
              <input
                required
                value={account.displayName}
                onChange={(event) =>
                  setAccount({ ...account, displayName: event.target.value })
                }
              />
            </label>
            <label>
              初始密码
              <input
                required
                type="password"
                minLength="8"
                value={account.password}
                onChange={(event) =>
                  setAccount({ ...account, password: event.target.value })
                }
              />
            </label>
            <label>
              {isSystemAdmin ? "系统角色" : "活动角色"}
              <select
                value={account.systemRole}
                onChange={(event) =>
                  setAccount({ ...account, systemRole: event.target.value })
                }
              >
                {isSystemAdmin && <option value="SYSTEM_ADMIN">系统管理员</option>}
                <option value="ACTIVITY_ADMIN">活动管理员</option>
                <option value="STAFF">活动工作人员</option>
              </select>
            </label>
            <button className="primary-button">
              创建账户
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {editingAccount && (
        <Dialog title="编辑用户账户" onClose={() => setEditingAccount(null)}>
          <form className="dialog-form" onSubmit={updateAccount}>
            <label>
              登录名
              <input
                required
                value={accountEditForm.username}
                onChange={(event) =>
                  setAccountEditForm({
                    ...accountEditForm,
                    username: event.target.value,
                  })
                }
              />
            </label>
            <label>
              显示名称
              <input
                required
                value={accountEditForm.displayName}
                onChange={(event) =>
                  setAccountEditForm({
                    ...accountEditForm,
                    displayName: event.target.value,
                  })
                }
              />
            </label>
            <label>
              新密码（留空则保持不变）
              <input
                type="password"
                minLength="8"
                value={accountEditForm.password}
                onChange={(event) =>
                  setAccountEditForm({
                    ...accountEditForm,
                    password: event.target.value,
                  })
                }
              />
            </label>
            <button className="primary-button" disabled={busy === "account"}>
              {busy === "account" ? "正在保存" : "保存账户"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {configDialog?.type === "venue" && (
        <Dialog
          title={configDialog.value ? "编辑会场" : "新增会场"}
          onClose={() => setConfigDialog(null)}
        >
          <form className="dialog-form" onSubmit={saveVenue}>
            <label>
              会场名称
              <input
                required
                value={venueForm.name}
                onChange={(event) =>
                  setVenueForm({ ...venueForm, name: event.target.value })
                }
                placeholder="例如：主会场"
              />
            </label>
            {!configDialog.value && (
              <label>
                会场代码
                <input
                  required
                  value={venueForm.code}
                  onChange={(event) =>
                    setVenueForm({
                      ...venueForm,
                      code: event.target.value.toLowerCase(),
                    })
                  }
                  placeholder="例如：main-hall"
                  pattern="[a-z0-9][a-z0-9_-]{0,79}"
                />
              </label>
            )}
            <label>
              登记容量（可选）
              <input
                type="number"
                min="1"
                value={venueForm.capacity}
                onChange={(event) =>
                  setVenueForm({ ...venueForm, capacity: event.target.value })
                }
                placeholder="不填则不限制"
              />
            </label>
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={venueForm.enabled}
                onChange={(event) =>
                  setVenueForm({ ...venueForm, enabled: event.target.checked })
                }
              />
              允许参与者登记到此会场
            </label>
            <button className="primary-button" disabled={busy === "venue"}>
              {busy === "venue" ? "正在保存" : "保存会场"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
      {configDialog?.type === "field" && (
        <Dialog
          title={configDialog.value ? "编辑报名字段" : "新增报名字段"}
          onClose={() => setConfigDialog(null)}
        >
          <form className="dialog-form" onSubmit={saveField}>
            <label>
              字段名称
              <input
                required
                value={fieldForm.label}
                onChange={(event) =>
                  setFieldForm({ ...fieldForm, label: event.target.value })
                }
                placeholder="例如：所属部门"
              />
            </label>
            {!configDialog.value && (
              <label>
                字段代码
                <input
                  required
                  value={fieldForm.fieldKey}
                  onChange={(event) =>
                    setFieldForm({
                      ...fieldForm,
                      fieldKey: event.target.value.toLowerCase(),
                    })
                  }
                  placeholder="例如：department"
                  pattern="[a-z][a-z0-9_]{0,79}"
                />
              </label>
            )}
            <label>
              输入类型
              <select
                value={fieldForm.type}
                onChange={(event) =>
                  setFieldForm({
                    ...fieldForm,
                    type: event.target.value,
                    options: ["SELECT", "RADIO", "CHECKBOX"].includes(
                      event.target.value,
                    )
                      ? fieldForm.options
                      : "",
                  })
                }
              >
                {[
                  "TEXT",
                  "TEXTAREA",
                  "EMAIL",
                  "PHONE",
                  "NUMBER",
                  "SELECT",
                  "RADIO",
                  "CHECKBOX",
                ].map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </label>
            {choiceField && (
              <label>
                选项（每行一个）
                <textarea
                  required
                  value={fieldForm.options}
                  onChange={(event) =>
                    setFieldForm({ ...fieldForm, options: event.target.value })
                  }
                  placeholder={"例如：\n市场部\n研发部"}
                />
              </label>
            )}
            <div className="form-grid">
              <label>
                显示顺序
                <input
                  required
                  type="number"
                  min="0"
                  value={fieldForm.displayOrder}
                  onChange={(event) =>
                    setFieldForm({
                      ...fieldForm,
                      displayOrder: event.target.value,
                    })
                  }
                />
              </label>
              <label className="toggle-control">
                <input
                  type="checkbox"
                  checked={fieldForm.required}
                  onChange={(event) =>
                    setFieldForm({
                      ...fieldForm,
                      required: event.target.checked,
                    })
                  }
                />
                设为必填
              </label>
            </div>
            <label className="toggle-control">
              <input
                type="checkbox"
                checked={fieldForm.enabled}
                onChange={(event) =>
                  setFieldForm({ ...fieldForm, enabled: event.target.checked })
                }
              />
              在参与者登记页展示
            </label>
            <button className="primary-button" disabled={busy === "field"}>
              {busy === "field" ? "正在保存" : "保存字段"}
              <Check size={17} />
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}

function ParticipantPortal({ lotteryMode = false }) {
  const { activityId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const requestedVenue =
    new URLSearchParams(location.search).get("venue") || "";
  const lotteryPoolId =
    new URLSearchParams(location.search).get("pool") || null;
  const [venue, setVenue] = useState(requestedVenue);
  const [venues, setVenues] = useState([]);
  const [participant, setParticipant] = useState(() =>
    readParticipant(activityId),
  );
  const [participantToken, setLocalParticipantToken] = useState(() =>
    getParticipantToken(activityId),
  );
  const [fields, setFields] = useState([]);
  const [questions, setQuestions] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [state, setState] = useState(null);
  const [scoreboard, setScoreboard] = useState([]);
  const [activity, setActivity] = useState(null);
  const [siteSettings, setSiteSettings] = useState(null);
  const [tab, setTab] = useState(lotteryMode ? "rewards" : "play");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const applyPublicBrand = (activityInfo, siteInfo) => {
    setActivity(activityInfo);
    setSiteSettings(siteInfo);
  };
  const load = useCallback(async () => {
    if (!participantToken) {
      try {
        const [registrationFields, venueList, activityInfo, siteInfo] =
          await Promise.all([
            api.registrationFields(activityId),
            api.venues(activityId),
            api.activity(activityId),
            api.siteSettings(),
          ]);
        const configuredFields =
          registrationFields.fields || registrationFields || [];
        setFields(configuredFields);
        setVenues(venueList || []);
        setVenue((current) =>
          activeVenueCode(venueList, current || requestedVenue),
        );
        applyPublicBrand(activityInfo, siteInfo);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 404) {
          try {
            const activities = await api.activities();
            const target =
              activities.find((item) => item.status === "LIVE") ||
              activities[0];
            if (target?.id && target.id !== activityId) {
              navigate(`/join/${target.id}`, { replace: true });
              return;
            }
          } catch {
            // Keep the original activity error when the fallback list is unavailable.
          }
        }
        setFields([]);
        setVenues([]);
        setError(cause.message);
      } finally {
        setLoading(false);
      }
      return;
    }
    try {
      const [questionList, control, board, answerList, activityInfo, siteInfo] =
        await Promise.all([
          api.questions(activityId, participantToken),
          api.controlState(activityId, participantToken),
          api.scoreboard(activityId, participantToken),
          participant?.id
            ? api.submissions(activityId, participant.id, participantToken)
            : Promise.resolve([]),
          api.activity(activityId),
          api.siteSettings(),
        ]);
      setQuestions(questionList);
      setState(control);
      setScoreboard(board);
      setSubmissions(answerList);
      applyPublicBrand(activityInfo, siteInfo);
      setParticipant((current) => {
        const score = board.find(
          (entry) => entry.participantId === current?.id,
        )?.score;
        return score === undefined ? current : { ...current, score };
      });
    } catch (cause) {
      setError(cause.message);
    } finally {
      setLoading(false);
    }
  }, [activityId, navigate, participant?.id, participantToken, requestedVenue]);
  useEffect(() => {
    load();
  }, [load]);
  useActivityStream(activityId, load, participantToken);
  if (loading) return <LoadingPage label="正在连接活动现场" />;
  if (error && !questions.length)
    return <BackendProblem message={error} onRetry={load} />;
  const orderedQuestions = [...questions]
    .filter((item) => item.enabled !== false)
    .sort((left, right) => (left.displayOrder ?? 0) - (right.displayOrder ?? 0));
  const question =
    questions.find((item) => item.id === state?.questionId) || orderedQuestions[0];
  const questionIndex = Math.max(0, orderedQuestions.findIndex((item) => item.id === question?.id));
  const brandName = activity?.clientDisplayName || activity?.name || "活动现场";
  const pageStyle = {
    "--client-theme": activity?.clientThemeColor || "#168F7C",
    backgroundImage: activity?.clientBackgroundImageUrl
      ? `url("${activity.clientBackgroundImageUrl}")`
      : undefined,
  };
  return (
    <main className="participant-page" style={pageStyle}>
      <ParticipantHeader
        state={state}
        activity={activity}
        siteSettings={siteSettings}
      />
      <section className="participant-workspace" aria-label="活动参与区">
        <div className="participant-workspace__content">
          {activity?.clientHeroImageUrl && (
            <img
              className="participant-brand-hero"
              src={activity.clientHeroImageUrl}
              alt="活动头图"
            />
          )}
          <div className="participant-score">
            <div>
              <span>我的积分</span>
              <strong>{participant?.score || 0}</strong>
              <small>
                {participant
                  ? "已完成现场身份确认"
                  : lotteryMode
                    ? "登记后可使用此奖池的抽奖机会"
                    : "完成登记后可参与答题"}
              </small>
            </div>
            <Trophy size={26} />
          </div>
          {!participant ? (
            <RegistrationCard
              activityId={activityId}
              venue={venue}
              venues={venues}
              setVenue={setVenue}
              fields={fields}
              onRegistered={(person, token) => {
                persistParticipant(activityId, person);
                setParticipantToken(activityId, token);
                setLocalParticipantToken(token);
                setParticipant(person);
                setTab(lotteryMode ? "rewards" : "play");
              }}
            />
          ) : (
            <>
              {tab === "play" && (
                <AnswerCard
                  activityId={activityId}
                  participant={participant}
                  participantToken={participantToken}
                  question={question}
                  questionIndex={questionIndex}
                  questionCount={orderedQuestions.length}
                  state={state}
                  submission={submissions.find(
                    (item) => item.questionId === question?.id,
                  )}
                  onAnswered={(result) =>
                    setParticipant({ ...participant, score: result.totalScore })
                  }
                />
              )}
              {tab === "rank" && (
                <MobileRanking
                  items={scoreboard}
                  participantId={participant.id}
                />
              )}
              {tab === "rewards" && (
                <RewardsCard
                  activityId={activityId}
                  participant={participant}
                  participantToken={participantToken}
                  preferredPoolId={lotteryPoolId}
                />
              )}
            </>
          )}
        </div>
        {participant && (
          <nav className="participant-nav" aria-label="参与者功能导航">
            <button
              className={tab === "play" ? "is-active" : ""}
              onClick={() => setTab("play")}
            >
              <CircleHelp size={18} />
              答题
            </button>
            <button
              className={tab === "rank" ? "is-active" : ""}
              onClick={() => setTab("rank")}
            >
              <Trophy size={18} />
              排行
            </button>
            <button
              className={tab === "rewards" ? "is-active" : ""}
              onClick={() => setTab("rewards")}
            >
              <Gift size={18} />
              奖励
            </button>
          </nav>
        )}
      </section>
      <div className="participant-sidecopy">
        <span className="eyebrow">
          {lotteryMode ? "LOTTERY ACCESS" : "PARTICIPANT EXPERIENCE"}
        </span>
        <h1>{brandName}</h1>
        <p>
          {lotteryMode
            ? "转动幸运轮盘，领取由活动奖池服务端确认的现场惊喜。"
            : "登记信息只作用于当前活动与会场。答题结果、得分和奖励均由服务端确认。"}
        </p>
        <div>
          <BadgeCheck size={17} />
          活动级身份鉴别
        </div>
        <div>
          <Radio size={17} />
          实时状态同步
        </div>
        <div>
          <Gift size={17} />
          安全领奖核销
        </div>
        {siteSettings?.footerCode && (
          <small className="participant-site-footer">
            {siteSettings.footerCode}
          </small>
        )}
      </div>
    </main>
  );
}

function ParticipantHeader({ state, activity, siteSettings }) {
  return (
    <header className="participant-header">
      <Link
        to="/login"
        aria-label={siteSettings?.siteName || "返回工作人员登录"}
      >
        {siteSettings?.logoUrl ? (
          <img
            src={siteSettings.logoUrl}
            alt={siteSettings.siteName || "站点 Logo"}
          />
        ) : (
          <Mark />
        )}
      </Link>
      <div>
        <strong>
          {activity?.clientDisplayName ||
            activity?.name ||
            siteSettings?.siteName ||
            "活动现场"}
        </strong>
        <span>{stageLabel(state?.stage || "LOBBY")}</span>
      </div>
      <button type="button" aria-label="活动说明">
        <CircleHelp size={19} />
      </button>
    </header>
  );
}

function RegistrationCard({
  activityId,
  venue,
  venues,
  setVenue,
  fields,
  onRegistered,
}) {
  const [values, setValues] = useState({
    name: "",
    contact: "",
    organization: "",
  });
  const [customValues, setCustomValues] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const dynamic = fields.filter(
    (field) =>
      field.enabled &&
      !["name", "contact", "organization"].includes(
        registrationFieldKey(field),
      ),
  );
  const activeVenues = venues.filter((item) => item.enabled);
  const submit = async (event) => {
    event.preventDefault();
    if (!venue) {
      setError("请先选择可用会场");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const payload = buildRegistrationPayload(values, customValues);
      const person = await api.register(activityId, venue, payload);
      const session = await api.participantToken({
        activityId,
        venue,
        contact: values.contact,
      });
      onRegistered(person, session.accessToken);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="registration-card" onSubmit={submit}>
      <p className="eyebrow">WELCOME TO THE EVENT</p>
      <h2>先确认你的现场身份</h2>
      <p>联系方式会在当前活动与会场内唯一识别你。</p>
      <label>
        会场
        <select
          required
          value={venue}
          disabled={!activeVenues.length}
          onChange={(event) => setVenue(event.target.value)}
        >
          {activeVenues.length ? (
            activeVenues.map((item) => (
              <option key={item.id} value={item.code}>
                {item.name}
              </option>
            ))
          ) : (
            <option value="">暂无可用会场</option>
          )}
        </select>
      </label>
      <label>
        姓名
        <input
          required
          value={values.name}
          onChange={(event) =>
            setValues({ ...values, name: event.target.value })
          }
          placeholder="输入真实姓名"
        />
      </label>
      <label>
        联系方式
        <input
          required
          value={values.contact}
          onChange={(event) =>
            setValues({ ...values, contact: event.target.value })
          }
          placeholder="手机号或邮箱"
        />
      </label>
      <label>
        所属组织（可选）
        <input
          value={values.organization}
          onChange={(event) =>
            setValues({ ...values, organization: event.target.value })
          }
          placeholder="公司或团队"
        />
      </label>
      {dynamic.map((field) => (
        <RegistrationFieldInput
          key={field.id || registrationFieldKey(field)}
          field={field}
          value={customValues[registrationFieldKey(field)] || ""}
          onChange={(value) =>
            setCustomValues({
              ...customValues,
              [registrationFieldKey(field)]: value,
            })
          }
        />
      ))}
      {error && (
        <p className="form-error">
          <CircleAlert size={16} />
          {error}
        </p>
      )}
      <button className="primary-button" disabled={busy || !venue}>
        {busy ? "正在提交" : "完成登记"}
        <ArrowRight size={17} />
      </button>
    </form>
  );
}

function RegistrationFieldInput({ field, value, onChange }) {
  const key = registrationFieldKey(field);
  const label = (
    <>
      {field.label}
      {field.required && <small>必填</small>}
    </>
  );
  const type = field.type || "TEXT";
  const inputType =
    { EMAIL: "email", PHONE: "tel", NUMBER: "number" }[type] || "text";
  if (type === "TEXTAREA")
    return (
      <label className="registration-dynamic-field">
        {label}
        <textarea
          required={field.required}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
    );
  if (type === "SELECT" || type === "CHECKBOX")
    return (
      <label className="registration-dynamic-field">
        {label}
        <select
          required={field.required}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        >
          <option value="">请选择</option>
          {(field.options || []).map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    );
  if (type === "RADIO")
    return (
      <fieldset className="registration-dynamic-field">
        <legend>{label}</legend>
        <div className="registration-radio-list">
          {(field.options || []).map((option) => (
            <label key={option}>
              <input
                required={field.required && !value}
                name={key}
                type="radio"
                checked={value === option}
                value={option}
                onChange={(event) => onChange(event.target.value)}
              />
              {option}
            </label>
          ))}
        </div>
      </fieldset>
    );
  return (
    <label className="registration-dynamic-field">
      {label}
      <input
        required={field.required}
        type={inputType}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function AnswerCard({
  activityId,
  participant,
  participantToken,
  question,
  questionIndex = 0,
  questionCount = 0,
  state,
  submission,
  onAnswered,
}) {
  const [answers, setAnswers] = useState([]);
  const [textAnswer, setTextAnswer] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const remaining = useLiveCountdown(state);
  useEffect(() => {
    setAnswers([]);
    setTextAnswer("");
    setResult(null);
    setError("");
  }, [question?.id]);
  useEffect(() => {
    if (submission)
      setResult((current) =>
        current?.submissionId === submission.id
          ? { ...current, ...submission }
          : { ...submission, submissionId: submission.id, replayed: false },
      );
  }, [submission]);
  if (!question)
    return (
      <EmptyState
        icon={Clock3}
        title="等待工作人员开题"
        description="题目开放后会自动显示在这里。"
      />
    );
  const multi = question.type === "MULTIPLE";
  const text = question.type === "TEXT";
  const revealed = state?.stage === "ANSWER_REVEALED";
  const canAnswer = state?.stage === "QUESTION_OPEN" && !result;
  const choose = (answer) => {
    if (!canAnswer) return;
    setAnswers((current) =>
      multi
        ? current.includes(answer)
          ? current.filter((item) => item !== answer)
          : [...current, answer]
        : [answer],
    );
  };
  const submit = async () => {
    const value = text ? [textAnswer] : answers;
    if (!value.filter(Boolean).length) {
      setError("请先完成回答");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const response = await api.answer(
        activityId,
        {
          participantId: participant.id,
          questionId: question.id,
          answers: value,
          idempotencyKey: createIdempotencyKey(),
        },
        participantToken,
      );
      setResult(response);
      onAnswered(response);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy(false);
    }
  };
  const resultLabel =
    {
      CORRECT: "回答正确",
      PARTIAL: "部分正确",
      INCORRECT: "回答不正确",
      PENDING_REVIEW: "回答已提交，等待工作人员评分",
      SCORED: `本题获得 ${result?.awardedPoints || 0} 分`,
    }[result?.status] || "回答已提交";
  const resultNote =
    result?.feedback ||
    (result?.replayed
      ? "已识别为重复提交，不会重复计分。"
      : result?.status === "PENDING_REVIEW"
        ? "评分完成后，你会在这里收到得分和反馈。"
        : `${result?.responseRank ? `本题第 ${result.responseRank} 位提交 · ` : ""}积分变化 ${result?.awardedPoints >= 0 ? "+" : ""}${result?.awardedPoints || 0} 分。`);
  return (
    <article className="answer-card">
      <div className="answer-card__meta">
        <span>
          <i />第 {String(questionIndex + 1).padStart(2, "0")} / {String(Math.max(questionCount, 1)).padStart(2, "0")} 题
        </span>
        <strong>
          {remaining
            ? formatSeconds(remaining)
            : state?.stage === "QUESTION_OPEN"
              ? "进行中"
              : stageLabel(state?.stage || "LOBBY")}
        </strong>
      </div>
      <span className="type-chip">
        {typeLabel(question.type)} · {question.fullScore || 100} 分
      </span>
      <h2>{question.title}</h2>
      <ScreenMedia src={question.mediaUrl} className="answer-question-media" />
      {text ? (
        <textarea
          className="text-answer"
          value={textAnswer}
          onChange={(event) => setTextAnswer(event.target.value)}
          disabled={!canAnswer}
          placeholder="输入你的回答"
        />
      ) : (
        <div className="answer-options">
          {asOptions(question).map((option, index) => (
            <button
              type="button"
              className={answers.includes(option) ? "is-selected" : ""}
              key={option}
              disabled={!canAnswer}
              onClick={() => choose(option)}
            >
              <b>{String.fromCharCode(65 + index)}</b>
              <span>{option}</span>
              {answers.includes(option) && <Check size={17} />}
            </button>
          ))}
        </div>
      )}
      {result && (
        <div
          className={`answer-feedback ${["CORRECT", "PARTIAL"].includes(result.status) ? "is-correct" : ""}`}
        >
          <BadgeCheck size={20} />
          <div>
            <strong>
              {resultLabel}
              {result.status !== "PENDING_REVIEW" &&
                ` · ${result.awardedPoints || 0} 分`}
            </strong>
            <span>{resultNote}</span>
          </div>
        </div>
      )}
      {error && (
        <p className="form-error">
          <CircleAlert size={16} />
          {error}
        </p>
      )}
      {canAnswer && (
        <button
          className="primary-button answer-submit"
          disabled={busy}
          onClick={submit}
        >
          {busy ? "正在确认" : result ? "答案已提交" : "确认提交"}
          <Send size={17} />
        </button>
      )}
      {revealed && (
        <div className="answer-feedback is-revealed">
          <BadgeCheck size={20} />
          <div>
            <strong>本题答案已公布</strong>
            <span>答题结果和积分会实时更新。</span>
          </div>
        </div>
      )}
    </article>
  );
}

function MobileRanking({ items, participantId }) {
  return (
    <article className="mobile-ranking">
      <p className="eyebrow">LIVE SCOREBOARD</p>
      <h2>当前排行</h2>
      {items.map((item) => (
        <div
          className={item.participantId === participantId ? "is-self" : ""}
          key={item.participantId}
        >
          <span>{item.rank}</span>
          <Avatar name={item.name} />
          <strong>{item.name}</strong>
          <b>
            {item.score}
            <small>分</small>
          </b>
        </div>
      ))}
    </article>
  );
}

function RewardsCard({
  activityId,
  participant,
  participantToken,
  preferredPoolId = null,
}) {
  const [awards, setAwards] = useState([]);
  const [chance, setChance] = useState(null);
  const [drawing, setDrawing] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try {
      const [nextAwards, nextChance] = await Promise.all([
        api.awards(activityId, participant.id, participantToken),
        api.lotteryChance(activityId, participant.id, participantToken),
      ]);
      setAwards(nextAwards);
      setChance(nextChance);
    } catch (cause) {
      setError(cause.message);
    }
  }, [activityId, participant.id, participantToken]);
  useEffect(() => {
    load();
  }, [load]);
  const draw = async () => {
    setDrawing(true);
    setError("");
    try {
      const outcome = await api.draw(
        activityId,
        {
          participantId: participant.id,
          prizePoolId: preferredPoolId || null,
          venue: participant.venue || null,
          idempotencyKey: createIdempotencyKey(),
        },
        participantToken,
      );
      setResult(outcome);
      await load();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setDrawing(false);
    }
  };
  return (
    <article className="mobile-rewards">
      <p className="eyebrow">MY REWARDS</p>
      <h2>待领取奖励</h2>
      {awards.map((award) => (
        <div className="mobile-award" key={award.id}>
          <Gift size={19} />
          <div>
            <strong>{award.prizeName}</strong>
            <span>
              {award.deliveryType === "DIGITAL"
                ? award.redemptionCode
                : award.status === "REDEEMED"
                  ? "已核销"
                  : "现场领取"}
            </span>
          </div>
          <ChevronRight size={17} />
        </div>
      ))}
      <div className="draw-card">
        <span>
          {preferredPoolId ? "本入口指定奖池" : "额外抽奖机会"} · 剩余{" "}
          {chance?.remainingDraws || 0} 次
        </span>
        <div className={`draw-wheel ${drawing ? "is-spinning" : ""}`}>
          <Sparkles size={25} />
        </div>
        <strong>{result ? `抽中 ${result.prizeName}` : "转动幸运轮盘"}</strong>
        <button
          type="button"
          disabled={drawing || !(chance?.remainingDraws > 0)}
          onClick={draw}
        >
          {drawing
            ? "正在校验结果"
            : chance?.remainingDraws > 0
              ? "开始抽奖"
              : "暂无抽奖机会"}
        </button>
      </div>
      {result && (
        <div className="answer-feedback is-correct">
          <BadgeCheck size={20} />
          <div>
            <strong>{result.prizeName}</strong>
            <span>
              {result.deliveryType === "DIGITAL"
                ? `兑换码：${result.redemptionCode}`
                : "奖品已加入待核销列表，请前往现场领取。"}
            </span>
          </div>
        </div>
      )}
      {error && (
        <p className="form-error">
          <CircleAlert size={16} />
          {error}
        </p>
      )}
    </article>
  );
}

function PublicScreen() {
  const { activityId } = useParams();
  const location = useLocation();
  const query = useMemo(
    () => new URLSearchParams(location.search),
    [location.search],
  );
  const deviceId = query.get("device");
  const pairingToken = query.get("pairing");
  const [session, setSession] = useState(() =>
    deviceId ? getScreenSession(activityId, deviceId) : null,
  );
  const pairingRequest = useRef("");
  const [display, setDisplay] = useState(null);
  const [status, setStatus] = useState(deviceId ? "pairing" : "missing-device");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!deviceId) {
      setStatus("missing-device");
      return undefined;
    }
    const stored = getScreenSession(activityId, deviceId);
    if (stored?.accessToken) {
      setSession(stored);
      setStatus("ready");
      return undefined;
    }
    if (!pairingToken) {
      setStatus("pairing-required");
      return undefined;
    }
    const requestKey = `${activityId}:${deviceId}:${pairingToken}`;
    if (pairingRequest.current === requestKey) return undefined;
    pairingRequest.current = requestKey;
    setStatus("pairing");
    setError("");
    api
      .exchangeScreenPairing(activityId, deviceId, pairingToken)
      .then((next) => {
        setScreenSession(activityId, deviceId, next);
        setSession(next);
        setStatus("ready");
        window.history.replaceState(
          {},
          "",
          `${window.location.pathname}?device=${encodeURIComponent(deviceId)}`,
        );
      })
      .catch(
        (cause) => (setError(cause.message), setStatus("pairing-required")),
      );
    return undefined;
  }, [activityId, deviceId, pairingToken]);
  const load = useCallback(async () => {
    if (!deviceId || !session?.accessToken) return;
    try {
      setDisplay(
        await api.screenState(activityId, deviceId, session.accessToken),
      );
      setStatus("ready");
      setError("");
    } catch (cause) {
      setError(cause.message);
      setStatus("offline");
    }
  }, [activityId, deviceId, session?.accessToken]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    if (!deviceId || !session?.accessToken) return undefined;
    const sendHeartbeat = () =>
      api
        .screenHeartbeat(activityId, deviceId, session.accessToken, {
          viewportWidth: window.innerWidth,
          viewportHeight: window.innerHeight,
        })
        .catch(() => {});
    sendHeartbeat();
    const interval = window.setInterval(sendHeartbeat, 30000);
    return () => window.clearInterval(interval);
  }, [activityId, deviceId, session?.accessToken]);
  useScreenStream(deviceId, load, session?.accessToken);
  if (
    !deviceId ||
    !session?.accessToken ||
    status === "pairing" ||
    status === "pairing-required"
  )
    return <ScreenPairingNotice status={status} error={error} />;
  const mode = display?.mode || "LOBBY";
  return (
    <main className="public-screen-page">
      <header className="public-screen-toolbar">
        <span className="product-logo product-logo--light">
          <Mark />
          <span>矩阵现场</span>
        </span>
        <div>
          <span
            className={status === "ready" ? "online-status" : "offline-status"}
          >
            <i />
            {status === "ready" ? "设备已受控" : "连接中断"}
          </span>
          <span className="screen-device-name">
            {display?.deviceName || "现场大屏"}
          </span>
        </div>
      </header>
      <section
        className="public-canvas"
        style={{ "--screen-font-scale": (display?.fontScale || 100) / 100 }}
      >
        <ScreenDisplay activityId={activityId} display={display} mode={mode} />
      </section>
    </main>
  );
}

function ScreenPairingNotice({ status, error }) {
  const waiting = status === "pairing";
  return (
    <main className="screen-pairing-page">
      <div>
        <Mark />
        <p className="eyebrow">SCREEN DEVICE SECURITY</p>
        <h1>{waiting ? "正在建立安全设备会话" : "等待工作人员配对此屏幕"}</h1>
        <p>
          {error ||
            "请在管理端注册屏幕并使用一次性配对链接打开本页面。配对成功后，该屏幕只能读取自身展示状态。"}
        </p>
        <span className={waiting ? "online-status" : "offline-status"}>
          <i />
          {waiting ? "正在验证配对令牌" : "未关联受控设备"}
        </span>
      </div>
    </main>
  );
}

function ScreenDisplay({ activityId, display, mode }) {
  if (!display || mode === "LOBBY")
    return (
      <div className="screen-lobby">
        <p className="eyebrow">MATRIX LIVE</p>
        <h1>现场内容即将开始</h1>
        <span>此屏幕已完成安全配对，等待工作人员下发内容。</span>
      </div>
    );
  if (mode === "TEMPLATE" && display.template)
    return <TemplateScreen activityId={activityId} display={display} />;
  const payload = display.data || {};
  const rows = payload.rows || payload.scoreboard || [];
  if (mode === "SCOREBOARD" || mode === "LEADERBOARD")
    return <ScreenScoreboard board={rows} display={display} />;
  if (mode === "WINNERS") return <ScreenWinners winners={rows} display={display} />;
  if (mode === "QUESTION" || mode === "RESULT")
    return (
      <ScreenQuestion
        question={{
          title: payload.title,
          type: payload.questionType,
          options: payload.options,
          answers: payload.answers,
          mediaUrl: payload.mediaUrl,
        }}
        state={{
          stage: mode === "RESULT" ? "ANSWER_REVEALED" : "QUESTION_OPEN",
          seconds: payload.seconds,
          updatedAt: payload.updatedAt || display.updatedAt,
        }}
        result={mode === "RESULT"}
        responses={payload.responses || payload.submissions || payload.response || []}
        volume={display.volume}
      />
    );
  return (
    <div className="screen-runtime">
      <p className="eyebrow">{mode.replaceAll("_", " ")}</p>
      <h1>{payload.title || payload.headline || "现场内容"}</h1>
      <ScreenMedia
        src={payload.mediaUrl}
        className="screen-runtime-media"
        volume={display.volume}
      />
      <div className="screen-runtime__body">
        {payload.body || payload.message || "内容由工作人员通过受控设备下发。"}
      </div>
      {Array.isArray(payload.options) && (
        <div className="screen-options-new">
          {payload.options.map((item, index) => (
            <div key={`${item}-${index}`}>
              <b>{String.fromCharCode(65 + index)}</b>
              <span>{item}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TemplateScreen({ activityId, display }) {
  const containerRef = useRef(null);
  const components = display.template?.components || [];
  const overrides = display.data?.overrides || {};
  const background =
    components.find((item) => item.type === "BACKGROUND")?.config || {};
  const joinUrl = window.location.origin + "/join/" + activityId;
  useScreenScroll(containerRef, display, display.template?.id);
  return (
    <div
      ref={containerRef}
      className="template-screen"
      style={{
        background: background.imageUrl
          ? "center / cover no-repeat url(" + background.imageUrl + ")"
          : background.color || "#132439",
      }}
    >
      {components
        .filter((item) => item.type !== "BACKGROUND")
        .map((item) => {
          const config = item.config || {};
          const value = overrides[item.id] ?? config.text ?? config.label;
          if (item.type === "ACTIVITY_QR" || item.type === "REGISTRATION_QR")
            return (
              <div className="template-qr" key={item.id}>
                <QRCodeSVG
                  value={config.url || joinUrl}
                  size={180}
                  level="M"
                  includeMargin
                />
                <strong>{value || "扫码进入活动"}</strong>
              </div>
            );
          if (item.type === "IMAGE")
            return (
              <img
                className="template-image"
                key={item.id}
                src={config.url}
                alt={config.alt || "大屏图片"}
              />
            );
          if (item.type === "FILE")
            return (
              <a
                className="template-file"
                key={item.id}
                href={config.url || undefined}
                target="_blank"
                rel="noreferrer"
                aria-label={value || config.url || "打开活动文件"}
              >
                <FilePlus2 size={42} />
                <strong>{value || config.url || "活动文件"}</strong>
              </a>
            );
          return (
            <div className="template-text" key={item.id}>
              {value || "现场公告"}
            </div>
          );
        })}
    </div>
  );
}

function ScreenQuestion({ question, state, result, responses = [], volume }) {
  const remaining = useLiveCountdown(state);
  const resultResponses = Array.isArray(responses)
    ? responses
    : responses
      ? [responses]
      : [];
  return (
    <div className="screen-question-new">
      <div className="screen-question-new__meta">
        <span>2025 知识挑战赛 · {typeLabel(question?.type || "SINGLE")}</span>
        <strong>
          {result
            ? "答案已公布"
            : remaining
              ? formatSeconds(remaining)
              : stageLabel(state?.stage || "LOBBY")}
        </strong>
      </div>
      <h1>{question?.title || "现场即将开始"}</h1>
      <ScreenMedia
        src={question?.mediaUrl}
        className="screen-question-media"
        volume={volume}
      />
      <div className="screen-options-new">
        {asOptions(question).map((option, index) => (
          <div
            className={
              result && answerSet(question).has(option) ? "is-correct" : ""
            }
            key={option}
          >
            <b>{String.fromCharCode(65 + index)}</b>
            <span>{option}</span>
            {result && answerSet(question).has(option) && (
              <BadgeCheck size={25} />
            )}
          </div>
        ))}
      </div>
      {result && question?.type === "TEXT" && answerSet(question).size > 0 && (
        <div className="screen-text-answer">
          <span>正确答案</span>
          <div>
            {[...answerSet(question)].map((answer) => (
              <strong key={answer}>{answer}</strong>
            ))}
          </div>
        </div>
      )}
      {result && resultResponses.length > 0 && (
        <div className="screen-result-summary" aria-label="答题结果摘要">
          {resultResponses.map((response, index) => (
            <article key={response.id || response.submissionId || response.participantId || index}>
              <strong>{response.participantName || response.name || `参与者 ${index + 1}`}</strong>
              <span>回答：{formatResponseAnswers(response)}</span>
              <small>{formatResponseDuration(response)} · {formatResponseScore(response)}</small>
            </article>
          ))}
        </div>
      )}
      <footer>
        <span>请在手机端提交答案</span>
        <span>活动数据由服务端实时确认</span>
      </footer>
    </div>
  );
}

function ScreenMedia({ src, className, volume = 100 }) {
  const mediaRef = useRef(null);
  const normalizedVolume =
    Math.max(0, Math.min(100, Number(volume ?? 100))) / 100;
  const source = String(src || "").trim();
  const extension = source.toLowerCase().split(/[?#]/)[0].split(".").pop();
  const isVideo = ["mp4", "m4v", "mov", "webm"].includes(extension);
  const isAudio = ["mp3", "wav", "m4a", "aac", "flac", "oga", "ogg"].includes(
    extension,
  );
  useEffect(() => {
    if (mediaRef.current) mediaRef.current.volume = normalizedVolume;
  }, [normalizedVolume, source]);
  if (!source) return null;
  if (isVideo)
    return (
      <video
        ref={mediaRef}
        className={className}
        src={source}
        autoPlay
        loop
        playsInline
        controls
        aria-label="现场视频素材"
      />
    );
  if (isAudio)
    return (
      <audio
        ref={mediaRef}
        className={`${className} screen-audio`}
        src={source}
        autoPlay
        loop
        controls
        aria-label="现场音频素材"
      />
    );
  return <img className={className} src={source} alt="现场展示素材" />;
}

function ScreenScoreboard({ board, display }) {
  const scrollRef = useRef(null);
  useScreenScroll(scrollRef, display, "scoreboard");
  return (
    <div ref={scrollRef} className="screen-scoreboard-new screen-scrollable">
      <div>
        <span>LIVE SCOREBOARD</span>
        <h1>
          每一次思考
          <br />
          都在改变现场
        </h1>
        <p>实时积分将由已确认的得分流水计算。</p>
      </div>
      <div className="screen-scoreboard-list">
        {board.map((item) => (
          <article key={item.participantId}>
            <b>{item.rank}</b>
            <Avatar name={item.name} />
            <strong>{item.name}</strong>
            <small>{item.venue}</small>
            <em>
              {item.score}
              <i>分</i>
            </em>
          </article>
        ))}
      </div>
    </div>
  );
}
function ScreenWinners({ winners, display }) {
  const scrollRef = useRef(null);
  useScreenScroll(scrollRef, display, "winners");
  return (
    <div ref={scrollRef} className="screen-scoreboard-new screen-scrollable">
      <div>
        <span>AWARD CEREMONY</span>
        <h1>本场获奖名单</h1>
        <p>奖项已由工作人员确认，请获奖参与者前往核销台领取。</p>
      </div>
      <div className="screen-scoreboard-list">
        {winners.map((winner, index) => (
          <article key={`${winner.name}-${winner.prizeName}-${index}`}>
            <b>{String(index + 1).padStart(2, "0")}</b>
            <Avatar name={winner.name} />
            <strong>{winner.name}</strong>
            <small>
              {winner.venue || "活动现场"} · {winner.prizeName}
            </small>
            <em>
              {winner.deliveryType === "PHYSICAL" ? "实物" : "兑换"}
              <i>奖</i>
            </em>
          </article>
        ))}
      </div>
    </div>
  );
}

function useActivityStream(activityId, onEvent, token) {
  useEffect(() => {
    if (!activityId) return undefined;
    let client;
    try {
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      const accessToken = token || getAccessToken();
      client = new Client({
        brokerURL: `${protocol}://${window.location.host}/ws`,
        reconnectDelay: 2500,
        connectHeaders: accessToken
          ? { Authorization: `Bearer ${accessToken}` }
          : {},
        onConnect: () =>
          client.subscribe(`/topic/activities/${activityId}`, (message) => {
            try {
              onEvent(JSON.parse(message.body));
            } catch {
              onEvent({});
            }
          }),
      });
      client.activate();
    } catch {}
    return () => client?.deactivate();
  }, [activityId, onEvent, token]);
}

function useScreenStream(deviceId, onEvent, token) {
  useEffect(() => {
    if (!deviceId || !token) return undefined;
    let client;
    try {
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      client = new Client({
        brokerURL: `${protocol}://${window.location.host}/ws`,
        reconnectDelay: 2500,
        connectHeaders: { Authorization: `Bearer ${token}` },
        onConnect: () =>
          client.subscribe(`/topic/screens/${deviceId}`, () => onEvent()),
      });
      client.activate();
    } catch {}
    return () => client?.deactivate();
  }, [deviceId, onEvent, token]);
}

function useLiveCountdown(state) {
  const seconds = Math.max(0, Math.floor(Number(state?.seconds) || 0));
  const updatedAt = state?.updatedAt;
  const calculate = useCallback(() => {
    if (!seconds) return 0;
    const timestamp = parseTimestamp(updatedAt);
    if (!Number.isFinite(timestamp)) return seconds;
    const elapsed = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
    return Math.max(0, seconds - elapsed);
  }, [seconds, updatedAt]);
  const [remaining, setRemaining] = useState(calculate);
  useEffect(() => {
    setRemaining(calculate());
    if (!seconds || !Number.isFinite(parseTimestamp(updatedAt))) return undefined;
    const timer = window.setInterval(() => {
      const next = calculate();
      setRemaining(next);
      if (next <= 0) window.clearInterval(timer);
    }, 250);
    return () => window.clearInterval(timer);
  }, [calculate, seconds, updatedAt]);
  return remaining;
}

function useScreenScroll(scrollRef, display, contentKey) {
  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return undefined;
    const target = Math.max(0, Number(display?.scrollPosition) || 0);
    container.scrollTop = Math.min(target, container.scrollHeight);
    if (!display?.autoScroll) return undefined;
    const timer = window.setInterval(() => {
      const maxScroll = Math.max(0, container.scrollHeight - container.clientHeight);
      if (!maxScroll) return;
      container.scrollTop = container.scrollTop >= maxScroll ? 0 : Math.min(maxScroll, container.scrollTop + 1);
    }, 80);
    return () => window.clearInterval(timer);
  }, [display?.autoScroll, display?.scrollPosition, contentKey, scrollRef]);
}

function ControlTimer({ state, onRestart }) {
  const remaining = useLiveCountdown(state);
  return (
    <article className="control-timer">
      <p className="eyebrow">QUESTION TIMER</p>
      <strong>{remaining ? formatSeconds(remaining) : "00:00"}</strong>
      <div>
        <i
          style={{
            width: `${Math.min(100, (remaining / Math.max(1, Number(state?.seconds) || 30)) * 100)}%`,
          }}
        />
      </div>
      <button className="secondary-button" onClick={onRestart}>
        <RefreshCw size={16} />
        重置 30 秒
      </button>
    </article>
  );
}
function ScoreList({ items }) {
  return (
    <div className="score-list">
      {items.map((item) => (
        <div key={item.participantId}>
          <span className={`rank rank--${item.rank}`}>{item.rank}</span>
          <Avatar name={item.name} />
          <div>
            <strong>{item.name}</strong>
            <small>{item.venue || "主会场"}</small>
          </div>
          <b>
            {item.score || 0}
            <small>分</small>
          </b>
        </div>
      ))}
      {!items.length && <p className="muted">尚无计分记录</p>}
    </div>
  );
}
function Metric({ icon: Icon, label, value, sub, tone }) {
  return (
    <article className={`metric-card metric-card--${tone}`}>
      <span>
        <Icon size={18} />
      </span>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
        <small>{sub}</small>
      </div>
    </article>
  );
}
function Dialog({ title, onClose, children }) {
  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog" role="dialog" aria-modal="true">
        <header>
          <h2>{title}</h2>
          <button
            className="toolbar-icon"
            type="button"
            aria-label="关闭"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
function EmptyState({ icon: Icon, title, description }) {
  return (
    <div className="empty-state">
      <Icon size={25} />
      <strong>{title}</strong>
      <span>{description}</span>
    </div>
  );
}
function InlineError({ text, onRetry }) {
  return text ? (
    <div className="inline-error">
      <CircleAlert size={17} />
      <span>{text}</span>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          <RefreshCw size={15} />
          重试
        </button>
      )}
    </div>
  ) : null;
}
function BackendProblem({ message, onRetry }) {
  return (
    <main className="backend-problem">
      <div>
        <CircleAlert size={28} />
        <p className="eyebrow">SERVICE UNAVAILABLE</p>
        <h1>无法连接到活动服务</h1>
        <p>{message || "请确认 Spring 服务已启动，并检查网络连接。"}</p>
        <button className="primary-button" onClick={onRetry}>
          <RefreshCw size={17} />
          重新连接
        </button>
      </div>
    </main>
  );
}
function Mark() {
  return (
    <span className="mark">
      <i />
      <i />
      <i />
    </span>
  );
}
function Avatar({ name }) {
  return <span className="avatar">{String(name || "?").slice(0, 1)}</span>;
}
function roleLabel(role) {
  return (
    {
      SYSTEM_ADMIN: "系统管理员",
      ACTIVITY_ADMIN: "活动管理员",
      STAFF: "活动工作人员",
    }[role] ||
    role ||
    "工作人员"
  );
}
function activityTypeLabel(type) {
  return (
    {
      EVENT: "顶层活动",
      QUIZ: "答题活动",
      LOTTERY: "抽奖活动",
      OTHER: "其他活动",
    }[type] || "活动"
  );
}
function stageLabel(stage) {
  return (
    {
      LOBBY: "等待开始",
      QUESTION_OPEN: "答题中",
      ANSWER_REVEALED: "答案已公布",
      SCOREBOARD: "积分榜",
      ENDED: "活动已结束",
    }[stage] || stage
  );
}
function submissionStatusLabel(status) {
  return (
    {
      CORRECT: "正确",
      PARTIAL: "部分正确",
      INCORRECT: "不正确",
      PENDING_REVIEW: "待评分",
      SCORED: "已评分",
    }[status] || status || "已提交"
  );
}
function typeLabel(type) {
  return (
    { SINGLE: "单选题", MULTIPLE: "多选题", TEXT: "文本题" }[type] ||
    type ||
    "题目"
  );
}
function formatSeconds(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const minutes = Math.floor(total / 60);
  return `${String(minutes).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}
function parseTimestamp(value) {
  if (typeof value === "number") return value < 1_000_000_000_000 ? value * 1000 : value;
  if (!value) return Number.NaN;
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? timestamp : Number.NaN;
}
function formatResponseAnswers(response) {
  const value = response?.answers ?? response?.answer ?? response?.response ?? response?.content;
  if (Array.isArray(value)) return value.map((item) => (typeof item === "string" ? item : item?.label || item?.text || String(item))).join("、") || "未记录回答";
  return value == null || value === "" ? "未记录回答" : String(value);
}
function formatResponseDuration(response) {
  let seconds = response?.elapsedSeconds ?? response?.durationSeconds ?? response?.responseTimeSeconds;
  if (seconds == null && response?.elapsedMs != null) seconds = Number(response.elapsedMs) / 1000;
  if (seconds == null) {
    const start = parseTimestamp(response?.openedAt || response?.questionOpenedAt || response?.startedAt);
    const end = parseTimestamp(response?.submittedAt);
    if (Number.isFinite(start) && Number.isFinite(end)) seconds = (end - start) / 1000;
  }
  return Number.isFinite(Number(seconds)) ? `耗时 ${formatSeconds(seconds)}` : "耗时待同步";
}
function formatResponseScore(response) {
  if (response?.status === "PENDING_REVIEW") return "待评分";
  const score = response?.awardedPoints ?? response?.score ?? response?.points;
  return score == null || score === "" ? "0 分" : `${score} 分`;
}
function formatDate(value) {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "未设置";
}
function toDateTimeInput(value) {
  if (!value) return "";
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
function shortId(id) {
  return id ? `ID · ${String(id).slice(0, 8).toUpperCase()}` : "ID · --";
}
function asOptions(question) {
  const options = question?.options || [];
  return Array.isArray(options)
    ? options.map((item) =>
        typeof item === "string"
          ? item
          : item.label || item.text || String(item),
      )
    : String(options).split("|").filter(Boolean);
}
function answerSet(question) {
  const answers = question?.answers || question?.correctAnswers || [];
  return new Set(Array.isArray(answers) ? answers : String(answers).split(","));
}
function textAcceptedAnswers(question) {
  const answers = question?.textAcceptedAnswers || [];
  return Array.isArray(answers)
    ? answers.filter(Boolean)
    : String(answers)
        .split("\n")
        .map((answer) => answer.trim())
        .filter(Boolean);
}
function textMatchLabel(mode) {
  return (
    {
      FUZZY: "自动匹配（模糊）",
      REGEX: "自动匹配（正则）",
      MANUAL: "人工评分",
    }[mode] || "人工评分"
  );
}
function persistParticipant(activityId, participant) {
  sessionStorage.setItem(
    `matrix.participant.${activityId}`,
    JSON.stringify(participant),
  );
}
function readParticipant(activityId) {
  try {
    return JSON.parse(
      sessionStorage.getItem(`matrix.participant.${activityId}`) || "null",
    );
  } catch {
    return null;
  }
}

export default App;
