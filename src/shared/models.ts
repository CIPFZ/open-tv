export type PlaybackBackend = 'auto' | 'native' | 'hls';
export type PlaybackStatus = 'idle' | 'loading' | 'buffering' | 'playing' | 'paused' | 'error';
export type ErrorCode = 'network' | 'timeout' | 'restricted' | 'decode' | 'invalid-source' | 'autoplay' | 'unsupported';
export interface PlaybackError { code: ErrorCode; message: string; retryable: boolean }
export type SourceHealth = 'unknown' | 'loading' | 'healthy' | 'degraded' | 'error';
export type SourceErrorCode = 'network' | 'timeout' | 'restricted' | 'http' | 'format' | 'empty' | 'insecure' | 'unknown';
export interface SourceError { code: SourceErrorCode; message: string; at: number }
export interface Channel {
  id: string; playlistId: string; number: number; name: string; url: string;
  group: string; tvgId: string; logo: string; language: string; country: string;
}
export interface PlaylistSource {
  id: string; name: string; kind: 'remote' | 'local'; url: string; enabled: boolean;
  channels: Channel[]; text?: string; lastLoadedAt: number | null; error?: string;
  health: SourceHealth; skippedCount: number; lastError?: SourceError;
}
export interface Program { channelId: string; startAt: number; endAt: number; title: string; description: string }
export interface EpgSource { url: string; lastLoadedAt: number | null }
export interface PlaybackPreferences {
  autoPlay: boolean; rememberVolume: boolean; autoFullscreen: boolean;
  backend: PlaybackBackend; networkTimeout: number; retryCount: number;
  defaultPlaylistId: string; defaultChannelId: string;
}
export interface AppConfig {
  schemaVersion: 1; playlists: PlaylistSource[]; activePlaylistId: string;
  lastChannelId: string; recent: string[]; volume: number; muted: boolean;
  settings: PlaybackPreferences; epg: EpgSource;
}
export interface PlaybackAdapter {
  load(url: string, autoplay?: boolean): Promise<void>; play(): Promise<void>;
  pause(): void; setVolume(value: number): void; setMuted(value: boolean): void;
  enterFullscreen(): Promise<void>; destroy(): void;
}
export interface DesktopAPI {
  config: { load(): Promise<AppConfig | null>; save(value: AppConfig): Promise<void> };
  source: { fetchText(id: string, url: string, timeout: number): Promise<string>; cancel(id: string): Promise<void>; importFile(): Promise<{name: string; text: string} | null> };
  window: { fullscreen(value: boolean): Promise<boolean>; isFullscreen(): Promise<boolean> };
  app: { version(): Promise<string> };
}
declare global { interface Window { openTvAPI?: DesktopAPI } }
