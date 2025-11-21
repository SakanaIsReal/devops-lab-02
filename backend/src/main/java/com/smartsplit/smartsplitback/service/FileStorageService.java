package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.StoredFile;
import com.smartsplit.smartsplitback.repository.StoredFileRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Locale;

@Service
public class FileStorageService {

    private final StoredFileRepository repo;

    public FileStorageService(StoredFileRepository repo) {
        this.repo = repo;
    }

    public String save(MultipartFile file, String folder, String preferredFileName, HttpServletRequest req) {
        if (file == null || file.isEmpty()) return null;
        try {
            byte[] bytes = file.getBytes();

            String orig = file.getOriginalFilename();
            String ext  = getExtension(orig);

            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                String key = (ext == null ? "" : ext.toLowerCase(Locale.ROOT));
                contentType = switch (key) {
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "webp" -> "image/webp";
                    case "svg" -> "image/svg+xml";
                    case "pdf" -> "application/pdf";
                    default -> "application/octet-stream";
                };
            }

            String base64 = Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:" + contentType + ";base64," + base64;

            StoredFile sf = new StoredFile();
            sf.setFolder((folder == null || folder.isBlank()) ? "misc" : folder);
            sf.setOriginalName((preferredFileName != null && !preferredFileName.isBlank()) ? preferredFileName : orig);
            sf.setContentType(contentType);
            sf.setExt(ext);
            sf.setSizeBytes((long) bytes.length);
            sf.setDataUrl(dataUrl);

            repo.save(sf);
            return buildPublicUrl(sf.getId());
        } catch (Exception e) {
            throw new RuntimeException("Cannot store file as base64: " + e.getMessage(), e);
        }
    }

    public boolean deleteByUrl(String publicUrl) {
        try {
            if (publicUrl == null || publicUrl.isBlank()) return false;
            if (publicUrl.startsWith("data:")) return true;

            // Support both /api/files/ and /files/ for backwards compatibility
            int idx = publicUrl.indexOf("/api/files/");
            if (idx < 0) {
                idx = publicUrl.indexOf("/files/");
                if (idx < 0) return false;
            }

            String prefix = publicUrl.contains("/api/files/") ? "/api/files/" : "/files/";
            String tail = publicUrl.substring(publicUrl.indexOf(prefix) + prefix.length());
            int q = tail.indexOf('?');
            if (q >= 0) tail = tail.substring(0, q);
            int slash = tail.indexOf('/');
            if (slash >= 0) tail = tail.substring(0, slash);

            Long id = Long.parseLong(tail);
            if (!repo.existsById(id)) return false;
            repo.deleteById(id);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private String buildPublicUrl(Long id) {
        return "/files/" + id;
    }

    private String getExtension(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
