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

package com.bbm.example.common.ui;

import com.bbm.sdk.support.identity.user.AppUser;

import java.util.Collection;

/**
 * Callback for user interaction when the user is viewing a list of appUsers in
 * a dialog, fragment, activity.
 */
public interface AppUserSelectedCallback {
    /**
     * Called when user selects user(s) from a list in a dialog.
     *
     * @param appUsers the selected app users
     * @param extraText any text that user entered in dialog,
     *                  this will be null or empty if not shown or user didn't enter anything
     */
    void selected(Collection<AppUser> appUsers, String extraText);

    /**
     * Called when user taps a user in a list
     */
    void selected(AppUser appUser);
}
