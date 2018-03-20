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

package com.bbm.example.datatransfer.utils;


import android.content.Context;
import android.text.TextUtils;

import com.bbm.example.datatransfer.BuildConfig;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.protect.ProtectedManager;
import com.bbm.sdk.support.protect.ProtectedManagerErrorHandler;
import com.bbm.sdk.support.protect.SimplePasswordProvider;
import com.bbm.sdk.support.protect.providers.AzureKeyStorageProvider;
import com.bbm.sdk.support.util.IdentityUtils;

public class AuthProvider {

    /**
     * Initialize the AzureAdAuthenticationManager with the application context.
     * Start the protected manager and user sync.
     * @param context android context
     */
    public static void initAuthProvider(final Context context) {
        //Start the auth manager
        AzureAdAuthenticationManager.initialize(context,
                BuildConfig.AZURE_AD_TENANT_ID,
                BuildConfig.BBME_AUTH_SCOPE,
                BuildConfig.AZURE_LOGIN_AUTHORITY);

        //Start user sync.
        IdentityUtils.initUserDbSync(context, true);

        //Start protected manager
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                String uid = AzureAdAuthenticationManager.getInstance().getUserIdentifier().get();
                if (!TextUtils.isEmpty(uid)) {
                    //Once we know the UID for the user trigger the protected manager to start syncing their keys
                    ProtectedManager.getInstance().start(
                            context,
                            uid,
                            new AzureKeyStorageProvider(BuildConfig.KMS_URL),
                            SimplePasswordProvider.getInstance(),
                            new ProtectedManagerErrorHandler()
                    );
                    return true;
                }
                return false;
            }
        });
    }
}
