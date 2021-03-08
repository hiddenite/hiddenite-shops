package eu.hiddenite.shops.helpers;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BukkitSerializer {
    public static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(object);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (T)dataInput.readObject();
        }
    }
}
