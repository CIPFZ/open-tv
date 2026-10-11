import { httpsUrl } from './config';
import type { Channel } from '../shared/models';
export function stableId(value: string): string {
  let a = 2166136261; let b = 5381;
  for (const c of value) { a = Math.imul(a ^ c.charCodeAt(0), 16777619); b = Math.imul(b,33) ^ c.charCodeAt(0); }
  return `${(a>>>0).toString(36)}${(b>>>0).toString(36)}`;
}
export function parseM3U(text: string, playlistId: string, base?: string): {channels: Channel[]; skipped: number} {
  if (text.length > 15 * 1024 * 1024) throw new Error('播放列表超过 15 MB 上限');
  if (!text.trimStart().startsWith('#EXTM3U')) throw new Error('文件不是有效的 M3U/M3U8');
  if (/#EXT-X-(TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE)/.test(text)) {
    if (!base) throw new Error('这是 HLS 媒体清单，请使用远程 URL 添加');
    return {channels: [{id:`${playlistId}-${stableId(base)}`,playlistId,number:1,name:'HLS 直播',url:httpsUrl(base),group:'直播',tvgId:'',logo:'',language:'',country:''}],skipped:0};
  }
  const channels: Channel[] = []; const seen = new Set<string>(); let skipped = 0;
  let info: {name:string; attrs:Record<string,string>} | undefined;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (line.startsWith('#EXTINF:')) {
      if (info) skipped++;
      let quote = ''; let comma = -1;
      for (let i=0;i<line.length;i++) { const c=line[i]; if ((c==='"'||c==="'") && (!quote||quote===c)) quote=quote?'':c; else if(c===','&&!quote){comma=i;break;} }
      const attrs:Record<string,string> = {};
      for (const match of line.slice(0,comma < 0 ? undefined : comma).matchAll(/([\w-]+)=(?:"([^"]*)"|'([^']*)')/g)) attrs[match[1].toLowerCase()] = match[2] ?? match[3];
      info={name:comma < 0 ? attrs['tvg-name'] || '未命名频道' : line.slice(comma+1).trim(), attrs};
    } else if (line && !line.startsWith('#') && info) {
      const {name,attrs} = info; info=undefined;
      try {
        const url = httpsUrl(line,base); if(seen.has(url)) continue; seen.add(url);
        const number = Number(attrs['tvg-chno']);
        channels.push({ id:`${playlistId}-${stableId(url)}`,playlistId,number:Number.isFinite(number)&&number>0?number:channels.length+1,name:name||'未命名频道',url,
          group:attrs['group-title']&&attrs['group-title']!=='Undefined'?attrs['group-title']:'其他',tvgId:attrs['tvg-id']||'',logo:attrs['tvg-logo']||'',language:attrs['tvg-language']||'',country:attrs['tvg-country']||'' });
      } catch { skipped++; }
    }
  }
  return {channels, skipped: skipped + (info?1:0)};
}
