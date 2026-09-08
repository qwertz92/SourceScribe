package app.sourcescribe.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Test-only DocumentsProvider backed by a UUID-scoped cache directory. */
public final class ExportFixtureProvider extends DocumentsProvider {
    public static final String AUTHORITY = "app.sourcescribe.test.documents";
    public static final String CONTROL_AUTHORITY = AUTHORITY + ".control";
    public static final String CONTROL_PERMISSION = "android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY";

    private static final String METHOD_CONFIGURE = "sourcescribe.configure";
    private static final String METHOD_SET_MODE = "sourcescribe.setMode";
    private static final String METHOD_SEED = "sourcescribe.seed";
    private static final String METHOD_BYTES = "sourcescribe.bytes";
    private static final String METHOD_DISK_FULL_ATTEMPTED = "sourcescribe.diskFullAttempted";
    private static final String METHOD_OUTPUT_FAILURE_ATTEMPTS = "sourcescribe.outputFailureAttempts";
    private static final String METHOD_NAMES = "sourcescribe.names";
    private static final String METHOD_REVOKE_TREE = "sourcescribe.revokeTree";
    private static final String METHOD_RESET = "sourcescribe.reset";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_TARGET_PACKAGE = "targetPackage";
    private static final String EXTRA_BYTES = "bytes";
    private static final String EXTRA_URI = "uri";
    private static final String RESULT_TREE_URI = "treeUri";
    private static final String RESULT_BYTES = "bytes";
    private static final String RESULT_DISK_FULL_ATTEMPTED = "diskFullAttempted";
    private static final String RESULT_OUTPUT_FAILURE_ATTEMPTS = "outputFailureAttempts";
    private static final String RESULT_DOCUMENT_URI = "documentUri";
    private static final String RESULT_NAMES = "names";
    private static final long FIXTURE_AVAILABLE_BYTES = 256L * 1024L * 1024L;
    private static final int TREE_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private static final Object LOCK = new Object();
    private static Fixture fixture;

    @Override
    public boolean onCreate() {
        synchronized (LOCK) {
            if (fixture == null) {
                configureLocked(attachedContext(), Mode.SUCCESS);
            }
        }
        return true;
    }

    private static Bundle controlCall(Context context, String method, Bundle extras) {
        synchronized (LOCK) {
            if (METHOD_CONFIGURE.equals(method)) {
                if (fixture != null) {
                    revokeTreePermission(context, fixture);
                    deleteRecursively(fixture.root);
                }
                configureLocked(context, Mode.valueOf(stringExtra(extras, EXTRA_MODE)));
                String treeUri = treeUri(fixture);
                context.grantUriPermission(
                        stringExtra(extras, EXTRA_TARGET_PACKAGE), Uri.parse(treeUri), TREE_GRANT_FLAGS);
                Bundle result = new Bundle();
                result.putString(RESULT_TREE_URI, treeUri);
                return result;
            }

            if (METHOD_RESET.equals(method)) {
                if (fixture != null) {
                    revokeTreePermission(context, fixture);
                    deleteRecursively(fixture.root);
                    fixture = null;
                }
                return new Bundle();
            }

            Fixture state = current();
            if (METHOD_REVOKE_TREE.equals(method)) {
                String requestedTreeUri = stringExtra(extras, EXTRA_URI);
                if (!treeUri(state).equals(requestedTreeUri)) {
                    throw new IllegalArgumentException("fixture tree does not match current state");
                }
                revokeTreePermission(context, state);
                return new Bundle();
            }
            if (METHOD_SET_MODE.equals(method)) {
                state.mode = Mode.valueOf(stringExtra(extras, EXTRA_MODE));
                return new Bundle();
            }
            if (METHOD_SEED.equals(method)) {
                String name = stringExtra(extras, EXTRA_NAME);
                byte[] bytes = byteExtra(extras, EXTRA_BYTES);
                requireSafeName(name);
                File file = new File(state.root, name);
                writeBytes(file, bytes);
                Bundle result = new Bundle();
                result.putString(
                        RESULT_DOCUMENT_URI,
                        DocumentsContract.buildDocumentUriUsingTree(
                                Uri.parse(treeUri(state)), state.documentIdFor(file)).toString());
                return result;
            }
            if (METHOD_BYTES.equals(method)) {
                String uriValue = stringExtra(extras, EXTRA_URI);
                String documentId = DocumentsContract.getDocumentId(android.net.Uri.parse(uriValue));
                Bundle result = new Bundle();
                try {
                    result.putByteArray(RESULT_BYTES, readBytes(state.fileFor(documentId)));
                } catch (FileNotFoundException failure) {
                    throw new IllegalStateException("fixture document is unavailable", failure);
                }
                return result;
            }
            if (METHOD_DISK_FULL_ATTEMPTED.equals(method)) {
                Bundle result = new Bundle();
                result.putBoolean(RESULT_DISK_FULL_ATTEMPTED, state.diskFullWriteAttempted);
                return result;
            }
            if (METHOD_OUTPUT_FAILURE_ATTEMPTS.equals(method)) {
                Bundle result = new Bundle();
                result.putInt(RESULT_OUTPUT_FAILURE_ATTEMPTS, state.outputFailureWriteAttempts);
                return result;
            }
            if (METHOD_NAMES.equals(method)) {
                Bundle result = new Bundle();
                ArrayList<String> names = new ArrayList<>();
                File[] files = state.root.listFiles();
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    for (File file : files) {
                        names.add(file.getName());
                    }
                }
                result.putStringArrayList(RESULT_NAMES, names);
                return result;
            }
            return null;
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        synchronized (LOCK) {
            Fixture state = current();
            MatrixCursor cursor = new MatrixCursor(projection == null ? DEFAULT_ROOT_PROJECTION : projection);
            MatrixCursor.RowBuilder row = cursor.newRow();
            row.add(DocumentsContract.Root.COLUMN_ROOT_ID, state.rootId);
            row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, state.rootId);
            row.add(DocumentsContract.Root.COLUMN_TITLE, "SourceScribe fixture");
            row.add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
            row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save);
            row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
            row.add(DocumentsContract.Root.COLUMN_SUMMARY, state.mode.name());
            row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, FIXTURE_AVAILABLE_BYTES);
            return cursor;
        }
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        synchronized (LOCK) {
            Fixture state = current();
            state.checkPermission();
            File file = state.fileFor(documentId);
            if (!file.exists()) {
                throw new FileNotFoundException("fixture document missing");
            }
            MatrixCursor cursor = new MatrixCursor(projection == null ? DEFAULT_DOCUMENT_PROJECTION : projection);
            addDocumentRow(cursor, state, documentId, file);
            return cursor;
        }
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId,
            String[] projection,
            String sortOrder
    ) throws FileNotFoundException {
        synchronized (LOCK) {
            Fixture state = current();
            File parent = state.fileFor(parentDocumentId);
            if (!parent.isDirectory()) {
                throw new FileNotFoundException("fixture parent is not a directory");
            }
            MatrixCursor cursor = new MatrixCursor(projection == null ? DEFAULT_DOCUMENT_PROJECTION : projection);
            File[] files = parent.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File file : files) {
                    addDocumentRow(cursor, state, state.documentIdFor(file), file);
                }
            }
            return cursor;
        }
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        synchronized (LOCK) {
            Fixture state = current();
            try {
                File parent = state.fileFor(parentDocumentId);
                File document = state.fileFor(documentId);
                return !parent.equals(document) && parent.equals(document.getParentFile());
            } catch (FileNotFoundException ignored) {
                return false;
            }
        }
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        synchronized (LOCK) {
            Fixture state = current();
            state.checkPermission();
            File parent = state.fileFor(parentDocumentId);
            requireSafeName(displayName);
            if (!parent.isDirectory()) {
                throw new FileNotFoundException("fixture parent is not a directory");
            }
            File file = new File(parent, displayName);
            if (file.exists()) {
                throw new FileNotFoundException("fixture name collision");
            }
            try {
                if (!file.createNewFile()) {
                    throw new FileNotFoundException("fixture document could not be created");
                }
            } catch (IOException failure) {
                FileNotFoundException error = new FileNotFoundException("fixture document could not be created");
                error.initCause(failure);
                throw error;
            }
            return state.documentIdFor(file);
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId,
            String mode,
            CancellationSignal signal
    ) throws FileNotFoundException {
        synchronized (LOCK) {
            Fixture state = current();
            state.checkPermission();
            File file = state.fileFor(documentId);
            if (!file.exists()) {
                throw new FileNotFoundException("fixture document missing");
            }
            if (state.mode == Mode.OUTPUT_FAILURE && canWrite(mode)) {
                return partialWriteFileDescriptor(attachedContext(), state);
            }
            if (state.mode == Mode.DISK_FULL && canWrite(mode)) {
                return diskFullFileDescriptor(attachedContext(), state);
            }
            if (state.mode == Mode.READBACK_UNAVAILABLE && !canWrite(mode)) {
                throw new FileNotFoundException("fixture readback unavailable");
            }
            if (state.mode == Mode.READBACK_PERMISSION_DENIED && !canWrite(mode)) {
                throw new SecurityException("fixture readback permission denied");
            }
            if (state.mode == Mode.READBACK_MISMATCH && !canWrite(mode)) {
                return bytesPipe("mismatch".getBytes());
            }
            return ParcelFileDescriptor.open(file, modeFlags(mode));
        }
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        synchronized (LOCK) {
            Fixture state = current();
            state.checkPermission();
            if (state.rootId.equals(documentId)) {
                throw new FileNotFoundException("fixture root cannot be deleted");
            }
            File file = state.fileFor(documentId);
            if (!file.delete()) {
                throw new FileNotFoundException("fixture document could not be deleted");
            }
        }
    }

    public static String configure(Context context, Mode mode, String targetPackage) {
        Bundle extras = bundle(EXTRA_MODE, mode.name());
        extras.putString(EXTRA_TARGET_PACKAGE, targetPackage);
        Bundle result = call(context, METHOD_CONFIGURE, extras);
        String tree = result.getString(RESULT_TREE_URI);
        if (tree == null) {
            throw new IllegalStateException("fixture provider returned no tree URI");
        }
        return tree;
    }

    public static void setMode(Context context, Mode mode) {
        call(context, METHOD_SET_MODE, bundle(EXTRA_MODE, mode.name()));
    }

    public static void seed(Context context, String name) {
        seed(context, name, "pre-existing fixture".getBytes());
    }

    public static void seed(Context context, String name, byte[] bytes) {
        seedDocument(context, name, bytes);
    }

    public static String seedDocument(Context context, String name, byte[] bytes) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_NAME, name);
        extras.putByteArray(EXTRA_BYTES, bytes);
        String documentUri = call(context, METHOD_SEED, extras).getString(RESULT_DOCUMENT_URI);
        if (documentUri == null) {
            throw new IllegalStateException("fixture provider returned no document URI");
        }
        return documentUri;
    }

    public static byte[] bytes(Context context, String documentUri) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_URI, documentUri);
        byte[] bytes = call(context, METHOD_BYTES, extras).getByteArray(RESULT_BYTES);
        if (bytes == null) {
            throw new IllegalStateException("fixture provider returned no bytes");
        }
        return bytes;
    }

    public static ArrayList<String> names(Context context) {
        ArrayList<String> names = call(context, METHOD_NAMES, null).getStringArrayList(RESULT_NAMES);
        return names == null ? new ArrayList<>() : names;
    }

    public static boolean diskFullWriteAttempted(Context context) {
        return call(context, METHOD_DISK_FULL_ATTEMPTED, null).getBoolean(RESULT_DISK_FULL_ATTEMPTED);
    }

    public static int outputFailureWriteAttempts(Context context) {
        return call(context, METHOD_OUTPUT_FAILURE_ATTEMPTS, null).getInt(RESULT_OUTPUT_FAILURE_ATTEMPTS);
    }

    public static void revokeTreeGrant(Context context, String treeUri) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_URI, treeUri);
        call(context, METHOD_REVOKE_TREE, extras);
    }

    public static void reset(Context context) {
        call(context, METHOD_RESET, null);
    }

    private static Bundle call(Context context, String method, Bundle extras) {
        Context application = context.getApplicationContext();
        if (application == null) {
            application = context;
        }
        Bundle result = application.getContentResolver().call(
                Uri.parse("content://" + CONTROL_AUTHORITY), method, null, extras);
        if (result == null) {
            throw new IllegalStateException("fixture provider returned no result");
        }
        return result;
    }

    private static Bundle bundle(String key, String value) {
        Bundle extras = new Bundle();
        extras.putString(key, value);
        return extras;
    }

    private static void addDocumentRow(MatrixCursor cursor, Fixture state, String documentId, File file) {
        boolean directory = file.isDirectory();
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                file.getName().isEmpty() ? "SourceScribe fixture" : file.getName());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                directory ? DocumentsContract.Document.MIME_TYPE_DIR : state.mimeType(file));
        row.add(DocumentsContract.Document.COLUMN_SIZE, directory ? null : file.length());
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(DocumentsContract.Document.COLUMN_FLAGS,
                directory
                        ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                        : DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
    }

    private static int modeFlags(String mode) {
        if ("r".equals(mode)) {
            return ParcelFileDescriptor.MODE_READ_ONLY;
        }
        if (mode.contains("w") && mode.contains("a")) {
            return ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        }
        if (mode.contains("w")) {
            return ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        if (mode.contains("a")) {
            return ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND;
        }
        return ParcelFileDescriptor.MODE_READ_WRITE;
    }

    private static boolean canWrite(String mode) {
        return mode.contains("w") || mode.contains("a") || mode.contains("+");
    }

    private static ParcelFileDescriptor partialWriteFileDescriptor(Context context, Fixture state) {
        HandlerThread callbackThread = startProxyThread("sourcescribe-partial-write");
        return openWriteProxy(
                context,
                callbackThread,
                new ProxyFileDescriptorCallback() {
                    @Override
                    public long onGetSize() {
                        return 0L;
                    }

                    @Override
                    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
                        state.outputFailureWriteAttempts++;
                        if (state.outputFailureWriteAttempts == 1 && size > 0) {
                            return 1;
                        }
                        throw new ErrnoException("write", OsConstants.EIO);
                    }

                    @Override
                    public void onFsync() {
                        // The fixture retains no data that needs flushing.
                    }

                    @Override
                    public void onRelease() {
                        callbackThread.quitSafely();
                    }
                },
                "partial-write");
    }

    private static ParcelFileDescriptor diskFullFileDescriptor(Context context, Fixture state) {
        HandlerThread callbackThread = startProxyThread("sourcescribe-disk-full");
        return openWriteProxy(
                context,
                callbackThread,
                new ProxyFileDescriptorCallback() {
                    @Override
                    public long onGetSize() {
                        return 0L;
                    }

                    @Override
                    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
                        state.diskFullWriteAttempted = true;
                        throw new ErrnoException("write", OsConstants.ENOSPC);
                    }

                    @Override
                    public void onFsync() {
                        // Every write fails, so there is no buffered data to flush.
                    }

                    @Override
                    public void onRelease() {
                        callbackThread.quitSafely();
                    }
                },
                "disk-full");
    }

    private static HandlerThread startProxyThread(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return thread;
    }

    private static ParcelFileDescriptor openWriteProxy(
            Context context,
            HandlerThread callbackThread,
            ProxyFileDescriptorCallback callback,
            String fixtureName
    ) {
        StorageManager storage = context.getSystemService(StorageManager.class);
        if (storage == null) {
            callbackThread.quitSafely();
            throw new AssertionError("storage manager unavailable for " + fixtureName + " fixture");
        }
        try {
            return storage.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_WRITE_ONLY,
                    callback,
                    new Handler(callbackThread.getLooper()));
        } catch (IOException failure) {
            callbackThread.quitSafely();
            throw new AssertionError("proxy descriptor unavailable for " + fixtureName + " fixture", failure);
        }
    }

    private static ParcelFileDescriptor bytesPipe(final byte[] bytes) throws FileNotFoundException {
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread thread = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                } catch (IOException ignored) {
                    // The caller owns the read side and may cancel it.
                }
            });
            thread.start();
            return pipe[0];
        } catch (IOException failure) {
            FileNotFoundException error = new FileNotFoundException("fixture readback pipe unavailable");
            error.initCause(failure);
            throw error;
        }
    }

    private static Fixture current() {
        if (fixture == null) {
            throw new IllegalStateException("fixture provider is not configured");
        }
        return fixture;
    }

    private Context attachedContext() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("fixture provider has no context");
        }
        return context;
    }

    private static String stringExtra(Bundle extras, String key) {
        if (extras == null) {
            throw new IllegalArgumentException("missing fixture argument");
        }
        String value = extras.getString(key);
        if (value == null) {
            throw new IllegalArgumentException("missing fixture argument");
        }
        return value;
    }

    private static byte[] byteExtra(Bundle extras, String key) {
        if (extras == null) {
            throw new IllegalArgumentException("missing fixture argument");
        }
        byte[] value = extras.getByteArray(key);
        if (value == null) {
            throw new IllegalArgumentException("missing fixture argument");
        }
        return value;
    }

    private static void requireSafeName(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("invalid fixture name");
        }
    }

    private static void configureLocked(Context context, Mode mode) {
        String rootId = UUID.randomUUID().toString();
        fixture = new Fixture(
                new File(context.getCacheDir(), "sourcescribe-export-fixture-" + rootId),
                rootId,
                mode);
    }

    private static String treeUri(Fixture state) {
        return DocumentsContract.buildTreeDocumentUri(AUTHORITY, state.rootId).toString();
    }

    private static void revokeTreePermission(Context context, Fixture state) {
        context.revokeUriPermission(
                Uri.parse(treeUri(state)),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private static void writeBytes(File file, byte[] bytes) {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("fixture seed could not be written", failure);
        }
    }

    private static byte[] readBytes(File file) {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("fixture bytes could not be read", failure);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("fixture data could not be removed");
        }
    }

    /** Privileged test-only control surface; document access still requires scoped URI grants. */
    public static final class ControlProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            Context context = getContext();
            if (context == null) {
                throw new IllegalStateException("fixture control provider has no context");
            }
            context.enforceCallingPermission(CONTROL_PERMISSION, "fixture control permission required");
            return controlCall(context, method, extras);
        }

        @Override
        public Cursor query(
                Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder
        ) {
            throw unsupportedControlOperation();
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw unsupportedControlOperation();
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw unsupportedControlOperation();
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw unsupportedControlOperation();
        }

        private static UnsupportedOperationException unsupportedControlOperation() {
            return new UnsupportedOperationException("fixture control provider only supports calls");
        }
    }

    public enum Mode {
        SUCCESS,
        PERMISSION_DENIED,
        OUTPUT_FAILURE,
        DISK_FULL,
        READBACK_MISMATCH,
        READBACK_UNAVAILABLE,
        READBACK_PERMISSION_DENIED,
        NAME_COLLISION,
    }

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
    };

    private static final class Fixture {
        private final File root;
        private final String rootId;
        private Mode mode;
        private volatile boolean diskFullWriteAttempted;
        private volatile int outputFailureWriteAttempts;
        private final Map<String, File> documents = new HashMap<>();

        private Fixture(File root, String rootId, Mode mode) {
            this.root = root;
            this.rootId = rootId;
            this.mode = mode;
            if (!root.mkdirs() && !root.isDirectory()) {
                throw new IllegalStateException("fixture root could not be created");
            }
            documents.put(rootId, root);
        }

        private File fileFor(String documentId) throws FileNotFoundException {
            File file = documents.get(documentId);
            if (file == null) {
                throw new FileNotFoundException("unknown fixture document");
            }
            return file;
        }

        private String documentIdFor(File file) {
            for (Map.Entry<String, File> entry : documents.entrySet()) {
                if (entry.getValue().equals(file)) {
                    return entry.getKey();
                }
            }
            String id = UUID.randomUUID().toString();
            documents.put(id, file);
            return id;
        }

        private void checkPermission() {
            if (mode == Mode.PERMISSION_DENIED) {
                throw new SecurityException("fixture permission denied");
            }
        }

        private String mimeType(File file) {
            String extension = file.getName();
            int dot = extension.lastIndexOf('.');
            extension = dot >= 0 ? extension.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            switch (extension) {
                case "md":
                    return "text/markdown";
                case "txt":
                    return "text/plain";
                case "json":
                case "json3":
                    return "application/json";
                case "srt":
                    return "application/x-subrip";
                case "vtt":
                    return "text/vtt";
                default:
                    return "application/octet-stream";
            }
        }
    }
}
