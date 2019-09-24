/*
 * Copyright (c) 2017 BlackBerry Limited. All Rights Reserved.
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

package com.bbm.example.common.reactive;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.DelegatingValue;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.reactive.AbstractObservableList;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.support.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around list of chat messages for a chat.
 * Each element is an observable value wrapping a message so the UI can get notifications as the message is loaded.
 * By default each element will be lazy loaded as needed.
 * This can be used for RecyclerView.Adapter, a ListView, or other purposes.
 * This is a simple and basic implementation, see the Rich Chat sample app for how it was implemented for BBM.
 */
public class SimpleChatMessageList extends AbstractObservableList<ObservableValue<ChatMessage>> {
    //list of the chat messages, each is wrapped in an observable
    private List<ObservableValue<ChatMessage>> mData = new ArrayList<>();
    private String mChatId;
    private long mLastNumMessages = 0;
    private boolean mPending = true;
    private boolean mLazyLoad;

    ObserveConnector mObserveConnector = new ObserveConnector();

    /**
     * Create a new observable chat message list in lazy load mode.
     *
     * @param chatId the chat ID to get messages for
     */
    public SimpleChatMessageList(String chatId) {
        this(chatId, true);
    }

    /**
     * Create a new observable chat message list.
     *
     * @param chatId the chat ID to get messages for
     * @param lazyLoad if true this will only request/load messages when they are needed,
     *                 if false this will request/load all messages immediately.
     */
    public SimpleChatMessageList(String chatId, boolean lazyLoad) {
        mLazyLoad = lazyLoad;
        mChatId = chatId;

        final ObservableValue<Chat> chat = BBMEnterprise.getInstance().getBbmdsProtocol().getChat(mChatId);
        mObserveConnector.connect(chat, new Observer() {
            @Override
            public void changed() {
                boolean notify = false;
                final long numMessages = chat.get().numMessages;
                Logger.d("changed: chat id="+mChatId+" exists="+chat.get().exists+" numMessages="+numMessages+" mLastNumMessages="+mLastNumMessages);
                if (chat.get().exists == Existence.YES) {
                    mPending = false;
                    if (numMessages > mLastNumMessages) {
                        for (long num = mLastNumMessages ; num < numMessages; ++num) {
                            ObservableValue<ChatMessage> message;
                            if (mLazyLoad) {
                                LazyChatMessage chatMessage = new LazyChatMessage();
                                chatMessage.chatId = mChatId;
                                chatMessage.messageId = num;
                                //create a delegating ObservableValue that we can later point to the real ObservableValue if needed,
                                //but don't call the getChatMessage() so we don't load data until needed
                                message = new DelegatingValue<ChatMessage>(chatMessage);
                            } else {
                                final ChatMessage.ChatMessageKey lookupKey = new ChatMessage.ChatMessageKey(mChatId, num + 1);
                                message = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(lookupKey);
                            }
                            mData.add(message);
                            notify = true;
                        }
                        mLastNumMessages = numMessages;
                    }
                }

                if (notify) {
                    dataSetChanged();
                    notifyObservers();
                }
            }
        }, true);
    }


    @Override
    public ObservableValue<ChatMessage> get(int index) {
        ObservableValue<ChatMessage> messageObservableValue = mData.get(index);
        ChatMessage message = messageObservableValue.get();
        if (message instanceof LazyChatMessage) {
            //it was lazy loaded, get real one now
            final ChatMessage.ChatMessageKey chatMessageKey = new ChatMessage.ChatMessageKey(mChatId, message.messageId + 1);
            ObservableValue<ChatMessage> realObservableValue = BBMEnterprise.getInstance().getBbmdsProtocol().getChatMessage(chatMessageKey);
            //still return the delegating OV in list, but point it to the real one so it is updated when the real data is
            ((DelegatingValue)messageObservableValue).delegateTo(realObservableValue);
            Logger.v("get: lazy loading  index="+index+" mChatId="+mChatId+" message.messageId="+message.messageId+" messageObservableValue="+messageObservableValue+" realObservableValue="+realObservableValue+" get="+realObservableValue.get());
        }

        return messageObservableValue;
    }

    @Override
    public int size() {
        return mData.size();
    }

    @Override
    public boolean isPending() {
        return mPending;
    }

    /**
     * WARNING: This will create a copy of the data and can be relatively expensive to call
     * @return the list of observable chat messages
     */
    @Override
    public List<ObservableValue<ChatMessage>> get() {
        ArrayList<ObservableValue<ChatMessage>> copy = new ArrayList<>(mData.size());
        copy.addAll(mData);
        return copy;
    }

    //just used to detect if an element needs to be lazy loaded
    class LazyChatMessage extends ChatMessage {

    }
}
