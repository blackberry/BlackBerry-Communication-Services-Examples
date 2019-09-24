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


package com.bbm.example.softphone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bbm.sdk.support.util.Logger;
import com.bbm.sdk.common.IOHelper;

import java.io.InputStream;
import java.net.URL;

/**
 * This is just a simple image loader implementation that doesn't do any caching.
 * A real application would probably use an existing image loading framework.
 */
public class ImageTask extends AsyncTask<Void, Void, Bitmap> {

    private String mUrl;
    private ImageView mImageView;

    public static ImageTask load(String url, ImageView imageView) {
        if (TextUtils.isEmpty(url) || imageView == null) {
            Logger.e("Invalid url="+url+" imageView="+imageView);
            return null;
        }

        ImageTask task = new ImageTask(url, imageView);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return task;
    }

    public ImageTask(String url, ImageView imageView) {
        mUrl = url;
        mImageView = imageView;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        InputStream is = null;
        try {
            is = new URL(mUrl).openStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) {
                Logger.d("Loaded bmp=" + bmp + " W=" + bmp.getWidth() + " H=" + bmp.getHeight() + " for mUrl=" + mUrl);
            } else {
                Logger.d("Null bitmap loaded for mUrl=" + mUrl);
            }
            return bmp;
        } catch (Exception e) {
            Logger.i(e, "Failed to load image for mUrl=" + mUrl);
        } catch (OutOfMemoryError oome) {
            Logger.i(oome, "Can not load image for mUrl=" + mUrl);
        } finally {
            IOHelper.safeClose(is);
        }

        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        //if this was cancelled then this view is now used for a different user
        if (!isCancelled()) {
            mImageView.setImageBitmap(bitmap);
            Logger.d("Just set bitmap="+bitmap+" for mUrl="+mUrl);
        } else {
            Logger.d("Discarding cancelled bitmap="+bitmap+" for mUrl="+mUrl);
        }
    }
}
