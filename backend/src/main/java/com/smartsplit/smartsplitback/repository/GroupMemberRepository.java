package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByGroup_Id(Long groupId);
    List<GroupMember> findByUser_Id(Long userId);
    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);

    @Query(value = """
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM group_members gm1
        JOIN group_members gm2 ON gm1.group_id = gm2.group_id
        WHERE gm1.user_id = :me AND gm2.user_id = :target
        """, nativeQuery = true)
    int existsSharedGroupInt(@Param("me") Long me, @Param("target") Long target);

    // สะดวกใช้ในโค้ดอื่น ๆ
    default boolean existsSharedGroup(Long me, Long target) {
        return existsSharedGroupInt(me, target) > 0;
    }
}
