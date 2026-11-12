import type { AgentEvent, AgentRun, Approval, ApprovalQueueItem, Health, InventoryRow, Product, PurchaseOrder, SupplierOffer, Warehouse } from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new Error('Could not reach the ProcureAI API. Check that the backend is running.')
  }
  const body = await response.text()
  let parsed: unknown
  try { parsed = body ? JSON.parse(body) : undefined } catch { parsed = body }
  if (!response.ok) {
    const payload = parsed as { message?: string; error?: string; detail?: string } | undefined
    throw new Error(payload?.message || payload?.detail || payload?.error || `Request failed with HTTP ${response.status}`)
  }
  return parsed as T
}

export const api = {
  health: () => request<Health>('/api/health'),
  products: () => request<Product[]>('/api/products'),
  warehouses: () => request<Warehouse[]>('/api/warehouses'),
  inventory: () => request<InventoryRow[]>('/api/inventory'),
  runs: () => request<AgentRun[]>('/api/agent-runs'),
  events: (id: string) => request<AgentEvent[]>(`/api/agent-runs/${encodeURIComponent(id)}/events`),
  run: (id: string) => request<AgentRun>(`/api/agent-runs/${encodeURIComponent(id)}`),
  startRun: (input: { productSku: string; warehouseCode: string; recommendedPurchaseUnits: number; planningStartDate: string; planningEndDate: string; userInstruction?: string }) => request<AgentRun>('/api/agent-runs', { method: 'POST', body: JSON.stringify(input) }),
  offers: (sku: string, warehouse: string) => request<SupplierOffer[]>(`/api/supplier-offers?productSku=${encodeURIComponent(sku)}&warehouseCode=${encodeURIComponent(warehouse)}`),
  approvals: () => request<ApprovalQueueItem[]>('/api/approvals'),
  approve: (runId: string, approvedQuantity: number, supplierProductId: number, approverIdentity: string) => request<Approval>('/api/agent-runs/' + encodeURIComponent(runId) + '/approve', { method: 'POST', body: JSON.stringify({ approvedQuantity, supplierProductId, approverIdentity }) }),
  reject: (runId: string, approverIdentity: string, reason: string) => request<Approval>('/api/agent-runs/' + encodeURIComponent(runId) + '/reject', { method: 'POST', body: JSON.stringify({ approverIdentity, reason }) }),
  execute: (approvalId: string) => request<Approval>(`/api/approvals/${encodeURIComponent(approvalId)}/execute`, { method: 'POST' }),
  purchaseOrders: () => request<PurchaseOrder[]>('/api/purchase-orders'),
  purchaseOrder: (id: number) => request<PurchaseOrder>(`/api/purchase-orders/${id}`),
}
