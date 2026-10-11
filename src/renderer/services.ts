import type {AppConfig,Channel,Program} from '../shared/models';
import {normalizeConfig,httpsUrl} from '../core/config';
import {parseM3U} from '../core/m3u';
import {parseXmltv} from '../core/epg';
const api=window.openTvAPI;
export async function loadConfig():Promise<AppConfig> {
  if(api){try{return normalizeConfig(await api.config.load());}catch{return normalizeConfig(null);}}
  try {return normalizeConfig(JSON.parse(localStorage.getItem('open-tv-state-v3')||localStorage.getItem('open-tv-state-v2')||'{}'));}catch{return normalizeConfig(null);}
}
export async function saveConfig(value:AppConfig) {if(api)await api.config.save(value);else localStorage.setItem('open-tv-state-v3',JSON.stringify(value));}
const requests=new Map<string,AbortController>();
export async function cancelSource(id:string) {requests.get(id)?.abort();requests.delete(id);if(api)await api.source.cancel(id);}
export async function fetchSource(id:string,url:string,timeout:number):Promise<string> {
  httpsUrl(url);await cancelSource(id);
  if(api)return api.source.fetchText(id,url,timeout);
  const controller=new AbortController();requests.set(id,controller);
  const timer=setTimeout(()=>controller.abort(),timeout);
  try {
    const response=await fetch(url,{signal:controller.signal,credentials:'omit',referrerPolicy:'no-referrer'});
    if(!response.ok)throw new Error(`HTTP ${response.status}`);
    httpsUrl(response.url);
    const reader=response.body?.getReader();if(!reader)throw new Error('无响应内容');
    const decoder=new TextDecoder();let size=0,text='';
    while(true){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>30*1024*1024){await reader.cancel();throw new Error('来源超过 30 MB');}text+=decoder.decode(part.value,{stream:true});}
    return text+decoder.decode();
  }finally{clearTimeout(timer);if(requests.get(id)===controller)requests.delete(id);}
}
let worker:Worker|undefined;
function ensureWorker(){
  if(worker)return worker;
  try{worker=new Worker(new URL('./parser.worker.ts',import.meta.url),{type:'module'});}
  catch{return undefined;}
  worker.onmessage=({data})=>{const task=pending.get(data.id);pending.delete(data.id);if(data.error)task?.reject(new Error(data.error));else task?.resolve(data.result);};
  worker.onerror=()=>{pending.forEach(task=>task.reject(new Error('解析进程异常，请重新导入')));pending.clear();worker=undefined;};
  return worker;
}
let sequence=0; const pending=new Map<number,{resolve:(v:any)=>void;reject:(e:Error)=>void}>();
function parse<T>(payload:Record<string,unknown>):Promise<T>{
 return new Promise((resolve,reject)=>{
  const current=ensureWorker();
  if(!current){queueMicrotask(()=>{try{resolve((payload.kind==='epg'?parseXmltv(String(payload.text||'')):parseM3U(String(payload.text||''),String(payload.playlistId||''),payload.url?String(payload.url):undefined)) as T);}catch(error){reject(error instanceof Error?error:new Error('解析失败'));}});return;}
  const id=++sequence;pending.set(id,{resolve,reject});
  try{current.postMessage({...payload,id});}catch(error){pending.delete(id);worker=undefined;reject(error instanceof Error?error:new Error('解析进程异常，请重新导入'));}
 });
}
export const parsePlaylist=(text:string,playlistId:string,url?:string)=>parse<{channels:Channel[];skipped:number}>({kind:'m3u',text,playlistId,url});
export const parseEpg=(text:string)=>parse<Program[]>({kind:'epg',text});
export function disposeServices(){worker?.terminate();worker=undefined;pending.forEach(task=>task.reject(new Error('解析服务已关闭')));pending.clear();for(const id of requests.keys())void cancelSource(id);}
