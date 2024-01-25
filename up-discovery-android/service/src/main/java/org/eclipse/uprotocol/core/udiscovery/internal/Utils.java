package org.eclipse.uprotocol.core.udiscovery.internal;

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.protobuf.ProtocolStringList;

import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.List;
import java.util.stream.Collectors;

public final class Utils {

    private Utils() {
    }

    public static Pair<String, String> parseAuthority(UAuthority authority) {
        final String name = authority.getName();
        final String[] tokens = name.split("[.]", 2);
        checkArgument(tokens.length == 2, "[parseAuthority] missing delimiter");
        final String device = tokens[0];
        final String domain = tokens[1];
        return new Pair<>(device, domain);
    }

    public static String toLongUri(UAuthority authority) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).build());
    }

    public static String toLongUri(UAuthority authority, UEntity entity) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).setEntity(entity).build());
    }

    public static String toLongUri(UUri uri) {
        return LongUriSerializer.instance().serialize(uri);
    }

    public static UUri fromLongUri(String uri) { return LongUriSerializer.instance().deserialize(uri); }

    public static String sanitizeUri(String uri) {
        LongUriSerializer lus = LongUriSerializer.instance();
        return lus.serialize(lus.deserialize(uri));
    }

    public static List<UUri> deserializeUriList(@NonNull ProtocolStringList list) {
        return list.stream().map(element -> LongUriSerializer.instance().deserialize(element)).collect(
                Collectors.toList());
    }

    public static boolean hasCharAt(@NonNull String string, int index, char ch) {
        if (index < 0 || index >= string.length()) {
            return false;
        }
        return string.charAt(index) == ch;
    }

    public static @NonNull UStatus logStatus(@NonNull String tag, @NonNull String method, @NonNull UStatus status,
                                         Object... args) {
        if ((status != null) && (status.getCode() == UCode.OK)) {
            Log.i(tag, status(method, status, args));
        } else {
            Log.e(tag, status(method, status, args));
        }
        return status;
    }
}
