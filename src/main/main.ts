import {app,BrowserWindow,dialog,ipcMain,session,protocol,screen} from 'electron';
import type {IpcMainInvokeEvent} from 'electron';
import fs from 'node:fs/promises';
import path from 'node:path';
import {normalizeConfig,httpsUrl} from '../core/config';
let window:BrowserWindow|null=null;
let writeQueue:Promise<unknown>=Promise.resolve();
const requests=new Map<string,AbortController>();
const root=path.join(__dirname,'../renderer');
const configPath=()=>path.join(app.getPath('userData'),'settings.json');
function rendererMime(file:string){
 const ext=path.extname(file).toLowerCase();
 return ext==='.html'?'text/html; charset=utf-8':ext==='.js'?'text/javascript; charset=utf-8':ext==='.css'?'text/css; charset=utf-8':ext==='.json'?'application/json; charset=utf-8':ext==='.svg'?'image/svg+xml':ext==='.png'?'image/png':'application/octet-stream';
}
protocol.registerSchemesAsPrivileged([{scheme:'opentv',privileges:{standard:true,secure:true,supportFetchAPI:true,stream:true}}]);
const hasSingleInstanceLock=app.requestSingleInstanceLock();
function trusted(event:IpcMainInvokeEvent){if(!window||event.sender!==window.webContents||event.senderFrame!==window.webContents.mainFrame||event.senderFrame?.url!=='opentv://app/index.html')throw new Error('Unauthorized IPC');}
function handle(name:string,fn:(...args:any[])=>unknown){ipcMain.handle(name,(event,...args)=>{trusted(event);return fn(...args);});}
async function readConfig(){
 try {
  const file=configPath();
  const stat=await fs.stat(file);
  if(stat.size>40*1024*1024)throw new Error('配置文件超过大小限制');
  return normalizeConfig(JSON.parse(await fs.readFile(file,'utf8')));
 }catch(error){
  if((error as NodeJS.ErrnoException).code==='ENOENT')return null;
  // A damaged settings file must not prevent the player from opening. Keep a
  // recoverable copy so the user can inspect it, then start from defaults.
  try {
   const file=configPath();
   const backup=`${file}.corrupt-${Date.now()}.json`;
   await fs.rename(file,backup);
  } catch {}
  return null;
 }
}
handle('config:load',readConfig);
handle('config:save',(input:unknown)=>{
 if(!input||typeof input!=='object')throw new Error('Invalid config');
 const value=normalizeConfig(input);const json=JSON.stringify(value);if(json.length>40*1024*1024)throw new Error('Config too large');
 writeQueue=writeQueue.catch(()=>{}).then(async()=>{await fs.mkdir(path.dirname(configPath()),{recursive:true});const tmp=configPath()+'.tmp';await fs.writeFile(tmp,JSON.stringify(value),{mode:0o600});await fs.rename(tmp,configPath());});return writeQueue;
});
handle('window:fullscreen',(value:unknown)=>{if(typeof value!=='boolean')throw new Error('Invalid boolean');window?.setFullScreen(value);return value;});
handle('window:is-fullscreen',()=>window?.isFullScreen()||false);
handle('app:version',()=>app.getVersion());
function validId(id:unknown):asserts id is string{if(typeof id!=='string'||id.length>100)throw new Error('Invalid request');}
handle('source:cancel',(id:unknown)=>{validId(id);requests.get(id)?.abort();requests.delete(id);});
handle('source:fetch',async(id:unknown,value:unknown,timeout:unknown)=>{
 validId(id);if(typeof value!=='string')throw new Error('Invalid URL');let url=httpsUrl(value);
 if(typeof timeout!=='number'||!Number.isFinite(timeout)||timeout<1000||timeout>60000)throw new Error('Invalid timeout');
 requests.get(id)?.abort();const controller=new AbortController();requests.set(id,controller);let timedOut=false;const timer=setTimeout(()=>{timedOut=true;controller.abort();},timeout);
 try{
  let response:Response|undefined;
  for(let i=0;i<6;i++){
   response=await fetch(url,{signal:controller.signal,redirect:'manual',credentials:'omit'});
   if([301,302,303,307,308].includes(response.status)){const destination=response.headers.get('location');await response.body?.cancel();if(!destination)throw new Error('Redirect invalid');url=httpsUrl(destination,url);continue;}break;
  }
  if(!response?.ok)throw new Error(`HTTP ${response?.status||0}`);
  const reader=response.body?.getReader();if(!reader)throw new Error('No body');const decoder=new TextDecoder();let size=0,text='';
  while(true){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>30*1024*1024){await reader.cancel();throw new Error('Source too large');}text+=decoder.decode(part.value,{stream:true});}
  return text+decoder.decode();
 }catch(error){
  if(controller.signal.aborted)throw new Error(timedOut?'来源请求超时':'来源请求已取消');
  const message=error instanceof Error?error.message:'';
  if(/^HTTP\s+\d{3}$/.test(message)||/^(来源必须使用|Redirect invalid|来源重定向)/.test(message))throw new Error(message);
  throw new Error('来源网络请求失败');
 }finally{clearTimeout(timer);if(requests.get(id)===controller)requests.delete(id);}
});
handle('source:import',async()=>{
 if(!window)return null;const result=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'M3U 播放列表',extensions:['m3u','m3u8','txt']}]});if(result.canceled)return null;
 const file=result.filePaths[0];if((await fs.stat(file)).size>15*1024*1024)throw new Error('文件超过 15 MB');return {name:path.basename(file),text:await fs.readFile(file,'utf8')};
});
async function createWindow(){
 let bounds:{width:number;height:number;x?:number;y?:number}={width:1440,height:900};try{const data=JSON.parse(await fs.readFile(path.join(app.getPath('userData'),'window.json'),'utf8'));if(Number.isFinite(data.width)&&Number.isFinite(data.height))bounds={width:Math.max(900,Math.min(2400,data.width)),height:Math.max(560,Math.min(1600,data.height))};if(Number.isFinite(data.x)&&Number.isFinite(data.y)){const x=Math.max(-10000,Math.min(10000,Math.round(data.x)));const y=Math.max(-10000,Math.min(10000,Math.round(data.y)));const visible=screen.getAllDisplays().some(display=>x<display.bounds.x+display.bounds.width&&x+bounds.width>display.bounds.x&&y<display.bounds.y+display.bounds.height&&y+bounds.height>display.bounds.y);if(visible){bounds.x=x;bounds.y=y;}}}catch{}
 const windowOptions={...bounds,minWidth:900,minHeight:560,backgroundColor:'#07090d',autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'../preload/preload.cjs'),contextIsolation:true,sandbox:true,nodeIntegration:false,webSecurity:true}} as Electron.BrowserWindowConstructorOptions;
 if(process.platform==='darwin'){windowOptions.titleBarStyle='hiddenInset';windowOptions.trafficLightPosition={x:18,y:18};}
 window=new BrowserWindow(windowOptions);
 window.webContents.setWindowOpenHandler(()=>({action:'deny'}));window.webContents.on('will-navigate',event=>event.preventDefault());window.webContents.on('will-attach-webview',event=>event.preventDefault());
 window.webContents.on('render-process-gone',()=>{if(window&&!window.isDestroyed())window.webContents.reload();});
 window.on('close',()=>{if(window&&!window.isFullScreen()){const size=window.getNormalBounds();void fs.mkdir(app.getPath('userData'),{recursive:true}).then(()=>fs.writeFile(path.join(app.getPath('userData'),'window.json'),JSON.stringify({width:size.width,height:size.height,x:size.x,y:size.y}),{mode:0o600})).catch(()=>{});}requests.forEach(c=>c.abort());requests.clear();});
 window.on('closed',()=>{window=null;});await window.loadURL('opentv://app/index.html');
}
if(!hasSingleInstanceLock){app.quit();}else{
 app.on('second-instance',()=>{if(window&&!window.isDestroyed()){if(window.isMinimized())window.restore();window.show();window.focus();}});
 app.whenReady().then(async()=>{
 protocol.handle('opentv',async request=>{const url=new URL(request.url);if(url.hostname!=='app')return new Response('',{status:403});const file=path.resolve(root,'.'+decodeURIComponent(url.pathname));if(!file.startsWith(root+path.sep))return new Response('',{status:403});try{return new Response(await fs.readFile(file),{headers:{'content-type':rendererMime(file)}});}catch{return new Response('',{status:404});}});
 session.defaultSession.setPermissionRequestHandler((_web,_permission,callback)=>callback(false));session.defaultSession.setPermissionCheckHandler(()=>false);
 session.defaultSession.webRequest.onBeforeRequest({urls:['http://*/*']},(_details,callback)=>callback({cancel:true}));
 await createWindow();app.on('activate',()=>{if(!window)void createWindow();});
 });
}
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
