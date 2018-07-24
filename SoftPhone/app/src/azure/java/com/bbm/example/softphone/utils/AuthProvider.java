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

import com.bbm.example.softphone.BuildConfig;
import com.bbm.sdk.support.identity.auth.AzureAdAuthenticationManager;
import com.bbm.sdk.support.kms.BlackBerryKMSSource;
import com.bbm.sdk.support.protect.AzureCloudKeySource;
import com.bbm.sdk.support.protect.KeySource;
import com.bbm.sdk.support.protect.UserChallengePasscodeProvider;
import com.bbm.sdk.support.util.KeySourceManager;

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

        KeySource keySource;
        //If using cloud key set the key source to Azure, otherwise we will default to using BlackBerry KMS as the key source
        if (BuildConfig.USE_CLOUD_KEY) {
            //Initialize an AzureCloudKeySource
            keySource = new AzureCloudKeySource(context, BuildConfig.KEY_PROVIDER_SERVER_URL, new UserChallengePasscodeProvider(context));
        } else {
            keySource = new BlackBerryKMSSource(new UserChallengePasscodeProvider(context));
        }

        KeySourceManager.setKeySource(keySource);
        keySource.start();
    }
}
