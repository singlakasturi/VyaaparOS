import { useCallback, useEffect, useState } from 'react'
import { Building2, Check, LoaderCircle, Plus, RefreshCw, Save, ShieldCheck, UserMinus, Users } from 'lucide-react'
import { ApiError } from '../../services/api.js'
import { addDistributorStaff, canManageDistributorStaff, fetchDistributorStaff, getDistributorStaffListState, revokeDistributorStaff, updateCurrentDistributor, validateDistributorBusinessName } from '../../services/distributor.js'
import { useDistributorWorkspace } from '../../routes/DistributorAccessRoute.jsx'
import '../workspace.css'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

export default function DistributorBusinessWorkspace() {
  const { business, setBusiness } = useDistributorWorkspace()
  const owner = canManageDistributorStaff(business)
  const [name, setName] = useState(business.businessName || '')
  const validBusinessName = validateDistributorBusinessName(name)
  const [savingBusiness, setSavingBusiness] = useState(false)
  const [businessMessage, setBusinessMessage] = useState('')
  const [businessError, setBusinessError] = useState('')
  const [staff, setStaff] = useState([])
  const [staffLoading, setStaffLoading] = useState(owner)
  const [staffError, setStaffError] = useState('')
  const [email, setEmail] = useState('')
  const [addingStaff, setAddingStaff] = useState(false)
  const [staffMessage, setStaffMessage] = useState('')
  const [revokingId, setRevokingId] = useState('')
  const staffListState = getDistributorStaffListState({ loading: staffLoading, error: staffError, staff })

  useEffect(() => { setName(business.businessName || '') }, [business.businessName])

  const loadStaff = useCallback(async () => {
    if (!owner) return
    setStaffLoading(true)
    setStaffError('')
    try {
      const data = await fetchDistributorStaff()
      setStaff(Array.isArray(data) ? data : [])
    } catch (error) {
      setStaffError(error.message || 'Could not load active staff.')
    } finally {
      setStaffLoading(false)
    }
  }, [owner])

  useEffect(() => { loadStaff() }, [loadStaff])

  const saveBusiness = async (event) => {
    event.preventDefault()
    setSavingBusiness(true)
    setBusinessMessage('')
    setBusinessError('')
    try {
      const updated = await updateCurrentDistributor({ businessName: name.trim() })
      setBusiness(updated)
      setName(updated.businessName)
      setBusinessMessage('Business details saved.')
    } catch (error) {
      setBusinessError(error instanceof ApiError && error.status === 403
        ? 'Your owner access is no longer active. Refresh workspace access or contact the business owner.'
        : error.message || 'Could not save business details.')
    } finally {
      setSavingBusiness(false)
    }
  }

  const inviteStaff = async (event) => {
    event.preventDefault()
    setAddingStaff(true)
    setStaffMessage('')
    setStaffError('')
    try {
      const added = await addDistributorStaff(email.trim())
      setStaff((current) => [...current, added])
      setEmail('')
      setStaffMessage(`Access granted to ${added.email}.`)
    } catch (error) {
      setStaffError(error.message || 'Could not add this account.')
    } finally {
      setAddingStaff(false)
    }
  }

  const revoke = async (member) => {
    const confirmed = window.confirm(`Revoke Order Desk access for ${member.fullName || member.email}? They will lose access immediately.`)
    if (!confirmed) return
    setRevokingId(member.userId)
    setStaffMessage('')
    setStaffError('')
    try {
      await revokeDistributorStaff(member.userId)
      setStaff((current) => current.filter((row) => row.userId !== member.userId))
      setStaffMessage(`Access revoked for ${member.email}.`)
    } catch (error) {
      setStaffError(error.message || 'Could not revoke access.')
    } finally {
      setRevokingId('')
    }
  }

  return (
    <main className="distributor-workspace">
      <header className="distributor-page-header"><span className="workspace-eyebrow">DISTRIBUTOR WORKSPACE</span><h1>Business & access</h1><p>Review the business identity and the people who can work in this distributor account.</p></header>
      <section className="distributor-card" aria-labelledby="business-heading">
        <div className="distributor-card-heading"><span className="distributor-icon"><Building2 size={21} aria-hidden="true" /></span><div><h2 id="business-heading">Business overview</h2><p>{owner ? 'You are the business owner.' : 'Read-only details for your active staff membership.'}</p></div><span className="distributor-badge"><ShieldCheck size={14} aria-hidden="true" />{owner ? 'Owner' : 'Order Desk staff'}</span></div>
        {owner ? <form className="distributor-business-form" onSubmit={saveBusiness}>
          <label htmlFor="distributor-business-name">Business name<input id="distributor-business-name" autoComplete="organization" maxLength={200} minLength={1} required value={name} onChange={(event) => { setName(event.target.value); setBusinessMessage(''); setBusinessError('') }} /></label>
          {!validBusinessName && <small className="workspace-field-hint" role="status">Enter a business name containing 1 to 200 non-space characters.</small>}
          <dl className="distributor-meta"><div><dt>Business owner</dt><dd>{business.ownerName || 'Business owner'}</dd></div><div><dt>Business created</dt><dd>{formatDate(business.createdAt)}</dd></div><div><dt>Last updated</dt><dd>{formatDate(business.updatedAt)}</dd></div></dl>
          <button className="workspace-button workspace-button-primary" disabled={savingBusiness || !validBusinessName || name.trim() === business.businessName} type="submit">{savingBusiness ? <LoaderCircle className="distributor-spin" size={17} aria-hidden="true" /> : <Save size={17} aria-hidden="true" />}{savingBusiness ? 'Saving…' : 'Save business details'}</button>
          {businessMessage && <p className="workspace-success" role="status"><Check size={16} aria-hidden="true" />{businessMessage}</p>}
          {businessError && <p className="workspace-error" role="alert">{businessError}</p>}
        </form> : <dl className="distributor-meta distributor-meta-staff"><div><dt>Business name</dt><dd>{business.businessName}</dd></div><div><dt>Business owner</dt><dd>{business.ownerName || 'Business owner'}</dd></div><div><dt>Business created</dt><dd>{formatDate(business.createdAt)}</dd></div></dl>}
      </section>

      {owner && <section className="distributor-card" aria-labelledby="staff-heading">
        <div className="distributor-card-heading"><span className="distributor-icon"><Users size={21} aria-hidden="true" /></span><div><h2 id="staff-heading">Order Desk staff</h2><p>Manage active accounts for this business. Staff access is checked against membership on every request.</p></div></div>
        <form className="distributor-staff-form" onSubmit={inviteStaff}>
          <label htmlFor="staff-email">Add an existing account by email<input id="staff-email" autoComplete="email" maxLength={320} placeholder="teammate@company.com" required type="email" value={email} onChange={(event) => { setEmail(event.target.value); setStaffError(''); setStaffMessage('') }} /></label>
          <button className="workspace-button workspace-button-primary" disabled={addingStaff || !email.trim()} type="submit">{addingStaff ? <LoaderCircle className="distributor-spin" size={17} aria-hidden="true" /> : <Plus size={17} aria-hidden="true" />}{addingStaff ? 'Adding…' : 'Add staff'}</button>
        </form>
        {staffMessage && <p className="workspace-success" role="status"><Check size={16} aria-hidden="true" />{staffMessage}</p>}
        {staffError && <div className="workspace-error" role="alert">{staffError}<button className="workspace-text-button" onClick={loadStaff} type="button">Retry loading staff</button></div>}
        {staffListState === 'loading' ? <div className="workspace-inline-state"><LoaderCircle className="distributor-spin" aria-hidden="true" />Loading active staff…</div>
          : staffListState === 'error' ? null
            : staffListState === 'empty' ? <div className="workspace-empty"><Users size={22} aria-hidden="true" /><strong>No staff access yet</strong><span>Add an existing active account to share the Order Desk workspace.</span></div>
              : <ul className="distributor-staff-list">{staff.map((member) => <li key={member.userId}><span className="staff-avatar" aria-hidden="true">{(member.fullName || member.email || '?').slice(0, 1).toUpperCase()}</span><span className="staff-identity"><strong>{member.fullName || 'VyapaarOS account'}</strong><small>{member.email}</small></span><span className="staff-granted">Added {formatDate(member.grantedAt)}</span><button aria-label={`Revoke access for ${member.fullName || member.email}`} className="workspace-button workspace-button-danger" disabled={Boolean(revokingId)} onClick={() => revoke(member)} type="button">{revokingId === member.userId ? <LoaderCircle className="distributor-spin" size={16} aria-hidden="true" /> : <UserMinus size={16} aria-hidden="true" />}<span>{revokingId === member.userId ? 'Revoking…' : 'Revoke'}</span></button></li>)}</ul>}
        <button className="workspace-text-button workspace-refresh" onClick={loadStaff} type="button" disabled={staffLoading}><RefreshCw size={15} aria-hidden="true" />Refresh staff list</button>
      </section>}
    </main>
  )
}
