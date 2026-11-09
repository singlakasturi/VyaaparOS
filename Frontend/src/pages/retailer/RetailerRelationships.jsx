import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Search, LoaderCircle } from 'lucide-react'
import { cancelRelationshipRequest, createRelationshipRequest, discoverDistributors, fetchCurrentRetailerDistributor, fetchRelationshipRequests } from '../../services/relationships.js'
import '../orderDesk/OrderDeskRelationships.css'

export default function RetailerRelationships() {
  const { retailer, retailerId } = useOutletContext()
  const [query,setQuery]=useState(''); const [businesses,setBusinesses]=useState([]); const [requests,setRequests]=useState([])
  const [active,setActive]=useState(null); const [loading,setLoading]=useState(true); const [busy,setBusy]=useState(''); const [error,setError]=useState(''); const [message,setMessage]=useState('')
  const refresh=async()=>{setLoading(true);setError('');try { const [reqs,linked]=await Promise.all([fetchRelationshipRequests(retailerId),fetchCurrentRetailerDistributor(retailerId)]); setRequests(reqs||[]); setActive(linked||null) } catch(e){setError(e.message)} finally{setLoading(false)}}
  useEffect(()=>{refresh()},[retailerId])
  useEffect(()=>{const timer=setTimeout(()=>{if(query.trim().length>=2)discoverDistributors(query).then(setBusinesses).catch((e)=>setError(e.message));else setBusinesses([])},250);return()=>clearTimeout(timer)},[query])
  const submit=async(distributorId)=>{setBusy(distributorId);setError('');try{const switching=active?.status==='ACTIVE';await createRelationshipRequest({retailerId,distributorId,type:switching?'SWITCH':'JOIN'});setMessage(switching?'Switch request sent. Your current distributor remains active until approval.':'Join request sent.');await refresh()}catch(e){setError(e.message)}finally{setBusy('')}}
  const cancel=async(id)=>{setBusy(id);try{await cancelRelationshipRequest(id);await refresh()}catch(e){setError(e.message)}finally{setBusy('')}}
  return <section className="relationship-page"><header><p className="workspace-eyebrow">RETAILER ACCESS</p><h1>Distributor relationship</h1><p>Choose the distributor connected to {retailer?.businessName}.</p></header>
    {error&&<p className="relationship-alert" role="alert">{error}</p>}{message&&<p className="relationship-success" role="status">{message}</p>}
    {loading?<p className="relationship-state"><LoaderCircle className="relationship-spin"/> Loading relationship…</p>:<>
      <section className="relationship-panel"><h2>Current distributor</h2>{active?.status==='ACTIVE'?<div className="relationship-card"><strong>{active.businessName}</strong><span>Active relationship</span></div>:active?.status==='ENDED'?<p>Your previous relationship with <strong>{active.businessName}</strong> ended. You can request a new relationship below.</p>:<p>No active distributor is linked. You can request to join a business below.</p>}</section>
      <section className="relationship-panel"><h2>Requests</h2>{requests.length===0?<p>No relationship requests yet.</p>:requests.map((r)=><div className="relationship-row" key={r.id}><div><strong>{r.distributorName}</strong><span>{r.type==='SWITCH'?'Switch':'Join'} · {r.status}</span></div>{r.status==='PENDING'&&<button className="relationship-button secondary" disabled={busy===r.id} onClick={()=>cancel(r.id)}>Cancel</button>}</div>)}</section>
      <section className="relationship-panel"><h2>{active?.status==='ACTIVE'?'Switch distributor':'Find a distributor'}</h2><label className="relationship-search"><Search size={18}/><input value={query} onChange={(e)=>setQuery(e.target.value)} placeholder="Search by business name" aria-label="Search distributors"/></label>
        {!query.trim()&&<p>Enter at least two characters to search.</p>}{query.trim().length>=2&&!businesses.length&&<p>No distributor businesses found.</p>}
        {businesses.map((b)=><div className="relationship-row" key={b.id}><strong>{b.businessName}</strong><button className="relationship-button" disabled={busy===b.id||requests.some((r)=>r.status==='PENDING')} onClick={()=>submit(b.id)}>{busy===b.id?'Sending…':active?.status==='ACTIVE'?'Request switch':'Request to join'}</button></div>)}
      </section>
    </>}
  </section>
}
