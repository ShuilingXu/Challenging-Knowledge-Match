import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { request, getAccessToken, createIdempotencyKey } from './api';
import { PageHeader } from './StaffUi';
import { parseTicketCsv, ticketRows, ticketToken, ticketFilename } from './checkin-import';
import './checkin.css';

const path = (id) => `/api/activities/${id}/checkin`;
const save = (data, name) => {
  const url = URL.createObjectURL(data instanceof Blob ? data : new Blob([data]));
  const a = document.createElement('a'); a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
};
const ticketUrl = (t) => `${window.location.origin}/checkin/${t.activityId}/${t.token}`;

function Scanner({ onScan, onError }) {
  const video = useRef(null), controls = useRef(null), reader = useRef(null), active = useRef(false);
  const [running, setRunning] = useState(false);
  const stop = useCallback(() => { active.current = false; controls.current?.stop(); controls.current = null; setRunning(false); }, []);
  useEffect(() => () => { active.current = false; controls.current?.stop(); }, []);
  const start = async () => {
    try {
      active.current = true; setRunning(true);
      const { BrowserQRCodeReader } = await import('@zxing/browser');
      reader.current = new BrowserQRCodeReader();
      const result = await reader.current.decodeFromConstraints({ video: { facingMode: { ideal: 'environment' } } }, video.current, (result, error, scanner) => {
        if (result && active.current) { active.current = false; scanner.stop(); setRunning(false); onScan(result.getText()); }
      });
      if (!active.current) result.stop(); else controls.current = result;
    } catch { stop(); onError('无法打开摄像头，请允许相机权限，或上传二维码图片、粘贴签到链接。'); }
  };
  const image = async (file) => {
    if (!file) return;
    stop(); const url = URL.createObjectURL(file);
    try { const { BrowserQRCodeReader } = await import('@zxing/browser'); const result = await new BrowserQRCodeReader().decodeFromImageUrl(url); onScan(result.getText()); }
    catch { onError('未识别到二维码，请上传清晰的签到码图片。'); }
    finally { URL.revokeObjectURL(url); }
  };
  return <div className="checkin-scanner"><video ref={video} hidden={!running} muted playsInline /><div className="checkin-actions"><button type="button" className="secondary-button" onClick={running ? stop : start}>{running ? '关闭摄像头' : '打开摄像头扫码'}</button><label className="secondary-button">识别二维码图片<input type="file" hidden accept="image/*" onChange={(e) => { image(e.target.files[0]); e.target.value = ''; }} /></label></div></div>;
}

function Redemption({ activityId, ticket, onDone, onError }) {
  const [busy, setBusy] = useState(false);
  const key = useRef(createIdempotencyKey());
  const redeem = async () => {
    setBusy(true);
    try { const t = await request(`${path(activityId)}/redeem`, { method: 'POST', body: { token: ticket.token, requestId: key.current } }); onDone(t); }
    catch (e) { onError(e.message); } finally { setBusy(false); }
  };
  return <article className="checkin-card"><h2>确认检票 · {ticket.name}</h2><p>{ticket.contact} · {ticket.activityName}</p><p>{ticket.type === 'MULTI' ? '多次票（不限次数）' : '单次票'} · 已核销 {ticket.checkinCount} 次</p>{ticket.note && <p>备注：{ticket.note}</p>}<button className="primary-button" disabled={busy || ticket.type === 'SINGLE' && ticket.checkinCount > 0} onClick={redeem}>{busy ? '正在核销' : ticket.type === 'SINGLE' && ticket.checkinCount > 0 ? '已核销，无法重复入场' : '确认核销入场'}</button></article>;
}

export function TicketLanding() {
  const { activityId, token } = useParams();
  const [ticket, setTicket] = useState(null), [redirect, setRedirect] = useState(!getAccessToken()), [error, setError] = useState(''), [done, setDone] = useState(false);
  useEffect(() => {
    if (!getAccessToken()) return;
    let live = true;
    request(`${path(activityId)}/tickets/${encodeURIComponent(token)}`).then((t) => { if (live) setTicket(t); }).catch((e) => { if (!live) return; if ([401,403].includes(e.status)) setRedirect(true); else setError(e.message); });
    return () => { live = false; };
  }, [activityId, token]);
  if (redirect) return <Navigate to={`/join/${activityId}`} replace />;
  return <main className="checkin-landing"><h1>现场检票</h1>{error && <p role="alert" className="form-error">{error}</p>}{done ? <article className="checkin-card" role="status"><h2>核销成功</h2><p>{ticket.name} · 累计 {ticket.checkinCount} 次</p><Link to="/admin/checkin">返回签到表，继续检票</Link></article> : ticket ? <Redemption activityId={activityId} ticket={ticket} onDone={(t) => { setTicket(t); setDone(true); }} onError={setError} /> : !error && <p>正在确认检票权限…</p>}</main>;
}

export function RegistrationControl({ activity, reload }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  return <section className="checkin-card"><h2>报名控制</h2><label className="checkin-switch"><input type="checkbox" checked={activity.registrationAllowed !== false} disabled={busy} onChange={async (e) => {
    const allowed = e.target.checked; setBusy(true); setError('');
    try { await request(`${path(activity.id)}/registration`, { method: 'PUT', body: { registrationAllowed: allowed } }); await reload(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }} />允许新人员报名</label><p>关闭后，仅允许已录入签到表或参与者名单的人员报名；活动原有的暂停、结束控制继续生效。</p>{error && <p className="form-error" role="alert">{error}</p>}</section>;
}

export function CheckinPage({ activity, activities, canManage, reload, setActivityId }) {
  const id = activity?.id;
  const [tickets, setTickets] = useState([]), [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const [query, setQuery] = useState(''), [filter, setFilter] = useState('all'), [code, setCode] = useState(''), [selected, setSelected] = useState(null), [preview, setPreview] = useState(null);
  const [form, setForm] = useState({ name: '', contact: '', type: 'SINGLE', note: '' });
  const load = useCallback(async () => { if (!id) return; try { setTickets(await request(path(id))); } catch (e) { setError(e.message); } }, [id]);
  useEffect(() => { load(); const interval = setInterval(load, 15000); return () => clearInterval(interval); }, [load]);
  if (!id || ['EVENT','TURTLE_SOUP'].includes(activity.activityType)) return <div className="page-content"><h1>签到与检票</h1><p>请先选择接受报名的会场。</p></div>;
  const run = async (action) => { setError(''); setNotice(''); setBusy(true); try { await action(); } catch (e) { setError(e.message); } finally { setBusy(false); } };
  const create = (entries) => run(async () => { setTickets(await request(path(id), { method: 'POST', body: { entries } })); setNotice(`已录入 ${entries.length} 人并关联参与者名单`); setPreview(null); setForm({ name: '', contact: '', type: 'SINGLE', note: '' }); });
  const template = async (excel) => run(async () => {
    const rows = [['昵称','联系方式','场次','类型','备注']];
    if (excel) {
      const { default: ExcelJS } = await import('exceljs'); const book = new ExcelJS.Workbook(); const sheet = book.addWorksheet('签到名单'); sheet.addRows(rows); sheet.getRow(1).font = { bold: true }; sheet.columns.forEach((c) => { c.width = 25; });
      const help = book.addWorksheet('填写说明'); help.addRows([['填写说明'],['联系方式请按文本格式填写，保留前导零'],['类型填写：单次 或 多次；多次票不限核销次数'],['场次填写下列 ID 或场次名称'],[activity.id,activity.name],['重复联系方式或已有签到票将拒绝整批导入']]); help.columns.forEach((c) => { c.width = 60; });
      sheet.getColumn(2).numFmt = '@'; sheet.getColumn(3).numFmt = '@';
      save(await book.xlsx.writeBuffer(), '签到名单导入模板.xlsx');
    } else save('\ufeff' + rows[0].join(',') + '\r\n', '签到名单导入模板.csv');
  });
  const importFile = async (file) => {
    if (!file) return;
    await run(async () => {
      if (file.size > 10 * 1024 * 1024) throw new Error('导入文件不能超过 10 MB');
      let rows;
      if (/\.csv$/i.test(file.name)) rows = parseTicketCsv(await file.text());
      else if (/\.xlsx$/i.test(file.name)) { const { default: ExcelJS } = await import('exceljs'); const book = new ExcelJS.Workbook(); await book.xlsx.load(await file.arrayBuffer()); const sheet = book.worksheets[0]; if (!sheet) throw new Error('文件中没有工作表'); rows = []; sheet.eachRow((r) => rows.push(Array.from({ length: Math.max(r.cellCount,5) }, (_,i) => r.getCell(i+1).text))); }
      else throw new Error('请选择 .xlsx 或 UTF-8 编码的 .csv 文件');
      setPreview(ticketRows(rows, activity));
    });
  };
  const download = () => run(async () => {
    const [{ default: JSZip }, { default: QRCode }] = await Promise.all([import('jszip'),import('qrcode')]); const zip = new JSZip();
    for (const t of tickets) {
      const qr = await QRCode.toDataURL(ticketUrl(t), { width: 600, margin: 4, errorCorrectionLevel: 'M' });
      const canvas = document.createElement('canvas'); canvas.width = 720; canvas.height = 850; const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0,0,720,850); const img = new Image(); img.src = qr; await img.decode(); ctx.drawImage(img,60,0); ctx.fillStyle = '#111827'; ctx.textAlign = 'center'; ctx.font = 'bold 28px sans-serif'; ctx.fillText(t.name,360,640,660); ctx.font = '22px sans-serif'; ctx.fillText(t.contact,360,680,660); ctx.fillText(t.activityName,360,720,660); ctx.fillText(t.type === 'MULTI' ? '多次票 · 不限次数' : '单次票',360,760,660);
      let filename = ticketFilename(t); if (zip.file(`${filename}.png`)) filename += `_${t.id}`;
      zip.file(`${filename}.png`,canvas.toDataURL('image/png').split(',')[1],{ base64: true });
    }
    save(await zip.generateAsync({ type: 'blob' }), `${activity.name}_签到码.zip`);
  });
  const scan = async (value) => {
    setError(''); setNotice(''); setSelected(null);
    try { const token = ticketToken(value, id); const t = await request(`${path(id)}/tickets/${encodeURIComponent(token)}`); setSelected(t); setCode(''); }
    catch (e) { setError(e.message); }
  };
  const shown = tickets.filter((t) => (filter === 'all' || (filter === 'arrived' ? t.checkinCount > 0 : t.checkinCount === 0)) && `${t.name} ${t.contact} ${t.note}`.toLowerCase().includes(query.toLowerCase()));
  return <div className="page-content checkin-page"><PageHeader eyebrow="现场入场管理" title="签到与检票" description="管理员维护名单，工作人员扫码后确认核销。名单自动同步为参与者。" />
    {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status" className="checkin-notice">{notice}</p>}
    <section className="checkin-card"><label>场次<select value={id} onChange={(e) => setActivityId(e.target.value)}>{activities.filter((a) => ['VENUE','QUIZ'].includes(a.activityType) && (a.activityType === 'VENUE' || !activities.some((v) => v.id === a.parentActivityId && v.activityType === 'VENUE'))).map((a) => <option value={a.id} key={a.id}>{a.name}</option>)}</select></label>
    {canManage && <label className="checkin-switch"><input type="checkbox" checked={activity.registrationAllowed !== false} disabled={busy} onChange={(e) => run(async () => { await request(`${path(id)}/registration`, { method: 'PUT', body: { registrationAllowed: e.target.checked } }); await reload(); })} />允许新人员报名</label>}<p>{activity.registrationAllowed === false ? '当前仅允许已录入签到表或参与者名单的人员报名。' : '当前允许新人员报名；暂停、结束状态仍按原有活动控制生效。'}</p></section>
    <section className="checkin-card"><h2>扫码检票</h2><Scanner onScan={scan} onError={setError} /><form className="checkin-actions" onSubmit={(e) => { e.preventDefault(); scan(code); }}><input aria-label="签到码或链接" placeholder="粘贴签到链接或扫码枪输入" value={code} onChange={(e) => setCode(e.target.value)} required /><button className="secondary-button">查看门票</button></form>{selected && <Redemption key={selected.id} activityId={id} ticket={selected} onError={setError} onDone={(t) => { setSelected(null); setNotice(`${t.name} 核销成功，累计入场 ${t.checkinCount} 次`); load(); }} />}</section>
    {canManage && <section className="checkin-card"><h2>录入签到名单</h2><form className="checkin-entry" onSubmit={(e) => { e.preventDefault(); create([{ ...form, activity: activity.id }]); }}><label>昵称<input required maxLength={120} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label><label>联系方式<input required maxLength={160} value={form.contact} onChange={(e) => setForm({ ...form, contact: e.target.value })} /></label><label>类型<select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="SINGLE">单次</option><option value="MULTI">多次（不限次数）</option></select></label><label>备注<input maxLength={1000} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></label><button className="primary-button" disabled={busy}>录入并生成签到码</button></form><div className="checkin-actions"><button className="secondary-button" disabled={busy} onClick={() => template(true)}>下载 Excel 模板</button><button className="secondary-button" disabled={busy} onClick={() => template(false)}>下载 CSV 模板</button><label className="secondary-button">导入 Excel / CSV<input type="file" hidden accept=".xlsx,.csv" disabled={busy} onChange={(e) => { importFile(e.target.files[0]); e.target.value = ''; }} /></label><button className="secondary-button" disabled={busy || !tickets.length} onClick={download}>{busy ? '处理中…' : '打包下载全部签到码'}</button></div><p>场次填写上方所选场次的名称或 ID：{activity.id}。文件名：昵称_联系方式_场次.png。</p>{preview && <div><p>预览：共 {preview.length} 人，全部导入当前场次。{preview.slice(0,5).map((r) => `${r.name}（${r.contact}）`).join('、')}</p><button className="primary-button" disabled={busy} onClick={() => create(preview)}>确认导入 {preview.length} 人</button><button className="text-button" disabled={busy} onClick={() => setPreview(null)}>取消</button></div>}</section>}
    <section className="checkin-card"><h2>签到表</h2><p>总人数 {tickets.length} · 已到 {tickets.filter((t) => t.checkinCount > 0).length} · 未到 {tickets.filter((t) => t.checkinCount === 0).length}</p><div className="checkin-actions"><input aria-label="搜索签到名单" placeholder="搜索昵称、联系方式、备注" value={query} onChange={(e) => setQuery(e.target.value)} /><select aria-label="到场状态" value={filter} onChange={(e) => setFilter(e.target.value)}><option value="all">全部</option><option value="absent">未到场</option><option value="arrived">已到场</option></select><button className="secondary-button" onClick={load}>刷新</button></div><div className="checkin-table"><table><thead><tr>{['昵称','联系方式','场次','类型','备注','状态','核销次数','最近核销'].map((h) => <th key={h}>{h}</th>)}</tr></thead><tbody>{shown.map((t) => <tr key={t.id}><td>{t.name}</td><td>{t.contact}</td><td>{t.activityName}</td><td>{t.type === 'MULTI' ? '多次' : '单次'}</td><td>{t.note || '—'}</td><td>{t.checkinCount ? '已到' : '未到'}</td><td>{t.checkinCount}</td><td>{t.lastCheckinAt ? new Date(t.lastCheckinAt).toLocaleString() : '—'}</td></tr>)}</tbody></table></div>{!shown.length && <p>暂无符合条件的签到记录。</p>}</section>
  </div>;
}
