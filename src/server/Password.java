package src.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
public class Password {

    private static String SECRET_KEY = readSecretKey();

    public static String hash(String password) {
        byte[] hashedBytes = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SECRET_KEY.getBytes());
            byte[] bytes = md.digest(password.getBytes());
            hashedBytes = bytes;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return Base64.getEncoder().encodeToString(hashedBytes);
    }

    public static boolean check(String providedPassword, String storedPassword) {
        byte[] storedHashedBytes = Base64.getDecoder().decode(storedPassword);

        byte[] hashedBytes = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SECRET_KEY.getBytes());
            byte[] bytes = md.digest(providedPassword.getBytes());
            hashedBytes = bytes;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (hashedBytes.length != storedHashedBytes.length) {
            return false;
        }

        for (int i = 0; i < hashedBytes.length; i++) {
            if (hashedBytes[i] != storedHashedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static String readSecretKey() {
        String key = null;
        try (BufferedReader br = new BufferedReader(new FileReader("src/server/.env"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2 && parts[0].trim().equals("SECRET_KEY")) {
                    key = parts[1].trim();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return key;
    }

    public static void main(String[] args) {
        String password = "password";
        String hashedPassword = hash(password);
        System.out.println(hashedPassword);

        boolean passwordsMatch = check("password", hashedPassword);
        System.out.println("Passwords Match: " + passwordsMatch);
    }
}
