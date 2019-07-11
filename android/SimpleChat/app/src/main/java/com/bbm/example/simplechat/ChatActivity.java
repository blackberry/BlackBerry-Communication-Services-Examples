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

package com.bbm.example.simplechat;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.outbound.ChatMessageRead;
import com.bbm.sdk.bbmds.outbound.ChatMessageSend;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.reactive.ChatMessageList;

public class ChatActivity extends AppCompatActivity {
    //The chatId for the Chat being displayed
    private String mChatId;

    //The ChatMessageList will lazily load chat messages from the BBM Enterprise SDK
    private ChatMessageList mChatMessageList;

    //This observer will set the chat subject as the activity title
    private Observer mChatSubjectObserver;

    //This observer will mark messages read when pausing or resuming the activity
    private Observer mMarkMessagesReadObserver;

    //Simple ViewHolder for display a text message
    private class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        MessageViewHolder(View itemView) {
            super(itemView);
            messageText = (TextView)itemView.findViewById(R.id.message_text);
        }
    }

    //Adapter for displaying chat messages
    private RecyclerView.Adapter mAdapter = new RecyclerView.Adapter<MessageViewHolder>() {
        private final int TYPE_INCOMING = 0;
        private final int TYPE_OUTGOING = 1;

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            //The incoming message layout is right justified, outgoing left justified
            int layoutRes = viewType == TYPE_INCOMING ? R.layout.incoming_message_item : R.layout.outgoing_message_item;
            View chatView = LayoutInflater.from(ChatActivity.this).inflate(layoutRes, parent, false);
            return new MessageViewHolder(chatView);
        }

        @Override
        public void onBindViewHolder(MessageViewHolder holder, int position) {
            //Get the message to display
            ChatMessage message = mChatMessageList.get(position);
            if (message.getExists() == Existence.MAYBE) {
                return;
            }
            String prefix = "";
            if (message.hasFlag(ChatMessage.Flags.Incoming)) {
                if (message.state != ChatMessage.State.Read) {
                    //instead of displaying sent status like BBM, just show bold until it is read
                    holder.messageText.setTypeface(null, Typeface.BOLD);
                } else {
                    holder.messageText.setTypeface(null, Typeface.NORMAL);
                }
            } else {
                //show the state of the outgoing message
                switch (message.state) {
                    case Sending:
                        prefix = "(...) ";
                        break;
                    case Sent:
                        prefix = "(S) ";
                        break;
                    case Delivered:
                        prefix = "(D) ";
                        break;
                    case Read:
                        prefix = "(R) ";
                        break;
                    case Failed:
                        prefix = "(F) ";
                        break;
                    default:
                        prefix = "(?) ";
                }
            }
            if (message.tag.equals(ChatMessage.Tag.Text)) {
                holder.messageText.setText(prefix + message.content);
            } else {
                //For non-text messages just display the message type Tag
                holder.messageText.setText(message.tag);
            }
        }

        @Override
        public int getItemCount() {
            return mChatMessageList.size();
        }

        @Override
        public int getItemViewType(int position) {
            //Use the ChatMessage.Flag to determine if the message is incoming or outgoing and use the correct type
            ChatMessage message = mChatMessageList.get(position);
            return message.hasFlag(ChatMessage.Flags.Incoming) ? TYPE_INCOMING : TYPE_OUTGOING;
        }
    };

    //This observer will notify the adapter when chat messages are added or changed.
    private IncrementalListObserver mMessageListObserver = new IncrementalListObserver() {
        @Override
        public void onItemsInserted(int position, int count) {
            mAdapter.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onItemsRemoved(int position, int count) {
            mAdapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onItemsChanged(int position, int count) {
            mAdapter.notifyItemRangeChanged(position, count);
        }

        @Override
        public void onDataSetChanged() {
            mAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChatId = getIntent().getStringExtra("chat-id");

        setContentView(R.layout.activity_chat);

        //Add an observer to set the title of the activity to the subject of the chat
        final ObservableValue<Chat> chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId);
        mChatSubjectObserver = new Observer() {
            @Override
            public void changed() {
                setTitle(chat.get().subject);
            }
        };
        chat.addObserver(mChatSubjectObserver);
        mChatSubjectObserver.changed();

        //Create the ChatMessageList
        mChatMessageList = new ChatMessageList(mChatId);

        //Initialize the recycler view
        final RecyclerView messageRecyclerView = (RecyclerView) findViewById(R.id.messages_list);
        messageRecyclerView.setAdapter(mAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(ChatActivity.this);
        layoutManager.setReverseLayout(true);
        messageRecyclerView.setLayoutManager(layoutManager);

        //Add our IncrementalListObserver to the ChatMessageList
        mChatMessageList.addIncrementalListObserver(mMessageListObserver);

        final EditText inputText = (EditText)findViewById(R.id.inputText);
        Button sendButton = (Button)findViewById(R.id.sendButton);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputText.getText().toString();
                if (!text.isEmpty()) {
                    //Send a new outgoing text message, setting the content to the input text
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatMessageSend(mChatId, ChatMessageSend.Tag.Text).content(text));
                    inputText.setText("");
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Stop loading messages from the ChatMessageList
        mChatMessageList.stop();
        //Mark all the messages as read when closing the chat
        markMessagesAsRead();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Start loading messages from the ChatMessageList
        mChatMessageList.start();
        //Mark all the messages as read when opening the chat
        markMessagesAsRead();
    }

    /**
     * Mark all messages in the chat as read
     */
    private void markMessagesAsRead() {
        final ObservableValue<Chat> obsChat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId);
        mMarkMessagesReadObserver = new Observer() {
            @Override
            public void changed() {
                final Chat chat = obsChat.get();
                if (chat.exists == Existence.YES) {
                    //remove ourselves as an observer so we don't get triggered again
                    obsChat.removeObserver(this);
                    if (chat.numMessages == 0 || chat.lastMessage == 0) {
                        return;
                    }

                    //Ask the BBM Enterprise SDK to mark the last message in the chat as read.
                    //All messages older then this will be marked as read.
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(
                            new ChatMessageRead(mChatId, chat.lastMessage)
                    );
                }
            }
        };
        //Add the chatObserver to the chat
        obsChat.addObserver(mMarkMessagesReadObserver);
        //Run the changed method
        mMarkMessagesReadObserver.changed();
    }
}
