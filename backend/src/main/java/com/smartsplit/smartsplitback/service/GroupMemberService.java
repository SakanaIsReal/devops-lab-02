// src/main/java/com/smartsplit/smartsplitback/service/GroupMemberService.java
package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class GroupMemberService {

    private final GroupMemberRepository repo;
    private final GroupRepository groupRepo;

    public GroupMemberService(GroupMemberRepository repo, GroupRepository groupRepo) {
        this.repo = repo;
        this.groupRepo = groupRepo;
    }

    public List<GroupMember> listByGroup(Long groupId){ return repo.findByGroup_Id(groupId); }

    public boolean exists(Long groupId, Long userId){ return repo.existsByGroup_IdAndUser_Id(groupId, userId); }

    public GroupMember save(GroupMember m){ return repo.save(m); }

    public void delete(Long groupId, Long userId){
        var g = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        if (g.getOwner() != null && g.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Owner cannot remove themselves from the group");
        }
        repo.deleteById(new GroupMemberId(groupId, userId));
    }

    public long countMembers(Long groupId) {
        return repo.countByGroupId(groupId);
    }
}
