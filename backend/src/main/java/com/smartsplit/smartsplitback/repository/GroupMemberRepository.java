package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByGroup_Id(Long groupId);
    List<GroupMember> findByUser_Id(Long userId);
    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
}
