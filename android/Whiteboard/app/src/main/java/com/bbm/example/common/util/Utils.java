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

package com.bbm.example.common.util;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.ChatParticipant;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ComputedList;
import com.bbm.sdk.reactive.ObservableTracker;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.reactive.TrackedGetter;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.reactive.AbstractObservableValue;
import com.bbm.sdk.support.util.BbmUtils;

import java.util.ArrayList;

public class Utils {
    /**
     * Observable that will generate a formatted string for the list of participants in the chat for specified chatId.
     * The value will be null until the full list of participants and their required data exists.
     * This will be truncated to just the first 200 characters.
     * The caller is responsible for keeping a reference to the returned value as long as it is needed since a new instance
     * is created each time it is called.
     * The formatting on this is not localized.
     */
    public static ObservableValue<String> getFormattedParticipantList(final String chatId) {
        return new AbstractObservableValue<String>() {
            String data;
            Observer observer;

            /**
             * Returns the current value of the observable.
             * @return The current value of the observable.
             * @trackedgetter This method is a {@link TrackedGetter}
             */
            @TrackedGetter
            @Override
            public String get() {
                ObservableTracker.getterCalled(this);
                return data;
            }

            @Override
            protected void init() {
                final ComputedList<ChatParticipant> participants = BbmUtils.getChatParticipantList(chatId);
                final ArrayList<ObservableValue<User>> users = new ArrayList<>();

                observer = new Observer() {
                    @Override
                    public void changed() {
                        //first need to get the User for each Participant and wait for them all to exist
                        boolean allReady = true;
                        if (users.size() < participants.size()) {
                            users.clear();
                            for (ChatParticipant participant : participants.get()) {
                                ObservableValue<User> user = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(participant.userUri);
                                users.add(user);
                                user.addObserver(this); //listen for changes to this user like their display name, or existence
                                if (user.get().getExists() == Existence.MAYBE) {
                                    allReady = false;
                                }
                            }
                        }

                        if (allReady) {
                            //this could get called when 1 of the users now exists, but might not have all, so double check
                            for (ObservableValue<User> user : users) {
                                if (user.get().getExists() == Existence.MAYBE) {
                                    return;
                                }
                            }
                        } else {
                            return;
                        }

                        //all users are ready to display
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < users.size(); ++i) {
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }

                            ObservableValue<User> user = users.get(i);
                            String name = null;

                            ObservableValue<AppUser> appUserOv = UserManager.getInstance().getUser(user.get().regId);
                            //listen to changes on the user in case it doesn't exist yet, or if name changes
                            appUserOv.addObserver(this);
                            AppUser appUser = appUserOv.get();
                            if (appUser.getExists() == Existence.YES) {
                                name = appUser.getName();
                            }

                            if (name == null || name.isEmpty()) {
                                name = String.valueOf(user.get().regId);
                            }

                            //a normal app would probably not display a user that wasn't in the active state, but here we display other status
                            switch (participants.get(i).state) {
                                case Pending:
                                    sb.append('(').append('P').append(')');
                                    break;
                                case Left:
                                    sb.append('(').append('L').append(')');
                                    break;
                                case Unspecified:
                                    sb.append('(').append('?').append(')');
                                    break;
                            }

                            sb.append(name);
                            if (sb.length() > 200) {
                                //too long
                                sb.append("...");
                                break;
                            }
                        }
                        //now set the real value.
                        data = sb.toString();

                        //notify observers that the data changed
                        notifyObservers();
                    }
                };

                participants.addObserver(observer);

                observer.changed(); //call the changed immediately in case it exists now
            }
        };
    }
}
