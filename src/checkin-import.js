export function parseTicketCsv(text) {
  const rows = []; let row = [], value = '', quoted = false;
  text = String(text).replace(/^\uFEFF/, '');
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) { if (c === '"' && text[i + 1] === '"') { value += '"'; i++; } else if (c === '"') quoted = false; else value += c; }
    else if (c === '"' && value === '') quoted = true;
    else if (c === ',') { row.push(value); value = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(value); rows.push(row); row = []; value = ''; }
    else value += c;
  }
  if (quoted) throw new Error('CSV 引号未闭合');
  if (value || row.length) { row.push(value); rows.push(row); }
  return rows;
}
export function ticketRows(rows, activity) {
  const clean = rows.filter((r) => r.some((c) => String(c ?? '').trim()));
  if (clean.length < 2) throw new Error('导入文件没有人员记录');
  const headers = clean[0].map((v) => String(v).replace(/^\uFEFF/, '').trim());
  const names = ['昵称','联系方式','场次','类型','备注'];
  if (names.some((n) => !headers.includes(n))) throw new Error('请使用导入模板，必须包含昵称、联系方式、场次、类型、备注五列');
  if (clean.length > 5001) throw new Error('每次最多导入 5000 人');
  const seen = new Set();
  return clean.slice(1).map((r, i) => {
    const [name, rawContact, scene, rawType, note] = names.map((n) => String(r[headers.indexOf(n)] ?? '').trim());
    const contact = rawContact.replace(/[\s-]/g, '');
    const type = ({ '单次': 'SINGLE', '多次': 'MULTI', SINGLE: 'SINGLE', MULTI: 'MULTI' })[rawType];
    const fail = (why) => { throw new Error(`第 ${i + 2} 行：${why}`); };
    if (!name || name.length > 120 || contact.length < 3 || contact.length > 160) fail('昵称或联系方式不正确');
    if (scene !== activity.id && scene !== activity.name) fail('场次与当前所选场次不符');
    if (!type) fail('类型必须为单次或多次');
    if (note.length > 1000) fail('备注不能超过 1000 字');
    if (seen.has(contact)) fail('联系方式重复'); seen.add(contact);
    return { name, contact, activity: activity.id, type, note };
  });
}
export function ticketToken(value, activityId) {
  value = value.trim();
  if (/^[0-9a-f-]{36}[0-9a-f]{16}$/i.test(value)) return value;
  let url; try { url = new URL(value); } catch { throw new Error('请输入有效的签到码或签到链接'); }
  const parts = url.pathname.split('/');
  if (parts.length !== 4 || parts[1] !== 'checkin' || parts[2] !== activityId) throw new Error('签到码不属于当前场次，请切换场次后检票');
  if (!/^[0-9a-f-]{36}[0-9a-f]{16}$/i.test(parts[3])) throw new Error('签到码格式不正确');
  return parts[3];
}
export function ticketFilename(t) {
  return [t.name,t.contact,t.activityName].map((v) => String(v).replace(/[<>:"/\\|?*\x00-\x1f]/g,'_').replace(/[. ]+$/g,'').slice(0,60) || '_').join('_');
}
