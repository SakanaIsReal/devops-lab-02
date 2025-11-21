package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.UserPublicDto;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock private UserRepository repo;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    /** helper: สร้าง User ครบทุกพารามิเตอร์ที่ entity มี */
    private static User user(Long id,
                             String email,
                             String userName,
                             String phone,
                             String avatarUrl,
                             String firstName,
                             String lastName,
                             Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUserName(userName);
        u.setPhone(phone);
        u.setAvatarUrl(avatarUrl);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setRole(role);
        return u;
    }

    @Nested @DisplayName("list()")
    class ListAll {
        @Test @DisplayName("มีข้อมูล → คืนลิสต์ทั้งหมดจาก repo")
        void list_hasData() {
            when(repo.findAll()).thenReturn(List.of(
                    user(1L,"a@x","A","090","u1","Alice","Able", Role.USER),
                    user(2L,"b@x","B","091","u2","Bob","Baker", Role.ADMIN)
            ));

            var result = service.list();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFirstName()).isEqualTo("Alice");
            assertThat(result.get(1).getRole()).isEqualTo(Role.ADMIN);
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
            when(repo.findById(10L)).thenReturn(Optional.of(
                    user(10L,"u@x","U","099","av","Uno","User", Role.USER)
            ));

            var u = service.get(10L);

            assertThat(u).isNotNull();
            assertThat(u.getId()).isEqualTo(10L);
            assertThat(u.getFirstName()).isEqualTo("Uno");
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
            User toSave = user(null,"a@x","A","090","av","Alice","Able", Role.USER);
            when(repo.save(any(User.class))).thenAnswer(inv -> {
                User saved = inv.getArgument(0);
                saved.setId(111L);
                return saved;
            });

            User saved = service.create(toSave);

            assertThat(saved).isSameAs(toSave);
            assertThat(saved.getId()).isEqualTo(111L);
            assertThat(saved.getLastName()).isEqualTo("Able");
            verify(repo).save(toSave);
            verifyNoMoreInteractions(repo);
        }
    }

    @Nested @DisplayName("update(user)")
    class UpdateOne {
        @Test @DisplayName("เรียก repo.save(user) และคืนค่าที่ได้")
        void update_ok() {
            User existing = user(22L,"b@x","B","091","av2","Bee","Beta", Role.ADMIN);
            when(repo.save(existing)).thenReturn(existing);

            var out = service.update(existing);

            assertThat(out).isSameAs(existing);
            assertThat(out.getRole()).isEqualTo(Role.ADMIN);
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
        @Test @DisplayName("q ปกติ → trim และค้นหาแบบ ignore-case จำกัด 100 รายการ")
        void normal_query_trimmed() {
            var alice = user(1L, "a@x", "Alice", "090", "av", "Alice", "Able", Role.USER);
            var alpha = user(2L, "b@x", "Alpha", "091", "av2", "Alpha", "Bee", Role.USER);

            when(repo.findTop100ByUserNameContainingIgnoreCase("a"))
                    .thenReturn(List.of(alice, alpha));

            var list = service.searchByName("  Alice  ");

            assertThat(list).isNotEmpty();
            assertThat(list.get(0).getUserName()).isEqualTo("Alice");

            verify(repo).findTop100ByUserNameContainingIgnoreCase("a");
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("q = null → ใช้ \"\" (คืนผลจาก findTop20ByUserNameContainingIgnoreCase(\"\") )")
        void null_query() {
            when(repo.findTop20ByUserNameContainingIgnoreCase("")
            ).thenReturn(List.of());

            var list = service.searchByName(null);

            assertThat(list).isEmpty();
            verify(repo).findTop20ByUserNameContainingIgnoreCase("");
            verifyNoMoreInteractions(repo);
        }

        @Test @DisplayName("q ว่าง/ช่องว่างล้วน → trim แล้วกลายเป็น \"\"")
        void blank_query() {
            when(repo.findTop20ByUserNameContainingIgnoreCase("")
            ).thenReturn(List.of());

            var list = service.searchByName("   ");

            assertThat(list).isEmpty();
            verify(repo).findTop20ByUserNameContainingIgnoreCase("");
            verifyNoMoreInteractions(repo);
        }
    }

    @Nested @DisplayName("toPublicDto(user)")
    class ToPublicDto {
        @Test @DisplayName("แมปทุกฟิลด์ตรง ๆ: id, email, userName, phone, avatarUrl (ไม่รวม first/last/role)")
        void map_all_fields() {
            User u = user(7L,"x@x","X","099","avatar.png","Xen","Xavier", Role.USER);

            UserPublicDto dto = UserService.toPublicDto(u);

            assertThat(dto.id()).isEqualTo(7L);
            assertThat(dto.email()).isEqualTo("x@x");
            assertThat(dto.userName()).isEqualTo("X");
            assertThat(dto.phone()).isEqualTo("099");
            assertThat(dto.avatarUrl()).isEqualTo("avatar.png");
        }
    }

    @Nested @DisplayName("password helpers")
    class PasswordHelpers {
        @Test @DisplayName("encodePassword → เรียก encoder.encode แล้วคืนค่าที่ได้")
        void encodePassword_ok() {
            when(passwordEncoder.encode("plain")).thenReturn("ENCODED");
            assertThat(service.encodePassword("plain")).isEqualTo("ENCODED");
            verify(passwordEncoder).encode("plain");
            verifyNoMoreInteractions(passwordEncoder);
        }

        @Test @DisplayName("passwordMatches → เรียก encoder.matches แล้วคืนค่าที่ได้")
        void passwordMatches_ok() {
            when(passwordEncoder.matches("raw","enc")).thenReturn(true);
            assertThat(service.passwordMatches("raw","enc")).isTrue();
            verify(passwordEncoder).matches("raw","enc");
            verifyNoMoreInteractions(passwordEncoder);
        }
    }
}
