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

import com.bbm.example.announcements.R;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.chats.holders.BaseIncomingBubbleHolder;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;


public class UnknownMessage extends BaseIncomingBubbleHolder implements RecyclerViewHolder<DecoratedMessage> {

    private TextView mMessageBody;

    public UnknownMessage(LayoutInflater layoutInflater, ViewGroup parent) {
        super(layoutInflater, parent);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup parent) {
        final View view = setContentSpecificView(inflater, R.layout.chat_bubble_text);
        mMessageBody = view.findViewById(R.id.message_body);

        return this.getRootView();
    }

    @Override
    public void updateView(DecoratedMessage decoratedMessage, int position) {
        updateGeneric(decoratedMessage);
        mMessageBody.setText(getContext().getText(R.string.conversation_unknown));
    }

    @Override
    public void onRecycled() {
        super.onRecycled();
        mMessageBody.setText(null);
    }
}
