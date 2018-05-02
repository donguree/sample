package com.tvstorm.sample;

import android.app.Activity;
import android.content.ComponentName;
import android.database.Cursor;
import android.graphics.Rect;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tvstorm.common.player.PlayerService;
import com.tvstorm.common.player.PlayerServiceAdapter;
import com.tvstorm.common.player.PlayerServiceListener;
import com.tvstorm.common.player.PlayerState;
import com.tvstorm.common.player.PlayerType;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String SERVICE_MANAGER = "com.tvstorm.service.trv";
    private static final String TVS_INPUT_SERVICE = "com.tvstorm.service.tif.TvsInputService";

    private final Object mLock = new Object();

    private TvView mTvView;

    private String mInputId;

    private MessageHandler mHandler;

    private PlayerState mPlayerState;
    private Rect mPendingTvViewSizeRect;

    private PlayerServiceListener mPlayerServiceListener = new PlayerServiceAdapter() {
        @Override
        public void onPlayerStatusChanged(
                @NonNull PlayerType playerType, @NonNull PlayerState playerState) {
            Log.v(TAG, "onPlayerStatusChanged(...)  " + playerType + "  " + playerState);
            if (playerType == PlayerType.LIVE) {
                mPlayerState = playerState;
                Log.d(TAG, "mPendingTvViewSizeRect=" + mPendingTvViewSizeRect);
                if (mPendingTvViewSizeRect != null &&
                        playerState.equals(
                                PlayerState.PREPARED,
                                PlayerState.STARTED,
                                PlayerState.STOPPED,
                                PlayerState.PAUSED,
                                PlayerState.PLAYBACK_COMPLETED)) {
                    synchronized (mLock) {
                        mHandler.sendMessage(MessageHandler.RESIZE_TV_VIEW, mPendingTvViewSizeRect);
                        mPendingTvViewSizeRect = null;
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");

        PlayerService.getInstance().init(this);
        PlayerService.getInstance().addListener(mPlayerServiceListener);

        setContentView(R.layout.activity_main);

        mTvView = findViewById(R.id.tv_view);

        View resizeButton = findViewById(R.id.resize_button);
        resizeButton.setOnClickListener(v -> resizeTvView(new Rect(480, 270, 1440, 810)));

        View fullScreenButton = findViewById(R.id.full_screen_button);
        fullScreenButton.setOnClickListener(v -> resizeTvView(new Rect(0, 0, 1920, 1080)));

        TextView versionTextView = findViewById(R.id.app_version);
        versionTextView.setText(BuildConfig.VERSION_NAME);

        mHandler = new MessageHandler(this);
        mInputId = TvContract.buildInputId(
                new ComponentName(SERVICE_MANAGER, TVS_INPUT_SERVICE));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart()");
        resizeTvView(new Rect(0, 0, 1920, 1080)); // full screen
        mTvView.tune(mInputId, TvContract.buildChannelUri(getFirstChannelId()));
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop()");
        mTvView.reset();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");
        PlayerService.getInstance().removeListener(mPlayerServiceListener);
        PlayerService.getInstance().terminate(this);
        super.onDestroy();
    }

    private void resizeTvView(@NonNull Rect rect) {
        Log.v(TAG, "resizeTvView(...)  " + rect);
        mPlayerState = PlayerService.getInstance().getPlayerState(PlayerType.LIVE);
        if (mPlayerState.equals(
                PlayerState.PREPARED,
                PlayerState.STARTED,
                PlayerState.STOPPED,
                PlayerState.PAUSED,
                PlayerState.PLAYBACK_COMPLETED)) {
            Log.d(TAG, "Requesting resize TV view...");
            mHandler.sendMessage(MessageHandler.RESIZE_TV_VIEW, rect);
        } else {
            synchronized (mLock) {
                Log.w(TAG, "Pending resize TV view...");
                mPendingTvViewSizeRect = rect;
            }
        }
    }

    private void resizeTvViewInternal(@NonNull Rect rect) {
        Log.v(TAG, "resizeVideoInternal(...)  " + rect);

        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mTvView.getLayoutParams();

        params.leftMargin = rect.left;
        params.topMargin = rect.top;
        params.width = rect.right - rect.left;;
        params.height = rect.bottom - rect.top;;

        mTvView.setLayoutParams(params);
    }

    private long getFirstChannelId() {
        Uri uri = TvContract.buildChannelsUriForInput(mInputId);
        Log.d(TAG, uri.toString());
        String[] projection = {TvContract.Channels._ID};

        try (Cursor cursor = getContentResolver()
                .query(uri, projection, null, null, null)) {
            if (cursor == null) {
                Log.w(TAG, "Failed to get first channel ID (null Cursor)");
                return 0L;
            }

            if (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                Log.d(TAG, "Channel ID: " + channelId);
                return channelId;
            }
        }

        return 0L;
    }

    private static class MessageHandler extends Handler {

        private static final int RESIZE_TV_VIEW = 1;

        private final WeakReference<MainActivity> mWeakReference;

        MessageHandler(@NonNull MainActivity mainActivity) {
            mWeakReference = new WeakReference<>(mainActivity);
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
