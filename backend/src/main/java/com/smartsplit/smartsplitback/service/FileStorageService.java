package com.smartsplit.smartsplitback.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {


    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }


    public String save(MultipartFile file, String folder, String preferredFileName, HttpServletRequest req) {
        if (file == null || file.isEmpty()) return null;

        try {

            String safeFolder = sanitize(folder);
            Path dir = root.resolve(safeFolder);
            Files.createDirectories(dir);


            String ext = getExtension(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
            String baseName = (preferredFileName != null && !preferredFileName.isBlank())
                    ? sanitize(preferredFileName)
                    : UUID.randomUUID().toString();
            String finalName = ext.isBlank() ? baseName : (baseName + "." + ext);


            Path target = dir.resolve(finalName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);


            String url = ServletUriComponentsBuilder.fromRequestUri(req)
                    .replacePath(null) // เคลียร์ path ปัจจุบัน
                    .path("/files/")
                    .path(safeFolder + "/")
                    .path(finalName)
                    .toUriString();
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Cannot store file: " + e.getMessage(), e);
        }
    }

    public boolean deleteByUrl(String publicUrl) {
        try {
            if (publicUrl == null || publicUrl.isBlank()) return false;


            int idx = publicUrl.indexOf("/files/");
            if (idx < 0) return false;
            String rel = publicUrl.substring(idx + "/files/".length()); // <folder>/<name>

            Path target = root.resolve(rel).normalize();
            if (!target.startsWith(root)) return false; // กัน path traversal
            return java.nio.file.Files.deleteIfExists(target);
        } catch (Exception ignore) {
            return false;
        }
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1);
    }
}
