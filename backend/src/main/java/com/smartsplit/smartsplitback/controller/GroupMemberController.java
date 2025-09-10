package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.model.dto.GroupMemberDto;
import com.smartsplit.smartsplitback.service.GroupMemberService;
import com.smartsplit.smartsplitback.service.GroupService;
import com.smartsplit.smartsplitback.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/members")
public class GroupMemberController {
    private final GroupMemberService members;
    private final GroupService groups;
    private final UserService users;

    public GroupMemberController(GroupMemberService members, GroupService groups, UserService users){
        this.members = members; this.groups = groups; this.users = users;
    }

    @PreAuthorize("@perm.isGroupMember(#groupId)")
    @GetMapping
    public List<GroupMemberDto> list(@PathVariable Long groupId){
        ensureGroup(groupId);
        return members.listByGroup(groupId).stream()
                .map(m -> new GroupMemberDto(m.getGroup().getId(), m.getUser().getId()))
                .toList();
    }

    @PreAuthorize("@perm.canManageMembers(#groupId)")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public GroupMemberDto add(@PathVariable Long groupId, @RequestBody GroupMemberDto in){
        if(in.userId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"userId is required");
        Group g = ensureGroup(groupId);
        User u = users.get(in.userId());
        if(u==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        if(members.exists(groupId, in.userId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Already a member");

        GroupMember gm = new GroupMember();
        gm.setGroup(g);
        gm.setUser(u);
        gm.setId(new GroupMemberId(g.getId(), u.getId()));
        members.save(gm);
        return new GroupMemberDto(g.getId(), u.getId());
    }

    @PreAuthorize("@perm.canManageMembers(#groupId)")
    @DeleteMapping("/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long groupId, @PathVariable Long userId){
        if(!members.exists(groupId, userId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Membership not found");
        members.delete(groupId, userId);
    }

    private Group ensureGroup(Long id){
        var g = groups.get(id);
        if(g==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found");
        return g;
    }
}
