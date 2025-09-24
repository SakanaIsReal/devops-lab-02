package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupServiceTest {

    @Mock private GroupRepository repo;
    @InjectMocks private GroupService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    private static User user(Long id, String email) {
        User u = new User();
        try { User.class.getMethod("setId", Long.class).invoke(u, id); } catch (Exception ignored) {}
        try { User.class.getMethod("setEmail", String.class).invoke(u, email); } catch (Exception ignored) {}
        return u;
    }

    private static Group group(Long id, User owner) {
        Group g = new Group();
        try { Group.class.getMethod("setId", Long.class).invoke(g, id); } catch (Exception ignored) {}
        try { Group.class.getMethod("setOwner", User.class).invoke(g, owner); } catch (Exception ignored) {}
        return g;
    }


    @Nested
    @DisplayName("list()")
    class ListAll {
        @Test
        @DisplayName("มีข้อมูล → คืนลิสต์ทั้งหมดจาก repo")
        void list_hasData() {
            when(repo.findAll()).thenReturn(List.of(
                    group(1L, user(10L, "a@x")),
                    group(2L, user(11L, "b@x"))
            ));

            var result = service.list();

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isNotNull();
            verify(repo).findAll();
        }

        @Test
        @DisplayName("ไม่มีข้อมูล → คืนลิสต์ว่าง")
        void list_empty() {
            when(repo.findAll()).thenReturn(List.of());

            var result = service.list();

            assertThat(result).isEmpty();
            verify(repo).findAll();
        }
    }

    @Nested
    @DisplayName("listByOwner(ownerId)")
    class ListByOwner {
        @Test
        @DisplayName("มีรายการของ owner → คืนลิสต์ตาม ownerId")
        void byOwner_hasData() {
            Long ownerId = 10L;
            when(repo.findByOwner_Id(ownerId)).thenReturn(List.of(
                    group(100L, user(ownerId, "o@x"))
            ));

            var result = service.listByOwner(ownerId);

            assertThat(result).hasSize(1);
            verify(repo).findByOwner_Id(ownerId);
        }

        @Test
        @DisplayName("ไม่มีรายการของ owner → คืนลิสต์ว่าง")
        void byOwner_empty() {
            when(repo.findByOwner_Id(99L)).thenReturn(List.of());

            var result = service.listByOwner(99L);

            assertThat(result).isEmpty();
            verify(repo).findByOwner_Id(99L);
        }
    }

    @Nested
    @DisplayName("listByMember(userId)")
    class ListByMember {
        @Test
        @DisplayName("ผู้ใช้เป็นสมาชิกของบางกลุ่ม → คืนลิสต์กลุ่มที่เกี่ยวข้อง")
        void byMember_hasData() {
            Long userId = 7L;
            when(repo.findAllByMemberUserId(userId)).thenReturn(List.of(
                    group(200L, user(1L, "owner@x")),
                    group(201L, user(2L, "owner2@x"))
            ));

            var result = service.listByMember(userId);

            assertThat(result).hasSize(2);
            verify(repo).findAllByMemberUserId(userId);
        }

        @Test
        @DisplayName("ผู้ใช้ไม่เป็นสมาชิกที่ไหนเลย → คืนลิสต์ว่าง")
        void byMember_empty() {
            when(repo.findAllByMemberUserId(8L)).thenReturn(List.of());

            var result = service.listByMember(8L);

            assertThat(result).isEmpty();
            verify(repo).findAllByMemberUserId(8L);
        }
    }


    @Nested
    @DisplayName("get(id)")
    class GetOne {
        @Test
        @DisplayName("พบ → คืน Group")
        void get_found() {
            when(repo.findById(300L)).thenReturn(Optional.of(group(300L, user(10L, "o@x"))));

            var g = service.get(300L);

            assertThat(g).isNotNull();
            verify(repo).findById(300L);
        }

        @Test
        @DisplayName("ไม่พบ → คืน null")
        void get_notFound() {
            when(repo.findById(999L)).thenReturn(Optional.empty());

            var g = service.get(999L);

            assertThat(g).isNull();
            verify(repo).findById(999L);
        }
    }

    @Nested
    @DisplayName("save(group)")
    class SaveOne {
        @Test
        @DisplayName("บันทึกสำเร็จ → คืน entity จาก repo.save")
        void save_ok() {
            Group toSave = group(null, user(10L, "o@x"));

            when(repo.save(any(Group.class))).thenAnswer(inv -> {
                Group g = inv.getArgument(0);
                try { Group.class.getMethod("setId", Long.class).invoke(g, 777L); } catch (Exception ignored) {}
                return g;
            });

            Group saved = service.save(toSave);

            assertThat(saved).isSameAs(toSave);
            // ถ้ามี getId() ก็จะเป็น 777L — ไม่ assert ก็ได้เพื่อหลีกเลี่ยงผูกกับโมเดล
            verify(repo).save(toSave);
        }
    }

    @Nested
    @DisplayName("delete(id)")
    class DeleteOne {
        @Test
        @DisplayName("เรียก repo.deleteById ด้วย id ที่ถูกต้อง")
        void delete_ok() {
            service.delete(888L);
            verify(repo).deleteById(888L);
        }
    }
}
