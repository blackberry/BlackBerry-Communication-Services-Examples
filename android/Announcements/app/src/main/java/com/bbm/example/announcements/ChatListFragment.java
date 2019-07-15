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
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.support.ui.widgets.recycler.MonitoredRecyclerAdapter;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;
import com.bbm.sdk.support.util.Logger;


/**
 * A Fragment that will display all the Chats for the local user.
 */
public final class ChatListFragment extends Fragment {

    // The custom adapter to get the chat data
    private ChatsAdapter mAdapter;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        final View rootView = inflater.inflate(R.layout.fragment_chats, container, false);

        final RecyclerView recyclerView = rootView.findViewById(R.id.chats_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(inflater.getContext());
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(inflater.getContext(),
                linearLayoutManager.getOrientation()));

        mAdapter = new ChatsAdapter(getActivity(), recyclerView);
        recyclerView.setAdapter(mAdapter);

        return rootView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Logger.d("onOptionsItemSelected: " + item);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.resume();
    }

    /**
     * Helper to notify the ChatsAdapter of changes in the data from the
     * BBM Enterprise SDK.
     */
    private final IncrementalListObserver mChatListObserver = new IncrementalListObserver() {
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
     * The adapter that will contain a list of Chats.
     */
    public class ChatsAdapter extends MonitoredRecyclerAdapter<Chat> {

        private final ObservableList<Chat> mChatList;

        ChatsAdapter(Context context, RecyclerView recyclerView) {
            super(context, recyclerView);
            mChatList = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList();
        }

        void resume() {
            mChatList.addIncrementalListObserver(mChatListObserver);
        }

        void pause() {
            mChatList.removeIncrementalListObserver(mChatListObserver);
        }

        @Override
        public Chat getItem(int position) {
            return position < 0 ? null : mChatList.get().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position < 0 ? -1 : mChatList.get().get(position).getPrimaryKey().hashCode();
        }

        @Override
        public int getItemCount() {
            return mChatList.get().size();
        }

        @Override
        public RecyclerViewHolder<Chat> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType) {
            return new ChatViewHolder();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }

    /**
     * A basic view to display the chat title and number of unread messages.
     */
    private class ChatViewHolder implements RecyclerViewHolder<Chat>, View.OnClickListener {

        private Chat mItem;
        private TextView mTitle;
        private TextView mUnRead;

        @Override
        public View createView(LayoutInflater inflater, ViewGroup parent) {
            View view = inflater.inflate(R.layout.item_chat, parent, false);

            view.setClickable(true);
            view.setOnClickListener(this);

            mTitle = view.findViewById(R.id.chat_title);
            mUnRead = view.findViewById(R.id.chat_unread);

            return view;
        }

        @Override
        public void updateView(Chat chatDataItem, int position) {
            mItem = chatDataItem;

            if (mItem == null) {
                return;
            }

            if (!TextUtils.isEmpty(mItem.subject)) {
                mTitle.setText(mItem.subject);
            } else {
                mTitle.setText(R.string.no_subject);
            }

            if (mItem.numUnread == 0) {
                mUnRead.setVisibility(View.GONE);
            } else {
                mUnRead.setVisibility(View.VISIBLE);
                mUnRead.setText(String.valueOf(mItem.numUnread));
            }
        }

        @Override
        public void onRecycled() {
            mItem = null;
            mTitle.setText(null);
            mUnRead.setText(null);
        }

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra(ChatActivity.INTENT_EXTRA_CHAT_ID, mItem.chatId);
            startActivity(intent);
        }
    }
}
