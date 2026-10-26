package fr.makoff.music.compat;

import dalvik.system.PathClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.util.ArrayList;

/** Check actual old-APK serialization against the new APK using Android's runtime. */
public final class QueueFormatProbe {
    public static void main(String[] args) throws Exception {
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        ClassLoader previous = new PathClassLoader(args[0], parent);
        ClassLoader current = new PathClassLoader(args[1], parent);
        String name = "fr.makoff.music.models.MediaMetadata";
        Class<?> oldType = Class.forName(name, true, previous);
        Class<?> newType = Class.forName(name, true, current);
        for (String nested : new String[] {"$Artist", "$Album"}) {
            long oldNestedUid = ObjectStreamClass.lookup(Class.forName(name + nested, true, previous)).getSerialVersionUID();
            long newNestedUid = ObjectStreamClass.lookup(Class.forName(name + nested, true, current)).getSerialVersionUID();
            System.out.println("QUEUE_FORMAT_UID: " + nested + " previous=" + oldNestedUid + ", current=" + newNestedUid);
            if (oldNestedUid != newNestedUid) throw new AssertionError("Nested queue metadata identity changed: " + nested);
        }
        long oldUid = ObjectStreamClass.lookup(oldType).getSerialVersionUID();
        long newUid = ObjectStreamClass.lookup(newType).getSerialVersionUID();
        System.out.println("QUEUE_FORMAT_UID: previous=" + oldUid + ", current=" + newUid);
        if (oldUid != newUid) throw new AssertionError("Persisted metadata serialization identity changed");

        Constructor<?> constructor = null;
        for (Constructor<?> candidate : oldType.getDeclaredConstructors()) {
            if (!candidate.isSynthetic() && (constructor == null || candidate.getParameterCount() > constructor.getParameterCount()))
                constructor = candidate;
        }
        if (constructor == null) throw new AssertionError("No metadata constructor");
        Class<?>[] types = constructor.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == String.class) values[i] = "queue-compatibility";
            else if (type == int.class) values[i] = 193;
            else if (type == boolean.class) values[i] = false;
            else if (java.util.List.class.isAssignableFrom(type)) values[i] = new ArrayList<>();
        }
        constructor.setAccessible(true);
        Object original = constructor.newInstance(values);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) { stream.writeObject(original); }
        Object restored;
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)
                    throws IOException, ClassNotFoundException {
                return Class.forName(descriptor.getName(), false, current);
            }
        }) { restored = stream.readObject(); }
        for (String fieldName : new String[] {"id", "title", "duration"}) {
            Field oldField = oldType.getDeclaredField(fieldName);
            Field newField = newType.getDeclaredField(fieldName);
            oldField.setAccessible(true); newField.setAccessible(true);
            if (!oldField.get(original).equals(newField.get(restored)))
                throw new AssertionError("Queue field lost: " + fieldName);
        }
        System.out.println("QUEUE_UPGRADE_OK: actual previous APK metadata reads with the current APK");
    }
}
