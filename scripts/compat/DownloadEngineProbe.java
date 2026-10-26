package fr.makoff.music.compat;

import dalvik.system.PathClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercise the actual optimized APK's ZIP installer, not a debug copy of its dependencies. */
public final class DownloadEngineProbe {
    public static void main(String[] args) throws Exception {
        try {
            verify(args[0]);
            System.out.println("RELEASE_ENGINE_ZIP_OK: file and stream extraction, extra fields and exact payload");
        } catch (Throwable error) {
            while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
            error.printStackTrace(System.out);
            if (args.length > 1 && args[1].equals("diagnose")) {
                System.out.println("PREVIOUS_ENGINE_ZIP_FAILED: " + error.getClass().getName());
                return;
            }
            throw new AssertionError("Published engine installer is broken", error);
        }
    }

    private static void verify(String apk) throws Exception {
        ClassLoader loader = new PathClassLoader(apk, ClassLoader.getSystemClassLoader());
        Class<?> utils = Class.forName("com.yausername.youtubedl_common.utils.ZipUtils", true, loader);
        Object receiver = utils.getField("INSTANCE").get(null);
        File root = new File("/data/local/tmp/engine-zip-" + System.nanoTime());
        if (!root.mkdirs()) throw new IOException("Cannot prepare test directory");
        byte[] payload = "audio-engine-installer".getBytes("UTF-8");
        File archive = new File(root, "fixture.zip");
        try {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
                ZipEntry entry = new ZipEntry("nested/payload.txt");
                // Extended timestamp: forces the library's registered ZIP extra-field constructors.
                entry.setExtra(new byte[] {0x55, 0x54, 5, 0, 1, 1, 0, 0, 0});
                out.putNextEntry(entry);
                out.write(payload);
                out.closeEntry();
            }
            File fromFile = new File(root, "file");
            utils.getMethod("unzip", File.class, File.class).invoke(receiver, archive, fromFile);
            verifyPayload(fromFile, payload);
            File fromStream = new File(root, "stream");
            try (InputStream input = new FileInputStream(archive)) {
                utils.getMethod("unzip", InputStream.class, File.class).invoke(receiver, input, fromStream);
            }
            verifyPayload(fromStream, payload);
        } finally { remove(root); }
    }

    private static void verifyPayload(File root, byte[] expected) throws IOException {
        try (InputStream input = new FileInputStream(new File(root, "nested/payload.txt"));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            if (!Arrays.equals(expected, bytes.toByteArray())) throw new AssertionError("ZIP payload changed");
        }
    }

    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
