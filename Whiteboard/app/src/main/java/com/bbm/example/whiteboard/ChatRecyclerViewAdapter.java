/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
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

package com.bbm.example.whiteboard;


import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bbm.example.common.util.Utils;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.support.util.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This adapter displays a simple chat list. The chat subject or participants and count of messages in it.
 * For a more complete chat list see the Rich Chat app.
 */
public class ChatRecyclerViewAdapter extends RecyclerView.Adapter<ChatRecyclerViewAdapter.ViewHolder> {
    private ObserveConnector mObserveConnector = new ObserveConnector();
    private final ObservableList<Chat> mChats = BBMEnterprise.getInstance().getBbmdsProtocol().getChatList();
    private List<Chat> mSortedChats;
    private Context mContext;

    public ChatRecyclerViewAdapter(Context context) {
        mContext = context;

        mObserveConnector.connect(mChats, new Observer() {
            @Override
            public void changed() {
                Logger.d("mChats.changed: pending="+mChats.isPending()+" size="+mChats.size()+" list="+mChats.get());
                mSortedChats = mChats.get();
                Collections.sort(mSortedChats, new Comparator<Chat>() {
                    @Override
                    public int compare(Chat c1, Chat c2) {
                        long diff = c1.lastActivity - c2.lastActivity;
                        if (diff == 0) {
                            return 0;
                        } else if (diff < 0) { //convert the long value to int
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                });
                notifyDataSetChanged();
            }
        }, true);
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chat_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        //remove any observers that might be observing data from another row
        holder.mObserveConnector.removeObservers();

        Chat chat = mSortedChats.get(position);

        Logger.d("onBindViewHolder: pos="+position+" chat="+chat+" chat.exists="+chat.exists);

        holder.mItem = chat;
        holder.mMessageCount.setText(String.valueOf(chat.numMessages));

        if (!TextUtils.isEmpty(chat.subject)) {
            holder.mContentView.setText(chat.subject);
        } else {
            //get the list of participants, wrapped in a observable value and listen for when it changes to update UI
            //this is needed since when the app first loads the data needed to format user names might not be available yet
            //so we need to handle when it is ready (exists) and update UI then otherwise the default (user PIN's) will be displayed
            final ObservableValue<String> formattedParticipants = Utils.getFormattedParticipantList(chat.chatId);
            holder.mObserveConnector.connect(formattedParticipants, new Observer() {
                @Override
                public void changed() {
                    holder.mContentView.setText(formattedParticipants.get());
                }
            }, true);
        }

        if (chat.numMessages > 0) {
            final ObservableValue<ChatMessage> lastChatMessage = BBMEnterprise.getInstance().getBbmdsProtocol().
                    getChatMessage(new ChatMessage.ChatMessageKey(chat.chatId, chat.lastMessage));

            holder.mObserveConnector.connect(lastChatMessage, new Observer() {
                @Override
                public void changed() {
                    if (lastChatMessage.get().exists == Existence.YES && lastChatMessage.get().state != ChatMessage.State.Read) {
                        holder.mContentView.setTypeface(null, Typeface.BOLD);
                    } else {
                        holder.mContentView.setTypeface(null, Typeface.NORMAL);
                    }
                }
            }, true);
        } else {
            holder.mContentView.setTypeface(null, Typeface.NORMAL);
        }


        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.d("onClick: v="+v);
                //view the chat when the user selects it
                WhiteboardUtils.openChat(mContext, holder.mItem.chatId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mSortedChats.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final View mView;
        final TextView mContentView;

        final TextView mMessageCount;
        Chat mItem;
        ObserveConnector mObserveConnector = new ObserveConnector();

        ViewHolder(View view) {
            super(view);
            mView = view;
            mContentView = (TextView) view.findViewById(R.id.chat_subject);
            mMessageCount = (TextView) view.findViewById(R.id.message_count);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}