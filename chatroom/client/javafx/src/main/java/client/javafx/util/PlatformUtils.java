package client.javafx.util;

import java.io.File;

public class PlatformUtils {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isLinux() {
        return OS.contains("linux") || OS.contains("unix");
    }

    public static String getAppDataDir() {
        String homeDir = System.getProperty("user.home");
        
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return appData + File.separator + "chatroom";
            }
            return homeDir + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "chatroom";
        } else if (isMac()) {
            return homeDir + File.separator + "Library" + File.separator + "Application Support" + File.separator + "chatroom";
        } else {
            return homeDir + File.separator + ".chatroom";
        }
    }

    public static String getChatClientDir() {
        String homeDir = System.getProperty("user.home");
        
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return appData + File.separator + "chat_client";
            }
            return homeDir + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "chat_client";
        } else {
            return homeDir + File.separator + ".chat_client";
        }
    }

    public static String getAvatarsCacheDir() {
        return getAppDataDir() + File.separator + "avatars";
    }

    public static String getLogsDir() {
        return getAppDataDir() + File.separator + "logs";
    }

    public static String getFilesDir() {
        return getAppDataDir() + File.separator + "files";
    }

    public static String getUserAvatarsUploadDir(String username) {
        return getFilesDir() + File.separator + "chatroom" + File.separator + "avatars" + File.separator + "users" + File.separator + username;
    }

    public static String getDbFilePath(String username) {
        return getChatClientDir() + File.separator + username + "_messages.db";
    }

    public static void ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}