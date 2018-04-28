package com.tvstorm.sample;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String SERVICE_MANAGER = "com.tvstorm.service.trv";
    private static final String TVS_INPUT_SERVICE = "com.tvstorm.service.tif.TvsInputService";

    private TvView mTvView;

    private String mInputId;

    private MessageHandler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvView = findViewById(R.id.tv_view);
        Button button1 = findViewById(R.id.resize_button);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.sendMessage(MessageHandler.RESIZE_TV_VIEW,
                        new Rect(96, 54, 960, 540));
            }
        });

        mHandler = new MessageHandler(this);
        mInputId = TvContract.buildInputId(
                new ComponentName(SERVICE_MANAGER, TVS_INPUT_SERVICE));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.v(TAG, "onNewIntent(...)");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart()");
        mHandler.sendMessage(MessageHandler.RESIZE_TV_VIEW, null);
        mTvView.tune(mInputId, TvContract.buildChannelUri(1L));
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop()");
        super.onStop();
    }

    private void resizeTvViewInternal(@Nullable Rect rect) {
        Log.e(TAG, "resizeVideoInternal(...)  " + rect);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mTvView.getLayoutParams();

        Log.d(TAG, "Current TV view size: "
                + "(" + params.leftMargin + "," + params.topMargin + ") "
                + params.width + "x" + params.height);

        final int leftMargin;
        final int topMargin;
        final int width;
        final int height;

        if (rect == null) {
            Log.d(TAG, "Full screen");
            leftMargin = 0;
            topMargin = 0;
            width = ViewGroup.LayoutParams.MATCH_PARENT;
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            width = rect.right - rect.left;
            height = rect.bottom - rect.top;
            Log.d(TAG, "New TV view size: "
                    + "(" + rect.left + "," + rect.top + ") "
                    + width + "x" + height);
            leftMargin = rect.left;
            topMargin = rect.top;
        }

        params.leftMargin = leftMargin;
        params.topMargin = topMargin;
        params.width = width;
        params.height = height;

        mTvView.setLayoutParams(params);
    }

    private static class MessageHandler extends Handler {

        private static final int RESIZE_TV_VIEW = 1;

        private final WeakReference<MainActivity> mWeakReference;

        MessageHandler(@NonNull MainActivity fragment) {
            mWeakReference = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity mainActivity = mWeakReference.get();
            if (mainActivity == null) {
                return;
            }

            switch (msg.what) {
                case RESIZE_TV_VIEW:
                    mainActivity.resizeTvViewInternal((Rect) msg.obj);
                    break;
            }
        }

        void sendMessage(int what, Object obj) {
            obtainMessage(what, obj).sendToTarget();
        }
    }
}
