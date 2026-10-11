import Hls from 'hls.js';
import { httpsUrl } from '../../core/config';
import type { PlaybackAdapter, PlaybackStatus, PlaybackError, PlaybackPreferences } from '../../shared/models';
export class HtmlHlsAdapter implements PlaybackAdapter {
  private hls?: Hls; private generation=0; private cleanup: (()=>void)[]=[];
  private timer?: ReturnType<typeof setTimeout>; private retryTimer?: ReturnType<typeof setTimeout>;
  private attempts=0; private url=''; private stopped=true;
  constructor(private video:HTMLVideoElement, private preferences:()=>PlaybackPreferences,
    private status:(state:PlaybackStatus,error?:PlaybackError)=>void, private fullscreen:()=>Promise<void>) {}
  async load(url:string, autoplay=true):Promise<void> {
    this.destroy(); this.attempts=0; this.url=httpsUrl(url); this.stopped=false; this.open(autoplay);
  }
  private open(autoplay:boolean) {
    this.release(); const version=++this.generation; const prefs=this.preferences();
    const active=()=>version===this.generation&&!this.stopped;
    const on=(name:string,fn:()=>void)=> { const handler=()=>{if(active())fn();}; this.video.addEventListener(name,handler); this.cleanup.push(()=>this.video.removeEventListener(name,handler)); };
    const timeout=()=> {clearTimeout(this.timer); this.timer=setTimeout(()=>{if(active())this.fail({code:'timeout',message:'连接或缓冲超时，请重试或换台',retryable:true});},prefs.networkTimeout);};
    on('playing',()=>{clearTimeout(this.timer); this.attempts=0; this.status('playing');});
    on('waiting',()=>{this.status('buffering');timeout();});
    on('pause',()=>{clearTimeout(this.timer);this.status('paused');});
    on('error',()=>this.fail({code:this.video.error?.code===3?'decode':'network',message:'频道网络请求或解码失败，请重试或换台',retryable:true}));
    on('ended',()=>this.fail({code:'network',message:'直播已结束或连接中断',retryable:true}));
    this.status(autoplay?'loading':'paused'); if(autoplay)timeout();
    const start=()=>{if(active()&&autoplay)void this.play();};
    const nativeHls=Boolean(this.video.canPlayType('application/vnd.apple.mpegurl'));
    const direct=/\.(mp4|webm)(\?|$)/i.test(this.url);
    if (direct || prefs.backend==='native' || (prefs.backend==='auto'&&nativeHls)) {
      this.video.src=this.url; if(autoplay)start(); else this.video.load();
    } else if(Hls.isSupported()) {
      const hls=this.hls=new Hls({enableWorker:true,lowLatencyMode:true,debug:false,
        manifestLoadPolicy:{default:{maxTimeToFirstByteMs:prefs.networkTimeout,maxLoadTimeMs:prefs.networkTimeout,timeoutRetry:null,errorRetry:null}},
        fragLoadPolicy:{default:{maxTimeToFirstByteMs:prefs.networkTimeout,maxLoadTimeMs:prefs.networkTimeout,timeoutRetry:null,errorRetry:null}}});
      hls.on(Hls.Events.MANIFEST_PARSED,start);
      hls.on(Hls.Events.ERROR,(_event,data)=>{
        if(!active()||!data.fatal)return;
        const code=data.response?.code;
        this.fail({code:code===401||code===403||code===451?'restricted':data.type===Hls.ErrorTypes.MEDIA_ERROR?'decode':'network',
          message:code===401||code===403||code===451?'源站拒绝访问，可能需要授权或存在地区限制；应用不会绕过限制':'HLS 网络请求或解码失败；浏览器也可能受跨域限制',retryable:![401,403,404,410,451].includes(code||0)});
      });
      hls.attachMedia(this.video); hls.loadSource(this.url);
    } else this.fail({code:'unsupported',message:'当前环境不支持 HLS，请选择原生后端或换台',retryable:false});
  }
  private fail(error:PlaybackError) {
    if(this.stopped)return;
    this.release(); ++this.generation;
    const retry=error.retryable && this.attempts<this.preferences().retryCount;
    if(retry) {
      this.attempts++;
      this.status('loading',{...error,message:`${error.message} · 第 ${this.attempts} 次重试`});
      this.retryTimer=setTimeout(()=>{if(!this.stopped)this.open(true);},Math.min(1000*2**(this.attempts-1),8000));
    } else this.status('error',error);
  }
  async play() {
    const version=this.generation;
    try {await this.video.play();}
    catch(error) {
      if(version!==this.generation||this.stopped)return;
      clearTimeout(this.timer);
      this.status('paused',{code:'autoplay',message:'请点击播放继续；浏览器可能阻止有声自动播放',retryable:false});
    }
  }
  pause() {clearTimeout(this.timer);clearTimeout(this.retryTimer);this.video.pause();this.status('paused');}
  setVolume(value:number) {this.video.volume=Math.max(0,Math.min(100,value))/100;}
  setMuted(value:boolean) {this.video.muted=value;}
  enterFullscreen() {return this.fullscreen();}
  private release() {clearTimeout(this.timer);clearTimeout(this.retryTimer);this.cleanup.splice(0).forEach(fn=>fn());this.hls?.destroy();this.hls=undefined;this.video.pause();this.video.removeAttribute('src');this.video.load();}
  destroy() {this.stopped=true;++this.generation;this.release();}
}
