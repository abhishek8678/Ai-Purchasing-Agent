import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('ProcureAI API client', () => {
  it('uses the existing same-origin Vite proxy and posts the backend request shape', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ runId: 'run-1' }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)
    await api.startRun({ productSku: 'WM-001', warehouseCode: 'DEL-01', recommendedPurchaseUnits: 800, planningStartDate: '2026-11-13', planningEndDate: '2026-12-12', userInstruction: 'Check lead time.' })
    expect(fetchMock).toHaveBeenCalledWith('/api/agent-runs', expect.objectContaining({ method: 'POST', body: JSON.stringify({ productSku: 'WM-001', warehouseCode: 'DEL-01', recommendedPurchaseUnits: 800, planningStartDate: '2026-11-13', planningEndDate: '2026-12-12', userInstruction: 'Check lead time.' }) }))
  })

  it('surfaces backend validation messages for non-2xx responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Supplier offer is no longer available.' }), { status: 409 })))
    await expect(api.execute('approval-1')).rejects.toThrow('Supplier offer is no longer available.')
  })

  it('surfaces a clear message when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(api.products()).rejects.toThrow('Could not reach the ProcureAI API')
  })
})
