/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.example.client;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static android.view.View.FOCUS_DOWN;
import static android.widget.TextView.BufferType.SPANNABLE;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class Logger {
    private static final int MAX_LINES_DEFAULT = 500;
    private static final int COLOR_WARNING = 0xFFD98806;
    private static final int COLOR_ERROR = 0xFFFF0000;

    private static final String TIME_FORMAT = "HH:mm:ss";

    private final int mMaxLines;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mFormatter = new SimpleDateFormat(TIME_FORMAT, Locale.US);
    private final Calendar mCalendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
    private TextView mOutput;
    private ScrollView mScroller;

    public Logger() {
        this(MAX_LINES_DEFAULT);
    }

    public Logger(int maxLines) {
        mMaxLines = maxLines;
    }

    public synchronized void setOutput(TextView output, ScrollView scroller) {
        mOutput = output;
        mScroller = scroller;
    }

    public synchronized void reset() {
        setOutput(null, null);
    }

    public void i(String tag, String message) {
        Log.i(tag, message);
        append(message);
    }

    public void w(String tag, String message) {
        Log.w(tag, message);
        append(message, COLOR_WARNING);
    }

    public void e(String tag, String message) {
        Log.e(tag, message);
        append(message, COLOR_ERROR);
    }

    public void d(String tag, String message) {
        Log.d(tag, message);
        append(message);
    }

    public void v(String tag, String message) {
        Log.v(tag, message);
        append(message);
    }

    public void println(int priority, String tag, String message) {
        switch (priority) {
            case Log.ASSERT, Log.ERROR -> e(tag, message);
            case Log.WARN -> w(tag, message);
            case Log.INFO -> i(tag, message);
            case Log.DEBUG -> d(tag, message);
            default -> v(tag, message);
        }
    }

    public String formatTime(long millis) {
        mCalendar.setTimeInMillis(millis);
        return mFormatter.format(mCalendar.getTime());
    }

    public synchronized void clear() {
        if (Looper.myLooper() == mHandler.getLooper()) {
            clearOnUiThread();
        } else {
            mHandler.post(this::clearOnUiThread);
        }
    }

    private void append(String message) {
        append(message, 0);
    }

    private void append(String message, int color) {
        if (Looper.myLooper() == mHandler.getLooper()) {
            appendOnUiThread(message, color);
        } else {
            mHandler.post(() -> appendOnUiThread(message, color));
        }
    }

    private synchronized void appendOnUiThread(String message, int color) {
        if (mOutput == null || mScroller == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        CharSequence text = String.format("%s: %s", formatTime(now), message);
        if (TextUtils.isEmpty(mOutput.getText())) {
            mOutput.setText(text, SPANNABLE);
        } else {
            if (color != 0) {
                Spannable spannable = new SpannableString(text);
                spannable.setSpan(new ForegroundColorSpan(color), 0, text.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
                text = spannable;
            }
            mOutput.append("\n");
            mOutput.append(text);
            final ScrollView scrollView = mScroller;
            scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));

        }
        final int linesToRemove = mOutput.getLineCount() - mMaxLines;
        if (linesToRemove > 0) {
            for (int i = 0; i < linesToRemove; i++) {
                final Editable editable = mOutput.getEditableText();
                final int lineStart = mOutput.getLayout().getLineStart(0);
                final int lineEnd = mOutput.getLayout().getLineEnd(0);
                editable.delete(lineStart, lineEnd);
            }
        }
    }

    public synchronized void clearOnUiThread() {
        if (mOutput != null) {
            mOutput.setText(null);
        }
    }
}
