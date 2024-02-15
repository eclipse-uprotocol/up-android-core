package org.eclipse.uprotocol.core.ustreamer;

import static android.content.Context.BIND_AUTO_CREATE;
import static java.util.Objects.requireNonNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;


public class UStreamerManager {

    private static final String TAG = "UStreamerManager";

    private final Context mContext;
    private boolean mServiceBound;

    public static final String ACTION_BIND_UBUS = "uprotocol.action.BIND_UBUS";

    private final ServiceConnection mServiceConnectionCallback = new ServiceConnection()  {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(TAG, "inside mServiceConnectionCallback onServiceConnected()");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            ServiceConnection.super.onNullBinding(name);
        }
    };

    public UStreamerManager(@NonNull Context context) {
        mContext = requireNonNull(context);
        Log.i(TAG, "inside UStreamerManager constructor");
    }

    public boolean connect() {
        Log.i(TAG, "inside UStreamerManager connect()");
        final Intent intent = new Intent(ACTION_BIND_UBUS);
        String mServiceConfig = "org.eclipse.uprotocol.core";
        intent.setPackage(mServiceConfig);
        Log.d(TAG, "intent: " + intent);
        mServiceBound = mContext.bindService(intent, mServiceConnectionCallback, BIND_AUTO_CREATE);
        return mServiceBound;
    }


}
