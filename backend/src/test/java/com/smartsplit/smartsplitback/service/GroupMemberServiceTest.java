package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupMemberServiceTest {

    @Mock private GroupMemberRepository repo;
    @Mock private GroupRepository groupRepo;

    @InjectMocks private GroupMemberService service;

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

    private static GroupMember member(Long groupId, Long userId) {
        GroupMember m = new GroupMember();
        try { GroupMember.class.getMethod("setId", GroupMemberId.class).invoke(m, new GroupMemberId(groupId, userId)); } catch (Exception ignored) {}
        try { GroupMember.class.getMethod("setGroup", Group.class).invoke(m, group(groupId, null)); } catch (Exception ignored) {}
        try { GroupMember.class.getMethod("setUser", User.class).invoke(m, user(userId, "x@x")); } catch (Exception ignored) {}
        return m;
    }


    @Nested
    @DisplayName("listByGroup(groupId)")
    class ListByGroupTests {
        @Test
        @DisplayName("มีสมาชิก → คืนลิสต์สมาชิกตาม groupId")
        void hasMembers() {
            Long gid = 10L;
            when(repo.findByGroup_Id(gid)).thenReturn(List.of(
                    member(gid, 1L),
                    member(gid, 2L)
            ));

            var list = service.listByGroup(gid);

            assertThat(list).hasSize(2);
            verify(repo).findByGroup_Id(gid);
        }

        @Test
        @DisplayName("ไม่มีสมาชิก → คืนลิสต์ว่าง")
        void empty() {
            Long gid = 11L;
            when(repo.findByGroup_Id(gid)).thenReturn(List.of());

            var list = service.listByGroup(gid);

            assertThat(list).isEmpty();
            verify(repo).findByGroup_Id(gid);
        }
    }


    @Nested
    @DisplayName("exists(groupId, userId)")
    class ExistsTests {
        @Test
        @DisplayName("มีอยู่จริง → true")
        void existsTrue() {
            when(repo.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);

            boolean ok = service.exists(10L, 5L);

            assertThat(ok).isTrue();
            verify(repo).existsByGroup_IdAndUser_Id(10L, 5L);
        }

        @Test
        @DisplayName("ไม่มี → false")
        void existsFalse() {
            when(repo.existsByGroup_IdAndUser_Id(10L, 6L)).thenReturn(false);

            boolean ok = service.exists(10L, 6L);

            assertThat(ok).isFalse();
            verify(repo).existsByGroup_IdAndUser_Id(10L, 6L);
        }
    }


    @Nested
    @DisplayName("save(member)")
    class SaveTests {
        @Test
        @DisplayName("บันทึกสำเร็จ → คืนค่า entity จาก repo.save")
        void saveOk() {
            GroupMember gm = member(7L, 3L);
            when(repo.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

            GroupMember saved = service.save(gm);

            assertThat(saved).isSameAs(gm);
            verify(repo).save(gm);
        }
    }


    @Nested
    @DisplayName("delete(groupId, userId)")
    class DeleteTests {

        @Test
        @DisplayName("กลุ่มมีอยู่ + ผู้ใช้ไม่ใช่ owner → เรียก deleteById สำเร็จ")
        void deleteOk_nonOwner() {
            Long gid = 100L, uid = 9L;
            when(groupRepo.findById(gid)).thenReturn(Optional.of(group(gid, user(1L, "owner@x"))));

            service.delete(gid, uid);

            verify(groupRepo).findById(gid);
            verify(repo).deleteById(new GroupMemberId(gid, uid));
        }

        @Test
        @DisplayName("กลุ่มไม่พบ → 404 NOT_FOUND และไม่เรียก deleteById")
        void groupNotFound_404() {
            Long gid = 101L, uid = 9L;
            when(groupRepo.findById(gid)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.delete(gid, uid),
                    ResponseStatusException.class
            );

            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Group not found");

            verify(groupRepo).findById(gid);
            verify(repo, never()).deleteById(any());
        }

        @Test
        @DisplayName("ผู้ใช้เป็น owner เอง → 400 BAD_REQUEST และไม่ลบ")
        void ownerCannotRemoveSelf_400() {
            Long gid = 102L, ownerId = 55L;
            when(groupRepo.findById(gid)).thenReturn(Optional.of(group(gid, user(ownerId, "o@x"))));

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.delete(gid, ownerId),
                    ResponseStatusException.class
            );

            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("Owner cannot remove themselves from the group");

            verify(groupRepo).findById(gid);
            verify(repo, never()).deleteById(any());
        }

        @Test
        @DisplayName("owner เป็น null → ถือว่าไม่ใช่เจ้าของ → ลบได้")
        void ownerNull_deletable() {
            Long gid = 103L, uid = 77L;
            when(groupRepo.findById(gid)).thenReturn(Optional.of(group(gid, null)));

            service.delete(gid, uid);

            verify(groupRepo).findById(gid);
            verify(repo).deleteById(new GroupMemberId(gid, uid));
        }
    }

  
    @Nested
    @DisplayName("countMembers(groupId)")
    class CountMembersTests {
        @Test
        @DisplayName("delegate ไป repo.countByGroupId และคืนค่าตามนั้น")
        void countOk() {
            when(repo.countByGroupId(500L)).thenReturn(42L);

            long c = service.countMembers(500L);

            assertThat(c).isEqualTo(42L);
            verify(repo).countByGroupId(500L);
        }
    }
}
