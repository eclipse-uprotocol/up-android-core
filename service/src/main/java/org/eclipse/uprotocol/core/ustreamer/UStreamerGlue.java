package org.eclipse.uprotocol.core.ustreamer;

import android.os.IBinder;

public class UStreamerGlue {

    public static native String forwardJavaBinder(IBinder binder);

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("ustreamer_glue");
    }
}
