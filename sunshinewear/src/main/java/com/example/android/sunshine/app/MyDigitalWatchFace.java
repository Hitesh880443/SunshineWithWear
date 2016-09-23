/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyDigitalWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    /**
     * private static final Typeface NORMAL_TYPEFACE =
     * Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
     * <p/>
     * /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyDigitalWatchFace.Engine> mWeakReference;

        public EngineHandler(MyDigitalWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyDigitalWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {


        private static final String TAG = "EngineWatchFace";
        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_TEMP_HIGH_KEY = "weather_temp_high_key";
        private static final String WEATHER_TEMP_LOW_KEY = "weather_temp_low_key";
        private static final String WEATHER_TEMP_ICON_KEY = "weather_temp_icon_key";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        String weatherTempHigh;
        String weatherTempLow;
        Bitmap weatherTempIcon = null;
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint linePaint;
        Paint textPaintTime;
        Paint textPaintTimeBold;
        Paint textPaintDate;
        Paint textPaintTemp;
        Paint textPaintTempBold;
        Rect textBounds = new Rect();
        boolean mAmbient;
        SimpleDateFormat mDateFormat;
        float mXOffset;
        float mYOffset;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
                invalidate();
            }
        };
        Date mDate;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private GoogleApiClient googleApiClient;
        DataApi.DataListener dataListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.e(TAG, "onDataChanged(): " + dataEvents);

                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        String path = event.getDataItem().getUri().getPath();
                        if (WEATHER_PATH.equals(path)) {
                            Log.e(TAG, "Data Changed for " + WEATHER_PATH);
                            try {
                                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                                weatherTempHigh = dataMapItem.getDataMap().getString(WEATHER_TEMP_HIGH_KEY);
                                weatherTempLow = dataMapItem.getDataMap().getString(WEATHER_TEMP_LOW_KEY);
                                final Asset photo = dataMapItem.getDataMap().getAsset(WEATHER_TEMP_ICON_KEY);
//                                weatherTempIcon = loadBitmapFromAsset(googleApiClient, photo);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        weatherTempIcon = loadBitmapFromAsset(googleApiClient, photo);
                                        Log.d("WEATHER-ICON",""+weatherTempIcon.getWidth());
                                    }
                                }).start();
                            } catch (Exception e) {
                                Log.e(TAG, "Exception   ", e);
                                weatherTempIcon = null;
                            }

                        } else {

                            Log.e(TAG, "Unrecognized path:  \"" + path + "\"  \"" + WEATHER_PATH + "\"");
                        }

                    } else {
                        Log.e(TAG, "Unknown data event type   " + event.getType());
                    }
                }
            }

        /*    private Bitmap loadBitmapFromAsset(GoogleApiClient apiClient, Asset asset) {
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(apiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);
            }*/

            private Bitmap loadBitmapFromAsset(GoogleApiClient apiClient, Asset asset) {
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }

            /*InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    apiClient, asset).await().getInputStream();
*/
                final InputStream[] assetInputStream = new InputStream[1];

                Wearable.DataApi.getFdForAsset(apiClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
                    @Override
                    public void onResult(DataApi.GetFdForAssetResult getFdForAssetResult) {
                        assetInputStream[0] = getFdForAssetResult.getInputStream();
                    }
                });


                if (assetInputStream[0] == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream[0]);
            }

        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyDigitalWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyDigitalWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            textPaintTime = new Paint();
            textPaintTime = createTextPaint(resources.getColor(R.color.main_text));

            textPaintTimeBold = new Paint();
            textPaintTimeBold = createTextPaint(resources.getColor(R.color.main_text));

            textPaintDate = new Paint();
            textPaintDate = createTextPaint(resources.getColor(R.color.second_text));

            textPaintTemp = new Paint();
            textPaintTemp = createTextPaint(resources.getColor(R.color.second_text));
            textPaintTempBold = new Paint();
            textPaintTempBold = createTextPaint(resources.getColor(R.color.main_text));

            linePaint = new Paint();
            linePaint.setColor(resources.getColor(R.color.second_text));
            linePaint.setStrokeWidth(0.5f);
            linePaint.setAntiAlias(true);

            mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);

            googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.e(TAG, "onConnected: Successfully connected to Google API client");
                            getInitialWeatherData();
                            Wearable.DataApi.addListener(googleApiClient, dataListener);
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.e(TAG, "onConnectionSuspended");
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.e(TAG, "onConnectionFailed(): Failed to connect, with result : " + connectionResult);
                        }
                    })
                    .build();
            googleApiClient.connect();
        }

        /**
         * When the watch face is used initially, access the data layer on the connected device and
         * retrieve weather information
         */
        void getInitialWeatherData() {
            Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                    Log.i("WATCH", "getConnectedNodes result");
                    Node connectedNode = null;
                    for (Node node : nodes.getNodes()) {
                        connectedNode = node;
                    }
                    if (connectedNode == null) {
                        return;
                    }

                    Uri uri = new Uri.Builder()
                            .scheme(PutDataRequest.WEAR_URI_SCHEME)
                            .path(WEATHER_PATH)
                            .authority(connectedNode.getId())
                            .build();

                    Wearable.DataApi.getDataItem(googleApiClient, uri)
                            .setResultCallback(
                                    new ResultCallback<DataApi.DataItemResult>() {
                                        @Override
                                        public void onResult(DataApi.DataItemResult dataItemResult) {
                                            Log.i("WATCH", "getDataItem result");
                                            if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
                                                Log.i("WATCH", "Received data item result from connected node");
                                                //DataMap dataMap = DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap();
                                                try {
                                                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItemResult.getDataItem());
                                                    weatherTempHigh = dataMapItem.getDataMap().getString(WEATHER_TEMP_HIGH_KEY);
                                                    weatherTempLow = dataMapItem.getDataMap().getString(WEATHER_TEMP_LOW_KEY);
                                                    final Asset photo = dataMapItem.getDataMap().getAsset(WEATHER_TEMP_ICON_KEY);
//                                                    weatherTempIcon = loadBitmapFromAsset(googleApiClient, photo);
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            weatherTempIcon = loadBitmapFromAsset(googleApiClient, photo);
                                                            Log.d("WEATHER-ICON",""+weatherTempIcon.getWidth());
                                                        }
                                                    }).start();

                                                } catch (Exception e) {
                                                    Log.e(TAG, "Exception   ", e);
                                                    weatherTempIcon = null;
                                                }
                                            }
                                        }
                                    });
                }

                private Bitmap loadBitmapFromAsset(GoogleApiClient apiClient, Asset asset) {
                    if (asset == null) {
                        throw new IllegalArgumentException("Asset must be non-null");
                    }
                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(apiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);
                }
            });
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyDigitalWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyDigitalWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyDigitalWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            textPaintTime.setTextSize(resources.getDimension(R.dimen.time_text_size));
            textPaintTimeBold.setTextSize(resources.getDimension(R.dimen.time_text_size));
            textPaintDate.setTextSize(resources.getDimension(R.dimen.date_text_size));
            textPaintTemp.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            textPaintTempBold.setTextSize(resources.getDimension(R.dimen.temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mBackgroundPaint.setColor(inAmbientMode ? getResources().getColor(R.color.digital_background_ambient) : getResources().getColor(R.color.digital_background));
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    textPaintTime.setAntiAlias(!inAmbientMode);
                    textPaintDate.setAntiAlias(!inAmbientMode);
                    textPaintTemp.setAntiAlias(!inAmbientMode);
                    linePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            Log.e("onDraw", "Watch on Draw");

            int spaceY = 20;
            int spaceX = 10;
            int spaceYTemp;

            String text;

            int centerX = bounds.width() / 2;
            int centerY = bounds.height() / 2;
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            text = mDateFormat.format(mDate).toUpperCase();
            textPaintDate.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY, textPaintDate);
            spaceYTemp = textBounds.height();

            text = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY)) + ":" + String.format("%02d", mCalendar.get(Calendar.MINUTE));
            textPaintTimeBold.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY - spaceY + 4 - spaceYTemp, textPaintTimeBold);

            if (!mAmbient) {
                spaceYTemp = spaceY;
                canvas.drawLine(centerX - 20, centerY + spaceY, centerX + 20, centerY + spaceYTemp, linePaint);
                if (weatherTempHigh != null && weatherTempLow != null) {

                    text = weatherTempHigh;
                    textPaintTempBold.getTextBounds(text, 0, text.length(), textBounds);
                    spaceYTemp = textBounds.height() + spaceY + spaceYTemp;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + spaceYTemp, textPaintTempBold);

                    text = weatherTempLow;
                    canvas.drawText(text, centerX + textBounds.width() / 2 + spaceX, centerY + spaceYTemp, textPaintTemp);

                    if (weatherTempIcon != null) {
                        // draw weather icon
                        canvas.drawBitmap(weatherTempIcon,
                                centerX - textBounds.width() / 2 - spaceX - weatherTempIcon.getWidth(),
                                centerY + spaceYTemp - weatherTempIcon.getHeight() / 2 - textBounds.height() / 2, null);
/*
                        canvas.drawBitmap(weatherTempIcon,
                                centerX - weatherTempIcon.getWidth() / 2,
                                centerY - weatherTempIcon.getHeight() / 2, null);*/
                        Log.d("weatherTempIcon", "got it");
                    } else {
                        Log.d("weatherTempIcon", "null");
                    }
                } else {
                    // draw temperature high
                    text = getString(R.string.info_not_available);
                    textPaintDate.getTextBounds(text, 0, text.length(), textBounds);
                    spaceYTemp = textBounds.height() + spaceY + spaceYTemp;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + spaceYTemp, textPaintDate);

                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private String transcriptionNodeId = null;

        private void updateTranscriptionCapability(CapabilityInfo capabilityInfo) {
            Set<Node> connectedNodes = capabilityInfo.getNodes();

            transcriptionNodeId = pickBestNodeId(connectedNodes);
        }

        private String pickBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            // Find a nearby node or pick one arbitrarily
            for (Node node : nodes) {
                if (node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }
            return bestNodeId;
        }
    }


}
