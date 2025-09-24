package com.smartsplit.smartsplitback.model;

import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class GroupMemberTest {

    private static Group group(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Nested
    @DisplayName("ตั้งค่า association พื้นฐาน")
    class Associations {
        @Test
        @DisplayName("setGroup/setUser แล้วอ่านคืนได้ และตั้งคีย์ประกอบถูกต้อง (หากคลาสกำหนด)")
        void set_refs() {
            GroupMember m = new GroupMember();
            m.setGroup(group(100L));
            m.setUser(user(200L));

            assertThat(m.getGroup()).isNotNull();
            assertThat(m.getGroup().getId()).isEqualTo(100L);
            assertThat(m.getUser()).isNotNull();
            assertThat(m.getUser().getId()).isEqualTo(200L);
        }
    }

    @Nested
    @DisplayName("equals/hashCode (ตามคีย์ประกอบ)")
    class EqualityCompositeKey {
        @Test
        @DisplayName("คีย์ประกอบเหมือนกัน → equal")
        void same_key_equal() {
            GroupMember a = new GroupMember();
            a.setId(new GroupMemberId(1L, 2L));

            GroupMember b = new GroupMember();
            b.setId(new GroupMemberId(1L, 2L));

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("คีย์ต่างกัน → not equal")
        void diff_key_notEqual() {
            GroupMember a = new GroupMember();
            a.setId(new GroupMemberId(1L, 2L));

            GroupMember b = new GroupMember();
            b.setId(new GroupMemberId(1L, 9L));

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("id = null vs มีค่า → not equal")
        void null_vs_value_notEqual() {
            GroupMember a = new GroupMember();                 // id = null
            GroupMember b = new GroupMember();
            b.setId(new GroupMemberId(1L, 2L));

            assertThat(a).isNotEqualTo(b);
            assertThat(b).isNotEqualTo(a);
        }
    }
}
