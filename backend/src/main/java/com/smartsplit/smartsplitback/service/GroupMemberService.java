package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GroupMemberService {
    private final GroupMemberRepository repo;
    public GroupMemberService(GroupMemberRepository repo){ this.repo = repo; }

    public List<GroupMember> listByGroup(Long groupId){ return repo.findByGroup_Id(groupId); }
    public boolean exists(Long groupId, Long userId){ return repo.existsByGroup_IdAndUser_Id(groupId, userId); }
    public GroupMember save(GroupMember m){ return repo.save(m); }
    public void delete(Long groupId, Long userId){ repo.deleteById(new GroupMemberId(groupId, userId)); }
}
