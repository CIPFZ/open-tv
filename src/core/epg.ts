import { XMLParser } from 'fast-xml-parser';
import type { Program } from '../shared/models';
export function xmltvTime(value: string): number {
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})?$/.exec(value);
  if (!m) return NaN;
  const [,y,mo,d,h,mi,s='00',z='+0000']=m;
  const offset = (Number(z.slice(1,3))*60+Number(z.slice(3,5)))*(z[0]==='-'?-1:1);
  return Date.UTC(+y,+mo-1,+d,+h,+mi,+s)-offset*60000;
}
function text(v: unknown): string { if (Array.isArray(v)) return text(v[0]); if(v && typeof v==='object') return text((v as Record<string,unknown>)['#text']); return v==null?'':String(v); }
export function parseXmltv(xml: string): Program[] {
  if (xml.length>30*1024*1024 || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('XMLTV 超出限制或包含不支持的实体声明');
  const tree = new XMLParser({ignoreAttributes:false,attributeNamePrefix:'@_',parseTagValue:false,processEntities:false}).parse(xml);
  if (!tree.tv) throw new Error('没有找到 XMLTV tv 节点');
  const items=tree.tv.programme ? (Array.isArray(tree.tv.programme)?tree.tv.programme:[tree.tv.programme]) : [];
  return items.map((p:Record<string,unknown>)=>({channelId:String(p['@_channel']||''),startAt:xmltvTime(String(p['@_start'])),endAt:xmltvTime(String(p['@_stop'])),title:text(p.title),description:text(p.desc)}))
    .filter((p:Program)=>p.channelId && p.title && Number.isFinite(p.startAt) && p.endAt>p.startAt).sort((a:Program,b:Program)=>a.startAt-b.startAt);
}
export function nowNext(programs: Program[], id: string, time=Date.now()) {
  const list=programs.filter(p=>p.channelId===id);
  return {now:list.find(p=>p.startAt<=time&&p.endAt>time),next:list.find(p=>p.startAt>time)};
}
