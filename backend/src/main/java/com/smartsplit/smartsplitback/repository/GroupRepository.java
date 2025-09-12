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


}
