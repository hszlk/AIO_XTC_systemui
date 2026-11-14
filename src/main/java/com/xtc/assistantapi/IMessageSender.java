package com.xtc.assistantapi;

import android.content.Context;

import com.xtc.assistantapi.message.DirectiveResult;

/**
 * Reply channel of the assistant protocol.
 * (Original: {@code com.xtc.assistantapi.c.g}, {@code IMessageSender}.)
 */
public interface IMessageSender {

    void send(Context context, DirectiveResult result);
}
