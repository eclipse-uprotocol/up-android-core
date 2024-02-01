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

/**
 * Utility class for handling UProtocol related operations.
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Parses the authority into a pair of device and domain.
     *
     * @param authority The authority to parse.
     * @return A pair where the first element is the device and the second is the domain.
     */
    public static Pair<String, String> parseAuthority(UAuthority authority) {
        final String name = authority.getName();
        final String[] tokens = name.split("[.]", 2);
        checkArgument(tokens.length == 2, "[parseAuthority] missing delimiter");
        final String device = tokens[0];
        final String domain = tokens[1];
        return new Pair<>(device, domain);
    }

    /**
     * Converts the given authority into a long URI.
     *
     * @param authority The authority to convert.
     * @return The long URI.
     */
    public static String toLongUri(UAuthority authority) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).build());
    }

    /**
     * Converts the given authority and entity into a long URI.
     *
     * @param authority The authority to convert.
     * @param entity The entity to convert.
     * @return The long URI.
     */
    public static String toLongUri(UAuthority authority, UEntity entity) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).setEntity(entity).build());
    }

    /**
     * Converts the given URI into a long URI.
     *
     * @param uri The URI to convert.
     * @return The long URI.
     */
    public static String toLongUri(UUri uri) {
        return LongUriSerializer.instance().serialize(uri);
    }

    /**
     * Converts the given long URI into a URI.
     *
     * @param uri The long URI to convert.
     * @return The URI.
     */
    public static UUri fromLongUri(String uri) { return LongUriSerializer.instance().deserialize(uri); }

    /**
     * Sanitizes the given URI by deserializing and serializing it.
     *
     * @param uri The URI to sanitize.
     * @return The sanitized URI.
     */
    public static String sanitizeUri(String uri) {
        LongUriSerializer lus = LongUriSerializer.instance();
        return lus.serialize(lus.deserialize(uri));
    }

    /**
     * Deserializes the given list of URIs.
     *
     * @param list The list of URIs to deserialize.
     * @return The list of deserialized URIs.
     */
    public static List<UUri> deserializeUriList(@NonNull ProtocolStringList list) {
        return list.stream().map(element -> LongUriSerializer.instance().deserialize(element)).collect(
                Collectors.toList());
    }

    /**
     * Checks if the given string has the specified character at the given index.
     *
     * @param string The string to check.
     * @param index The index to check at.
     * @param ch The character to check for.
     * @return True if the string has the character at the index, false otherwise.
     */
    public static boolean hasCharAt(@NonNull String string, int index, char ch) {
        if (index < 0 || index >= string.length()) {
            return false;
        }
        return string.charAt(index) == ch;
    }

    /**
     * Logs the status of a method.
     *
     * @param tag The tag to use for the log.
     * @param method The method to log the status of.
     * @param status The status to log.
     * @param args The arguments to the method.
     * @return The status.
     */
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
