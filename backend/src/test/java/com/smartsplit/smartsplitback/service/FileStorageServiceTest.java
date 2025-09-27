package com.smartsplit.smartsplitback.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {

    @TempDir
    Path tempRoot;

    FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(tempRoot.toString());
    }

    // ---------- Helpers ----------
    private static MultipartFile mockFile(byte[] data, String originalFilename, boolean empty) throws IOException {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(empty);
        when(f.getOriginalFilename()).thenReturn(originalFilename);
        when(f.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        return f;
    }

    /** ใช้ Spring MockHttpServletRequest เพื่อให้ ServletUriComponentsBuilder สร้าง absolute URL ได้ */
    private static HttpServletRequest mockReq(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("localhost");
        req.setServerPort(80);
        req.setRequestURI(uri); // เช่น "/api/upload"
        return req;
    }

    @SuppressWarnings("unused")
    private static String lastSegment(String url) {
        URI u = URI.create(url);
        String path = u.getPath();
        int slash = path.lastIndexOf('/');
        return (slash >= 0) ? path.substring(slash + 1) : path;
    }

    // ===================== save(...) =====================
    @Nested
    @DisplayName("save(file, folder, preferredFileName, req)")
    class SaveTests {

        @Test
        @DisplayName("file == null → คืน null และไม่สร้างไฟล์")
        void save_nullFile_returnsNull() {
            String url = service.save(null, "any", "name", mockReq("/api/upload"));
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("file.isEmpty() → คืน null และไม่สร้างไฟล์")
        void save_emptyFile_returnsNull() throws IOException {
            MultipartFile file = mockFile(new byte[0], "x.txt", true);
            String url = service.save(file, "folder", "n", mockReq("/api/upload"));
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("happy path: มี preferred + มีนามสกุลจาก original → เซฟไฟล์ + คืน URL absolute")
        void save_withPreferred_andExtension() throws IOException {
            MultipartFile file = mockFile("hello".getBytes(), "photo.jpg", false);
            HttpServletRequest req = mockReq("/api/upload");

            String url = service.save(file, "my group", "receipt_1", req);

            assertThat(url).isEqualTo("http://localhost/files/my_group/receipt_1.jpg");

            Path expected = tempRoot.resolve("my_group").resolve("receipt_1.jpg");
            assertThat(expected).exists().hasFileName("receipt_1.jpg");
            assertThat(Files.readAllBytes(expected)).isEqualTo("hello".getBytes());
        }

        @Test
        @DisplayName("ไม่มี preferred → ใช้ UUID + ต่อสกุล → URL absolute")
        void save_withoutPreferred_usesUUID() throws IOException {
            MultipartFile file = mockFile("data".getBytes(), "doc.pdf", false);

            String url = service.save(file, "docs", null, mockReq("/api/upload"));

            assertThat(url).startsWith("http://localhost/files/docs/");
            assertThat(url).endsWith(".pdf");

            Path dir = tempRoot.resolve("docs");
            assertThat(dir).exists().isDirectory();
            try (var stream = Files.list(dir)) {
                List<Path> all = stream.toList();
                assertThat(all).hasSize(1);
                assertThat(all.get(0).getFileName().toString()).endsWith(".pdf");
            }
        }

        @Test
        @DisplayName("originalFilename = null → ไม่มีสกุลไฟล์ → ใช้ preferred ตรง ๆ")
        void save_originalNull_extensionBlank() throws IOException {
            MultipartFile file = mockFile("x".getBytes(), null, false);

            String url = service.save(file, "bag", "ticket123", mockReq("/api/upload"));

            assertThat(url).isEqualTo("http://localhost/files/bag/ticket123");
            assertThat(tempRoot.resolve("bag").resolve("ticket123")).exists();
        }

        @Test
        @DisplayName("sanitize: โฟลเดอร์/ชื่อไฟล์ที่มีอักขระแปลก → แทนด้วย '_' (จุดยังคงอยู่ได้)")
        void save_sanitize_folder_and_name() throws IOException {
            MultipartFile file = mockFile("z".getBytes(), "photo-1.png", false);

            String url = service.save(file, "A B/../C", "my file", mockReq("/api/u"));

            // จุด (.) ได้รับอนุญาต → คาดว่าเป็น A_B_.._C
            assertThat(url).isEqualTo("http://localhost/files/A_B_.._C/my_file.png");

            Path expected = tempRoot.resolve("A_B_.._C").resolve("my_file.png");
            assertThat(expected).exists();
        }

        @Test
        @DisplayName("บันทึกทับ (REPLACE_EXISTING) → เขียนทับไฟล์เดิมได้")
        void save_overwrite_existing() throws IOException {
            MultipartFile f1 = mockFile("v1".getBytes(), "p.jpg", false);
            MultipartFile f2 = mockFile("v2".getBytes(), "p.jpg", false);
            HttpServletRequest req = mockReq("/api/u");

            String url1 = service.save(f1, "imgs", "same", req);
            assertThat(url1).isEqualTo("http://localhost/files/imgs/same.jpg");

            String url2 = service.save(f2, "imgs", "same", req);
            assertThat(url2).isEqualTo(url1);

            Path filePath = tempRoot.resolve("imgs").resolve("same.jpg");
            assertThat(Files.readAllBytes(filePath)).isEqualTo("v2".getBytes());
        }
    }

    // ===================== deleteByUrl(...) =====================
    @Nested
    @DisplayName("deleteByUrl(publicUrl)")
    class DeleteTests {

        @Test
        @DisplayName("ลบสำเร็จ: มีไฟล์อยู่จริง → true และไฟล์หายไป")
        void delete_existing_true() throws IOException {
            MultipartFile file = mockFile("content".getBytes(), "note.txt", false);
            String url = service.save(file, "bin", "one", mockReq("/api/u"));

            Path p = tempRoot.resolve("bin").resolve("one.txt");
            assertThat(p).exists();

            boolean ok = service.deleteByUrl(url);

            assertThat(ok).isTrue();
            assertThat(p).doesNotExist();
        }

        @Test
        @DisplayName("ไม่มีไฟล์ → false")
        void delete_nonExisting_false() {
            boolean ok = service.deleteByUrl("http://localhost/files/ghost/nope.bin");
            assertThat(ok).isFalse();
        }

        @Test
        @DisplayName("URL ไม่ใช่ /files/... → false (ไม่พยายามลบ)")
        void delete_malformedUrl_false() {
            boolean ok = service.deleteByUrl("http://localhost/public/abc.txt");
            assertThat(ok).isFalse();
        }

        @Test
        @DisplayName("กัน path traversal: /files/../../etc/passwd → false")
        void delete_pathTraversal_blocked() {
            boolean ok = service.deleteByUrl("http://localhost/files/../../etc/passwd");
            assertThat(ok).isFalse();
        }

        @Test
        @DisplayName("null/ว่าง → false")
        void delete_nullOrBlank_false() {
            assertThat(service.deleteByUrl(null)).isFalse();
            assertThat(service.deleteByUrl("  ")).isFalse();
        }
    }
}
