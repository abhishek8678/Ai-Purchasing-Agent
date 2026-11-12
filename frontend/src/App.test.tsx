import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { api } from './api'

vi.mock('./api', () => ({ api: {
  health: vi.fn(), products: vi.fn(), warehouses: vi.fn(), inventory: vi.fn(), runs: vi.fn(), approvals: vi.fn(), purchaseOrders: vi.fn(),
  run: vi.fn(), events: vi.fn(), startRun: vi.fn(), offers: vi.fn(), approve: vi.fn(), reject: vi.fn(), execute: vi.fn(), purchaseOrder: vi.fn(),
} }))

const product = { id: 1, sku: 'WM-001', name: 'Wireless Mouse', unitOfMeasure: 'EACH', unitVolumeM3: 0.001, active: true }
const warehouse = { id: 1, code: 'DEL-01', name: 'Delhi Fulfillment Center', city: 'Delhi', countryCode: 'IN', active: true }
const inventoryRow = { id: 1, productId: 1, productSku: 'WM-001', productName: 'Wireless Mouse', unitOfMeasure: 'EACH', warehouseId: 1, warehouseCode: 'DEL-01', warehouseName: 'Delhi Fulfillment Center', onHandUnits: 260, reservedUnits: 45, availableUnits: 215, lastCountedAt: '2026-11-12T08:00:00' }
const recommendation = { decision: 'MODIFY' as const, originalRecommendedQuantity: 800, proposedQuantity: 100, selectedSupplierId: 2, selectedSupplierProductId: 3, recommendationSummary: 'Reduce order to match the verified shortfall.', evidenceReferences: ['Forecast over next 30 days'], keySupportingFacts: ['215 available units', 'Confirmed inbound supply is considered'], risks: ['Lead time is six days'], unresolvedQuestions: [], constraintEvaluationReferences: ['Budget check passed'], confidence: 0.82 }
const run = { runId: 'run-1', productSku: 'WM-001', warehouseCode: 'DEL-01', originalRecommendedQuantity: 800, status: 'COMPLETED', decision: 'MODIFY', proposedQuantity: 100, recommendationSummary: recommendation.recommendationSummary, recommendation, startedAt: '2026-11-12T08:00:00', completedAt: '2026-11-12T08:01:00' }
const offer = { id: 3, supplierId: 2, supplierCode: 'SUP-NORTHSTAR', supplierName: 'Northstar Supply', productId: 1, productSku: 'WM-001', warehouseId: 1, warehouseCode: 'DEL-01', status: 'AVAILABLE', minimumOrderQuantity: 50, packSize: 10, leadTimeDays: 6, unitPrice: 425, currencyCode: 'INR' }
const queue = { run, approval: null }
const approval = { approvalId: 'approval-1', agentRunId: 'run-1', approvalStatus: 'APPROVED' as const, executionStatus: 'NOT_EXECUTED', approvedQuantity: 100, supplierProductId: 3, supplierId: 2, supplierCode: 'SUP-NORTHSTAR', approverIdentity: 'Demo Buyer', decisionAt: '2026-11-12T08:02:00', rejectionReason: null, idempotencyKey: 'key', purchaseOrderId: null, purchaseOrderNumber: null, executionSummary: null, executionAt: null }
const order = { id: 10, poNumber: 'PA-approval-1', supplierId: 2, supplierCode: 'SUP-NORTHSTAR', supplierName: 'Northstar Supply', warehouseId: 1, warehouseCode: 'DEL-01', warehouseName: 'Delhi Fulfillment Center', orderDate: '2026-11-12', status: 'APPROVED', currencyCode: 'INR', approvedBy: 'Demo Buyer', approvedAt: '2026-11-12T08:02:00', items: [{ id: 20, supplierProductId: 3, productSku: 'WM-001', productName: 'Wireless Mouse', unitOfMeasure: 'EACH', orderedUnits: 100, confirmedUnits: null, receivedUnits: 0, unitPriceAtOrder: 425, expectedDeliveryDate: '2026-11-18' }] }
const events = [{ sequenceNumber: 1, eventType: 'RUN_STARTED', toolName: null, argumentsJson: null, resultJson: null, summary: 'Investigation started.', occurredAt: '2026-11-12T08:00:00' }, { sequenceNumber: 2, eventType: 'DECISION_PROPOSED', toolName: null, argumentsJson: null, resultJson: JSON.stringify(recommendation), summary: recommendation.recommendationSummary, occurredAt: '2026-11-12T08:01:00' }]

function setup(options: { offline?: boolean } = {}) {
  vi.mocked(api.health).mockResolvedValue({ status: 'UP', message: 'ProcureAI API is ready', timestamp: '' })
  vi.mocked(api.products).mockResolvedValue([product]); vi.mocked(api.warehouses).mockResolvedValue([warehouse]); vi.mocked(api.inventory).mockResolvedValue([inventoryRow])
  vi.mocked(api.runs).mockResolvedValue([run]); vi.mocked(api.approvals).mockResolvedValue([queue]); vi.mocked(api.purchaseOrders).mockResolvedValue([order])
  vi.mocked(api.purchaseOrder).mockResolvedValue(order)
  vi.mocked(api.offers).mockResolvedValue([offer]); vi.mocked(api.events).mockResolvedValue(events); vi.mocked(api.run).mockResolvedValue(run)
  vi.mocked(api.approve).mockResolvedValue(approval); vi.mocked(api.reject).mockResolvedValue({ ...approval, approvalStatus: 'REJECTED' }); vi.mocked(api.execute).mockResolvedValue({ ...approval, executionStatus: 'SUCCEEDED', purchaseOrderNumber: order.poNumber, purchaseOrderId: order.id })
  if (options.offline) { vi.mocked(api.health).mockRejectedValue(new Error('Backend offline')); vi.mocked(api.products).mockRejectedValue(new Error('Backend offline')) }
  vi.stubGlobal('confirm', vi.fn(() => true))
  return render(<MemoryRouter><App /></MemoryRouter>)
}

describe('ProcureAI dashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the live overview and navigates to inventory with on-hand and available separated', async () => {
    const user = userEvent.setup(); setup()
    expect(await screen.findByText((_, element) => element?.tagName === 'STRONG' && Boolean(element.textContent?.includes('Wireless Mouse')))).toBeInTheDocument()
    expect(screen.getByText('215')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Inventory/ }))
    expect(await screen.findByText('Available')).toBeInTheDocument()
    expect(screen.getByText('260')).toBeInTheDocument()
    expect(screen.getByText('45')).toBeInTheDocument()
  })

  it('starts an investigation and renders its structured recommendation and persisted timeline', async () => {
    const user = userEvent.setup(); setup(); vi.mocked(api.startRun).mockResolvedValue(run)
    await user.click(await screen.findByRole('button', { name: /AI Purchasing Agent/ }))
    await user.click(screen.getByRole('button', { name: /Start investigation/ }))
    expect((await screen.findAllByText('Reduce order to match the verified shortfall.')).length).toBeGreaterThan(0)
    expect(await screen.findByText('Investigation timeline')).toBeInTheDocument()
    expect(await screen.findByText('Run Started')).toBeInTheDocument()
    expect(api.startRun).toHaveBeenCalledWith(expect.objectContaining({ productSku: 'WM-001', warehouseCode: 'DEL-01', recommendedPurchaseUnits: 800 }))
  })

  it('reviews and records approval without executing until the separate action is clicked', async () => {
    const user = userEvent.setup(); setup(); vi.mocked(api.approvals).mockResolvedValue([ { run, approval } ])
    await user.click(await screen.findByRole('button', { name: /Approvals/ }))
    await user.click(screen.getByRole('button', { name: /Review recommendation/ }))
    expect(await screen.findByRole('option', { name: /Northstar Supply/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Approve plan/ }))
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith('run-1', 100, 3, 'Demo Buyer'))
    expect(api.execute).not.toHaveBeenCalled()
    await waitFor(() => expect(api.approvals).toHaveBeenCalledTimes(2))
    await user.click(await screen.findByRole('button', { name: /Execute purchase/ }))
    await waitFor(() => expect(api.execute).toHaveBeenCalledWith('approval-1'))
  })

  it('records rejection with a reason', async () => {
    const user = userEvent.setup(); setup()
    await user.click(await screen.findByRole('button', { name: /Approvals/ }))
    await user.click(screen.getByRole('button', { name: /Review recommendation/ }))
    await user.type(await screen.findByRole('textbox', { name: 'Rejection reason' }), 'Budget review required')
    await user.click(screen.getByRole('button', { name: /^Reject$/ }))
    await waitFor(() => expect(api.reject).toHaveBeenCalledWith('run-1', 'Demo Buyer', 'Budget review required'))
  })

  it('shows real purchase order details when expanded', async () => {
    const user = userEvent.setup(); setup()
    await user.click(await screen.findByRole('button', { name: /Purchase Orders/ }))
    expect(await screen.findByText('PA-approval-1')).toBeInTheDocument()
    await user.click(screen.getByText('PA-approval-1'))
    expect(await screen.findByText('Order line items')).toBeInTheDocument()
    expect(api.purchaseOrder).toHaveBeenCalledWith(order.id)
    expect(screen.getByText('Not confirmed')).toBeInTheDocument()
    expect(screen.getByText(/internal persisted records/i)).toBeInTheDocument()
  })

  it('renders an actionable backend connection error without fabricated metrics', async () => {
    setup({ offline: true })
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend offline')
    expect(screen.getByText('Catalog products')).toBeInTheDocument()
  })
})
