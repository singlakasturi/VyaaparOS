import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AlertCircle, ArrowLeft, Check, Clock3, Package, RefreshCw, ShieldAlert, X } from 'lucide-react'
import { approveDeskOrder, approveOrderActionRequest, fetchDeskOrder, fetchDeskOrderInvoice, fetchOrderActionRequests, fetchOrderHistory, rejectDeskOrder, rejectOrderActionRequest, updateDeskFulfillment } from '../../services/orderDesk.js'
import { OrderStatusBadge } from './OrderDeskQueue.jsx'

const money = (amount, currency) => amount == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(Number(amount))
const dateLabel = (value) => value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
const titleCase = (value) => String(value || '').replaceAll('_', ' ').toLocaleLowerCase().replace(/\b\w/g, (letter) => letter.toLocaleUpperCase())
const historyLabel = (entry) => {
  if(entry.eventType==='FULFILLMENT') return `${titleCase(entry.previousFulfillmentStatus)} → ${titleCase(entry.newFulfillmentStatus)} fulfillment`
  if(entry.eventType==='CANCELLATION_REQUEST') return 'Cancellation request submitted'
  if(entry.eventType==='CANCELLATION_DECISION') return `Cancellation request ${titleCase(entry.requestStatus)}`
  if(entry.eventType==='RETURN_REQUEST') return 'Return request submitted'
  if(entry.eventType==='RETURN_DECISION') return `Return request ${titleCase(entry.requestStatus)}`
  return entry.previousStatus ? `${titleCase(entry.previousStatus)} → ${titleCase(entry.newStatus)}` : titleCase(entry.newStatus)
}
const fulfillmentChoices = (status) => ({ NOT_STARTED: ['PREPARING'], PREPARING: ['SHIPPED'], SHIPPED: ['SHIPPED', 'DELIVERED'], DELIVERED: [] }[status || 'NOT_STARTED'] || [])

export default function OrderDeskOrderDetails() {
  const { orderId } = useParams()
  const [order, setOrder] = useState(null)
  const [history, setHistory] = useState([])
  const [historyError, setHistoryError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [confirmAction, setConfirmAction] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [fulfillmentTarget, setFulfillmentTarget] = useState('')
  const [trackingReference, setTrackingReference] = useState('')
  const [fulfillmentBusy, setFulfillmentBusy] = useState(false)
  const [fulfillmentError, setFulfillmentError] = useState('')
  const [fulfillmentNotice, setFulfillmentNotice] = useState('')
  const [actionRequests, setActionRequests] = useState([])
  const [actionRequestLoading, setActionRequestLoading] = useState(true)
  const [actionRequestError, setActionRequestError] = useState('')
  const [actionRequestBusy, setActionRequestBusy] = useState('')
  const [actionRequestComments, setActionRequestComments] = useState({})
  const [actionRequestNotice, setActionRequestNotice] = useState('')
  const [invoiceBusy, setInvoiceBusy] = useState(false)
  const [invoiceError, setInvoiceError] = useState('')

  const loadActionRequests = useCallback(async () => {
    setActionRequestLoading(true); setActionRequestError('')
    try {
      const page=await fetchOrderActionRequests({orderId,status:undefined,size:100})
      setActionRequests(page?.content || [])
    } catch(err) { setActionRequestError(err.message || 'Cancellation and return requests could not be loaded.') }
    finally { setActionRequestLoading(false) }
  },[orderId])

  const loadHistory = useCallback(async () => {
    setHistoryError('')
    try { const data = await fetchOrderHistory(orderId); setHistory(Array.isArray(data) ? data : []) }
    catch (err) { setHistory([]); setHistoryError(err.message || 'Status history could not be loaded.') }
  }, [orderId])

  const load = useCallback(async () => {
    setLoading(true); setLoadError(''); setNotFound(false)
    try {
      const orderData = await fetchDeskOrder(orderId)
      setOrder(orderData)
      const choices=fulfillmentChoices(orderData.fulfillmentStatus)
      setFulfillmentTarget(choices[0] || '')
      setTrackingReference(orderData.trackingReference || '')
      await Promise.all([loadHistory(),loadActionRequests()])
    } catch (err) {
      setNotFound(err.status === 404)
      setLoadError(err.status === 404 ? 'This order could not be found.' : err.message || 'Order details could not be loaded.')
    }
    finally { setLoading(false) }
  }, [orderId, loadHistory, loadActionRequests])

  useEffect(() => { load() }, [load])

  const beginAction = (action) => { setActionError(''); setNotice(''); setReason(''); setConfirmAction(action) }
  const cancelAction = () => { if (!busy) { setConfirmAction(''); setActionError(''); setReason('') } }

  const submitAction = async (event) => {
    event.preventDefault()
    if (!confirmAction || busy || !order) return
    if ((confirmAction === 'reject' || (confirmAction === 'approve' && order.status === 'CREDIT_HOLD')) && !reason.trim()) {
      setActionError(confirmAction === 'reject' ? 'Enter a rejection reason to continue.' : 'A credit override reason is required.')
      return
    }
    setBusy(true); setActionError('')
    try {
      const updated = confirmAction === 'approve'
        ? await approveDeskOrder(order.id, reason.trim())
        : await rejectDeskOrder(order.id, reason.trim())
      setOrder(updated)
      setConfirmAction('')
      setReason('')
      setNotice(confirmAction === 'approve' ? 'Order approved.' : 'Order rejected. Reserved stock restoration is handled by the backend.')
      await loadHistory()
    } catch (err) { setActionError(err.message || 'The order action could not be completed.') }
    finally { setBusy(false) }
  }

  const saveFulfillment = async (event) => {
    event.preventDefault()
    if (!order || !fulfillmentTarget || fulfillmentBusy) return
    setFulfillmentBusy(true); setFulfillmentError(''); setFulfillmentNotice('')
    try {
      const updated=await updateDeskFulfillment(order.id,fulfillmentTarget,trackingReference)
      setOrder(updated)
      const choices=fulfillmentChoices(updated.fulfillmentStatus)
      setFulfillmentTarget(choices[0] || '')
      setTrackingReference(updated.trackingReference || '')
      setFulfillmentNotice('Fulfillment progress saved.')
      await loadHistory()
    } catch (err) { setFulfillmentError(err.message || 'Fulfillment progress could not be saved.') }
    finally { setFulfillmentBusy(false) }
  }

  const reviewActionRequest = async (request, approve) => {
    if(actionRequestBusy) return
    setActionRequestBusy(request.id);setActionRequestError('');setActionRequestNotice('')
    try {
      const comment=String(actionRequestComments[request.id]||'').trim()
      if(comment.length>500){setActionRequestError('Decision comment must be at most 500 characters.');return}
      if(approve) await approveOrderActionRequest(request.id,comment)
      else await rejectOrderActionRequest(request.id,comment)
      setActionRequestNotice(`${request.requestType==='CANCELLATION'?'Cancellation':'Return'} request ${approve?'approved':'rejected'}.`)
      await load()
    } catch(err) { setActionRequestError(err.message || 'The request decision could not be saved.') }
    finally { setActionRequestBusy('') }
  }

  const openInvoice = async () => {
    if (invoiceBusy) return
    const invoiceWindow = window.open('', '_blank')
    setInvoiceBusy(true); setInvoiceError('')
    try {
      const { blob } = await fetchDeskOrderInvoice(order.id)
      const url = URL.createObjectURL(blob)
      if (invoiceWindow) invoiceWindow.location.href = url
      else { const link = document.createElement('a'); link.href = url; link.download = `order-${order.orderNumber || order.id}-invoice.html`; link.click() }
      window.setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (err) { invoiceWindow?.close(); setInvoiceError(err.message || 'Invoice could not be loaded.') }
    finally { setInvoiceBusy(false) }
  }

  if (loading) return <div className="desk-page"><div className="desk-loading desk-loading-card"><RefreshCw size={22} className="desk-spin" />Loading order…</div></div>
  if (loadError) return <div className="desk-page"><Link className="desk-back-link" to="/order-desk"><ArrowLeft size={16} />Order queue</Link><div className="desk-message desk-message-error" role="alert"><AlertCircle size={20} /><div><strong>{notFound ? 'Order not found' : 'Couldn’t load this order'}</strong><p>{loadError}</p>{!notFound && <button className="desk-text-button" onClick={load} type="button">Try again</button>}</div></div></div>
  if (!order) return null

  const eligible = order.status === 'SUBMITTED' || order.status === 'CREDIT_HOLD'
  const requiresReason = confirmAction === 'reject' || (confirmAction === 'approve' && order.status === 'CREDIT_HOLD')

  return (
    <div className="desk-page">
      <Link className="desk-back-link" to="/order-desk"><ArrowLeft size={16} />Back to order queue</Link>
      <header className="desk-detail-header"><div><span className="desk-eyebrow">ORDER REVIEW</span><h1>{order.orderNumber || 'Order details'}</h1><p>Order ID · {order.id}</p><p>Submitted {dateLabel(order.createdAt)}</p></div><div className="desk-detail-header-actions">
          {eligible && (
            <>
              <button className="desk-button desk-button-danger" type="button" onClick={() => beginAction('reject')} disabled={busy}>
                <X size={16} />Reject order
              </button>
              <button className="desk-button desk-button-primary" type="button" onClick={() => beginAction('approve')} disabled={busy}>
                <Check size={16} />{order.status === 'CREDIT_HOLD' ? 'Approve with override' : 'Approve order'}
              </button>
            </>
          )}
          {order.status === 'APPROVED' && <Link className="desk-button desk-button-primary" to="/order-desk/exports">Continue to Tally export</Link>}
          <OrderStatusBadge status={order.status} />
        </div></header>

      <div className="desk-invoice-action"><button className="desk-button desk-button-secondary" type="button" onClick={openInvoice} disabled={invoiceBusy}>{invoiceBusy ? 'Loading invoice…' : 'View / Download invoice'}</button>{invoiceError && <p role="alert" className="desk-dialog-error">{invoiceError}</p>}</div>

      {notice && <div className="desk-message desk-message-success" role="status"><Check size={18} />{notice}</div>}
      {actionError && !confirmAction && <div className="desk-message desk-message-error" role="alert"><AlertCircle size={18} />{actionError}</div>}

      <section className="desk-order-overview">
        <article className="desk-panel desk-retailer-panel"><span className="desk-eyebrow">RETAILER</span><h2>{order.retailerName || 'Retailer'}</h2><p>{order.retailerContact || 'No contact number on file'}</p><small>Retailer ID · {order.retailerId}</small></article>
        <article className="desk-panel desk-status-panel"><span className="desk-eyebrow">CURRENT STATUS</span><div><OrderStatusBadge status={order.status} /></div>{order.status === 'CREDIT_HOLD' && <p className="desk-credit-note"><ShieldAlert size={16} />This order needs an explicit credit override reason before approval.</p>}{order.creditHoldReason && <p className="desk-credit-reason">{order.creditHoldReason}</p>}</article>
        <article className="desk-panel desk-total-panel"><span className="desk-eyebrow">SERVER-CALCULATED TOTALS</span><dl><div><dt>Subtotal</dt><dd>{money(order.baseAmount, order.currency)}</dd></div><div><dt>Discounts</dt><dd>−{money(order.discountAmount, order.currency)}</dd></div><div className="desk-final-total"><dt>Final total</dt><dd>{money(order.finalAmount, order.currency)}</dd></div></dl></article>
      </section>

      {eligible && <section className="desk-action-panel"><div><span className="desk-eyebrow">REVIEW ACTION</span><h2>Decide this order</h2><p>Actions are recorded in the order history and enforced by the backend.</p></div><div className="desk-action-buttons"><button className="desk-button desk-button-danger" type="button" onClick={() => beginAction('reject')} disabled={busy}><X size={16} />Reject order</button><button className="desk-button desk-button-primary" type="button" onClick={() => beginAction('approve')} disabled={busy}><Check size={16} />{order.status === 'CREDIT_HOLD' ? 'Approve with override' : 'Approve order'}</button></div></section>}

      <div className="desk-detail-columns">
        <section className="desk-panel desk-items-panel"><div className="desk-panel-title"><div><span className="desk-eyebrow">ORDER CONTENTS</span><h2>Products</h2></div><span>{order.items?.length || 0} lines</span></div>
          {!order.items?.length ? <p className="desk-subtle">No order items were returned.</p> : <div className="desk-line-items">{order.items.map((item, index) => <article className="desk-line-item" key={`${item.productId}-${index}`}><div className="desk-line-product"><span className="desk-product-mark"><Package size={18} /></span><div><h3>{item.name}</h3><small>SKU {item.sku || '—'} · {item.paidCases} paid + {item.freeCases} free · {item.totalCases} total cases</small>{item.schemeName && <span className="desk-scheme-note">{item.schemeName}{item.schemeType ? ` · ${titleCase(item.schemeType)}` : ''}</span>}</div></div><div className="desk-line-values"><span>{money(item.unitPricePerCase, item.currency)}<small> per case{item.gstIncluded === true ? ' · GST included' : item.gstIncluded === false ? ' · GST excluded' : ''}</small></span><strong>{money(item.finalAmount, item.currency)}</strong><small>Line total</small></div></article>)}</div>}
          {order.notes && <div className="desk-order-notes"><span className="desk-eyebrow">ORDER NOTE</span><p>{order.notes}</p></div>}
        </section>

        <section className="desk-panel desk-history-panel"><div className="desk-panel-title"><div><span className="desk-eyebrow">AUDIT TRAIL</span><h2>Status history</h2></div><Clock3 size={17} /></div>
          {historyError ? <div className="desk-inline-error" role="alert">{historyError}<button type="button" className="desk-text-button" onClick={loadHistory}>Retry</button></div> : !history.length ? <p className="desk-subtle">No status history is available for this order.</p> : <ol className="desk-timeline">{[...history].reverse().map((entry) => <li key={entry.id}><span className="desk-timeline-dot" /><div><strong>{historyLabel(entry)}</strong><time>{dateLabel(entry.createdAt)}</time>{entry.reason && <p>{entry.reason}</p>}{entry.trackingReference && <p>Tracking · {entry.trackingReference}</p>}<small>Changed by · {entry.changedBy}</small></div></li>)}</ol>}
        </section>
      </div>

      <section className="desk-panel desk-action-request-panel" aria-label="Cancellation and return requests">
        <div className="desk-panel-title"><div><span className="desk-eyebrow">RETAILER REQUESTS</span><h2>Cancellation & returns</h2></div><span>{actionRequests.filter(item=>item.status==='PENDING').length} pending</span></div>
        {actionRequestLoading?<p className="desk-subtle">Loading requests…</p>:actionRequestError&&!actionRequests.length?<div className="desk-inline-error" role="alert">{actionRequestError}<button type="button" className="desk-text-button" onClick={loadActionRequests}>Retry</button></div>:!actionRequests.length?<p className="desk-subtle">No cancellation or return requests for this order.</p>:<div className="desk-action-request-list">{actionRequests.map(request=><article className="desk-action-request" key={request.id}>
          <header><div><strong>{request.requestType==='CANCELLATION'?'Cancellation request':'Return request'}</strong><small>Submitted {dateLabel(request.createdAt)}</small></div><span className={`desk-request-status desk-request-${request.status?.toLowerCase()}`}>{request.status}</span></header>
          {request.reason&&<p>{request.reason}</p>}{request.decisionComment&&<small>Decision note · {request.decisionComment}</small>}
          {request.status==='PENDING'&&<><label><span>Decision comment <small>Optional, up to 500 characters</small></span><textarea maxLength={500} value={actionRequestComments[request.id]||''} onChange={event=>setActionRequestComments(current=>({...current,[request.id]:event.target.value}))} /></label><div className="desk-action-request-buttons"><button className="desk-button desk-button-danger" type="button" disabled={Boolean(actionRequestBusy)} onClick={()=>reviewActionRequest(request,false)}>{actionRequestBusy===request.id?'Saving…':'Reject request'}</button><button className="desk-button desk-button-primary" type="button" disabled={Boolean(actionRequestBusy)} onClick={()=>reviewActionRequest(request,true)}>{actionRequestBusy===request.id?'Saving…':'Approve request'}</button></div></>}
        </article>)}</div>}
        {actionRequestError&&actionRequests.length>0&&<p className="desk-dialog-error" role="alert">{actionRequestError}</p>}{actionRequestNotice&&<p className="desk-fulfillment-success" role="status">{actionRequestNotice}</p>}
      </section>

      <section className="desk-panel desk-fulfillment-panel" aria-label="Fulfillment progress">
        <div><span className="desk-eyebrow">FULFILLMENT</span><h2>Progress & tracking</h2><p>Retailers can see fulfillment status and the tracking reference.</p></div>
        <div className="desk-fulfillment-current"><span>Current status</span><strong>{titleCase(order.fulfillmentStatus || 'NOT_STARTED')}</strong>{order.trackingReference && <small>Tracking · {order.trackingReference}</small>}</div>
        {order.status === 'APPROVED' || order.status === 'TALLY_EXPORTED' ? fulfillmentChoices(order.fulfillmentStatus).length > 0 ? <form className="desk-fulfillment-form" onSubmit={saveFulfillment}>
          <label><span>Update status</span><select aria-label="Fulfillment status" value={fulfillmentTarget} onChange={(event)=>setFulfillmentTarget(event.target.value)}>{fulfillmentChoices(order.fulfillmentStatus).map((status)=> <option value={status} key={status}>{status === order.fulfillmentStatus ? 'Update tracking only' : titleCase(status)}</option>)}</select></label>
          {(fulfillmentTarget === 'SHIPPED' || order.fulfillmentStatus === 'SHIPPED') && <label><span>Tracking reference <small>Optional, up to 100 characters</small></span><input aria-label="Tracking reference" value={trackingReference} onChange={(event)=>setTrackingReference(event.target.value)} maxLength={100} pattern="[\\p{L}\\p{N}][\\p{L}\\p{N} ._/#-]{0,99}" title="Use letters, numbers, spaces, and . _ / # -" /></label>}
          {fulfillmentError && <p className="desk-dialog-error" role="alert">{fulfillmentError}</p>}{fulfillmentNotice && <p className="desk-fulfillment-success" role="status">{fulfillmentNotice}</p>}
          <button className="desk-button desk-button-primary" type="submit" disabled={fulfillmentBusy || !fulfillmentTarget}>{fulfillmentBusy ? 'Saving…' : 'Save fulfillment'}</button>
        </form> : <p className="desk-subtle">Fulfillment is complete.</p> : <p className="desk-subtle">Fulfillment updates are available after an order is approved.</p>}
      </section>

      {confirmAction && <div className="desk-confirm-backdrop"><section className="desk-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="desk-confirm-title"><span className={`desk-confirm-icon ${confirmAction === 'reject' ? 'desk-confirm-icon-danger' : ''}`}>{confirmAction === 'approve' ? <Check size={20} /> : <AlertCircle size={20} />}</span><h2 id="desk-confirm-title">{confirmAction === 'approve' ? 'Confirm order approval' : 'Confirm order rejection'}</h2><p>{confirmAction === 'approve' ? (order.status === 'CREDIT_HOLD' ? 'This approves an order on credit hold. The override reason will be recorded in the order history.' : 'This will mark the submitted order as approved.') : 'The order will be rejected. The backend restores reserved stock and records your reason.'}</p><form onSubmit={submitAction}><label className="desk-reason-field"><span>{confirmAction === 'reject' ? 'Rejection reason' : 'Credit override reason'}{requiresReason ? ' · required' : ' · optional'}</span><textarea autoFocus value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} required={requiresReason} placeholder={confirmAction === 'reject' ? 'Explain why this order is being rejected' : order.status === 'CREDIT_HOLD' ? 'Explain the approved credit override' : 'Add an optional review note'} /></label>{actionError && <p className="desk-dialog-error" role="alert"><AlertCircle size={15} />{actionError}</p>}<div className="desk-confirm-actions"><button className="desk-button desk-button-secondary" type="button" onClick={cancelAction} disabled={busy}>Cancel</button><button className={`desk-button ${confirmAction === 'approve' ? 'desk-button-primary' : 'desk-button-danger'}`} type="submit" disabled={busy}>{busy ? 'Saving…' : confirmAction === 'approve' ? 'Confirm approval' : 'Confirm rejection'}</button></div></form></section></div>}
    </div>
  )
}
