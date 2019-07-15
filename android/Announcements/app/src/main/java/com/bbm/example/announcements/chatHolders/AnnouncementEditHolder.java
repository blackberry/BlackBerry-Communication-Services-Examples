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


import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.example.announcements.R;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;
import com.bbm.sdk.support.ui.widgets.util.DateUtil;
import com.bbm.sdk.support.util.BbmUtils;


/**
 * A custom chat bubble used to show a ChatMessage tagged as an Announcement and has been
 * edited by a user.
 */
public final class AnnouncementEditHolder implements RecyclerViewHolder<DecoratedMessage> {

    private TextView mMessageBody;
    private TextView mEditBy;
    private TextView mDateTextView;
    private View mBackground;

    private final Context mContext;

    private ObservableValue<User> mUser;
    private ObservableValue<AppUser> mAppUser;

    /**
     * Observe the BBM Enterprise SDK user. When a valid user is returned trigger the
     * update to get the AppUser
     */
    private final Observer mObserver = new Observer() {
        @Override
        public void changed() {

            // Only proceed if a valid user is provided.
            if (mUser == null || mUser.get().exists != Existence.YES) {
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
     * the mEditBy field.
     */
    private final Observer mAppUserObserver = new Observer() {
        @Override
        public void changed() {

            if (mAppUser.get().getExists() != Existence.YES) {
                return;
            }

            if (mEditBy != null) {
                String message = mContext.getString(R.string.edit_announcement_by, BbmUtils.getAppUserName(mAppUser.get()));
                mEditBy.setText(message);
            }
        }
    };

    public AnnouncementEditHolder(@NonNull final Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.chat_bubble_announcement_edit, null);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(lp);

        mBackground = view.findViewById(R.id.chat_bubble_background_container);
        mEditBy = view.findViewById(R.id.edit_by);
        mMessageBody = view.findViewById(R.id.message_body);
        mDateTextView = view.findViewById(com.bbm.sdk.support.R.id.message_date);
        return view;
    }

    @Override
    public void updateView(DecoratedMessage decoratedMessage, int position) {

        // Update the background for the view
        mBackground.setBackgroundResource(R.drawable.announcement_updated_background);
        mDateTextView.setText(DateUtil.observableChatBubbleHeaderTimestamp(mContext, decoratedMessage.getTimestamp()));
        mMessageBody.setText(decoratedMessage.getChatMessage().content);

        // Get the user information to be displayed
        if (mUser != null) {
            mUser.removeObserver(mObserver);
        }

        mUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(decoratedMessage.getChatMessage().senderUri);
        mUser.addObserver(mObserver);
        mObserver.changed();
    }

    @Override
    public void onRecycled() {
        mDateTextView.setText(null);
        mMessageBody.setText(null);
        mEditBy.setText(null);
        mBackground.setBackground(null);

        if (mAppUser != null) {
            mAppUser.removeObserver(mAppUserObserver);
            mAppUser = null;
        }

        if (mUser != null) {
            mUser.removeObserver(mObserver);
            mUser = null;
        }
    }
}