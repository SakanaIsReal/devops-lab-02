package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stored_files",
        indexes = {
                @Index(name = "idx_stored_files_folder", columnList = "folder")
        })
public class StoredFile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** โฟลเดอร์ตรรกะ เช่น "group-covers", "avatars" */
    @Column(nullable = false, length = 100)
    private String folder;

    /** ชื่อไฟล์ต้นทาง (ไว้แสดงผล) */
    @Column(name = "original_name", length = 255)
    private String originalName;

    /** MIME type เช่น image/png */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** นามสกุล เช่น png, jpg */
    @Column(name = "ext", length = 10)
    private String ext;

    /** ขนาดไฟล์เดิม (ไบต์) */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** เก็บ Data URL (data:<mime>;base64,...) */
    @Lob
    @Column(name = "data_url", columnDefinition = "LONGTEXT", nullable = false)
    private String dataUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ---- getters/setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getDataUrl() { return dataUrl; }
    public void setDataUrl(String dataUrl) { this.dataUrl = dataUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
