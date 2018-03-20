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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.bbm.example.announcements.chatHolders.AnnouncementEditHolder;
import com.bbm.example.announcements.chatHolders.AnnouncementHolder;
import com.bbm.example.announcements.chatHolders.EventHolder;
import com.bbm.example.announcements.chatHolders.IncomingTextHolder;
import com.bbm.example.announcements.chatHolders.OutgoingTextHolder;
import com.bbm.example.announcements.chatHolders.UnknownMessage;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.ChatInvite;
import com.bbm.sdk.bbmds.outbound.ChatLeave;
import com.bbm.sdk.bbmds.outbound.ChatMessageSend;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.ui.widgets.chats.ChatBubbleColorProvider;
import com.bbm.sdk.support.ui.widgets.chats.ChatBubbleColors;
import com.bbm.sdk.support.ui.widgets.chats.ChatMessageRecyclerViewAdapter;
import com.bbm.sdk.support.ui.widgets.chats.ChatMessageViewProvider;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerContextMenuInfoWrapperView;
import com.bbm.sdk.support.ui.widgets.recycler.RecyclerViewHolder;
import com.bbm.sdk.support.util.BbmUtils;

import java.util.Collections;

/**
 * The basic chat activity. This will display all the chats messages for the provided chat identifier.
 */
public final class ChatActivity extends AppCompatActivity {

    // The ChatId found in the Chat object.
    public static final String INTENT_EXTRA_CHAT_ID = "chat-id";

    // The custom tag used to identify an announcement message
    public final static String TAG_ANNOUNCEMENTS = "announcement";

    // A unique ID for the announcement popup menu
    private static final int MENU_ANNOUNCEMENT_ID = 1171;

    // The chatId for the Chat being displayed
    private String mChatId;

    // This observer will set the chat subject as the activity title
    private Observer mChatSubjectObserver;

    // The recycling data adapter to fetch the chat messages
    private ChatMessageRecyclerViewAdapter mAdapter;

    // The announcement message that will be edited
    private DecoratedMessage mAnnouncementMessageToEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get the Chat ID from intent.
        mChatId = getIntent().getStringExtra(INTENT_EXTRA_CHAT_ID);

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

        // Add an observer to set the title of the activity to the subject of the chat
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

        // Initialize the recycler view
        final RecyclerView recyclerView = findViewById(R.id.messages_list);
        mAdapter = new ChatMessageRecyclerViewAdapter(this,
                recyclerView,
                mChatId,
                new MessageViewProvider(),          // our extend ChatMessageRecyclerViewAdapter
                new MessageColorProvider());        // our extend ChatBubbleColorProvider

        // Set the adapter to auto-scroll on new items
        mAdapter.setAutoScrollOnNewItem(true);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(ChatActivity.this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(mAdapter);

        final EditText inputText = findViewById(R.id.inputText);
        final Button sendButton = findViewById(R.id.sendButton);

        // Hook up the send button. Takes the inputText and sends, so long as the text value is not empty.
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String text = inputText.getText().toString().trim();

                if (TextUtils.isEmpty(text)) {
                    // Do nothing, no text entered.
                    return;
                }

                //Send a new outgoing text message, setting the content to the input text
                BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatMessageSend(mChatId, ChatMessageSend.Tag.Text).content(text));
                inputText.setText("");
            }
        });

        final Button announcementButton = findViewById(R.id.announcementButton);

        // Hook up the announcement button. Confirm the user wants to send an announcement message before sending. Only
        // send if the inputText is not empty
        announcementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final String text = inputText.getText().toString().trim();

                if (TextUtils.isEmpty(text)) {
                    // Do nothing, no text entered.
                    return;
                }

                // Make sure the user wants to send an announcement
                final AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this, R.style.AppTheme_dialog)
                        .setMessage(R.string.announcement_confirm)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                final ChatMessageSend message = new ChatMessageSend(mChatId, TAG_ANNOUNCEMENTS)
                                        .content(text);
                                BBMEnterprise.getInstance().getBbmdsProtocol().send(message);

                                inputText.setText("");
                            }
                        })
                        .setNegativeButton(android.R.string.no, null);

                final AppCompatDialog dialog = builder.create();
                dialog.show();
                Helper.resizeAlertDialog(dialog);
            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();

        // When the activity is paused, stop the data adapter
        if (mAdapter != null) {
            mAdapter.pause();
        }
        BbmUtils.markChatMessagesAsRead(mChatId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mChatSubjectObserver.changed();

        // When the activity is resumed, start the data adapter
        if (mAdapter != null) {
            mAdapter.start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null) {
            return false;
        }

        switch (item.getItemId()) {
            case R.id.announcementMenu:
                // Open the list of announcements for this Chat
                Intent intent = new Intent(ChatActivity.this, ViewAnnouncementsActivity.class);
                intent.putExtra(ChatActivity.INTENT_EXTRA_CHAT_ID, mChatId);
                startActivity(intent);
                return true;

            case R.id.endChatMenu:
                BBMEnterprise.getInstance().getBbmdsProtocol().send(new ChatLeave(Collections.singletonList(mChatId)));
                ChatActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ChatActivity.this.finish();
                    }
                });
                return true;

            case R.id.addUserMenu:
                showAddUserDialog();
                return true;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (menuInfo instanceof RecyclerContextMenuInfoWrapperView.RecyclerContextMenuInfo) {
            RecyclerContextMenuInfoWrapperView.RecyclerContextMenuInfo info =
                    (RecyclerContextMenuInfoWrapperView.RecyclerContextMenuInfo) menuInfo;

            int position = info.position;

            DecoratedMessage decoratedMessage = mAdapter.getItem(position);
            ChatMessage chatMessage = decoratedMessage.getChatMessage();
            if (chatMessage.tag.equals(TAG_ANNOUNCEMENTS)) {
                menu.add(Menu.NONE, MENU_ANNOUNCEMENT_ID, Menu.NONE, R.string.edit_announcement);
                mAnnouncementMessageToEdit = decoratedMessage;
            } else {
                mAnnouncementMessageToEdit = null;
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_ANNOUNCEMENT_ID && mAnnouncementMessageToEdit != null) {
            Helper.showEditAnnouncementDialog(ChatActivity.this, mAnnouncementMessageToEdit.getChatMessage());
            return true;
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Show a dialog to allow the user to add more users to this Chat.
     */
    private void showAddUserDialog() {

        // Get the view layout for the dialog.
        final View contents = LayoutInflater.from(ChatActivity.this).inflate(R.layout.dialog_add_user, null);
        final EditText regIdText = contents.findViewById(R.id.reg_id);

        // Setup the dialog to add a user
        AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this, R.style.AppTheme_dialog)
                .setTitle(R.string.add_user_menu)
                .setView(contents)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Long regId = Long.valueOf(regIdText.getText().toString());

                        final ChatInvite.Invitees invite = new ChatInvite.Invitees();
                        invite.regId(regId);

                        final ChatInvite message = new ChatInvite(mChatId, Collections.singletonList(invite));
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(message);
                    }
                });

        final AlertDialog dialog = builder.create();

        // Set a text watcher to enable the alert dialog positive button when a value is entered.
        regIdText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(regIdText.getText().toString().trim())) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        Helper.resizeAlertDialog(dialog);
    }


    /**
     * Helper for determine the type of message the recycling view will need to create.
     * This defines types:
     * 1. Outgoing - For all chat messages sent by the local user
     * 2. Incoming - For all chat messages from other users
     * 3. Chat event - For events that occur in a chat. For example "join" or "leave"
     * 4. Announcement - Our custom  message for this example.
     * 5. Announcement Edit - Our custom message for an announcement that has been edited.
     * 6. Other - a catch all for messages that are not defined for this example
     */
    public final class MessageViewProvider implements ChatMessageViewProvider {

        static final int ITEM_VIEW_TYPE_TEXT_OUTGOING = 0;
        static final int ITEM_VIEW_TYPE_TEXT_INCOMING = 1;
        static final int ITEM_VIEW_TYPE_CHAT_EVENT = 2;
        static final int ITEM_VIEW_TYPE_ANNOUNCEMENT = 3;
        static final int ITEM_VIEW_TYPE_ANNOUNCEMENT_EDIT = 4;
        static final int ITEM_VIEW_TYPE_OTHER = 99;

        @Override
        public boolean canMerge(ChatMessage m1, ChatMessage m2) {
            return false;
        }

        @Override
        public RecyclerViewHolder<DecoratedMessage> onCreateRecyclerViewHolder(ViewGroup viewGroup, int viewType) {
            switch (viewType) {
                case ITEM_VIEW_TYPE_TEXT_OUTGOING:
                    return new OutgoingTextHolder(getLayoutInflater(), viewGroup);
                case ITEM_VIEW_TYPE_TEXT_INCOMING:
                    return new IncomingTextHolder(getLayoutInflater(), viewGroup);
                case ITEM_VIEW_TYPE_ANNOUNCEMENT:
                    return new AnnouncementHolder(getLayoutInflater(), viewGroup);
                case ITEM_VIEW_TYPE_CHAT_EVENT:
                    return new EventHolder(getLayoutInflater(), viewGroup);
                case ITEM_VIEW_TYPE_ANNOUNCEMENT_EDIT:
                    return new AnnouncementEditHolder(ChatActivity.this);
                case ITEM_VIEW_TYPE_OTHER:
                default:
                    return new UnknownMessage(getLayoutInflater(), viewGroup);
            }
        }

        @Override
        public int getItemTypeForMessage(ChatMessage item) {

            if (item.exists != Existence.YES) {
                return ITEM_VIEW_TYPE_OTHER;
            }

            // If this message has an "Edit" reference then use the
            // ITEM_VIEW_TYPE_ANNOUNCEMENT_EDIT type.
            for (ChatMessage.Ref ref : item.ref) {
                if (ref.tag == ChatMessage.Ref.Tag.Edit) {
                    return ITEM_VIEW_TYPE_ANNOUNCEMENT_EDIT;
                }
            }

            // Next look at the item.tag, handle the SDK supported tags and
            // look for the custom Announcement tag.
            switch (item.tag) {
                case ChatMessage.Tag.Join:
                case ChatMessage.Tag.Leave:
                case ChatMessage.Tag.Subject:
                    return ITEM_VIEW_TYPE_CHAT_EVENT;
                case ChatMessage.Tag.Text:
                    boolean isIncoming = item.hasFlag(ChatMessage.Flags.Incoming);
                    return isIncoming ? ITEM_VIEW_TYPE_TEXT_INCOMING : ITEM_VIEW_TYPE_TEXT_OUTGOING;

                case TAG_ANNOUNCEMENTS:
                    return ITEM_VIEW_TYPE_ANNOUNCEMENT;
            }

            return ITEM_VIEW_TYPE_OTHER;
        }
    }

    /**
     * Our helper for the chat bubble colors. There are 3 defined, incoming, outgoing
     * and announcement.
     * <p>
     * Incoming is applied to all chat messages from other users.
     * Outgoing is applied to all chat messages the local user sends.
     * Announcement is applied to all announcement type of messages, incoming or outgoing.
     */
    public final class MessageColorProvider implements ChatBubbleColorProvider {

        private final ChatBubbleColors mIncoming;
        private final ChatBubbleColors mOutgoing;
        private final ChatBubbleColors mAnnouncement;

        MessageColorProvider() {
            mIncoming = new ChatBubbleColors(
                    R.drawable.incoming_message_background,
                    R.color.chat_bubble_incoming,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_highlight_outgoing,
                    R.color.chat_bubble_status_outgoing,
                    R.color.chat_bubble_error_outgoing,
                    R.color.chat_bubble_alert_text_outgoing);

            mOutgoing = new ChatBubbleColors(
                    R.drawable.outgoing_message_background,
                    R.color.chat_bubble_outgoing,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_highlight_outgoing,
                    R.color.chat_bubble_status_outgoing,
                    R.color.chat_bubble_error_outgoing,
                    R.color.chat_bubble_alert_text_outgoing);

            mAnnouncement = new ChatBubbleColors(
                    R.drawable.announcement_message_background,
                    R.color.announcement_background,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_text_incoming,
                    R.color.chat_bubble_highlight_outgoing,
                    R.color.chat_bubble_status_outgoing,
                    R.color.chat_bubble_error_outgoing,
                    R.color.chat_bubble_alert_text_outgoing);
        }

        @Override
        public ChatBubbleColors getMpcMessageColors(@NonNull ChatMessage message) {
            return getOneToOneIncomingMessageColors(message);
        }

        @Override
        public ChatBubbleColors getOutgoingMessageColors(@NonNull ChatMessage message) {

            // Check if our custom tag is found. If so then return the mAnnouncement provider
            if (message.tag != null && message.tag.equals(TAG_ANNOUNCEMENTS)) {
                return mAnnouncement;
            }

            return mOutgoing;
        }

        @Override
        public ChatBubbleColors getOneToOneIncomingMessageColors(@NonNull ChatMessage message) {

            // Check if our custom tag is found. If so then return the mAnnouncement provider
            if (message.tag != null && message.tag.equals(TAG_ANNOUNCEMENTS)) {
                return mAnnouncement;
            }

            return mIncoming;
        }
    }
}
