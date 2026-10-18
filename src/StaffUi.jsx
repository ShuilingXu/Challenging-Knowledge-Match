import { Check, CircleAlert, RefreshCw, X } from 'lucide-react'

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

/** 破坏性操作前的统一确认层，样式复用现有 .dialog 主题。 */
export function ConfirmDialog({ title, description, details = [], confirmLabel = '确认', cancelLabel = '取消', tone = 'default', busy = false, onConfirm, onCancel }) {
  return <div className="dialog-backdrop" role="presentation">
    <section className="dialog dialog--confirm" role="dialog" aria-modal="true" aria-label={title}>
      <header>
        <h2>{title}</h2>
        <button className="toolbar-icon" type="button" aria-label="关闭" onClick={onCancel}>
          <X size={18} />
        </button>
      </header>
      {description && <p className="dialog-confirm__text">{description}</p>}
      {details.length > 0 && <ul className="dialog-confirm__list">{details.map((item, index) => <li key={index}>{item}</li>)}</ul>}
      <div className="dialog-actions">
        <button className="secondary-button" type="button" onClick={onCancel} disabled={busy}>{cancelLabel}</button>
        <button className={tone === 'danger' ? 'danger-button' : 'primary-button'} type="button" onClick={onConfirm} disabled={busy}>
          {busy ? '处理中…' : confirmLabel}
        </button>
      </div>
    </section>
  </div>
}
