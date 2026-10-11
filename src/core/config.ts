import type { AppConfig, Channel, PlaylistSource, SourceError, SourceHealth } from '../shared/models';
export const CURRENT_CONFIG_VERSION = 1;
export function defaults(): AppConfig {
  const sample: PlaylistSource = { id: 'dev-cn', name: '研发 · 中国频道', kind: 'remote', url: 'https://iptv-org.github.io/iptv/countries/cn.m3u', enabled: true, channels: [], lastLoadedAt: null, health: 'unknown', skippedCount: 0 };
  return { schemaVersion: 1, playlists: [sample], activePlaylistId: sample.id, lastChannelId: '', recent: [], volume: 70, muted: false,
    settings: { autoPlay: true, rememberVolume: true, autoFullscreen: false, backend: 'auto', networkTimeout: 12000, retryCount: 2, defaultPlaylistId: '', defaultChannelId: '' }, epg: {url: '', lastLoadedAt: null} };
}
export function httpsUrl(value: string, base?: string): string {
  const url = new URL(value, base);
  if (url.protocol !== 'https:' || url.username || url.password || value.length > 8192) throw new Error('仅支持 HTTPS 地址，且不能在地址中包含用户名和密码');
  return url.href;
}
export function migrateConfig(input: unknown): unknown {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return input;
  const raw = input as Record<string, unknown>;
  const version = raw.schemaVersion;
  if (version === undefined) return {...raw, schemaVersion: CURRENT_CONFIG_VERSION};
  if (version === 0) {
    const legacyChannel = typeof raw.currentChannelId === 'string' ? raw.currentChannelId : '';
    const settings = raw.settings && typeof raw.settings === 'object' && !Array.isArray(raw.settings)
      ? {...raw.settings as Record<string, unknown>}
      : {};
    return {...raw, schemaVersion: CURRENT_CONFIG_VERSION, lastChannelId: raw.lastChannelId || legacyChannel, settings};
  }
  return input;
}
export function normalizeConfig(input: unknown): AppConfig {
  const d = defaults();
  if (!input || typeof input !== 'object') return d;
  const x = migrateConfig(input) as Partial<AppConfig>;
  if (x.schemaVersion && x.schemaVersion !== CURRENT_CONFIG_VERSION) throw new Error('配置版本不受支持');
  if (Array.isArray(x.playlists)) d.playlists = x.playlists.slice(0, 100).filter(p => p && typeof p.id === 'string' && typeof p.name === 'string').map(p => {
    const kind = p.kind === 'local' ? 'local' : 'remote';
    let url = '';
    if (typeof p.url === 'string' && p.url) { try { url = httpsUrl(p.url); } catch { url = ''; } }
    const channels = Array.isArray(p.channels) ? p.channels.filter(c => c && typeof c.id === 'string' && typeof c.name === 'string' && typeof c.url === 'string').flatMap(c => {
      try { return [{...c, url: httpsUrl(c.url), group: c.group || '其他', tvgId: c.tvgId || ''}]; } catch { return []; }
    }) : [];
    const health: SourceHealth = ['unknown','loading','healthy','degraded','error'].includes(p.health) ? p.health as SourceHealth : (p.error ? 'error' : channels.length ? 'healthy' : 'unknown');
    const lastError = p.lastError && typeof p.lastError === 'object' && typeof p.lastError.message === 'string' ? {
      code: ['network','timeout','restricted','http','format','empty','insecure','unknown'].includes(p.lastError.code) ? p.lastError.code : 'unknown',
      message: p.lastError.message.slice(0, 240), at: typeof p.lastError.at === 'number' ? p.lastError.at : Date.now()
    } as SourceError : undefined;
    return { id: p.id.slice(0,100), name: p.name.slice(0,80), kind, url: kind === 'remote' ? url : '', enabled: p.enabled !== false,
      channels, ...(kind === 'local' && typeof p.text === 'string' ? {text: p.text.slice(0, 15 * 1024 * 1024)} : {}),
      lastLoadedAt: typeof p.lastLoadedAt === 'number' ? p.lastLoadedAt : null,
      error: lastError?.message || (typeof p.error === 'string' ? p.error.slice(0,240) : undefined), health,
      skippedCount: Number.isFinite(p.skippedCount) ? Math.max(0, Math.floor(p.skippedCount)) : 0, ...(lastError ? {lastError} : {})
    } as PlaylistSource;
  });
  for (const key of ['activePlaylistId', 'lastChannelId'] as const) if (typeof x[key] === 'string') d[key] = x[key];
  d.recent = Array.isArray(x.recent) ? x.recent.filter(v => typeof v === 'string').slice(0,20) : [];
  const s = x.settings;
  if (s) {
    for (const k of ['autoPlay','rememberVolume','autoFullscreen'] as const) if (typeof s[k] === 'boolean') d.settings[k] = s[k];
    if (['auto','native','hls'].includes(s.backend)) d.settings.backend = s.backend;
    for (const k of ['defaultPlaylistId','defaultChannelId'] as const) if (typeof s[k] === 'string') d.settings[k] = s[k];
    if (Number.isFinite(s.networkTimeout)) d.settings.networkTimeout = Math.max(3000, Math.min(60000, s.networkTimeout));
    if (Number.isFinite(s.retryCount)) d.settings.retryCount = Math.max(0, Math.min(5, Math.floor(s.retryCount)));
  }
  d.volume = d.settings.rememberVolume && Number.isFinite(x.volume) ? Math.max(0,Math.min(100,x.volume!)) : 70;
  d.muted = d.settings.rememberVolume && x.muted === true;
  if (x.epg && typeof x.epg.url === 'string') { try { d.epg = { url: x.epg.url ? httpsUrl(x.epg.url) : '', lastLoadedAt: typeof x.epg.lastLoadedAt === 'number' ? x.epg.lastLoadedAt : null }; } catch { d.epg = {url: '', lastLoadedAt: null}; } }
  return d;
}
export function startupChannel(config: AppConfig, channels: Channel[]): Channel | undefined {
  // Television behaviour: resume the channel that was playing when the app
  // was closed. The old defaultChannelId field is retained only for config
  // compatibility and is intentionally ignored by the current UI.
  return channels.find(c => c.id === config.lastChannelId) || channels[0];
}
