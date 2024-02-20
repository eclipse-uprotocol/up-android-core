package org.eclipse.uprotocol.core.ustreamer;

import android.os.IBinder;

public class UStreamerGlue {

    public static String forwardJavaBinder(IBinder binder) {
        return "noop";
    }
}