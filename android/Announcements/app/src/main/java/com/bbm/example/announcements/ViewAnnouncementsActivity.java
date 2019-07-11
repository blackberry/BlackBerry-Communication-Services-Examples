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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.ChatMessageCriteria;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.ui.widgets.recycler.MonitoredRecyclerAdapter;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;


/**
 * Activity to show all the announcements contained in the given chat.
 */
public class ViewAnnouncementsActivity extends AppCompatActivity {

    // The chat Id to reference
    private String mChatId;

    // Helper to set the activity title. Held to avoid GC.
    @SuppressWarnings("FieldCanBeLocal")
    private Observer mChatSubjectObserver;

    // The data adapter that contains all the announcement data.
    private AnnouncementsDataAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_announcements);

        mChatId = getIntent().getStringExtra(ChatActivity.INTENT_EXTRA_CHAT_ID);

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
        }

        //Add an observer to set the title of the activity to the subject of the chat
        final ObservableValue<Chat> chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId);
        mChatSubjectObserver = new Observer() {
            @Override
            public void changed() {
                final ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    if (chat.get().getExists() == Existence.YES) {
                        actionBar.setTitle(chat.get().subject);
                    }
                }
            }
        };
        chat.addObserver(mChatSubjectObserver);

        // Create the RecyclingView & Adapter
        final RecyclerView recyclerView = findViewById(R.id.announcements_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        linearLayoutManager.setReverseLayout(true);
        linearLayoutManager.setStackFromEnd(true);


        mAdapter = new AnnouncementsDataAdapter(this, recyclerView);
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
     * The adapter that is used to get all the ChatMessages that are of tagged as
     * {@link ChatActivity#TAG_ANNOUNCEMENTS}
     */
    public class AnnouncementsDataAdapter extends MonitoredRecyclerAdapter<ChatMessage> {

        private final ObservableList<ChatMessage> mList;

        /**
         * Constructor
         *
         * @param context      The activity context
         * @param recyclerView The RecyclerView to be used.
         */
        AnnouncementsDataAdapter(@NonNull final Context context, @NonNull final RecyclerView recyclerView) {
            super(context, recyclerView);

            // Create a lookup criteria. We need to supply the ChatId and our custom Announcement Tag
            final ChatMessageCriteria criteria = new ChatMessageCriteria()
                    .chatId(mChatId)
                    .tag(ChatActivity.TAG_ANNOUNCEMENTS);

            // Query BBM Enterprise SDK for the data.
            mList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessageList(criteria);
        }

        /**
         * Adds a IncrementalListObserver to receive changes in the mList.
         */
        void resume() {
            mList.addIncrementalListObserver(mListObserver);
        }

        /**
         * Removes a IncrementalListObserver to stop receiving changes.
         */
        void pause() {
            mList.removeIncrementalListObserver(mListObserver);
        }

        @Override
        public ChatMessage getItem(int position) {
            return position < 0 ? null : mList.get().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position < 0 ? -1 : mList.get().get(position).getPrimaryKey().hashCode();
        }

        @Override
        public int getItemCount() {
            return mList.get().size();
        }

        @Override
        public RecyclerViewHolder<ChatMessage> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType) {
            return new AnnouncementViewHolder();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }

    /**
     * A view holder that will display the announcement title. The user can also
     * edit the announcement or view past edit history.
     */
    private class AnnouncementViewHolder implements RecyclerViewHolder<ChatMessage> {

        private TextView mTitle;
        private Button mHistory;
        private Button mEdit;
        private ChatMessage mMessage;

        @Override
        public View createView(final LayoutInflater inflater, ViewGroup parent) {
            View view = inflater.inflate(R.layout.item_announcement, parent, false);
            mTitle = view.findViewById(R.id.announcement_title);

            mEdit = view.findViewById(R.id.edit_item);
            mHistory = view.findViewById(R.id.view_history);

            mEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mMessage != null) {
                        Helper.showEditAnnouncementDialog(ViewAnnouncementsActivity.this, mMessage);
                    }
                }
            });

            mHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ViewAnnouncementsActivity.this, ViewAnnouncementHistory.class);
                    intent.putExtra(ViewAnnouncementHistory.EXTRA_CHAT_ID, mChatId);
                    intent.putExtra(ViewAnnouncementHistory.EXTRA_MESSAGE_ID, mMessage.messageId);
                    startActivity(intent);
                }
            });

            return view;
        }

        @Override
        public void updateView(ChatMessage chatMessage, int position) {
            mMessage = chatMessage;
            mTitle.setText(Helper.getMessageContent(mMessage));
        }

        @Override
        public void onRecycled() {
            mTitle.setText(null);
            mEdit.setOnClickListener(null);
            mHistory.setOnClickListener(null);
        }
    }
}
