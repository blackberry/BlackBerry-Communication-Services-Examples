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

package com.bbm.example.softphone;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.BbmdsProtocol;
import com.bbm.sdk.bbmds.Chat;
import com.bbm.sdk.bbmds.ChatMessage;
import com.bbm.sdk.bbmds.ChatMessageCriteria;
import com.bbm.sdk.bbmds.ChatParticipant;
import com.bbm.sdk.bbmds.ChatParticipantCriteria;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.reactive.ObserveConnector;
import com.bbm.sdk.support.util.TimeRangeFormatter;
import com.bbm.sdk.support.util.TimestampScheduler;

import java.util.HashMap;


/**
 * A Fragment which displays a list of call history events.
 */
public class CallHistoryFragment extends Fragment {

    private ObserveConnector mObserveConnector = new ObserveConnector();

    //This map is used to avoid processing messages for which we already have a call history event.
    private HashMap<ChatMessage.ChatMessageKey, CallHistoryEvent> mEventsMap = new HashMap<>();

    //This sorted list informs the adapter when items have changed
    private SortedList<CallHistoryEvent> mSortedHistoryEvents = new SortedList<>(
            CallHistoryEvent.class, new SortedList.Callback<CallHistoryEvent>() {

        @Override
        public void onInserted(int position, int count) {
            mCallHistoryAdapter.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            mCallHistoryAdapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            mCallHistoryAdapter.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public int compare(CallHistoryEvent left, CallHistoryEvent right) {
            if (left.getCallEndTime() < right.getCallEndTime()) {
                return 1;
            } else if (left.getCallEndTime() == right.getCallEndTime()) {
                return 0;
            }
            return -1;
        }

        @Override
        public void onChanged(int position, int count) {
            mCallHistoryAdapter.notifyItemRangeChanged(position, count);
        }

        @Override
        public boolean areContentsTheSame(CallHistoryEvent oldItem, CallHistoryEvent newItem) {
            return (oldItem == null && newItem == null)
                    || oldItem != null && oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(CallHistoryEvent item1, CallHistoryEvent item2) {
            return areContentsTheSame(item1, item2);
        }
    });

    /**
     * This monitor gets all of the chat messages from all users with the "CALL_EVENT" tag.
     * These messages are added to a SortedList which is attached to a RecyclerView adapter
     */
    private ObservableMonitor mCallHistoryEventMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            BbmdsProtocol protocol = BBMEnterprise.getInstance().getBbmdsProtocol();
            //Iterate through all of the chats
            for (Chat chat : protocol.getChatList().get()) {

                //Skip chats without keys or messages
                if (chat.keyState != Chat.KeyState.Synced && chat.numMessages <= 0) {
                    continue;
                }

                //Get the list of chat messages where the Tag = CALL_EVENT
                ChatMessageCriteria criteria = new ChatMessageCriteria().tag(CallUtils.CALL_EVENT_TAG).chatId(chat.chatId);
                final ObservableList<ChatMessage> callEventMessages = protocol.getChatMessageList(criteria);

                //Check if the matching list of messages is pending
                if (!callEventMessages.isPending()) {
                    //Loop through all of the messages
                    for (ChatMessage message : callEventMessages.get()) {
                        //Checking here to make sure that these messages are valid call events.
                        if (!message.hasFlag(ChatMessage.Flags.Deleted) && message.data != null && !mEventsMap.containsKey(message.getPrimaryKey())) {
                            //Add the registration ID of the caller to the call event
                            ChatParticipantCriteria participantCriteria = new ChatParticipantCriteria().chatId(chat.chatId);
                            ObservableList<ChatParticipant> participants = protocol.getChatParticipantList(participantCriteria);
                            if (!participants.isPending() && participants.size() > 0) {
                                User user = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(participants.get(0).userUri).get();
                                if (user.exists == Existence.YES) {
                                    //Create a call history event and set the attributes from the message.data
                                    final CallHistoryEvent event = new CallHistoryEvent();
                                    event.setAttributes(message.data);
                                    //Set the call as incoming if the chat message is incoming
                                    event.setIsIncomingCall(message.hasFlag(ChatMessage.Flags.Incoming));
                                    event.setParticipantRegId(user.regId);
                                    //Add the call history event to the list
                                    mEventsMap.put(message.getPrimaryKey(), event);
                                    mSortedHistoryEvents.add(event);
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    /**
     * This view holder displays a call history event with a button to start a new call with the same person.
     */
    private class CallHistoryEventViewHolder extends RecyclerView.ViewHolder implements Observer {

        private TextView mCallerTextView;
        private TextView mCallTimeTextView;
        private ImageView mCallerAvatar;
        private long mRegId;
        private ObservableValue<AppUser> mObsUser;
        private CallHistoryEvent mEvent;

        CallHistoryEventViewHolder(View itemView) {
            super(itemView);
            mCallerTextView = (TextView)itemView.findViewById(R.id.caller_text);
            mCallTimeTextView = (TextView)itemView.findViewById(R.id.call_time);
            mCallerAvatar = (ImageView)itemView.findViewById(R.id.avatar);
        }

        void bindHolder(int position) {
            //Populate the viewholder
            mEvent = mSortedHistoryEvents.get(position);
            mRegId = mEvent.getParticipantRegId();
            mObsUser = UserManager.getInstance().getUser(mRegId);
            mObserveConnector.connect(mObsUser, this, true);
        }

        @Override
        public void changed() {
            AppUser user = mObsUser.get();

            String name = user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getName()) ?
                    user.getName() : Long.toString(mRegId);
            mCallerTextView.setText(name);

            String callStartTime = TimestampScheduler.getInstance().
                    process(
                            getContext(),
                            mEvent.getCallEndTime() / 1000,
                            TimeRangeFormatter.getVerboseRangesFormatter()
                    );
            String callDurationTime = CallUtils.getCallElapsedTimeFromMilliseconds(mEvent.getCallDuration());

            if (user.getExists() == Existence.YES && !TextUtils.isEmpty(user.getAvatarUrl())) {
                ImageTask.load(user.getAvatarUrl(), mCallerAvatar);
            } else {
                //set to default image immediately otherwise if user scrolls quick they would see previous users avatar for a bit
                mCallerAvatar.setImageResource(R.drawable.default_avatar);
            }

            int callDrawableResource;
            //Set the text and icon based on the call log reason.
            switch (mEvent.getCallLogReason()) {
                case DECLINED:
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_missed : R.drawable.ic_call_unavailable;
                    mCallTimeTextView.setText(getString(mEvent.isIncomingCall()
                            ? R.string.declined : R.string.unavailable, callStartTime));
                    break;
                case UNAVAILABLE:
                case BUSY:
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_missed : R.drawable.ic_call_unavailable;
                    mCallTimeTextView.setText(getString(mEvent.isIncomingCall()
                            ? R.string.missed_call : R.string.unavailable, callStartTime));
                    break;
                case CANCELLED:
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_missed : R.drawable.ic_call_unavailable;
                    mCallTimeTextView.setText(getString(R.string.cancelled, callStartTime));
                    break;
                case CONNECTION_ERROR:
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_missed : R.drawable.ic_call_unavailable;
                    mCallTimeTextView.setText(getString(R.string.connection_error, callStartTime));
                    break;
                case ENDED:
                case DISCONNECTED:
                    mCallTimeTextView.setText(getString(R.string.call_log_formatting,
                            callDurationTime,
                            callStartTime));
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_received : R.drawable.ic_call_made;
                    break;
                default:
                    mCallTimeTextView.setText(getString(R.string.unknown_reason, callStartTime));
                    callDrawableResource = mEvent.isIncomingCall() ? R.drawable.ic_call_received : R.drawable.ic_call_made;

            }
            mCallTimeTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(callDrawableResource, 0, 0, 0);
        }

        void onRecycled() {
            mObserveConnector.remove(this);
            if (mObsUser != null) {
                //Remove ourselves as an observer from the AppUser.
                mObsUser.removeObserver(this);
            }
            mObsUser = null;
            mEvent = null;
            mRegId = 0;
        }
    }

    /**
     * Create an adapter using the call history event list.
     */
    private RecyclerView.Adapter<CallHistoryEventViewHolder> mCallHistoryAdapter = new RecyclerView.Adapter<CallHistoryEventViewHolder>() {

        @Override
        public CallHistoryEventViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View itemView = inflater.inflate(R.layout.call_history_item, parent, false);

            final CallHistoryEventViewHolder holder = new CallHistoryEventViewHolder(itemView);
            itemView.findViewById(R.id.call_history_item_call_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (holder.mRegId != 0) {
                        CallUtils.makeCall(getActivity(), CallHistoryFragment.this, holder.mRegId);
                    } else {
                        Toast.makeText(getContext(), R.string.error_no_regid, Toast.LENGTH_LONG).show();
                    }
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(CallHistoryEventViewHolder holder, int position) {
            holder.bindHolder(position);
        }

        @Override
        public int getItemCount() {
            return mSortedHistoryEvents.size();
        }

        @Override
        public void onViewRecycled(CallHistoryEventViewHolder holder) {
            holder.onRecycled();
        }
    };

    public CallHistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View contentView = inflater.inflate(R.layout.fragment_call_history, container, false);

        RecyclerView recyclerView = (RecyclerView)contentView.findViewById(R.id.call_history_list);
        recyclerView.setAdapter(mCallHistoryAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        //Active our callHistoryEventMonitor which requests CALL_EVENT chat messages and adds them to the adapter
        mCallHistoryEventMonitor.activate();
    }

    @Override
    public void onPause() {
        super.onPause();
        //Clean up observers
        mCallHistoryEventMonitor.dispose();
    }

}
