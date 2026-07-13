package client.javafx.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public class Logger {
    
    private static final String LOG_DIR = PlatformUtils.getLogsDir();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ReentrantLock lock = new ReentrantLock();
    
    private final String tag;
    
    public Logger(Class<?> clazz) {
        this.tag = clazz.getSimpleName();
    }
    
    public Logger(String tag) {
        this.tag = tag;
    }
    
    public void debug(String message) {
        writeLog("DEBUG", tag, message);
    }
    
    public void info(String message) {
        writeLog("INFO", tag, message);
    }
    
    public void warn(String message) {
        writeLog("WARN", tag, message);
    }
    
    public void error(String message) {
        writeLog("ERROR", tag, message);
    }
    
    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(message);
        if (throwable != null) {
            sb.append("\n").append(throwable.getClass().getName()).append(": ").append(throwable.getMessage());
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("\n\tat ").append(element.toString());
            }
        }
        writeLog("ERROR", tag, sb.toString());
    }
    
    public void trace(String message) {
        writeLog("TRACE", tag, message);
    }
    
    private static void writeLog(String level, String tag, String message) {
        lock.lock();
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
            String logFileName = "chatroom_" + dateStr + ".log";
            File logFile = new File(logDir, logFileName);
            
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String logLine = "[" + timestamp + "] [" + level + "] [" + tag + "] " + message + "\n";
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(logLine);
            }
            
            if (level.equals("ERROR") || level.equals("WARN")) {
                System.err.println(logLine.trim());
            } else {
                System.out.println(logLine.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
    
    public static void logToFile(String message) {
        lock.lock();
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
            String logFileName = "chatroom_" + dateStr + ".log";
            File logFile = new File(logDir, logFileName);
            
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String logLine = "[" + timestamp + "] [INFO] [GLOBAL] " + message + "\n";
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(logLine);
            }
            
            System.out.println(logLine.trim());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}