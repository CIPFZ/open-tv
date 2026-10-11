import test from 'node:test';
import assert from 'node:assert/strict';
import {parseM3U} from '../src/core/m3u';
import {parseXmltv,xmltvTime} from '../src/core/epg';
import {defaults,normalizeConfig,startupChannel} from '../src/core/config';
import {classifySourceError,sourceErrorMessage,sourceHealthLabel,playbackFailureCode,playbackFailureHealth,playbackRecoveredHealth} from '../src/core/source-health';
import {readFile} from 'node:fs/promises';

test('M3U parser preserves commas in names, deduplicates URLs, and skips insecure entries',()=>{
  const result=parseM3U(`#EXTM3U\n#EXTINF:-1 tvg-id="a" group-title="News",Channel, East\nhttps://example.com/a.m3u8\n#EXTINF:-1 group-title="News",Duplicate\nhttps://example.com/a.m3u8\n#EXTINF:-1,HTTP\nhttp://example.com/nope\n`, 'p');
  assert.equal(result.channels.length,1); assert.equal(result.channels[0].name,'Channel, East'); assert.equal(result.skipped,1);
});
test('XMLTV parser rejects entity declarations and parses timezone',()=>{
  assert.equal(xmltvTime('20261009120000 +0800'),Date.UTC(2026,9,9,4,0,0));
  const items=parseXmltv(`<tv><programme channel="c" start="20261009120000 +0800" stop="20261009130000 +0800"><title>News</title><desc>Brief</desc></programme></tv>`);
  assert.equal(items[0].title,'News'); assert.throws(()=>parseXmltv('<!DOCTYPE tv [<!ENTITY x "bad">]><tv/>'));
});
test('config normalization clamps preferences and drops malformed playlists',()=>{
  const value=normalizeConfig({schemaVersion:1,volume:140,settings:{networkTimeout:999,retryCount:99,backend:'bad'},playlists:[{id:'ok',name:'Ok',channels:[]},{id:3}]});
  assert.equal(value.volume,100);assert.equal(value.settings.networkTimeout,3000);assert.equal(value.settings.retryCount,5);assert.equal(value.playlists.length,1);assert.equal(defaults().schemaVersion,1);
});
test('fresh config includes a clearly labeled development sample source',()=>{
  const value=defaults();
  assert.equal(value.playlists[0].name,'研发 · 中国频道');
  assert.equal(value.playlists[0].url,'https://iptv-org.github.io/iptv/countries/cn.m3u');
});
test('fixed M3U fixture parses the same way without network access',async()=>{
  const text=await readFile(new URL('./fixtures/sample.m3u',import.meta.url),'utf8');
  const result=parseM3U(text,'fixture');
  assert.equal(result.channels.length,2); assert.equal(result.skipped,1);
  assert.equal(result.channels[0].name,'示例新闻, 东部'); assert.equal(result.channels[0].number,1);
});
test('fixed XMLTV fixture provides current and next programmes',async()=>{
  const text=await readFile(new URL('./fixtures/sample.xmltv',import.meta.url),'utf8');
  const items=parseXmltv(text); const at=Date.UTC(2026,9,10,4,30,0); const result=(await import('../src/core/epg')).nowNext(items,'news-1',at);
  assert.equal(result.now?.title,'午间新闻'); assert.equal(result.next?.title,'财经观察');
});
test('source errors have stable categories and user-facing messages',()=>{
  assert.equal(classifySourceError(new Error('HTTP 403')), 'restricted');
  assert.equal(classifySourceError(new Error('没有有效 HTTPS 频道')), 'empty');
  assert.equal(classifySourceError(new Error('连接超时')), 'timeout');
  assert.equal(sourceHealthLabel('degraded'),'部分跳过');
  assert.match(sourceErrorMessage('insecure'),/HTTPS/);
});
test('playback failures update source health without hiding partial source quality',()=>{
  assert.equal(playbackFailureCode('timeout'),'timeout');
  assert.equal(playbackFailureCode('decode'),'format');
  assert.equal(playbackFailureHealth(277,266),'degraded');
  assert.equal(playbackFailureHealth(1,0),'error');
  assert.equal(playbackRecoveredHealth(0),'healthy');
  assert.equal(playbackRecoveredHealth(2),'degraded');
});
test('config recovery keeps defaults when storage is empty and rejects insecure persisted data',()=>{
  const fresh=normalizeConfig({});
  assert.equal(fresh.playlists[0].id,'dev-cn');
  const value=normalizeConfig({playlists:[{id:'legacy',name:'Legacy',kind:'remote',url:'http://bad.test/list.m3u',channels:[{id:'bad',name:'Bad',url:'http://bad.test/a.m3u8'}]}],epg:{url:'http://bad.test/epg.xml'}});
  assert.equal(value.playlists[0].url,''); assert.equal(value.playlists[0].channels.length,0); assert.equal(value.epg.url,'');
});
test('startup resumes the last channel before legacy default settings',()=>{
  const config=defaults();
  config.lastChannelId='second';
  config.settings.defaultChannelId='first';
  const channels=[{id:'first',name:'First'},{id:'second',name:'Second'}] as any;
  assert.equal(startupChannel(config,channels)?.id,'second');
});
test('config migration upgrades legacy current channel before normalization',()=>{
  const value=normalizeConfig({schemaVersion:0,currentChannelId:'legacy-channel',playlists:[]});
  assert.equal(value.schemaVersion,1); assert.equal(value.lastChannelId,'legacy-channel');
});
