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

package com.bbm.example.announcements;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.ChatMessageCriteria;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.ui.widgets.recycler.MonitoredRecyclerAdapter;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;
import com.bbm.sdk.support.util.BbmUtils;


/**
 * Activity to view all the edits done to a specified Announcement ChatMessage.
 */
public final class ViewAnnouncementHistory extends AppCompatActivity {

    // The Intent extra for setting the ChatId
    public final static String EXTRA_CHAT_ID = "chat_id";

    // The Intent extra for setting the messageId
    public final static String EXTRA_MESSAGE_ID = "message_id";

    // The ChatID value
    private String mChatId;

    // The MessageId value
    private long mMessageId;

    // The data adapter for the edit history
    private HistoryDataAdapter mAdapter;

    // A simple textview for an empty view.
    private TextView mEmptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_announcement_history);

        // Get the chatId and Message Id
        mChatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        mMessageId = getIntent().getLongExtra(EXTRA_MESSAGE_ID, 0);

        mEmptyView = findViewById(R.id.empty_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onBackPressed();
            }
        });

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle(R.string.announcement_history);
        }

        // Create the recycling view & adapters.
        final RecyclerView recyclerView = findViewById(R.id.history_list);

        // Setup the LinearLayoutManager to load in reverse order and display accordingly.
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        linearLayoutManager.setReverseLayout(true);
        linearLayoutManager.setStackFromEnd(true);

        mAdapter = new HistoryDataAdapter(this, recyclerView);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Pause the adapter when the Activity is paused, we don't
        // need to get any events when the UI is hidden.
        mAdapter.pause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resume the adapter when the Activity is resumed.
        mAdapter.resume();
    }


    /**
     * Helper to monitor for data changes in our adapter query. This will simply inform the
     * adapter to do specific actions to update recycling view.
     */
    private final IncrementalListObserver mListObserver = new IncrementalListObserver() {
        @Override
        public void onItemsInserted(int position, int itemCount) {
            mAdapter.notifyItemRangeInserted(position, itemCount);
        }

        @Override
        public void onItemsRemoved(int position, int itemCount) {
            mAdapter.notifyItemRangeRemoved(position, itemCount);
        }

        @Override
        public void onItemsChanged(int position, int itemCount) {
            mAdapter.notifyItemRangeChanged(position, itemCount);
        }

        @Override
        public void onDataSetChanged() {
            mAdapter.notifyDataSetChanged();
        }
    };

    /**
     * Helper to update the recycling view with the root announcement message data.
     */
    private final Observer mRootObserver = new Observer() {
        @Override
        public void changed() {
            mAdapter.notifyDataSetChanged();
        }
    };

    /**
     * The data adapter for the activity. The adapter requires the ChatID and MessageId
     * fields which are used to create a lookup criteria needed to find all the ChatMessages
     * that are related to given messageId.
     */
    private class HistoryDataAdapter extends MonitoredRecyclerAdapter<ChatMessage> {

        private final ObservableList<ChatMessage> mList;
        private final ObservableValue<ChatMessage> mRootMessage;

        /**
         * Constructor
         *
         * @param context      The activity context
         * @param recyclerView The associated RecyclerView.
         */
        HistoryDataAdapter(@NonNull final Context context, @NonNull final RecyclerView recyclerView) {
            super(context, recyclerView);

            // Get the root message for the history. It is not included in the edit lookup.
            mRootMessage = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(
                    new ChatMessage.ChatMessageKey(mChatId, mMessageId));

            // First create a reference query object. In this case we want all history of
            // the given mMessageId that is of type ChatMessage.Ref.Tag.Edit
            ChatMessage.Ref ref = new ChatMessage.Ref();
            ref.messageId = mMessageId;
            ref.tag = ChatMessage.Ref.Tag.Edit;

            // Next create the lookup criteria object, pass in the ChatId associated with the
            // messageId, our custom Announcement Tag and the ChatMessage.Ref created above.
            final ChatMessageCriteria criteria = new ChatMessageCriteria()
                    .chatId(mChatId)
                    .ref(ref);

            // Now query the BBM Enterprise SDK for data.
            mList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessageList(criteria);
        }

        /**
         * Adds a IncrementalListObserver to receive changes in the mList.
         */
        void resume() {
            mList.addIncrementalListObserver(mListObserver);
            mRootMessage.addObserver(mRootObserver);
        }

        /**
         * Removes a IncrementalListObserver to stop receiving changes.
         */
        void pause() {
            mList.removeIncrementalListObserver(mListObserver);
            mRootMessage.removeObserver(mRootObserver);
        }

        @Override
        public ChatMessage getItem(int position) {
            if (position < 0) {
                return null;
            }

            if (position == 0) {
                return mRootMessage.get();
            }

            return mList.get(position - 1);
        }

        @Override
        public long getItemId(int position) {
            if (position < 0) {
                return 0;
            }

            // Asking for the first item, so return the mRootMessage item.
            if (position == 0) {
                return mRootMessage.get().getPrimaryKey().hashCode();
            }

            // Return items in the list, adjust by -1 to account for mRootMessage
            return mList.get(position - 1).getPrimaryKey().hashCode();
        }

        @Override
        public int getItemCount() {
            // Get the list size
            final int size = mList.get().size();

            // If the list size is 0, then there is nothing to show.
            mEmptyView.setVisibility(size == 0 ? View.VISIBLE : View.GONE);

            // return the size, if the size is >0 add 1 to account for the mRootMessage
            return size == 0 ? 0 : size + 1;
        }

        @Override
        public RecyclerViewHolder<ChatMessage> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType) {
            return new HistoryViewHolder();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }

    /**
     * Simple view holder to display the data found in the HistoryDataAdapter.
     */
    private class HistoryViewHolder implements RecyclerViewHolder<ChatMessage> {

        // The original ChatMessage text that was edited
        private TextView mTitle;

        // The user that edited the ChatMessage
        private TextView mEditBy;

        // The date the ChatMessage was edited
        private TextView mDate;

        // The data format flags to be applied to the POSIX timestamp
        private final static int mDateFlags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_SHOW_TIME;

        private ObservableValue<User> mUser;
        private ObservableValue<AppUser> mAppUser;

        // Indicates the user for this view holder is the creator of the original announcement.
        private boolean mCreator;

        /**
         * Observe the BBM Enterprise SDK user. When a valid user is returned trigger the
         * update to get the AppUser
         */
        private final Observer mObserver = new Observer() {
            @Override
            public void changed() {
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

                if (mCreator) {
                    mEditBy.setText(getString(R.string.create_announcement_by_short, BbmUtils.getAppUserName(mAppUser.get())));
                } else {
                    mEditBy.setText(getString(R.string.edit_announcement_by_short, BbmUtils.getAppUserName(mAppUser.get())));
                }
            }
        };

        @Override
        public View createView(LayoutInflater inflater, ViewGroup parent) {
            View view = inflater.inflate(R.layout.item_history, parent, false);

            mTitle = view.findViewById(R.id.message_body);
            mEditBy = view.findViewById(R.id.edit_by);
            mDate = view.findViewById(R.id.message_date);
            return view;
        }

        @Override
        public void updateView(ChatMessage chatMessage, int position) {

            // Get the user, if we have one, update the user.
            if (mUser != null) {
                mUser.removeObserver(mObserver);
            }

            mCreator = (chatMessage.refBy != null && chatMessage.refBy.size() > 0);

            mUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(chatMessage.senderUri);
            mUser.addObserver(mObserver);
            mObserver.changed();

            mTitle.setText(chatMessage.content);
            mDate.setText(DateUtils.formatDateTime(ViewAnnouncementHistory.this, chatMessage.timestamp * 1000, mDateFlags));
        }

        @Override
        public void onRecycled() {
            mTitle.setText(null);
            mEditBy.setText(null);
            mDate.setText(null);

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
}
