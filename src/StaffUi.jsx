import { Check, CircleAlert, RefreshCw } from 'lucide-react'

export function PageHeader({ eyebrow, title, description, action }) {
  return <div className="page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>
    {description && <p className="page-header__description">{description}</p>}</div>{action}</div>
}

export function StatusNotice({ text, tone = 'success', onRetry }) {
  if (!text) return null
  const Icon = tone === 'error' ? CircleAlert : Check
  return <div className={`staff-notice staff-notice--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
    <Icon size={17} /><span>{text}</span>{onRetry && <button className="text-button" type="button" onClick={onRetry}><RefreshCw size={15} />刷新状态</button>}
  </div>
}
