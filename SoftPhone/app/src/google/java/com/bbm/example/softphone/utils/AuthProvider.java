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

package com.bbm.example.softphone.utils;


import android.content.Context;

import com.bbm.example.softphone.R;
import com.bbm.sdk.support.identity.auth.GoogleAuthHelper;
import com.bbm.sdk.support.identity.user.FirebaseUserDbSync;
import com.bbm.sdk.support.protect.SimplePasswordProvider;
import com.bbm.sdk.support.util.FirebaseHelper;
import com.bbm.sdk.support.util.IdentityUtils;

/**
 * Provides access to the GoogleAuth implementation.
 */
public class AuthProvider {
    /**
     * Initialize the GoogleAuthHelper with the application context, FirebaseUserDbSync and client id.
     * @param context android context
     */
    public static void initAuthProvider(Context context) {
        //Init google authentication provider
        GoogleAuthHelper.initGoogleSignIn(context, FirebaseUserDbSync.getInstance(), context.getString(R.string.default_web_client_id));
        //Init protected manager
        FirebaseHelper.initProtected(context, SimplePasswordProvider.getInstance());
        //Init syncing users
        IdentityUtils.initUserDbSync(context, true);
    }
}
