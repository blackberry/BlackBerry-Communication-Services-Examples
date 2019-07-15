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

import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.example.announcements.R;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.chats.holders.BaseIncomingBubbleHolder;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;
import com.bbm.sdk.support.util.BbmUtils;


/**
 * Custom chat bubble that used when a ChatMessage contains a defined ChatMessage.Tag. This
 * usually is an event that occurs in the chat, i.e, a user joining the chat. The view
 * will handle ChatMessage.Tag.Join, ChatMessage.Tag.Leave and ChatMessage.Tag.Subject, all
 * other types are unhandled.
 */
public class EventHolder extends BaseIncomingBubbleHolder implements RecyclerViewHolder<DecoratedMessage> {

    private TextView mMessageBody;
    private ObservableValue<User> mUser;
    private ObservableValue<AppUser> mAppUser;

    /**
     * Observe the BBM Enterprise SDK user. When a valid user is returned trigger the
     * update to get the AppUser
     */
    private final Observer mUserObserver = new Observer() {
        @Override
        public void changed() {

            if (mDecoratedMessage == null || mDecoratedMessage.getChatMessage() == null) {
                return;
            }

            if (mUser.get().getExists() != Existence.YES) {
                return;
            }

            // Check if the user is the local device user. If so we use the local user
            // app data instead.
            ObservableValue<AppUser> localUser = UserManager.getInstance().getLocalAppUser();

            if (mAppUser != null) {
                mAppUser.removeObserver(mAppUserObserver);
            }

            if (localUser.get().getExists() == Existence.YES && localUser.get().getRegId() == mUser.get().regId) {
                mAppUser = localUser;
            } else {
                mAppUser = UserManager.getInstance().getUser(mUser.get().regId);
            }
            mAppUser.addObserver(mAppUserObserver);
            mAppUserObserver.changed();
        }
    };

    /**
     * Observer to an AppUser. When a valid AppUser is returned trigger an update to
     * the mMessageBody field.
     */
    private final Observer mAppUserObserver = new Observer() {
        @Override
        public void changed() {

            if (mAppUser.get().getExists() != Existence.YES) {
                return;
            }

            String name = BbmUtils.getAppUserName(mAppUser.get());

            Spanned s;
            switch (mDecoratedMessage.getChatMessage().tag) {
                case ChatMessage.Tag.Join: {
                    s = Html.fromHtml(getContext().getString(R.string.conversation_joined_the_chat, name));
                    break;
                }
                case ChatMessage.Tag.Leave: {

                    s = Html.fromHtml(getContext().getString(R.string.conversation_left_the_chat, name));
                    break;
                }
                case ChatMessage.Tag.Subject: {
                    s = Html.fromHtml(getContext().getString(R.string.conversation_subject_change, name,
                            mDecoratedMessage.getChatMessage().content));
                    break;
                }
                default:
                    s = Html.fromHtml(getContext().getString(R.string.conversation_unknown));
            }

            if (mMessageBody != null) {
                mMessageBody.setText(s);
            }
        }
    };

    public EventHolder(LayoutInflater layoutInflater, ViewGroup parent) {
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

        ChatMessage chatMessage = decoratedMessage.getChatMessage();

        if (chatMessage != null) {
            if (mUser == null) {
                mUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(chatMessage.senderUri);
                mUser.addObserver(mUserObserver);
            }
            mUserObserver.changed();
        }
    }

    @Override
    public void onRecycled() {
        super.onRecycled();
        mMessageBody.setText(null);

        if (mAppUser != null) {
            mAppUser.removeObserver(mAppUserObserver);
            mAppUser = null;
        }

        if (mUser != null) {
            mUser.removeObserver(mUserObserver);
            mUser = null;
        }
    }
}
