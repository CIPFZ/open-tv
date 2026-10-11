package org.opentv.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opentv.shared.AppConfig
import org.opentv.shared.Channel
import org.opentv.shared.PlaylistKind
import org.opentv.shared.PlaylistSource
import org.opentv.shared.Program
import org.opentv.shared.startupChannel
import org.opentv.tv.TvAction
import org.opentv.tv.actionForKeyCode
import java.net.URI
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class OpenTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenTvShell() }
    }
}

@Composable
private fun OpenTvShell() {
    val context = LocalContext.current
    val lifecycleOwner = context as ComponentActivity
    val playback = remember { Media3PlaybackPort(context) }
    val configStore = remember { AndroidConfigStore(context) }
    var config by remember { mutableStateOf(configStore.load()) }
    val loader = remember { AndroidPlaylistLoader() }
    val epgLoader = remember { AndroidEpgLoader() }
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var programs by remember { mutableStateOf<List<Program>>(emptyList()) }
    var statusText by remember { mutableStateOf("正在准备来源…") }
    var volume by remember { mutableFloatStateOf(config.volume.toFloat()) }
    var muted by remember { mutableStateOf(config.muted) }
    var controlsVisible by remember { mutableStateOf(true) }
    var focusedControl by remember { mutableStateOf<String?>(null) }
    var channelDrawer by remember { mutableStateOf(false) }
    var settingsDrawer by remember { mutableStateOf(false) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf("") }
    var sourceBusy by remember { mutableStateOf(false) }
    val drawerFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    fun showControls() { controlsVisible = true }
    fun savePlaybackState() {
        config = config.copy(volume = volume.toInt(), muted = muted)
        configStore.save(config)
    }
    suspend fun playChannel(channel: Channel, autoplay: Boolean = true) {
        selectedChannel = channel
        config = config.copy(lastChannelId = channel.id, activePlaylistId = channel.playlistId)
        configStore.save(config)
        statusText = "正在连接 ${channel.name}…"
        channelDrawer = false
        try {
            playback.load(channel.url, autoplay)
            statusText = if (autoplay) "LIVE" else "已暂停"
        } catch (_: Throwable) {
            statusText = "播放失败 · 请重试或换台"
        }
    }
    suspend fun loadSource(source: PlaylistSource, closeOnSuccess: Boolean = false) {
        sourceBusy = true
        sourceError = ""
        try {
            val loaded = withContext(Dispatchers.IO) { loader.load(source, config.settings.networkTimeout) }
            config = config.copy(
                activePlaylistId = loaded.id,
                playlists = config.playlists.filterNot { it.id == loaded.id } + loaded
            )
            configStore.save(config)
            if (loaded.channels.isEmpty()) {
                sourceError = loaded.error ?: "来源没有可用频道"
                return
            }
            channels = loaded.channels
            val startup = startupChannel(config, loaded.channels) ?: loaded.channels.first()
            if (closeOnSuccess) settingsDrawer = false
            playChannel(startup, config.settings.autoPlay)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            sourceError = error.message ?: "来源加载失败"
            statusText = "来源加载失败 · 请重试或换一个来源"
        } finally {
            sourceBusy = false
        }
    }
    fun addSource() {
        val url = sourceUrl.trim()
        if (url.isBlank()) {
            sourceError = "请输入 HTTPS M3U/M3U8 地址"
            return
        }
        if (config.playlists.any { it.url == url }) {
            sourceError = "这个来源已经存在"
            return
        }
        val name = sourceName.trim().ifBlank { runCatching { URI(url).host }.getOrNull() ?: "新来源" }
        val source = PlaylistSource(UUID.randomUUID().toString(), name, PlaylistKind.REMOTE, url)
        scope.launch { loadSource(source, closeOnSuccess = true) }
    }
    fun selectSource(source: PlaylistSource) {
        scope.launch { loadSource(source, closeOnSuccess = true) }
    }
    fun refreshSource(source: PlaylistSource) {
        scope.launch { loadSource(source, closeOnSuccess = true) }
    }
    fun toggleSource(source: PlaylistSource) {
        if (source.enabled && config.playlists.count { it.enabled } <= 1) {
            sourceError = "至少保留一个启用来源"
            return
        }
        val enabled = !source.enabled
        val next = if (!enabled && source.id == config.activePlaylistId) {
            config.playlists.firstOrNull { it.id != source.id && it.enabled }
        } else null
        config = config.copy(
            activePlaylistId = next?.id ?: if (enabled || source.id != config.activePlaylistId) config.activePlaylistId else "",
            playlists = config.playlists.map { if (it.id == source.id) it.copy(enabled = enabled) else it }
        )
        configStore.save(config)
        if (next != null) {
            channels = emptyList()
            selectedChannel = null
            scope.launch { loadSource(next) }
        }
    }
    fun deleteSource(source: PlaylistSource) {
        if (config.playlists.size <= 1) {
            sourceError = "至少保留一个播放列表来源"
            return
        }
        val wasActive = source.id == config.activePlaylistId
        val next = if (wasActive) config.playlists.firstOrNull { it.id != source.id && it.enabled } else null
        val removedChannelIds = source.channels.map { it.id }.toSet()
        config = config.copy(
            activePlaylistId = next?.id ?: if (wasActive) "" else config.activePlaylistId,
            lastChannelId = if (config.lastChannelId in removedChannelIds) "" else config.lastChannelId,
            playlists = config.playlists.filterNot { it.id == source.id }
        )
        configStore.save(config)
        if (wasActive) {
            channels = emptyList()
            selectedChannel = null
            if (next != null) scope.launch { loadSource(next) } else statusText = "请在设置中启用一个播放列表"
        }
    }
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sourceBusy = true
            sourceError = ""
            try {
                val text = withContext(Dispatchers.IO) { readPlaylistText(context, uri) }
                val name = sourceName.trim().ifBlank { "本地播放列表" }
                loadSource(
                    PlaylistSource(UUID.randomUUID().toString(), name, PlaylistKind.LOCAL, text = text),
                    closeOnSuccess = true
                )
            } catch (error: Throwable) {
                sourceError = error.message ?: "本地文件读取失败"
            } finally {
                sourceBusy = false
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { writeConfigText(context, uri, config) }
                .onFailure { sourceError = it.message ?: "配置导出失败" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) { AndroidConfigCodec.decodeStrict(readConfigText(context, uri)) }
                config = imported
                configStore.save(imported)
                sourceError = ""
                val source = imported.playlists.firstOrNull { it.enabled && it.id == imported.activePlaylistId }
                    ?: imported.playlists.firstOrNull { it.enabled }
                if (source == null) {
                    channels = emptyList()
                    selectedChannel = null
                    statusText = "配置已导入，请在设置中添加播放列表"
                } else if (source.channels.isNotEmpty()) {
                    channels = source.channels
                    val startup = startupChannel(imported, source.channels) ?: source.channels.first()
                    playChannel(startup, imported.settings.autoPlay)
                } else {
                    loadSource(source)
                }
            } catch (error: Throwable) {
                sourceError = error.message ?: "配置导入失败"
            }
        }
    }
    fun moveChannel(delta: Int) {
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == selectedChannel?.id }.let { if (it < 0) 0 else it }
        val next = channels[(index + delta + channels.size) % channels.size]
        scope.launch { playChannel(next) }
    }
    LaunchedEffect(Unit) {
        val source = config.playlists.firstOrNull { it.enabled && it.id == config.activePlaylistId }
            ?: config.playlists.firstOrNull { it.enabled }
        if (source == null) {
            statusText = "请在设置中添加播放列表"
            return@LaunchedEffect
        }
        statusText = "正在读取 ${source.name}…"
        loadSource(source)
        if (channels.isEmpty()) statusText = sourceError.ifBlank { "来源没有可用频道" }
    }
    LaunchedEffect(config.epg.url) {
        val url = config.epg.url
        if (url.isBlank()) {
            programs = emptyList()
            return@LaunchedEffect
        }
        runCatching { withContext(Dispatchers.IO) { epgLoader.load(url, config.settings.networkTimeout) } }
            .onSuccess { loaded ->
                programs = loaded
                config = config.copy(epg = config.epg.copy(lastLoadedAt = System.currentTimeMillis()))
                configStore.save(config)
            }
            .onFailure { programs = emptyList() }
    }
    LaunchedEffect(playback) {
        playback.setStatusListener { state, detail ->
            statusText = when (state) {
                "buffering" -> "正在缓冲…"
                "playing" -> "LIVE"
                "paused" -> "已暂停"
                "error" -> detail ?: "播放失败 · 请重试或换台"
                else -> statusText
            }
        }
        playback.setVolume(volume.toInt())
        playback.setMuted(muted)
    }
    DisposableEffect(lifecycleOwner, selectedChannel?.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    savePlaybackState()
                    playback.pause()
                }
                Lifecycle.Event.ON_START -> {
                    selectedChannel?.let { channel ->
                        scope.launch {
                            runCatching { playback.load(channel.url, config.settings.autoPlay) }
                                .onFailure { statusText = "播放失败 · 请重试或换台" }
                        }
                    }
                    showControls()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selectedChannel, channelDrawer, settingsDrawer) {
        if (selectedChannel != null && !channelDrawer && !settingsDrawer) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(channelDrawer, channels) {
        if (channelDrawer && channels.isNotEmpty()) drawerFocusRequester.requestFocus()
    }
    LaunchedEffect(settingsDrawer) {
        if (settingsDrawer) settingsFocusRequester.requestFocus()
    }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }

    val epgChannelId = selectedChannel?.tvgId.orEmpty()
    val epgNow = System.currentTimeMillis()
    val currentProgram = programs.firstOrNull { it.channelId == epgChannelId && it.startAt <= epgNow && it.endAt > epgNow }
    val nextProgram = programs.filter { it.channelId == epgChannelId && it.startAt > epgNow }.minByOrNull { it.startAt }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val action = actionForKeyCode(event.nativeKeyEvent.keyCode)
                    if (channelDrawer || settingsDrawer) {
                        return@onPreviewKeyEvent when (action) {
                            TvAction.BACK -> { channelDrawer = false; settingsDrawer = false; true }
                            TvAction.OPEN_MENU -> { channelDrawer = false; settingsDrawer = false; true }
                            else -> false
                        }
                    }
                    when (action) {
                        TvAction.PREVIOUS_CHANNEL -> { moveChannel(-1); showControls(); true }
                        TvAction.NEXT_CHANNEL -> { moveChannel(1); showControls(); true }
                        TvAction.VOLUME_DOWN -> { volume = (volume - 5f).coerceAtLeast(0f); muted = volume == 0f; playback.setVolume(volume.toInt()); playback.setMuted(muted); savePlaybackState(); showControls(); true }
                        TvAction.VOLUME_UP -> { volume = (volume + 5f).coerceAtMost(100f); muted = false; playback.setVolume(volume.toInt()); playback.setMuted(false); savePlaybackState(); showControls(); true }
                        TvAction.PLAY_PAUSE -> { if (playback.player.isPlaying) playback.pause() else playback.player.play(); showControls(); true }
                        TvAction.OPEN_MENU -> { channelDrawer = true; showControls(); true }
                        TvAction.BACK -> { if (!controlsVisible) { showControls(); true } else false }
                        else -> { showControls(); false }
                    }
                },
            color = Color(0xFF08090C)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { PlayerView(it).apply { player = playback.player; useController = false } },
                    modifier = Modifier.fillMaxSize()
                )
                Column(modifier = Modifier.align(Alignment.TopStart).padding(32.dp)) {
                    Text(selectedChannel?.number?.toString()?.padStart(2, '0') ?: "—", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.headlineMedium)
                    Text(selectedChannel?.name ?: "Open TV", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(selectedChannel?.group ?: statusText, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
                }
                if (controlsVisible) {
                    TvControlDock(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp),
                        volume = volume,
                        muted = muted,
                        focusedControl = focusedControl,
                        onFocus = { focusedControl = it },
                        onPrevious = { moveChannel(-1); showControls() },
                        onPlayPause = { if (playback.player.isPlaying) playback.pause() else playback.player.play(); showControls() },
                        onNext = { moveChannel(1); showControls() },
                        onMute = { muted = !muted; playback.setMuted(muted); savePlaybackState(); showControls() },
                        onVolume = { value -> volume = value; muted = value == 0f; playback.setVolume(value.toInt()); playback.setMuted(muted); savePlaybackState(); showControls() },
                        onSettings = { settingsDrawer = true; showControls() }
                    )
                }
                TvEpgPanel(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 178.dp),
                    current = currentProgram,
                    next = nextProgram
                )
                if (channelDrawer) {
                    TvChannelDrawer(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        channels = channels,
                        selected = selectedChannel,
                        focusRequester = drawerFocusRequester,
                        onSelect = { scope.launch { playChannel(it) } },
                        onClose = { channelDrawer = false }
                    )
                }
                if (settingsDrawer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .58f))
                            .clickable { settingsDrawer = false }
                    )
                    TvSettingsDrawer(
                        modifier = Modifier.align(Alignment.Center),
                        config = config,
                        focusRequester = settingsFocusRequester,
                        sourceName = sourceName,
                        sourceUrl = sourceUrl,
                        sourceError = sourceError,
                        sourceBusy = sourceBusy,
                        onNameChange = { sourceName = it },
                        onUrlChange = { sourceUrl = it },
                        onAddSource = ::addSource,
                        onImportFile = { localPicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")) },
                        onSelectSource = ::selectSource,
                        onRefreshSource = ::refreshSource,
                        onToggleSource = ::toggleSource,
                        onDeleteSource = ::deleteSource,
                        onExportConfig = { exportLauncher.launch("open-tv-config.json") },
                        onImportConfig = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onClose = { settingsDrawer = false }
                    )
                }
            }
        }
    }
}

private fun readPlaylistText(context: android.content.Context, uri: android.net.Uri): String {
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 15 * 1024 * 1024) { "播放列表超过 15 MB 上限" }
            output.write(buffer, 0, count)
        }
    } ?: error("无法读取本地播放列表")
    return output.toString(Charsets.UTF_8.name())
}

private fun readConfigText(context: android.content.Context, uri: android.net.Uri): String {
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 40 * 1024 * 1024) { "配置文件超过 40 MB 上限" }
            output.write(buffer, 0, count)
        }
    } ?: error("无法读取配置文件")
    return output.toString(Charsets.UTF_8.name())
}

private fun writeConfigText(context: android.content.Context, uri: android.net.Uri, config: AppConfig) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(AndroidConfigCodec.encode(config).toByteArray(Charsets.UTF_8))
    } ?: error("无法写入配置文件")
}

@Composable
private fun TvChannelDrawer(
    modifier: Modifier,
    channels: List<Channel>,
    selected: Channel?,
    focusRequester: FocusRequester,
    onSelect: (Channel) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight().fillMaxWidth(.42f),
        color = Color(0xF215161B),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column {
                    Text("频道列表", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text("${channels.size} 个频道", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
                }
                TvDrawerButton("关闭", "×", onClose)
            }
            Spacer(Modifier.height(18.dp))
            if (channels.isEmpty()) {
                Text("来源没有可用频道", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { channel ->
                        val active = channel.id == selected?.id
                        var focused by remember(channel.id) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (channel == channels.first()) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .focusable()
                                .clickable { onSelect(channel) }
                                .background(if (active || focused) Color(0xFF0A84FF) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(channel.number.toString().padStart(2, '0'), color = Color.White.copy(alpha = .72f), modifier = Modifier.width(38.dp))
                            Column {
                                Text(channel.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                Text(channel.group, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsDrawer(
    modifier: Modifier,
    config: AppConfig,
    focusRequester: FocusRequester,
    sourceName: String,
    sourceUrl: String,
    sourceError: String,
    sourceBusy: Boolean,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onAddSource: () -> Unit,
    onImportFile: () -> Unit,
    onSelectSource: (PlaylistSource) -> Unit,
    onRefreshSource: (PlaylistSource) -> Unit,
    onToggleSource: (PlaylistSource) -> Unit,
    onDeleteSource: (PlaylistSource) -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(.78f)
            .fillMaxHeight(.84f)
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(28.dp)),
        color = Color(0xF215161B),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("设置", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                TvDrawerButton("关闭", "×", onClose)
            }
            Text("播放列表来源", color = Color.White, style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.height(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(config.playlists, key = { it.id }) { source ->
                    val active = source.id == config.activePlaylistId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (active) Color(0xFF0A84FF) else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().focusable().clickable { onSelectSource(source) },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text("${source.channels.size} 个频道 · ${source.health.name.lowercase()}", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(if (active) "当前" else "切换", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRefreshSource(source) }, enabled = !sourceBusy) { Text("刷新") }
                            Button(onClick = { onToggleSource(source) }, enabled = !sourceBusy) { Text(if (source.enabled) "停用" else "启用") }
                            Button(onClick = { onDeleteSource(source) }, enabled = !sourceBusy) { Text("删除") }
                        }
                    }
                }
            }
            TextField(value = sourceName, onValueChange = onNameChange, label = { Text("来源名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester))
            TextField(value = sourceUrl, onValueChange = onUrlChange, label = { Text("HTTPS M3U/M3U8 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = onAddSource, enabled = !sourceBusy && sourceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (sourceBusy) "加载中…" else "加载并启用")
            }
            Button(onClick = onImportFile, enabled = !sourceBusy, modifier = Modifier.fillMaxWidth()) {
                Text("选择本地 M3U/M3U8")
            }
            if (sourceError.isNotBlank()) Text(sourceError, color = Color(0xFFFF817A), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImportConfig, enabled = !sourceBusy) { Text("导入配置") }
                Button(onClick = onExportConfig, enabled = !sourceBusy) { Text("导出配置") }
            }
            TvSettingLine("启动时自动播放", if (config.settings.autoPlay) "开启" else "关闭")
            TvSettingLine("记住上次音量", if (config.settings.rememberVolume) "开启" else "关闭")
            TvSettingLine("网络超时", "${config.settings.networkTimeout / 1000} 秒")
            TvSettingLine("自动重试", "${config.settings.retryCount} 次")
        }
    }
}

@Composable
private fun TvSettingLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TvEpgPanel(modifier: Modifier, current: Program?, next: Program?) {
    if (current == null && next == null) return
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(
        modifier = modifier.width(420.dp),
        color = Color(0xD916171C),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            current?.let {
                Text("正在播放  ${timeFormat.format(java.util.Date(it.startAt))}–${timeFormat.format(java.util.Date(it.endAt))}", color = Color.White.copy(alpha = .66f), style = MaterialTheme.typography.labelSmall)
                Text(it.title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                if (it.description.isNotBlank()) Text(it.description, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            next?.let {
                Text("接下来  ${timeFormat.format(java.util.Date(it.startAt))}  ${it.title}", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TvDrawerButton(label: String, glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(50.dp)
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(glyph, color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun TvControlDock(
    modifier: Modifier,
    volume: Float,
    muted: Boolean,
    focusedControl: String?,
    onFocus: (String?) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onVolume: (Float) -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(.82f),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xE61A1B20),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvControl("previous", "上一台", "◀◀", focusedControl == "previous", onFocus, onPrevious)
            Spacer(Modifier.width(10.dp))
            TvControl("play", "播放/暂停", "▶", focusedControl == "play", onFocus, onPlayPause, circle = true)
            Spacer(Modifier.width(10.dp))
            TvControl("next", "下一台", "▶▶", focusedControl == "next", onFocus, onNext)
            Spacer(Modifier.width(20.dp))
            Text(if (muted) "静音" else "音量", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Slider(value = volume, onValueChange = onVolume, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(36.dp))
            Text("${volume.toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(46.dp))
            TvControl("mute", if (muted) "取消静音" else "静音", if (muted) "×" else "🔊", focusedControl == "mute", onFocus, onMute)
            TvControl("settings", "设置", "⚙", focusedControl == "settings", onFocus, onSettings)
        }
    }
}

@Composable
private fun TvControl(
    id: String,
    label: String,
    glyph: String,
    focused: Boolean,
    onFocus: (String?) -> Unit,
    onClick: () -> Unit,
    circle: Boolean = false
) {
    Box(
        modifier = Modifier
            .then(if (circle) Modifier.width(68.dp) else Modifier.width(62.dp))
            .height(if (circle) 68.dp else 54.dp)
            .onFocusChanged { onFocus(if (it.isFocused) id else null) }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = if (circle) CircleShape else RoundedCornerShape(14.dp),
            color = if (focused) Color(0xFF0A84FF) else Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Text(glyph, color = Color.White, style = if (circle) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                Text(label, color = Color.White.copy(alpha = if (focused) 1f else .58f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
