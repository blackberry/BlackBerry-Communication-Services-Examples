/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bbm.example.announcements.chatHolders;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.example.announcements.Helper;
import com.bbm.example.announcements.R;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.chats.holders.BaseBubbleHolder;
import com.bbm.sdk.support.ui.widgets.recycler.ContextMenuAwareHolder;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;

import java.util.Collections;
import java.util.List;

/**
 * A custom chat bubble used to show a ChatMessage tagged as an Announcement. Any
 * user can edit the message by long-clicking on the bubble.
 */
public final class AnnouncementHolder extends BaseBubbleHolder implements RecyclerViewHolder<DecoratedMessage>, ContextMenuAwareHolder {

    private TextView mMessageBody;
    private View mContextView;

    public AnnouncementHolder(LayoutInflater layoutInflater, ViewGroup parent) {
        super(layoutInflater, parent, com.bbm.sdk.support.R.layout.chat_bubble_base_outgoing_holder);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup parent) {
        mContextView = super.setContentSpecificView(inflater, R.layout.chat_bubble_announcement);
        mMessageBody = mContextView.findViewById(R.id.message_body);
        return super.getRootView();
    }

    @Override
    public void updateView(DecoratedMessage decoratedMessage, int position) {
        super.updateGeneric(decoratedMessage);

        ChatMessage message = decoratedMessage.getChatMessage();
        if (message != null) {
            mMessageBody.setText(Helper.getMessageContent(message));
        }
    }

    @Override
    public void onRecycled() {
        mMessageBody.setText(null);
        mContextView = null;
    }

    @Override
    public List<View> getContextMenuAwareView() {
        return Collections.singletonList(mContextView);
    }
}
