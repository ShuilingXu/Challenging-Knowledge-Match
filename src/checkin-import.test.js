import { describe, it, expect } from 'vitest';
import { parseTicketCsv, ticketRows, ticketToken, ticketFilename } from './checkin-import';
const activity = { id: '49fd4be3-2e60-4122-b409-29834bcb1ef1', name: '秋日场次' };
const header = ['昵称','联系方式','场次','类型','备注'];
describe('签到名单导入与扫码', () => {
  it('保留 CSV 前导零，支持 BOM、逗号、换行、双引号与 CRLF', () => {
    const rows = parseTicketCsv('\ufeff昵称,联系方式,场次,类型,备注\r\n"小,明",001234,秋日场次,多次,"备注\n""同行"""\r\n');
    expect(ticketRows(rows,activity)).toEqual([{ name: '小,明', contact: '001234', activity: activity.id, type: 'MULTI', note: '备注\n"同行"' }]);
  });
  it('列可以换序，场次支持 ID，空行不生成门票', () => {
    const rows = [['类型','场次','备注','联系方式','昵称'],['单次',activity.id,'','phone-123','小明'],['','','','','']];
    expect(ticketRows(rows,activity)[0]).toMatchObject({ name: '小明', contact: 'phone123', type: 'SINGLE' });
  });
  it('拒绝跨场次、无效类型、缺列、重复联系方式与损坏的 CSV', () => {
    expect(() => ticketRows([header,['A','123','其他场次','单次','']],activity)).toThrow('场次');
    expect(() => ticketRows([header,['A','123',activity.id,'限三次','']],activity)).toThrow('类型');
    expect(() => ticketRows([['昵称'],['A']],activity)).toThrow('模板');
    expect(() => ticketRows([header,['A','123-456',activity.id,'单次',''],['B','123456',activity.id,'多次','']],activity)).toThrow('重复');
    expect(() => parseTicketCsv('"未闭合')).toThrow('未闭合');
  });
  it('扫码仅接受本场次的有效静态码', () => {
    const token = 'fbe74340-3712-4d6e-a476-9222432615320123456789abcdef';
    expect(ticketToken(`https://match.soyorin.love/checkin/${activity.id}/${token}`,activity.id)).toBe(token);
    expect(ticketToken(token,activity.id)).toBe(token);
    expect(() => ticketToken(`https://match.soyorin.love/checkin/other/${token}`,activity.id)).toThrow('当前场次');
    expect(() => ticketToken('javascript:alert(1)',activity.id)).toThrow();
  });
  it('下载命名移除不兼容字符并保留人员和场次', () => {
    expect(ticketFilename({name:'小/明',contact:'00123',activityName:'秋日:场次'})).toBe('小_明_00123_秋日_场次');
  });
});
