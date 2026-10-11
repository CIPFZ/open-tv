import { parseM3U } from '../core/m3u';
import { parseXmltv } from '../core/epg';
self.onmessage = (event) => {
  const {id,kind,text,playlistId,url} = event.data;
  try { self.postMessage({id,result:kind==='epg'?parseXmltv(text):parseM3U(text,playlistId,url)}); }
  catch { self.postMessage({id,error:kind==='epg'?'XMLTV 格式不正确或超过限制':'列表无效、过大，或本地 HLS 需改用远程地址'}); }
};
