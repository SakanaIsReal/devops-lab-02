package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByOwner_Id(Long ownerUserId);

    @Query("select g.owner.id from Group g where g.id = :groupId")
    Long findOwnerIdById(@Param("groupId") Long groupId);

    @Query("select distinct g from GroupMember gm join gm.group g where gm.user.id = :userId")
    List<Group> findAllByMemberUserId(@Param("userId") Long userId);

    @Query("""
        select distinct g
        from GroupMember gm
        join gm.group g
        where gm.user.id = :userId
          and lower(g.name) like lower(concat('%', :name, '%'))
    """)
    List<Group> findAllByMemberUserIdAndNameContainingIgnoreCase(
            @Param("userId") Long userId,
            @Param("name") String name
    );

    @Query("""
        select distinct g
        from Group g
        left join GroupMember gm on gm.group = g and gm.user.id = :me
        where lower(g.name) like lower(concat('%', :q, '%'))
          and (g.owner.id = :me or gm.id is not null)
    """)
    List<Group> searchMyOwnedOrMemberGroupsByName(
            @Param("me") Long me,
            @Param("q") String q
    );
}
