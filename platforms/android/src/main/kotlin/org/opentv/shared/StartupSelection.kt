package org.opentv.shared

/** TV startup rule: restore the last channel before falling back to the first one. */
fun startupChannel(config: AppConfig, channels: List<Channel>): Channel? =
    channels.firstOrNull { it.id == config.lastChannelId } ?: channels.firstOrNull()
