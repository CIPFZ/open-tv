import {contextBridge,ipcRenderer} from 'electron';
import type {DesktopAPI} from '../shared/models';
const api:DesktopAPI={
 config:{load:()=>ipcRenderer.invoke('config:load'),save:value=>ipcRenderer.invoke('config:save',value)},
 source:{fetchText:(id,url,timeout)=>ipcRenderer.invoke('source:fetch',id,url,timeout),cancel:id=>ipcRenderer.invoke('source:cancel',id),importFile:()=>ipcRenderer.invoke('source:import')},
 window:{fullscreen:value=>ipcRenderer.invoke('window:fullscreen',value),isFullscreen:()=>ipcRenderer.invoke('window:is-fullscreen')},
 app:{version:()=>ipcRenderer.invoke('app:version')}
};contextBridge.exposeInMainWorld('openTvAPI',api);
