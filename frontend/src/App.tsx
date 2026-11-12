import { useCallback, useEffect, useState } from 'react'
import { Activity, AlertCircle, ArrowRight, BadgeCheck, Boxes, Check, CheckCircle2, ChevronDown, ClipboardList, Clock3, FileClock, LayoutDashboard, LoaderCircle, Package, PackageCheck, Play, Plus, RefreshCw, Search, ShieldCheck, ShoppingCart, Sparkles, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from './api'
import type { AgentEvent, AgentRun, ApprovalQueueItem, InventoryRow, Product, PurchaseOrder, SupplierOffer, Warehouse } from './types'

type Page = 'Overview' | 'Inventory' | 'AI Purchasing Agent' | 'Purchase Orders' | 'Approvals' | 'Activity / Audit'
const nav: { label: Page; icon: typeof LayoutDashboard }[] = [
  { label: 'Overview', icon: LayoutDashboard }, { label: 'Inventory', icon: Boxes },
  { label: 'AI Purchasing Agent', icon: Sparkles }, { label: 'Purchase Orders', icon: ShoppingCart },
  { label: 'Approvals', icon: ShieldCheck }, { label: 'Activity / Audit', icon: Activity },
]
const pagePaths: Record<Page, string> = {
  Overview: '/overview', Inventory: '/inventory', 'AI Purchasing Agent': '/agent',
  'Purchase Orders': '/purchase-orders', Approvals: '/approvals', 'Activity / Audit': '/activity',
}
const pathPages = Object.fromEntries(Object.entries(pagePaths).map(([label, path]) => [path, label])) as Record<string, Page>
const demoBuyer = 'Demo Buyer'
const dateOnly = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const shortDate = (value?: string | null) => value ? new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—'
const dateTime = (value?: string | null) => value ? new Date(value).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—'
const currency = (value: number, code = 'INR') => new Intl.NumberFormat(undefined, { style: 'currency', currency: code, maximumFractionDigits: 2 }).format(value)
const titleCase = (value?: string | null) => (value || '').toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter: string) => letter.toUpperCase())

function Status({ value }: { value?: string | null }) {
  const tone = value === 'ACCEPT' || value === 'APPROVED' || value === 'SUCCEEDED' || value === 'AVAILABLE' || value === 'COMPLETED' ? 'green'
    : value === 'MODIFY' || value === 'PENDING_APPROVAL' || value === 'IN_PROGRESS' || value === 'BLOCKED' ? 'amber'
      : value === 'REJECT' || value === 'REJECTED' || value === 'FAILED' || value === 'VERIFICATION_FAILED' ? 'red' : 'slate'
  return <span className={`status-badge ${tone}`}><span />{titleCase(value) || 'Not available'}</span>
}

function Button({ children, onClick, variant = 'primary', disabled = false, type = 'button', title }: { children: React.ReactNode; onClick?: () => void; variant?: 'primary' | 'secondary' | 'quiet' | 'danger'; disabled?: boolean; type?: 'button' | 'submit'; title?: string }) {
  return <button className={`button ${variant}`} onClick={onClick} disabled={disabled} type={type} title={title}>{children}</button>
}

function Empty({ icon: Icon, title, detail, action }: { icon: typeof Package; title: string; detail: string; action?: React.ReactNode }) {
  return <div className="empty-state"><span className="empty-icon"><Icon size={20} /></span><strong>{title}</strong><p>{detail}</p>{action}</div>
}

function ErrorBox({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="error-banner" role="alert"><AlertCircle size={18} /><span>{message}</span>{retry && <button onClick={retry} className="text-button">Try again</button>}</div>
}

function Spinner({ label = 'Loading workspace data…' }: { label?: string }) {
  return <div className="loading-state"><LoaderCircle size={22} className="spin" /><span>{label}</span></div>
}

export default function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const page = pathPages[location.pathname] || 'Overview'
  const setPage = (nextPage: Page) => navigate(pagePaths[nextPage])
  const [products, setProducts] = useState<Product[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [inventory, setInventory] = useState<InventoryRow[]>([])
  const [runs, setRuns] = useState<AgentRun[]>([])
  const [approvals, setApprovals] = useState<ApprovalQueueItem[]>([])
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [health, setHealth] = useState('Checking')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const refresh = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [h, p, w, i, r, a, po] = await Promise.all([api.health(), api.products(), api.warehouses(), api.inventory(), api.runs(), api.approvals(), api.purchaseOrders()])
      setHealth(h.status); setProducts(p); setWarehouses(w); setInventory(i); setRuns(r); setApprovals(a); setOrders(po)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to load ProcureAI data'); setHealth('Unavailable') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => { if (location.pathname === '/') navigate('/overview', { replace: true }) }, [location.pathname, navigate])
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(''), 4000); return () => window.clearTimeout(timer) }, [toast])

  const startInvestigation = async (input: Parameters<typeof api.startRun>[0]) => {
    const run = await api.startRun(input)
    await refresh()
    setPage('AI Purchasing Agent')
    setToast(`Investigation ${run.status === 'FAILED' ? 'finished with an error' : 'completed'}`)
    return run
  }
  const pageProps = { products, warehouses, inventory, runs, approvals, orders, refresh, setPage, setToast, startInvestigation, loading }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand-lockup"><span className="brand-mark"><Boxes size={19} /></span><span>Procure<span>AI</span></span></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="Main navigation">{nav.map(({ label, icon: Icon }) => <button key={label} onClick={() => setPage(label)} className={`nav-link ${page === label ? 'active' : ''}`} aria-current={page === label ? 'page' : undefined}><Icon size={18} /><span>{label}</span>{label === 'Approvals' && approvals.filter((item) => !item.approval && ['ACCEPT', 'MODIFY'].includes(item.run.decision || '')).length > 0 && <small>{approvals.filter((item) => !item.approval && ['ACCEPT', 'MODIFY'].includes(item.run.decision || '')).length}</small>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-env"><span className={health === 'UP' ? 'online-dot' : 'offline-dot'} /><span>Demo environment</span><span className="env-status">{health}</span></div><div className="sidebar-user"><div className="avatar">DB</div><div><strong>{demoBuyer}</strong><span>Buyer workspace</span></div><ChevronDown size={15} /></div></div>
    </aside>
    <div className="main-shell">
      <header className="topbar"><div className="mobile-brand"><span className="brand-mark"><Boxes size={17} /></span>ProcureAI</div><div className="breadcrumbs"><span>ProcureAI</span><span>/</span><strong>{page}</strong></div><div className="top-actions"><span className="demo-chip"><span />DEMO</span><Button variant="quiet" onClick={() => void refresh()} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''} /> Refresh</Button></div></header>
      <main className="page-content">
        <div className="page-heading"><div><div className="eyebrow">PURCHASING OPERATIONS</div><h1>{page}</h1><p>{pageSubtitle(page)}</p></div>{page === 'AI Purchasing Agent' && <Button onClick={() => document.getElementById('investigation-form')?.scrollIntoView({ behavior: 'smooth' })}><Plus size={16} /> New investigation</Button>}</div>
        {error && <ErrorBox message={error} retry={() => void refresh()} />}
        {loading && !products.length && !error ? <Spinner /> : <>
          {page === 'Overview' && <Overview {...pageProps} />}
          {page === 'Inventory' && <InventoryPage {...pageProps} />}
          {page === 'AI Purchasing Agent' && <AgentPage {...pageProps} />}
          {page === 'Approvals' && <ApprovalsPage {...pageProps} />}
          {page === 'Purchase Orders' && <PurchaseOrdersPage {...pageProps} />}
          {page === 'Activity / Audit' && <ActivityPage {...pageProps} />}
        </>}
      </main>
      <footer className="app-footer"><span>ProcureAI <i>·</i> Purchasing intelligence</span><span>Human approval required for every purchase</span></footer>
    </div>
    {toast && <div className="toast" role="status"><CheckCircle2 size={17} />{toast}<button onClick={() => setToast('')} aria-label="Dismiss notification"><X size={15} /></button></div>}
  </div>
}

function pageSubtitle(page: Page) {
  const copy: Record<Page, string> = {
    Overview: 'A live view of inventory, investigations, and purchasing activity.',
    Inventory: 'On-hand and available quantities by product and warehouse.',
    'AI Purchasing Agent': 'Investigate demand and supply evidence before making a purchasing decision.',
    'Purchase Orders': 'Internal purchasing records and persisted order details.',
    Approvals: 'Review recommendations and explicitly authorize any purchase execution.',
    'Activity / Audit': 'A chronological record of persisted agent runs and their events.',
  }
  return copy[page]
}

type ViewProps = { products: Product[]; warehouses: Warehouse[]; inventory: InventoryRow[]; runs: AgentRun[]; approvals: ApprovalQueueItem[]; orders: PurchaseOrder[]; refresh: () => Promise<void>; setPage: (page: Page) => void; setToast: (value: string) => void; startInvestigation: (input: Parameters<typeof api.startRun>[0]) => Promise<AgentRun>; loading: boolean }

function Overview(props: ViewProps) {
  const pending = props.approvals.filter((a) => !a.approval && ['ACCEPT', 'MODIFY'].includes(a.run.decision || '')).length
  const activePOs = props.orders.filter((order) => !['RECEIVED', 'CANCELLED', 'FAILED'].includes(order.status)).length
  const available = props.inventory.reduce((sum, row) => sum + row.availableUnits, 0)
  const mouse = props.products.find((product) => product.sku === 'WM-001')
  const delhi = props.warehouses.find((warehouse) => warehouse.code === 'DEL-01')
  return <>
    <div className="welcome-strip"><div className="welcome-copy"><div className="welcome-icon"><Sparkles size={19} /></div><div><span>SCENARIO 1 · READY TO INVESTIGATE</span><strong>{mouse?.name ?? 'Wireless Mouse'} <em>at</em> {delhi?.name ?? 'Delhi Fulfillment Center'}</strong><small>Review the seeded 800-unit recommendation against current purchasing evidence.</small></div></div><Button onClick={() => props.setPage('AI Purchasing Agent')}>Start investigation <ArrowRight size={16} /></Button></div>
    <div className="stat-grid">
      <StatCard icon={Package} label="Catalog products" value={props.products.length} detail="Current product records" tone="blue" />
      <StatCard icon={Boxes} label="Available inventory" value={available.toLocaleString()} detail="Units across warehouses" tone="green" />
      <StatCard icon={ShieldCheck} label="Awaiting review" value={pending} detail="Actionable recommendations" tone="amber" />
      <StatCard icon={ShoppingCart} label="Active purchase orders" value={activePOs} detail="Not received, cancelled, or failed" tone="violet" />
    </div>
    <div className="content-grid overview-grid">
      <section className="panel"><PanelHeader title="Recent investigations" detail="Latest persisted agent runs" action={<Button variant="quiet" onClick={() => props.setPage('Activity / Audit')}>View activity <ArrowRight size={14} /></Button>} />
        {props.runs.length ? <div className="run-list">{props.runs.slice(0, 5).map((run) => <RunRow key={run.runId} run={run} onClick={() => props.setPage('AI Purchasing Agent')} />)}</div> : <Empty icon={Sparkles} title="No investigations yet" detail="Start with the seeded Wireless Mouse scenario." action={<Button onClick={() => props.setPage('AI Purchasing Agent')}>Start an investigation</Button>} />}
      </section>
      <section className="panel"><PanelHeader title="Recent purchase orders" detail="Internal records · not supplier transmissions" action={<Button variant="quiet" onClick={() => props.setPage('Purchase Orders')}>View all <ArrowRight size={14} /></Button>} />
        {props.orders.length ? <div className="compact-orders">{props.orders.slice(0, 5).map((order) => <div className="compact-order" key={order.id}><span className="po-icon"><ShoppingCart size={16} /></span><div className="compact-order-copy"><strong>{order.poNumber}</strong><span>{order.supplierName} · {order.warehouseCode}</span></div><Status value={order.status} /></div>)}</div> : <Empty icon={ShoppingCart} title="No purchase orders" detail="Orders appear here after an explicitly approved execution." />}
      </section>
    </div>
    <section className="panel activity-panel"><PanelHeader title="Latest purchasing activity" detail="Recent decisions from persisted agent runs" />{props.runs.length ? <div className="activity-strip">{props.runs.slice(0, 4).map((run) => <div key={run.runId} className="activity-card"><span className="activity-card-icon"><FileClock size={16} /></span><div><strong>{run.decision ? `${titleCase(run.decision)} recommendation` : titleCase(run.status)}</strong><span>{run.productSku} · {run.warehouseCode}</span><small>{dateTime(run.startedAt)}</small></div><Status value={run.status} /></div>)}</div> : <p className="muted-note">Persisted activity will appear after an investigation is run.</p>}</section>
  </>
}

function StatCard({ icon: Icon, label, value, detail, tone }: { icon: typeof Package; label: string; value: number | string; detail: string; tone: string }) {
  return <div className="stat-card"><span className={`stat-icon ${tone}`}><Icon size={18} /></span><div><span>{label}</span><strong>{value}</strong><small>{detail}</small></div></div>
}
function PanelHeader({ title, detail, action }: { title: string; detail?: string; action?: React.ReactNode }) { return <div className="panel-header"><div><h2>{title}</h2>{detail && <p>{detail}</p>}</div>{action}</div> }
function RunRow({ run, onClick }: { run: AgentRun; onClick?: () => void }) {
  return <button className="run-row" onClick={onClick}><span className="run-row-icon"><Sparkles size={17} /></span><span className="run-row-main"><strong>{run.recommendation?.recommendationSummary || run.recommendationSummary}</strong><small>{run.productSku} <i>·</i> {run.warehouseCode} <i>·</i> {dateTime(run.startedAt)}</small></span>{run.decision && <Status value={run.decision} />}<ArrowRight size={15} className="row-arrow" /></button>
}

function InventoryPage({ inventory }: ViewProps) {
  const [search, setSearch] = useState(''); const [warehouse, setWarehouse] = useState('All warehouses')
  const warehouses = [...new Set(inventory.map((row) => row.warehouseCode))]
  const rows = inventory.filter((row) => (warehouse === 'All warehouses' || row.warehouseCode === warehouse) && `${row.productSku} ${row.productName} ${row.warehouseName}`.toLowerCase().includes(search.toLowerCase()))
  return <section className="panel"><div className="toolbar"><label className="search-box"><Search size={16} /><input placeholder="Search product or SKU" value={search} onChange={(event) => setSearch(event.target.value)} /></label><select value={warehouse} onChange={(event) => setWarehouse(event.target.value)}><option>All warehouses</option>{warehouses.map((code) => <option key={code}>{code}</option>)}</select><span className="result-count">{rows.length} records</span></div>
    {rows.length ? <div className="table-wrap"><table><thead><tr><th>Product</th><th>Warehouse</th><th>On hand</th><th>Reserved</th><th>Available</th><th>Last counted</th><th>Data quality</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td><strong>{row.productName}</strong><small className="cell-sub">{row.productSku} · {row.unitOfMeasure}</small></td><td><strong>{row.warehouseName}</strong><small className="cell-sub">{row.warehouseCode}</small></td><td>{row.onHandUnits.toLocaleString()}</td><td>{row.reservedUnits.toLocaleString()}</td><td><strong className="available-number">{row.availableUnits.toLocaleString()}</strong></td><td>{dateTime(row.lastCountedAt)}</td><td>{row.availableUnits < 0 ? <Status value="INVESTIGATE" /> : <span className="freshness"><span /> Recorded</span>}</td></tr>)}</tbody></table></div> : <Empty icon={Boxes} title="No inventory matches" detail="Try a different search or warehouse filter." />}
    <div className="panel-footnote"><AlertCircle size={14} /> Available is shown separately from on-hand and is calculated by the backend as on-hand less reserved.</div>
  </section>
}

function AgentPage(props: ViewProps) {
  const [selectedRunId, setSelectedRunId] = useState(''); const [events, setEvents] = useState<AgentEvent[]>([]); const [eventError, setEventError] = useState(''); const [run, setRun] = useState<AgentRun | null>(null)
  const [submitting, setSubmitting] = useState(false); const [loadRun, setLoadRun] = useState(false)
  const [productSku, setProductSku] = useState('WM-001'); const [warehouseCode, setWarehouseCode] = useState('DEL-01'); const [quantity, setQuantity] = useState('800'); const [horizon, setHorizon] = useState('30'); const [instruction, setInstruction] = useState('')
  const load = async (id: string) => { setSelectedRunId(id); setLoadRun(true); setEventError(''); try { const [r, e] = await Promise.all([api.run(id), api.events(id)]); setRun(r); setEvents(e) } catch (e) { setEventError(e instanceof Error ? e.message : 'Could not load this investigation') } finally { setLoadRun(false) } }
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setSubmitting(true); setRun(null); setEvents([]); setEventError('')
    const start = new Date(); start.setDate(start.getDate() + 1); const end = new Date(start); end.setDate(end.getDate() + Math.max(0, Number(horizon) - 1))
    try { const result = await props.startInvestigation({ productSku, warehouseCode, recommendedPurchaseUnits: Number(quantity), planningStartDate: dateOnly(start), planningEndDate: dateOnly(end), userInstruction: instruction.trim() || undefined }); setRun(result); setSelectedRunId(result.runId); try { setEvents(await api.events(result.runId)) } catch (reason) { setEventError(reason instanceof Error ? reason.message : 'Could not load investigation events') } }
    catch (reason) { setEventError(reason instanceof Error ? reason.message : 'The investigation could not be started') } finally { setSubmitting(false) }
  }
  const recommendation = run?.recommendation ?? null
  return <div className="content-grid agent-grid"><div className="agent-left">
    <section className="panel" id="investigation-form"><PanelHeader title="Start an investigation" detail="The recommendation quantity is a starting input, not a purchase instruction." />
      <form className="form-grid" onSubmit={(event) => void submit(event)}>
        <label>Product<select value={productSku} onChange={(event) => setProductSku(event.target.value)} required>{props.products.filter((p) => p.active).map((p) => <option key={p.id} value={p.sku}>{p.name} · {p.sku}</option>)}</select></label>
        <label>Warehouse<select value={warehouseCode} onChange={(event) => setWarehouseCode(event.target.value)} required>{props.warehouses.filter((w) => w.active).map((w) => <option key={w.id} value={w.code}>{w.name} · {w.code}</option>)}</select></label>
        <label>Original recommended quantity<input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} required /><small>Units of measure come from the selected product.</small></label>
        <label>Planning horizon<select value={horizon} onChange={(event) => setHorizon(event.target.value)}><option value="7">Next 7 days</option><option value="14">Next 14 days</option><option value="30">Next 30 days</option><option value="60">Next 60 days</option></select><small>Demand starts tomorrow.</small></label>
        <label className="wide-label">Optional purchasing instructions<textarea value={instruction} maxLength={1500} onChange={(event) => setInstruction(event.target.value)} placeholder="For example: prioritize a shorter lead time if cost is comparable." rows={3} /></label>
        {eventError && !run && <div className="form-message error-banner"><AlertCircle size={17} />{eventError}</div>}
        <div className="form-submit"><span><Sparkles size={15} /> Dynamic evidence gathering · no automatic purchasing</span><Button type="submit" disabled={submitting || !props.products.length || !props.warehouses.length}>{submitting ? <><LoaderCircle className="spin" size={16} /> Investigating…</> : <><Play size={15} /> Start investigation</>}</Button></div>
      </form>
    </section>
    <section className="panel"><PanelHeader title="Investigation result" detail="Recommendation and supporting business evidence" action={run && <Status value={run.status} />} />
      {loadRun ? <Spinner label="Loading persisted investigation…" /> : run ? <RecommendationView run={run} recommendation={recommendation} /> : <Empty icon={Sparkles} title="No investigation selected" detail="Start an investigation or select one of the recent runs below." />}
      {eventError && run && <ErrorBox message={eventError} />}
    </section>
    <section className="panel"><PanelHeader title="Recent runs" detail="Select a persisted run to inspect its recommendation and event history" />{props.runs.length ? <div className="run-list">{props.runs.slice(0, 8).map((item) => <button key={item.runId} className={`run-row ${selectedRunId === item.runId ? 'selected' : ''}`} onClick={() => void load(item.runId)}><span className="run-row-icon"><Sparkles size={16} /></span><span className="run-row-main"><strong>{item.productSku} · {item.warehouseCode}</strong><small>{dateTime(item.startedAt)}</small></span><Status value={item.decision || item.status} /><ArrowRight size={15} /></button>)}</div> : <p className="muted-note">No persisted runs yet.</p>}</section>
  </div><section className="panel timeline-panel"><PanelHeader title="Investigation timeline" detail={events.length ? `${events.length} persisted events in sequence` : 'Actual audit events from the selected run'} />{events.length ? <EventTimeline events={events} /> : <Empty icon={Clock3} title="Timeline is empty" detail="Start an investigation to view the persisted activity and evidence trail." />}</section></div>
}

function RecommendationView({ run, recommendation }: { run: AgentRun; recommendation: AgentRun['recommendation'] }) {
  if (!recommendation) return <div className="recommendation-empty"><Status value={run.status} /><h3>{run.recommendationSummary || 'No structured recommendation was returned.'}</h3><p>The backend did not persist a valid structured recommendation for this run.</p></div>
  return <div className="recommendation-view"><div className="decision-banner"><div><span>AI RECOMMENDATION</span><h3>{titleCase(recommendation.decision)}</h3></div><Status value={recommendation.decision} /></div><p className="recommendation-summary">{recommendation.recommendationSummary}</p>
    <div className="quantity-pair"><div><span>Original input</span><strong>{recommendation.originalRecommendedQuantity?.toLocaleString()} <small>units</small></strong></div><ArrowRight size={17} /><div><span>Proposed quantity</span><strong>{recommendation.proposedQuantity == null ? '—' : recommendation.proposedQuantity.toLocaleString()} <small>units</small></strong></div>{recommendation.confidence != null && <div className="confidence"><span>Confidence</span><strong>{Math.round(recommendation.confidence * 100)}%</strong></div>}</div>
    {recommendation.keySupportingFacts?.length > 0 && <EvidenceList title="Supporting evidence" items={recommendation.keySupportingFacts} icon={CheckCircle2} />}
    <div className="two-lists">{recommendation.risks?.length > 0 && <EvidenceList title="Risks" items={recommendation.risks} icon={AlertCircle} warning />}{recommendation.unresolvedQuestions?.length > 0 && <EvidenceList title="Open questions" items={recommendation.unresolvedQuestions} icon={FileClock} warning />}</div>
    {recommendation.constraintEvaluationReferences?.length > 0 && <EvidenceList title="Constraint evaluation" items={recommendation.constraintEvaluationReferences} icon={ShieldCheck} />}
    {recommendation.evidenceReferences?.length > 0 && <EvidenceList title="Evidence references" items={recommendation.evidenceReferences} icon={ClipboardList} />}
    <div className="human-gate"><ShieldCheck size={17} /><span>This is an unapproved recommendation. Review and explicitly approve it before any separate execution request.</span></div>
  </div>
}
function EvidenceList({ title, items, icon: Icon, warning = false }: { title: string; items: string[]; icon: typeof CheckCircle2; warning?: boolean }) { return <div className="evidence-list"><h4>{title}</h4><ul>{items.map((item, i) => <li key={`${title}-${i}`} className={warning ? 'warn' : ''}><Icon size={14} />{item}</li>)}</ul></div> }
function EventTimeline({ events }: { events: AgentEvent[] }) {
  const [expanded, setExpanded] = useState<number | null>(null)
  return <ol className="timeline">{events.map((event) => <li key={event.sequenceNumber} className="timeline-item"><span className="timeline-marker">{event.eventType.includes('FAILED') || event.eventType.includes('ERROR') ? <AlertCircle size={13} /> : event.eventType.includes('DECISION') ? <Sparkles size={13} /> : <Check size={13} />}</span><div className="timeline-content"><button className="timeline-toggle" onClick={() => setExpanded(expanded === event.sequenceNumber ? null : event.sequenceNumber)} aria-expanded={expanded === event.sequenceNumber}><span><strong>{titleCase(event.eventType)}</strong><small>{event.toolName ? `Tool · ${event.toolName} · ` : ''}{dateTime(event.occurredAt)}</small>{event.summary && <p>{event.summary}</p>}</span><ChevronDown size={15} className={expanded === event.sequenceNumber ? 'rotated' : ''} /></button>{expanded === event.sequenceNumber && <div className="event-details">{event.argumentsJson && <JsonDetail title="Arguments" value={event.argumentsJson} />}{event.resultJson && <JsonDetail title="Result" value={event.resultJson} />}</div>}</div></li>)}</ol>
}
function JsonDetail({ title, value }: { title: string; value: string }) { let pretty = value; try { pretty = JSON.stringify(JSON.parse(value), null, 2) } catch { /* Keep persisted text as-is. */ } return <details><summary>{title}</summary><pre>{pretty}</pre></details> }

function ApprovalsPage(props: ViewProps) {
  const [selected, setSelected] = useState<string | null>(null); const [offers, setOffers] = useState<SupplierOffer[]>([]); const [offerLoading, setOfferLoading] = useState(false); const [offerError, setOfferError] = useState(''); const [quantity, setQuantity] = useState(''); const [offerId, setOfferId] = useState(''); const [approver, setApprover] = useState(demoBuyer); const [reason, setReason] = useState(''); const [working, setWorking] = useState(false); const [error, setError] = useState('')
  const selectedItem = props.approvals.find((item) => item.run.runId === selected)
  const reviewable = props.approvals.filter((item) => !item.approval && item.run.recommendation && ['COMPLETED', 'NEEDS_INPUT'].includes(item.run.status))
  const setReview = async (item: ApprovalQueueItem) => { setSelected(item.run.runId); setError(''); setReason(''); setQuantity(String(item.run.proposedQuantity ?? '')); setOfferId(String(item.run.recommendation?.selectedSupplierProductId ?? '')); const canApprove = item.run.status === 'COMPLETED' && ['ACCEPT', 'MODIFY'].includes(item.run.decision || ''); setOfferLoading(canApprove); setOfferError(''); if (!canApprove) { setOffers([]); return } try { const result = await api.offers(item.run.productSku, item.run.warehouseCode); setOffers(result); if (!item.run.recommendation?.selectedSupplierProductId) setOfferId(String(result.find((offer) => offer.status === 'AVAILABLE')?.id ?? '')) } catch (e) { setOffers([]); setOfferError(e instanceof Error ? e.message : 'Could not load supplier offers') } finally { setOfferLoading(false) } }
  const afterDecision = async (message: string) => { await props.refresh(); setSelected(null); props.setToast(message) }
  const approve = async (event: React.FormEvent) => { event.preventDefault(); if (!selectedItem) return; if (!window.confirm(`Approve ${Number(quantity).toLocaleString()} units from the selected supplier offer? This will not execute or transmit a purchase order.`)) return; setWorking(true); setError(''); try { await api.approve(selectedItem.run.runId, Number(quantity), Number(offerId), approver.trim()); await afterDecision('Human approval recorded. No purchase order has been created.') } catch (e) { setError(e instanceof Error ? e.message : 'Approval could not be recorded') } finally { setWorking(false) } }
  const reject = async () => { if (!selectedItem || !reason.trim() || !window.confirm('Reject this recommendation? This decision cannot be changed for this AgentRun.')) return; setWorking(true); setError(''); try { await api.reject(selectedItem.run.runId, approver.trim(), reason.trim()); await afterDecision('Recommendation rejected.') } catch (e) { setError(e instanceof Error ? e.message : 'Rejection could not be recorded') } finally { setWorking(false) } }
  const execute = async (item: ApprovalQueueItem) => { if (!item.approval || !window.confirm(`Execute approved purchase ${item.approval.approvedQuantity?.toLocaleString()} units? ProcureAI will revalidate current evidence and create an internal PO if it still passes.`)) return; setWorking(true); setError(''); try { const result = await api.execute(item.approval.approvalId); await props.refresh(); props.setToast(result.executionStatus === 'SUCCEEDED' ? 'Purchase order created and read-back verified.' : `Execution ${titleCase(result.executionStatus)}.`) } catch (e) { setError(e instanceof Error ? e.message : 'Execution request failed') } finally { setWorking(false) } }
  return <div className="approval-layout"><div className="approval-main"><section className="panel"><PanelHeader title="Recommendations awaiting review" detail="Only completed ACCEPT or MODIFY runs can be approved; any finished recommendation may be rejected" action={<span className="count-chip">{reviewable.length} to review</span>} />{reviewable.length ? <div className="approval-list">{reviewable.map((item) => <div className={`approval-card ${selected === item.run.runId ? 'selected' : ''}`} key={item.run.runId}><div className="approval-card-top"><span className="approval-card-icon"><ClipboardList size={17} /></span><div className="approval-card-title"><strong>{item.run.recommendation?.recommendationSummary || item.run.recommendationSummary}</strong><span>{item.run.productSku} · {item.run.warehouseCode} · {dateTime(item.run.completedAt || item.run.startedAt)}</span></div><Status value={item.run.decision} /></div><div className="approval-card-bottom"><span>Proposed <b>{item.run.proposedQuantity?.toLocaleString() ?? '—'} units</b></span><Button variant={selected === item.run.runId ? 'secondary' : 'primary'} onClick={() => void setReview(item)}>{selected === item.run.runId ? 'Reviewing' : 'Review recommendation'} <ArrowRight size={14} /></Button></div></div>)}</div> : <Empty icon={BadgeCheck} title="No recommendations awaiting review" detail="Finished recommendations without a human decision will appear here." />}</section>
      <section className="panel"><PanelHeader title="Decision history" detail="Recorded human approvals, rejections, and execution results" />{props.approvals.filter((item) => item.approval).length ? <div className="decision-history">{props.approvals.filter((item) => item.approval).map((item) => <div className="history-row" key={item.approval!.approvalId}><span className="history-icon"><ShieldCheck size={17} /></span><div className="history-copy"><strong>{item.run.productSku} · {item.run.warehouseCode}</strong><small>{item.approval!.approverIdentity} · {dateTime(item.approval!.decisionAt)}</small>{item.approval!.executionSummary && <p>{item.approval!.executionSummary}</p>}{item.approval!.rejectionReason && <p>Reason: {item.approval!.rejectionReason}</p>}</div><div className="history-status"><Status value={item.approval!.approvalStatus} /><Status value={item.approval!.executionStatus} />{item.approval!.purchaseOrderNumber && <span className="po-ref">{item.approval!.purchaseOrderNumber}</span>}</div>{item.approval!.approvalStatus === 'APPROVED' && item.approval!.executionStatus !== 'SUCCEEDED' && item.approval!.executionStatus !== 'BLOCKED' && <Button disabled={working} onClick={() => void execute(item)}><Play size={14} /> Execute purchase</Button>}{item.approval!.executionStatus === 'BLOCKED' && <Button variant="secondary" disabled title="Evidence changed. A new AgentRun and approval are required.">Revalidation blocked</Button>}</div>)}</div> : <p className="muted-note">No approval decisions have been recorded yet.</p>}</section>
      <div className="identity-notice"><AlertCircle size={16} /><span><strong>Demo identity:</strong> the approver name is supplied by this client and is recorded for audit display only. It is not authenticated.</span></div>
      {error && <ErrorBox message={error} />}
    </div>
    <aside className="panel review-panel"><PanelHeader title="Review recommendation" detail={selectedItem ? `${selectedItem.run.productSku} · ${selectedItem.run.warehouseCode}` : 'Select an actionable recommendation'} />{selectedItem ? <>
      <RecommendationView run={selectedItem.run} recommendation={selectedItem.run.recommendation} />{offerLoading ? <Spinner label="Loading current supplier offers…" /> : offerError ? <ErrorBox message={offerError} /> : <form className="review-form" onSubmit={(event) => void approve(event)}>
        {selectedItem.run.status === 'COMPLETED' && ['ACCEPT', 'MODIFY'].includes(selectedItem.run.decision || '') ? <>
          <label>Approved quantity<input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} required /><small>Must match the validated recommendation quantity.</small></label>
          <label>Supplier offer<select value={offerId} onChange={(event) => setOfferId(event.target.value)} required><option value="">Choose supplier offer</option>{offers.map((offer) => <option key={offer.id} value={offer.id} disabled={offer.status !== 'AVAILABLE'}>{offer.supplierName} · {currency(offer.unitPrice, offer.currencyCode)} / unit · {titleCase(offer.status)}</option>)}</select></label>
          {offerId && (() => { const offer = offers.find((o) => o.id === Number(offerId)); return offer ? <div className="offer-summary"><strong>{offer.supplierCode}</strong><span>MOQ {offer.minimumOrderQuantity} · Pack {offer.packSize} · {offer.leadTimeDays}-day lead time</span></div> : null })()}
        </> : <div className="notice-box"><AlertCircle size={15} /> This recommendation cannot be approved. You may record a rejection with a reason.</div>}
        <label>Approver identity<input value={approver} maxLength={120} onChange={(event) => setApprover(event.target.value)} required /></label>
        {error && <ErrorBox message={error} />}
        <label>Rejection reason (required to reject)<input aria-label="Rejection reason" placeholder="Add a reason before rejecting" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} /></label>
        <div className="review-actions">{selectedItem.run.status === 'COMPLETED' && ['ACCEPT', 'MODIFY'].includes(selectedItem.run.decision || '') && <Button type="submit" disabled={working || offerLoading || !offerId || !approver.trim()}><ShieldCheck size={15} /> Approve plan</Button>}<Button variant="danger" onClick={() => void reject()} disabled={working || !reason.trim() || !approver.trim()}><X size={15} /> Reject</Button></div>
        {selectedItem.run.status === 'COMPLETED' && ['ACCEPT', 'MODIFY'].includes(selectedItem.run.decision || '') && <p className="separate-action"><ShieldCheck size={14} /> Approval only records a decision. Execution remains a separate action.</p>}
      </form>}</> : <Empty icon={ShieldCheck} title="Nothing selected" detail="Choose a recommendation to review the approved quantity and supplier offer." />}</aside>
  </div>
}

function PurchaseOrdersPage({ orders }: ViewProps) {
  const [search, setSearch] = useState(''); const [status, setStatus] = useState('All statuses'); const [expanded, setExpanded] = useState<number | null>(null)
  const statuses = [...new Set(orders.map((order) => order.status))]
  const list = orders.filter((order) => (status === 'All statuses' || order.status === status) && `${order.poNumber} ${order.supplierName} ${order.warehouseCode}`.toLowerCase().includes(search.toLowerCase()))
  return <section className="panel"><div className="toolbar"><label className="search-box"><Search size={16} /><input placeholder="Search PO, supplier, or warehouse" value={search} onChange={(event) => setSearch(event.target.value)} /></label><select value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option>{statuses.map((value) => <option key={value}>{value}</option>)}</select><span className="result-count">{list.length} orders</span></div>
    {list.length ? <div className="table-wrap"><table className="po-table"><thead><tr><th>Purchase order</th><th>Supplier</th><th>Warehouse</th><th>Order date</th><th>Status</th><th>Currency</th><th>Items</th></tr></thead><tbody>{list.map((order) => <POExpandable key={order.id} order={order} expanded={expanded === order.id} onToggle={() => setExpanded(expanded === order.id ? null : order.id)} />)}</tbody></table></div> : <Empty icon={ShoppingCart} title="No purchase orders found" detail="Orders will appear here after the buyer explicitly executes an approved plan." />}
    <div className="internal-order-note"><PackageCheck size={16} /><span>Purchase orders shown here are internal persisted records. This demo does not transmit orders to suppliers or record supplier acknowledgement.</span></div>
  </section>
}
function POExpandable({ order, expanded, onToggle }: { order: PurchaseOrder; expanded: boolean; onToggle: () => void }) {
  const [details, setDetails] = useState<PurchaseOrder | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const toggle = async () => {
    onToggle()
    if (expanded || details) return
    setLoading(true); setError('')
    try { setDetails(await api.purchaseOrder(order.id)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Purchase order details could not be loaded') }
    finally { setLoading(false) }
  }
  const detailOrder = details || order
  return <><tr className="po-row" onClick={() => void toggle()} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') void toggle() }}><td><strong>{order.poNumber}</strong><small className="cell-sub">PO #{order.id}</small></td><td>{order.supplierName}<small className="cell-sub">{order.supplierCode}</small></td><td>{order.warehouseName}<small className="cell-sub">{order.warehouseCode}</small></td><td>{shortDate(order.orderDate)}</td><td><Status value={order.status} /></td><td>{order.currencyCode}</td><td>{order.items.length} <ChevronDown size={14} className={expanded ? 'rotated' : ''} /></td></tr>{expanded && <tr><td colSpan={7} className="line-detail-cell">{loading ? <Spinner label="Loading persisted order details…" /> : error ? <div className="line-detail"><ErrorBox message={error} /></div> : <div className="line-detail"><div className="line-detail-head"><strong>Order line items</strong><span>{detailOrder.approvedBy ? `Approved by ${detailOrder.approvedBy}` : 'Buyer approval not recorded'}</span></div><table><thead><tr><th>Product</th><th>Ordered</th><th>Confirmed</th><th>Received</th><th>Unit price</th><th>Expected delivery</th></tr></thead><tbody>{detailOrder.items.map((item) => <tr key={item.id}><td><strong>{item.productName}</strong><small className="cell-sub">{item.productSku} · {item.unitOfMeasure}</small></td><td>{item.orderedUnits}</td><td>{item.confirmedUnits ?? 'Not confirmed'}</td><td>{item.receivedUnits}</td><td>{currency(item.unitPriceAtOrder, detailOrder.currencyCode)}</td><td>{shortDate(item.expectedDeliveryDate)}</td></tr>)}</tbody></table></div>}</td></tr>}</>
}

function ActivityPage({ runs }: ViewProps) {
  const [eventsByRun, setEventsByRun] = useState<Record<string, AgentEvent[]>>({}); const [loadingRun, setLoadingRun] = useState(''); const [error, setError] = useState('')
  const selectRun = async (run: AgentRun) => { if (eventsByRun[run.runId]) { setEventsByRun((current) => ({ ...current, [run.runId]: [] })); return } setLoadingRun(run.runId); setError(''); try { const events = await api.events(run.runId); setEventsByRun((current) => ({ ...current, [run.runId]: events })) } catch (e) { setError(e instanceof Error ? e.message : 'Could not load audit events') } finally { setLoadingRun('') } }
  return <div className="audit-page"><div className="audit-intro"><span className="audit-intro-icon"><Activity size={19} /></span><div><strong>Persisted activity only</strong><p>Event details come from the AgentRun audit records. Hidden model reasoning is not displayed.</p></div><span className="count-chip">{runs.length} runs</span></div>{error && <ErrorBox message={error} />}{runs.length ? <div className="audit-runs">{runs.map((run) => <section className="panel audit-run" key={run.runId}><button className="audit-run-header" onClick={() => void selectRun(run)} aria-expanded={!!eventsByRun[run.runId]}><span className="run-row-icon"><Sparkles size={16} /></span><span><strong>{run.productSku} · {run.warehouseCode}</strong><small>{dateTime(run.startedAt)} · {run.recommendation?.recommendationSummary || run.recommendationSummary}</small></span><Status value={run.decision || run.status} />{loadingRun === run.runId ? <LoaderCircle size={16} className="spin" /> : <ChevronDown size={16} className={eventsByRun[run.runId]?.length ? 'rotated' : ''} />}</button>{eventsByRun[run.runId]?.length ? <EventTimeline events={eventsByRun[run.runId]} /> : eventsByRun[run.runId] ? <p className="muted-note">No persisted events were returned for this run.</p> : null}</section>)}</div> : <Empty icon={FileClock} title="No persisted activity" detail="Agent investigations and their real tool activity will appear here." />}</div>
}
