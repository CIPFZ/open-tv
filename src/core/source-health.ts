import type {SourceErrorCode, SourceHealth} from '../shared/models';

export function classifySourceError(error: unknown): SourceErrorCode {
  const message = error instanceof Error ? error.message : String(error ?? '');
  if (/超时|timeout|aborted|abort/i.test(message)) return 'timeout';
  if (/401|403|451|授权|地区限制|拒绝访问/.test(message)) return 'restricted';
  if (/404|410|HTTP\s+4\d\d/.test(message)) return 'http';
  if (/没有有效/.test(message)) return 'empty';
  if (/M3U|格式无效|不是有效/.test(message)) return 'format';
  if (/HTTPS|用户名|密码|协议/.test(message)) return 'insecure';
  return 'network';
}

export function sourceErrorMessage(code: SourceErrorCode): string {
  const messages: Record<SourceErrorCode, string> = {
    network: '来源不可访问，请检查网络或稍后重试',
    timeout: '来源连接超时，请检查网络或稍后重试',
    restricted: '来源需要授权或受到地区限制；应用不会绕过限制',
    http: '来源返回了不可用的 HTTP 状态',
    format: '来源格式无效，请确认是 M3U/M3U8 播放列表',
    empty: '来源没有可播放的 HTTPS 频道',
    insecure: '仅支持 HTTPS 频道地址，应用不会加载不安全来源',
    unknown: '来源加载失败，请重试或换一个来源'
  };
  return messages[code];
}

export function sourceHealthLabel(health: SourceHealth): string {
  return ({unknown: '未加载', loading: '加载中', healthy: '可用', degraded: '部分跳过', error: '失败'})[health];
}

export function playbackFailureCode(code: string): SourceErrorCode {
  if (code === 'timeout') return 'timeout';
  if (code === 'restricted') return 'restricted';
  if (code === 'decode' || code === 'unsupported' || code === 'invalid-source') return 'format';
  return 'network';
}

export function playbackFailureHealth(channelCount: number, skippedCount: number): SourceHealth {
  return channelCount > 1 || skippedCount > 0 ? 'degraded' : 'error';
}

export function playbackRecoveredHealth(skippedCount: number): SourceHealth {
  return skippedCount > 0 ? 'degraded' : 'healthy';
}
