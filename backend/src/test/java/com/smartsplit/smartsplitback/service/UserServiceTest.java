package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.UserPublicDto;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock private UserRepository repo;
    @InjectMocks private UserService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    private static User user(Long id, String email, String name, String phone, String avatar) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUserName(name);
        u.setPhone(phone);
        u.setAvatarUrl(avatar);
        return u;
    }


    @Nested @DisplayName("list()")
    class ListAll {
        @Test @DisplayName("มีข้อมูล → คืนลิสต์ทั้งหมดจาก repo")
        void list_hasData() {
            when(repo.findAll()).thenReturn(List.of(user(1L,"a@x","A","090","u1"), user(2L,"b@x","B","091","u2")));

            var result = service.list();

            assertThat(result).hasSize(2);
            verify(repo).findAll();
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("ไม่มีข้อมูล → คืนลิสต์ว่าง")
        void list_empty() {
            when(repo.findAll()).thenReturn(List.of());

            var result = service.list();

            assertThat(result).isEmpty();
            verify(repo).findAll();
            verifyNoMoreInteractions(repo);
        }
    }


    @Nested @DisplayName("get(id)")
    class GetOne {
        @Test @DisplayName("พบ → คืน User")
        void get_found() {
            when(repo.findById(10L)).thenReturn(Optional.of(user(10L,"u@x","U","099","av")));

            var u = service.get(10L);

            assertThat(u).isNotNull();
            assertThat(u.getId()).isEqualTo(10L);
            verify(repo).findById(10L);
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("ไม่พบ → คืน null")
        void get_notFound() {
            when(repo.findById(99L)).thenReturn(Optional.empty());

            var u = service.get(99L);

            assertThat(u).isNull();
            verify(repo).findById(99L);
            verifyNoMoreInteractions(repo);
        }
    }


    @Nested @DisplayName("create(user)")
    class CreateOne {
        @Test @DisplayName("บันทึกสำเร็จ → คืน entity จาก repo.save")
        void create_ok() {
            User toSave = user(null,"a@x","A","090","av");
            when(repo.save(any(User.class))).thenAnswer(inv -> {
                User saved = inv.getArgument(0);
                saved.setId(111L);
                return saved;
            });

            User saved = service.create(toSave);

            assertThat(saved).isSameAs(toSave);
            assertThat(saved.getId()).isEqualTo(111L);
            verify(repo).save(toSave);
            verifyNoMoreInteractions(repo);
        }
    }


    @Nested @DisplayName("update(user)")
    class UpdateOne {
        @Test @DisplayName("เรียก repo.save(user) และคืนค่าที่ได้")
        void update_ok() {
            User existing = user(22L,"b@x","B","091","av2");
            when(repo.save(existing)).thenReturn(existing);

            var out = service.update(existing);

            assertThat(out).isSameAs(existing);
            verify(repo).save(existing);
            verifyNoMoreInteractions(repo);
        }
    }


    @Nested @DisplayName("delete(id)")
    class DeleteOne {
        @Test @DisplayName("เรียก repo.deleteById ด้วย id ที่ถูกต้อง")
        void delete_ok() {
            service.delete(333L);
            verify(repo).deleteById(333L);
            verifyNoMoreInteractions(repo);
        }
    }


    @Nested @DisplayName("searchByName(q)")
    class SearchByName {
        @Test @DisplayName("q ปกติ → trim และค้นหาแบบ ignore-case จำกัด 20 รายการ")
        void normal_query_trimmed() {
            when(repo.findTop20ByUserNameContainingIgnoreCase(anyString()))
                    .thenReturn(List.of(user(1L,"a@x","Alice","090","av")));

            var list = service.searchByName("  Alice  ");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getUserName()).isEqualTo("Alice");

            // ยืนยันว่า service ส่ง "Alice" (หลัง trim)
            verify(repo).findTop20ByUserNameContainingIgnoreCase("Alice");
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("q = null → ใช้ \"\" (คืนผลจาก findTop20ByUserNameContainingIgnoreCase(\"\") )")
        void null_query() {
            when(repo.findTop20ByUserNameContainingIgnoreCase("")).thenReturn(List.of());

            var list = service.searchByName(null);

            assertThat(list).isEmpty();
            verify(repo).findTop20ByUserNameContainingIgnoreCase("");
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("q ว่าง/ช่องว่างล้วน → trim แล้วกลายเป็น \"\"")
        void blank_query() {
            when(repo.findTop20ByUserNameContainingIgnoreCase("")).thenReturn(List.of());

            var list = service.searchByName("   ");

            assertThat(list).isEmpty();
            verify(repo).findTop20ByUserNameContainingIgnoreCase("");
            verifyNoMoreInteractions(repo);
        }
    }

    @Nested @DisplayName("toPublicDto(user)")
    class ToPublicDto {
        @Test @DisplayName("แมปทุกฟิลด์ตรง ๆ: id, email, userName, phone, avatarUrl")
        void map_all_fields() {
            User u = user(7L,"x@x","X","099","avatar.png");

            UserPublicDto dto = UserService.toPublicDto(u);

            assertThat(dto.id()).isEqualTo(7L);
            assertThat(dto.email()).isEqualTo("x@x");
            assertThat(dto.userName()).isEqualTo("X");
            assertThat(dto.phone()).isEqualTo("099");
            assertThat(dto.avatarUrl()).isEqualTo("avatar.png");
        }
    }
}
