package app.sourcescribe.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Test-only content source; register this provider in the androidTest manifest. */
public final class AudioImportFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "app.sourcescribe.audio-import-fixture";
    public static final String WAV_PATH = "/audio.wav";
    public static final String TEXT_PATH = "/text.txt";
    public static final Uri WAV_URI = Uri.parse("content://" + AUTHORITY + WAV_PATH);
    public static final Uri TEXT_URI = Uri.parse("content://" + AUTHORITY + TEXT_PATH);
    public static final byte[] WAV_BYTES = pcmWav(8_000, 100);
    public static final byte[] TEXT_BYTES = "this is not audio\n".getBytes(StandardCharsets.UTF_8);
    public static final String WAV_SHA256 = sha256(WAV_BYTES);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        if (WAV_PATH.equals(uri.getPath())) return "audio/wav";
        if (TEXT_PATH.equals(uri.getPath())) return "text/plain";
        return null;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        Fixture fixture = fixture(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = fixture.name;
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = (long) fixture.bytes.length;
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Fixture fixture;
        try {
            fixture = fixture(uri);
        } catch (IllegalArgumentException failure) {
            FileNotFoundException wrapped = new FileNotFoundException(uri.toString());
            wrapped.initCause(failure);
            throw wrapped;
        }
        Context providerContext = getContext();
        if (providerContext == null) throw new FileNotFoundException(uri.toString());
        File file = new File(providerContext.getCacheDir(), fixture.name);
        if (!file.isFile() || file.length() != fixture.bytes.length) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(fixture.bytes);
            } catch (IOException failure) {
                FileNotFoundException wrapped = new FileNotFoundException(file.toString());
                wrapped.initCause(failure);
                throw wrapped;
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw unsupported();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw unsupported();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("read-only test provider");
    }

    private static Fixture fixture(Uri uri) {
        if (WAV_PATH.equals(uri.getPath())) return new Fixture("audio-import-fixture.wav", WAV_BYTES);
        if (TEXT_PATH.equals(uri.getPath())) return new Fixture("audio-import-fixture.txt", TEXT_BYTES);
        throw new IllegalArgumentException(uri.toString());
    }

    private static byte[] pcmWav(int sampleRate, int durationMs) {
        int samples = sampleRate * durationMs / 1_000;
        int dataSize = samples * 2;
        ByteBuffer output = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        output.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        output.putInt(36 + dataSize);
        output.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        output.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        output.putInt(16);
        output.putShort((short) 1);
        output.putShort((short) 1);
        output.putInt(sampleRate);
        output.putInt(sampleRate * 2);
        output.putShort((short) 2);
        output.putShort((short) 16);
        output.put("data".getBytes(StandardCharsets.US_ASCII));
        output.putInt(dataSize);
        for (int index = 0; index < samples; index++) output.putShort((short) 0);
        return output.array();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                int unsigned = value & 0xff;
                result.append(digits[unsigned >>> 4]);
                result.append(digits[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Fixture {
        private final String name;
        private final byte[] bytes;

        private Fixture(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
