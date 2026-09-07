package app.sourcescribe.data;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

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

    private static final String METHOD_CONFIGURE = "sourcescribe.configure";
    private static final String METHOD_SET_MODE = "sourcescribe.setMode";
    private static final String METHOD_SEED = "sourcescribe.seed";
    private static final String METHOD_BYTES = "sourcescribe.bytes";
    private static final String METHOD_NAMES = "sourcescribe.names";
    private static final String METHOD_RESET = "sourcescribe.reset";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_BYTES = "bytes";
    private static final String EXTRA_URI = "uri";
    private static final String RESULT_TREE_URI = "treeUri";
    private static final String RESULT_BYTES = "bytes";
    private static final String RESULT_NAMES = "names";
    private static final long FIXTURE_AVAILABLE_BYTES = 256L * 1024L * 1024L;

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

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        synchronized (LOCK) {
            if (METHOD_CONFIGURE.equals(method)) {
                if (fixture != null) {
                    deleteRecursively(fixture.root);
                }
                configureLocked(attachedContext(), Mode.valueOf(stringExtra(extras, EXTRA_MODE)));
                Bundle result = new Bundle();
                result.putString(RESULT_TREE_URI, treeUri(fixture));
                return result;
            }

            Fixture state = current();
            if (METHOD_SET_MODE.equals(method)) {
                state.mode = Mode.valueOf(stringExtra(extras, EXTRA_MODE));
                return new Bundle();
            }
            if (METHOD_SEED.equals(method)) {
                String name = stringExtra(extras, EXTRA_NAME);
                byte[] bytes = byteExtra(extras, EXTRA_BYTES);
                requireSafeName(name);
                writeBytes(new File(state.root, name), bytes);
                return new Bundle();
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
            if (METHOD_RESET.equals(method)) {
                deleteRecursively(state.root);
                fixture = null;
                return new Bundle();
            }
            return super.call(method, arg, extras);
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
                return failingWritePipe();
            }
            if (state.mode == Mode.READBACK_UNAVAILABLE && !canWrite(mode)) {
                throw new FileNotFoundException("fixture readback unavailable");
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

    public static String configure(Context context, Mode mode) {
        Bundle result = call(context, METHOD_CONFIGURE, bundle(EXTRA_MODE, mode.name()));
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
        Bundle extras = new Bundle();
        extras.putString(EXTRA_NAME, name);
        extras.putByteArray(EXTRA_BYTES, bytes);
        call(context, METHOD_SEED, extras);
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

    public static void reset(Context context) {
        call(context, METHOD_RESET, null);
    }

    private static Bundle call(Context context, String method, Bundle extras) {
        Context application = context.getApplicationContext();
        if (application == null) {
            application = context;
        }
        Bundle result = application.getContentResolver().call(
                android.net.Uri.parse("content://" + AUTHORITY), method, null, extras);
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

    private static ParcelFileDescriptor failingWritePipe() throws FileNotFoundException {
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread thread = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseInputStream input =
                             new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                    input.read();
                } catch (IOException ignored) {
                    // The fixture deliberately closes the reader after one byte.
                }
            });
            thread.start();
            return pipe[1];
        } catch (IOException failure) {
            FileNotFoundException error = new FileNotFoundException("fixture output pipe unavailable");
            error.initCause(failure);
            throw error;
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

    public enum Mode {
        SUCCESS,
        PERMISSION_DENIED,
        OUTPUT_FAILURE,
        READBACK_MISMATCH,
        READBACK_UNAVAILABLE,
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
