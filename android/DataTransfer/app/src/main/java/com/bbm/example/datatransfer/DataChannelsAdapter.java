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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bbm.sdk.media.BBMEDataChannel;
import com.bbm.sdk.media.BBMEDataChannelSender;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.google.common.io.Files;

import java.util.ArrayList;

/**
 * An adapter for displaying a list of "transfer items".
 * A transfer item represents a file sent via a data channel.
 */
public class DataChannelsAdapter extends RecyclerView.Adapter<DataChannelsAdapter.BBMEDataChannelViewHolder> {

    public static class TransferItem {
        public Uri mFileUri;
        public BBMEDataChannel mDataChannel;
        public boolean mError;
        public TransferItem(Uri fileUri, BBMEDataChannel channel) {
            mFileUri = fileUri;
            mDataChannel = channel;
        }
    }

    public class BBMEDataChannelViewHolder extends RecyclerView.ViewHolder {

        public TextView mNameView;
        public ProgressBar mProgressBarView;
        public ImageView mTypeView;
        public TransferItem mItem;
        public TextView mSizeView;

        public ObservableMonitor mProgressObserver = new ObservableMonitor() {
            @Override
            protected void run() {
                mProgressBarView.setProgress(mItem.mDataChannel.getProgress().get());
                long bytesTransferred = mItem.mDataChannel.getBytesTransferred().get() / 1024;
                long expectedSize = mItem.mDataChannel.getExpectedSize() / 1024;
                mSizeView.setText(mSizeView.getContext().getString(R.string.transfer_size, bytesTransferred, expectedSize));
            }
        };

        public BBMEDataChannelViewHolder(View itemView) {
            super(itemView);
            mNameView = (TextView)itemView.findViewById(R.id.transfer_name);
            mProgressBarView = (ProgressBar)itemView.findViewById(R.id.transfer_progress);
            mTypeView = (ImageView)itemView.findViewById(R.id.transfer_type);
            mSizeView = (TextView)itemView.findViewById(R.id.transfer_bytes_size);
        }
    }

    private Context mContext;
    private ArrayList<TransferItem> mTransferItems;

    public DataChannelsAdapter(Context context, ArrayList<TransferItem> transferItems) {
        mContext = context;
        mTransferItems = transferItems;
    }

    @Override
    public BBMEDataChannelViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView = inflater.inflate(R.layout.connection_transfer_item, parent, false);
        final BBMEDataChannelViewHolder holder = new BBMEDataChannelViewHolder(itemView);
        //Add click listener to open file
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder.mItem.mFileUri != null) {
                    Intent myIntent = new Intent(Intent.ACTION_VIEW);
                    String ext = Files.getFileExtension(holder.mItem.mDataChannel.getName());
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    myIntent.setDataAndType(holder.mItem.mFileUri, mimeType);
                    myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    mContext.startActivity(myIntent);
                }
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(BBMEDataChannelViewHolder holder, int position) {
        TransferItem item = mTransferItems.get(position);
        holder.mItem = item;
        //Activate an observer to track the progress sending/receiving the file
        holder.mProgressObserver.activate();
        //Set the name from the channel (file name in this example)
        holder.mNameView.setText(item.mDataChannel.getName());
        boolean isSending = item.mDataChannel instanceof BBMEDataChannelSender;
        //Display a sending/receiving/error icon
        if (item.mError) {
            holder.mTypeView.setImageResource(R.drawable.ic_error_black_24dp);
        } else {
            holder.mTypeView.setImageResource(
                    isSending ? R.drawable.ic_file_upload_black_24dp : R.drawable.ic_file_download_black_24dp
            );
        }
    }

    @Override
    public int getItemCount() {
        return mTransferItems.size();
    }

    @Override
    public void onViewRecycled(BBMEDataChannelViewHolder holder) {
        holder.mItem = null;
        holder.mProgressObserver.dispose();
    }
}