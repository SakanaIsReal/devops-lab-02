package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.StoredFile;
import com.smartsplit.smartsplitback.repository.StoredFileRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {

    FileStorageService service;
    StoredFileRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(StoredFileRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            StoredFile s = inv.getArgument(0);
            if (s.getId() == null) s.setId(123L);
            return s;
        });
        service = new FileStorageService(repo);
    }

    private static MultipartFile mockFile(byte[] bytes, String originalFilename, String contentType, boolean empty) throws Exception {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(empty);
        when(f.getBytes()).thenReturn(bytes);
        when(f.getOriginalFilename()).thenReturn(originalFilename);
        when(f.getContentType()).thenReturn(contentType);
        return f;
    }

    private static HttpServletRequest req(String scheme, String host, int port, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setScheme(scheme);
        r.setServerName(host);
        r.setServerPort(port);
        r.setRequestURI(uri);
        return r;
    }

    @Nested
    class SaveTests {

        @Test
        void nullFile_returnsNull_and_noRepoCall() {
            String url = service.save(null, "any", "name", req("http","localhost",80,"/upload"));
            assertThat(url).isNull();
            verify(repo, never()).save(any());
        }

        @Test
        void emptyFile_returnsNull_and_noRepoCall() throws Exception {
            MultipartFile f = mockFile(new byte[0], "x.txt", "text/plain", true);
            String url = service.save(f, "any", "name", req("http","localhost",80,"/upload"));
            assertThat(url).isNull();
            verify(repo, never()).save(any());
        }

        @Test
        void save_withExplicitContentType_buildsDataUrl_andReturnsPublicFilesId() throws Exception {
            byte[] bytes = "hello".getBytes();
            MultipartFile f = mockFile(bytes, "photo.jpg", "image/jpeg", false);

            String url = service.save(f, "my group", "receipt_1", req("http","localhost",80,"/upload"));

            assertThat(url).isEqualTo("http://localhost/files/123");

            ArgumentCaptor<StoredFile> cap = ArgumentCaptor.forClass(StoredFile.class);
            verify(repo).save(cap.capture());
            StoredFile sf = cap.getValue();

            assertThat(sf.getFolder()).isEqualTo("my group");
            assertThat(sf.getOriginalName()).isEqualTo("receipt_1");
            assertThat(sf.getContentType()).isEqualTo("image/jpeg");
            assertThat(sf.getExt()).isEqualTo("jpg");
            assertThat(sf.getSizeBytes()).isEqualTo((long) bytes.length);
            assertThat(sf.getDataUrl()).startsWith("data:image/jpeg;base64,");
            String b64 = sf.getDataUrl().substring(sf.getDataUrl().indexOf(',') + 1);
            assertThat(Base64.getDecoder().decode(b64)).isEqualTo(bytes);
        }

        @Test
        void save_withoutContentType_derivesFromExtension_caseInsensitive() throws Exception {
            byte[] bytes = "x".getBytes();
            MultipartFile f = mockFile(bytes, "PIC.PNG", null, false);

            String url = service.save(f, "img", "hero", req("http","localhost",80,"/upload"));

            assertThat(url).isEqualTo("http://localhost/files/123");

            ArgumentCaptor<StoredFile> cap = ArgumentCaptor.forClass(StoredFile.class);
            verify(repo).save(cap.capture());
            StoredFile sf = cap.getValue();
            assertThat(sf.getContentType()).isEqualTo("image/png");
            assertThat(sf.getExt()).isEqualTo("png");
            assertThat(sf.getDataUrl()).startsWith("data:image/png;base64,");
        }

        @Test
        void save_noOriginalFilename_andNoContentType_usesOctetStream_andNoExt() throws Exception {
            byte[] bytes = "bin".getBytes();
            MultipartFile f = mockFile(bytes, null, null, false);

            String url = service.save(f, "misc-folder", "blob", req("http","localhost",80,"/upload"));

            assertThat(url).isEqualTo("http://localhost/files/123");

            ArgumentCaptor<StoredFile> cap = ArgumentCaptor.forClass(StoredFile.class);
            verify(repo).save(cap.capture());
            StoredFile sf = cap.getValue();
            assertThat(sf.getOriginalName()).isEqualTo("blob");
            assertThat(sf.getExt()).isNull();
            assertThat(sf.getContentType()).isEqualTo("application/octet-stream");
            assertThat(sf.getDataUrl()).startsWith("data:application/octet-stream;base64,");
        }

        @Test
        void save_preferredBlank_usesOriginalFilename() throws Exception {
            byte[] bytes = "d".getBytes();
            MultipartFile f = mockFile(bytes, "doc.PDF", null, false);

            String url = service.save(f, "docs", "   ", req("http","localhost",80,"/upload"));

            assertThat(url).isEqualTo("http://localhost/files/123");

            ArgumentCaptor<StoredFile> cap = ArgumentCaptor.forClass(StoredFile.class);
            verify(repo).save(cap.capture());
            StoredFile sf = cap.getValue();
            assertThat(sf.getOriginalName()).isEqualTo("doc.PDF");
            assertThat(sf.getExt()).isEqualTo("pdf");
            assertThat(sf.getContentType()).isEqualTo("application/pdf");
        }

        @Test
        void save_folderNullOrBlank_fallsBackTo_misc() throws Exception {
            MultipartFile a = mockFile("a".getBytes(), "a.txt", "text/plain", false);
            MultipartFile b = mockFile("b".getBytes(), "b.txt", "text/plain", false);
            MultipartFile c = mockFile("c".getBytes(), "c.txt", "text/plain", false);

            service.save(a, null, "f1", req("http","localhost",80,"/u"));
            service.save(b, "", "f2", req("http","localhost",80,"/u"));
            service.save(c, "   ", "f3", req("http","localhost",80,"/u"));

            ArgumentCaptor<StoredFile> cap = ArgumentCaptor.forClass(StoredFile.class);
            verify(repo, times(3)).save(cap.capture());
            assertThat(cap.getAllValues().get(0).getFolder()).isEqualTo("misc");
            assertThat(cap.getAllValues().get(1).getFolder()).isEqualTo("misc");
            assertThat(cap.getAllValues().get(2).getFolder()).isEqualTo("misc");
        }

        @Test
        void save_httpsCustomPort_reflectedInPublicUrl() throws Exception {
            MultipartFile f = mockFile("s".getBytes(), "screen.PNG", null, false);

            String url = service.save(f, "shots", "sc1", req("https","cdn.example.com",4443,"/upload"));

            assertThat(url).isEqualTo("https://cdn.example.com:4443/files/123");
        }

        @Test
        void save_httpDefaultPort_omitsPortInUrl() throws Exception {
            MultipartFile f = mockFile("x".getBytes(), "a.png", null, false);

            String url = service.save(f, "imgs", "a", req("http","localhost",80,"/upload"));

            assertThat(url).isEqualTo("http://localhost/files/123");
        }

        @Test
        void save_httpsDefaultPort_omitsPortInUrl() throws Exception {
            MultipartFile f = mockFile("x".getBytes(), "a.png", null, false);

            String url = service.save(f, "imgs", "a", req("https","example.com",443,"/upload"));

            assertThat(url).isEqualTo("https://example.com/files/123");
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void delete_dataUrlLegacy_returnsTrue() {
            boolean ok = service.deleteByUrl("data:image/png;base64,AAAA");
            assertThat(ok).isTrue();
            verify(repo, never()).deleteById(any());
        }

        @Test
        void delete_existingId_true_andDeletes() {
            when(repo.existsById(999L)).thenReturn(true);

            boolean ok = service.deleteByUrl("http://host/files/999");

            assertThat(ok).isTrue();
            verify(repo).deleteById(999L);
        }

        @Test
        void delete_nonExistingId_false() {
            when(repo.existsById(555L)).thenReturn(false);

            boolean ok = service.deleteByUrl("http://x/files/555");

            assertThat(ok).isFalse();
            verify(repo, never()).deleteById(any());
        }

        @Test
        void delete_malformedUrl_false() {
            assertThat(service.deleteByUrl("http://localhost/public/abc.txt")).isFalse();
            assertThat(service.deleteByUrl("http://localhost/files/abc")).isFalse();
            assertThat(service.deleteByUrl("http://localhost/files/../../etc/passwd")).isFalse();
            assertThat(service.deleteByUrl(null)).isFalse();
            assertThat(service.deleteByUrl("  ")).isFalse();
        }

        @Test
        void delete_urlWithQueryOrExtraSegments_parsesIdOnly() {
            when(repo.existsById(42L)).thenReturn(true);

            boolean ok1 = service.deleteByUrl("https://ex/files/42?x=1&y=2");
            boolean ok2 = service.deleteByUrl("https://ex/files/42/anything");

            assertThat(ok1).isTrue();
            assertThat(ok2).isTrue();
            verify(repo, times(2)).deleteById(42L);
        }
    }
}
