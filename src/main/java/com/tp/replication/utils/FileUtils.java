package com.tp.replication.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Ensures the directory exists, creating it if necessary.
     */
    public static void ensureDirectory(String dirPath) {
        try {
            Path dir = Paths.get(dirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                logger.info("Created directory: {}", dirPath);
            }
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", dirPath, e);
        }
    }

    /**
     * Appends a line to a file.
     */
    public static void appendLine(String filePath, String line) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            
            // Append with newline
            String content = line + System.lineSeparator();
            Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.debug("Appended to {}: {}", filePath, line);
        } catch (IOException e) {
            logger.error("Failed to append to {}: {}", filePath, e);
        }
    }

    /**
     * Reads the last line from a file.
     */
    public static String readLastLine(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "";
            }
            
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return "";
            }
            return lines.get(lines.size() - 1);
        } catch (IOException e) {
            logger.error("Failed to read last line from {}: {}", filePath, e);
            return "";
        }
    }

    /**
     * Reads all lines from a file.
     */
    public static List<String> readAllLines(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to read lines from {}: {}", filePath, e);
            return new ArrayList<>();
        }
    }

    /**
     * Checks if a file exists.
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
}
