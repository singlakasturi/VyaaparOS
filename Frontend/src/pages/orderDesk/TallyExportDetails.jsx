import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, FileSpreadsheet, RefreshCw } from 'lucide-react'
import { fetchExportBatch } from '../../services/orderDesk.js'

const dateLabel = (value) => value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'

export default function TallyExportDetails() {
  const { batchId } = useParams()
  const [batch, setBatch] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setBatch(await fetchExportBatch(batchId)) }
    catch (err) { setError(err.message || 'Export batch details could not be loaded.') }
    finally { setLoading(false) }
  }, [batchId])
  useEffect(() => { load() }, [load])

  return <div className="desk-page"><Link className="desk-back-link" to="/order-desk/exports"><ArrowLeft size={16} />Back to Tally exports</Link>
    {loading ? <div className="desk-loading desk-loading-card"><RefreshCw size={20} className="desk-spin" />Loading export batch…</div> : error ? <div className="desk-message desk-message-error" role="alert">{error}<button type="button" className="desk-text-button" onClick={load}>Retry</button></div> : batch && <>
      <header className="desk-page-header"><div><span className="desk-eyebrow">TALLY EXPORT BATCH</span><h1>{batch.filename}</h1><p>Created {dateLabel(batch.createdAt)}</p></div><span className="desk-export-count"><FileSpreadsheet size={17} />{batch.orderCount} orders</span></header>
      <section className="desk-panel desk-batch-detail"><div className="desk-panel-title"><div><span className="desk-eyebrow">INCLUDED ORDERS</span><h2>Batch contents</h2></div></div>
        <dl className="desk-batch-metadata"><div><dt>Batch ID</dt><dd>{batch.id}</dd></div><div><dt>Created by</dt><dd>{batch.createdBy}</dd></div><div><dt>Created at</dt><dd>{dateLabel(batch.createdAt)}</dd></div><div><dt>CSV filename</dt><dd>{batch.filename}</dd></div></dl>
        {!batch.orderNumbers?.length ? <p className="desk-subtle">No order numbers were returned for this batch.</p> : <ol className="desk-batch-order-numbers">{batch.orderNumbers.map((number) => <li key={number}>{number}</li>)}</ol>}
        <p className="desk-subtle desk-batch-note">The backend provides batch metadata and included order numbers. It does not expose a CSV re-download endpoint; the CSV was downloaded when the batch was created.</p>
      </section>
    </>}
  </div>
}
