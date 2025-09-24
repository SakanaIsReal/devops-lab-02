package com.smartsplit.smartsplitback.model;

import com.smartsplit.smartsplitback.model.GroupMemberId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupMemberIdTest {

    @Test
    void same_pair_equal() {
        GroupMemberId a = new GroupMemberId(1L, 2L);
        GroupMemberId b = new GroupMemberId(1L, 2L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void diff_groupId_notEqual() {
        GroupMemberId a = new GroupMemberId(1L, 2L);
        GroupMemberId b = new GroupMemberId(9L, 2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void diff_userId_notEqual() {
        GroupMemberId a = new GroupMemberId(1L, 2L);
        GroupMemberId b = new GroupMemberId(1L, 3L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void null_vs_value_notEqual() {
        GroupMemberId a = new GroupMemberId(null, 2L);
        GroupMemberId b = new GroupMemberId(1L, 2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void both_nulls_equal_if_both_match() {
        GroupMemberId a = new GroupMemberId(null, null);
        GroupMemberId b = new GroupMemberId(null, null);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
