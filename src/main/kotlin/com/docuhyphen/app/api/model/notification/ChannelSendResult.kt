package com.docuhyphen.app.api.model.notification

sealed class ChannelSendResult
{
    object Delivered : ChannelSendResult()
    object Suppressed : ChannelSendResult()
    object QuietHours : ChannelSendResult()
    data class Failed(val errorMessage: String) : ChannelSendResult()
}
