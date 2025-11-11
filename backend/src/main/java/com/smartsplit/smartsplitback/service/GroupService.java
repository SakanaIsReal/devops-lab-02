package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {
    private final GroupRepository repo;
    public GroupService(GroupRepository repo){ this.repo = repo; }

    public List<Group> list(){ return repo.findAll(); }
    public List<Group> listByOwner(Long ownerId){ return repo.findByOwner_Id(ownerId); }
    public List<Group> listByMember(Long userId){ return repo.findAllByMemberUserId(userId); }
    public Group get(Long id){ return repo.findById(id).orElse(null); }
    public Group save(Group g){ return repo.save(g); }
    public void delete(Long id){ repo.deleteById(id); }

    public List<Group> searchMyGroups(Long me, String q){
        return repo.searchMyOwnedOrMemberGroupsByName(me, q);
    }
}