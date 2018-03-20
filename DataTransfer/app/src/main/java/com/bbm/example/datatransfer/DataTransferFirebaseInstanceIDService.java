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

package com.bbm.example.datatransfer;

import android.text.TextUtils;

import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.service.BBMEnterpriseState;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

public class DataTransferFirebaseInstanceIDService extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {
        Logger.d("onTokenRefresh:");
        if (BBMEnterprise.getInstance().getState().get() != BBMEnterpriseState.UNINITIALIZED) {
            String token = FirebaseInstanceId.getInstance().getToken();
            if (!TextUtils.isEmpty(token)) {
                BBMEnterprise.getInstance().setPushToken(token);
            }
        }
    }
}