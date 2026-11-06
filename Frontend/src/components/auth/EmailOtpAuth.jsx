import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  LoaderCircle,
  Mail,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'
import { ApiError } from '../../services/api'
import { requestOtp, verifyOtp } from '../../services/auth'
import { useAuth } from '../../services/AuthProvider'

const OTP_LENGTH = 6
const RESEND_COOLDOWN_SECONDS = 60
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function AuthIntro() {
  return (
    <section className="auth-intro" aria-label="VyapaarOS introduction">
      <div className="auth-intro-icon"><ShieldCheck aria-hidden="true" size={22} /></div>
      <p className="auth-eyebrow">The smarter way to trade</p>
      <h1>Wholesale, made wonderfully simple.</h1>
      <p className="auth-intro-copy">One secure workspace for your catalogue, orders, and business growth.</p>
      <div className="auth-trust"><span className="auth-trust-dot" />Trusted by growing businesses across India</div>
    </section>
  )
}

function ErrorMessage({ children }) {
  return <p className="auth-message auth-message-error" role="alert"><AlertCircle aria-hidden="true" size={16} />{children}</p>
}

function EmailStep({ email, error, isLoading, onChange, onSubmit }) {
  return (
    <>
      <div className="auth-form-heading">
        <span className="auth-step">01 / 02</span>
        <h2>Welcome to VyapaarOS</h2>
        <p>Enter your work email to securely access your workspace.</p>
      </div>
      <form className="auth-form" noValidate onSubmit={onSubmit}>
        <label className="auth-label" htmlFor="email">Work email</label>
        <div className={`auth-input-wrap${error ? ' auth-input-error' : ''}`}>
          <Mail aria-hidden="true" size={19} />
          <input autoComplete="email" autoFocus id="email" onChange={(event) => onChange(event.target.value)} placeholder="you@company.com" type="email" value={email} />
        </div>
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <button className="auth-submit" disabled={isLoading} type="submit">
          {isLoading ? <><LoaderCircle aria-hidden="true" className="auth-spinner" size={18} />Sending code…</> : <>Continue<ArrowRight aria-hidden="true" size={18} /></>}
        </button>
      </form>
      <p className="auth-privacy">By continuing, you agree to our Terms of Service and Privacy Policy.</p>
    </>
  )
}

function OtpStep({ email, error, isLoading, otp, onChangeEmail, onChangeOtp, onResend, onSubmit, secondsRemaining }) {
  const inputRefs = useRef([])

  useEffect(() => {
    inputRefs.current[0]?.focus()
  }, [])

  const updateOtp = (value) => onChangeOtp(value.replace(/\D/g, '').slice(0, OTP_LENGTH))
  const handleOtpChange = (index, value) => {
    const digits = value.replace(/\D/g, '')
    if (digits.length > 1) {
      updateOtp(digits)
      inputRefs.current[Math.min(digits.length, OTP_LENGTH) - 1]?.focus()
      return
    }
    const nextOtp = otp.split('')
    nextOtp[index] = digits
    updateOtp(nextOtp.join(''))
    if (nextOtp[index] && index < OTP_LENGTH - 1) inputRefs.current[index + 1]?.focus()
  }
  const handlePaste = (event) => {
    event.preventDefault()
    const pastedOtp = event.clipboardData.getData('text')
    updateOtp(pastedOtp)
    inputRefs.current[Math.min(pastedOtp.replace(/\D/g, '').length, OTP_LENGTH - 1)]?.focus()
  }

  return (
    <>
      <button className="auth-back-button" onClick={onChangeEmail} type="button"><ArrowLeft aria-hidden="true" size={17} />Change email</button>
      <div className="auth-form-heading">
        <span className="auth-step">02 / 02</span>
        <h2>Check your inbox</h2>
        <p>We sent a six-digit verification code to <strong>{email}</strong>.</p>
      </div>
      <form className="auth-form" onSubmit={onSubmit}>
        <fieldset className="otp-fieldset">
          <legend className="auth-label">Verification code</legend>
          <div className="otp-inputs" onPaste={handlePaste}>
            {Array.from({ length: OTP_LENGTH }, (_, index) => (
              <input
                aria-label={`Digit ${index + 1}`}
                autoComplete={index === 0 ? 'one-time-code' : 'off'}
                className={`otp-input${error ? ' otp-input-error' : ''}`}
                inputMode="numeric"
                key={index}
                maxLength={1}
                onChange={(event) => handleOtpChange(index, event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Backspace' && !otp[index] && index > 0) inputRefs.current[index - 1]?.focus()
                }}
                ref={(element) => { inputRefs.current[index] = element }}
                type="text"
                value={otp[index] || ''}
              />
            ))}
          </div>
        </fieldset>
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <button className="auth-submit" disabled={isLoading} type="submit">
          {isLoading ? <><LoaderCircle aria-hidden="true" className="auth-spinner" size={18} />Verifying…</> : <>Verify and continue<ArrowRight aria-hidden="true" size={18} /></>}
        </button>
      </form>
      <div className="auth-resend">
        <span>Didn’t receive the code?</span>
        <button disabled={secondsRemaining > 0 || isLoading} onClick={onResend} type="button">
          <RefreshCw aria-hidden="true" size={15} />{secondsRemaining > 0 ? `Resend in 00:${String(secondsRemaining).padStart(2, '0')}` : 'Resend code'}
        </button>
      </div>
    </>
  )
}

function AuthSuccess({ email, onContinue }) {
  return (
    <div className="auth-success">
      <div className="auth-success-icon"><CheckCircle2 aria-hidden="true" size={30} /></div>
      <span className="auth-step">VERIFIED</span>
      <h2>You’re all set.</h2>
      <p>Your workspace is ready. We verified {email} successfully.</p>
      <button className="auth-submit" onClick={onContinue} type="button">Enter workspace<ArrowRight aria-hidden="true" size={18} /></button>
    </div>
  )
}

function getErrorMessage(error, fallback) {
  if (error instanceof ApiError && error.status === 401) return 'That code is invalid or has expired. Request a new code and try again.'
  if (error instanceof ApiError && error.status === 429) return 'Too many attempts. Please wait a moment and try again.'
  return error?.message || fallback
}

function EmailOtpAuth() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [otp, setOtp] = useState('')
  const [secondsRemaining, setSecondsRemaining] = useState(0)
  const [step, setStep] = useState('email')
  const [authResponse, setAuthResponse] = useState(null)
  const { completeSignIn } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    if (!secondsRemaining) return undefined
    const timer = window.setInterval(() => setSecondsRemaining((seconds) => Math.max(seconds - 1, 0)), 1000)
    return () => window.clearInterval(timer)
  }, [secondsRemaining])

  const handleEmailSubmit = async (event) => {
    event.preventDefault()
    const normalizedEmail = email.trim().toLowerCase()
    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      setError('Enter a valid work email address to continue.')
      return
    }
    setError('')
    setIsLoading(true)
    try {
      await requestOtp(normalizedEmail)
      setEmail(normalizedEmail)
      setOtp('')
      setSecondsRemaining(RESEND_COOLDOWN_SECONDS)
      setStep('otp')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Unable to send a verification code. Please try again.'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleOtpSubmit = async (event) => {
    event.preventDefault()
    if (otp.length !== OTP_LENGTH) {
      setError('Enter all six digits to verify your email.')
      return
    }
    setError('')
    setIsLoading(true)
    try {
      const response = await verifyOtp(email, otp)
      setAuthResponse(response)
      setStep('success')
    } catch (verifyError) {
      setError(getErrorMessage(verifyError, 'Unable to verify the code. Please try again.'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleResend = async () => {
    setError('')
    setOtp('')
    setIsLoading(true)
    try {
      await requestOtp(email)
      setSecondsRemaining(RESEND_COOLDOWN_SECONDS)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Unable to resend the code. Please try again.'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleContinue = () => {
    completeSignIn(authResponse)
    const from = location.state?.from
    const destination = from ? `${from.pathname}${from.search || ''}` : '/retailer'
    navigate(destination, { replace: true })
  }

  const handleChangeEmail = () => {
    setError('')
    setOtp('')
    setSecondsRemaining(0)
    setStep('email')
  }

  return (
    <div className="auth-experience">
      <AuthIntro />
      <section className="auth-card" aria-live="polite">
        {step === 'email' && <EmailStep email={email} error={error} isLoading={isLoading} onChange={(value) => { setEmail(value); setError('') }} onSubmit={handleEmailSubmit} />}
        {step === 'otp' && <OtpStep email={email} error={error} isLoading={isLoading} onChangeEmail={handleChangeEmail} onChangeOtp={(value) => { setOtp(value); setError('') }} onResend={handleResend} onSubmit={handleOtpSubmit} otp={otp} secondsRemaining={secondsRemaining} />}
        {step === 'success' && <AuthSuccess email={email} onContinue={handleContinue} />}
      </section>
    </div>
  )
}

export default EmailOtpAuth
