package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.GroupDto;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.security.SecurityFacade;
import com.smartsplit.smartsplitback.service.FileStorageService;
import com.smartsplit.smartsplitback.service.GroupMemberService;
import com.smartsplit.smartsplitback.service.GroupService;
import com.smartsplit.smartsplitback.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groups;
    private final UserService users;
    private final GroupMemberService members;
    private final SecurityFacade sec;
    private final FileStorageService storage;
    private final Perms perm;

    public GroupController(GroupService groups,
                           UserService users,
                           GroupMemberService members,
                           SecurityFacade sec,
                           FileStorageService storage,
                           Perms perm) {
        this.groups = groups;
        this.users = users;
        this.members = members;
        this.sec = sec;
        this.storage = storage;
        this.perm = perm;
    }

    @PreAuthorize("#ownerUserId == null ? isAuthenticated() : @perm.isAdmin()")
    @GetMapping
    public List<GroupDto> list(@RequestParam(required = false) Long ownerUserId) {
        if (ownerUserId == null) {
            Long me = sec.currentUserId();
            if (me == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
            }
            return groups.listByOwner(me).stream().map(this::toDto).toList();
        } else {
            // ผ่าน @PreAuthorize มาได้แปลว่าเป็นแอดมินแล้ว
            return groups.listByOwner(ownerUserId).stream().map(this::toDto).toList();
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mine")
    public List<GroupDto> listMine(){
        Long me = sec.currentUserId();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        var list = groups.listByMember(me);
        return list.stream().map(this::toDto).toList();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public GroupDto get(@PathVariable Long id){
        var g = groups.get(id);
        // ให้ 404 ก่อน (ถ้าไม่พบ)
        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
        // แล้วค่อยเช็คสิทธิ์สมาชิก/แอดมิน → 403 ถ้าไม่ผ่าน
        if (!perm.isGroupMember(id) && !perm.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return toDto(g);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto create(@RequestPart("group") GroupDto in,
                           @RequestPart(value = "cover", required = false) MultipartFile cover,
                           HttpServletRequest req){
        Long me = sec.currentUserId();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        if (in.ownerUserId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ownerUserId is required");

        if (!perm.isAdmin() && !me.equals(in.ownerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        User owner = users.get(in.ownerUserId());
        if(owner==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner user not found");

        Group g = new Group();
        g.setOwner(owner);
        g.setName(in.name());
        g = groups.save(g);

        if (cover != null && !cover.isEmpty()) {
            String url = storage.save(cover, "group-covers", "group-" + g.getId(), req);
            g.setCoverImageUrl(url);
            g = groups.save(g);
        }

        if (!members.exists(g.getId(), owner.getId())) {
            GroupMember gm = new GroupMember();
            gm.setGroup(g);
            gm.setUser(owner);
            gm.setId(new GroupMemberId(g.getId(), owner.getId()));
            members.save(gm);
        }
        return toDto(g);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto createJson(@RequestBody GroupDto in){
        Long me = sec.currentUserId();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        if (in.ownerUserId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ownerUserId is required");

        if (!perm.isAdmin() && !me.equals(in.ownerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        User owner = users.get(in.ownerUserId());
        if(owner==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner user not found");

        Group g = new Group();
        g.setOwner(owner);
        g.setName(in.name());
        g.setCoverImageUrl(in.coverImageUrl());
        g = groups.save(g);

        if (!members.exists(g.getId(), owner.getId())) {
            GroupMember gm = new GroupMember();
            gm.setGroup(g);
            gm.setUser(owner);
            gm.setId(new GroupMemberId(g.getId(), owner.getId()));
            members.save(gm);
        }
        return toDto(g);
    }

    @PreAuthorize("@perm.canManageGroup(#id)")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GroupDto updateJson(@PathVariable Long id, @RequestBody GroupDto in){
        var g = groups.get(id);
        if(g==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found");

        if(in.ownerUserId()!=null){
            var owner = users.get(in.ownerUserId());
            if(owner==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner user not found");
            g.setOwner(owner);
            if (!members.exists(g.getId(), owner.getId())) {
                GroupMember gm = new GroupMember();
                gm.setGroup(g);
                gm.setUser(owner);
                gm.setId(new GroupMemberId(g.getId(), owner.getId()));
                members.save(gm);
            }
        }
        if(in.name()!=null) g.setName(in.name());
        if(in.coverImageUrl()!=null) g.setCoverImageUrl(in.coverImageUrl());

        return toDto(groups.save(g));
    }

    @PreAuthorize("@perm.canManageGroup(#id)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GroupDto update(@PathVariable Long id,
                           @RequestPart("group") GroupDto in,
                           @RequestPart(value = "cover", required = false) MultipartFile cover,
                           HttpServletRequest req){
        var g = groups.get(id);
        if(g==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found");

        if(in.ownerUserId()!=null){
            var owner = users.get(in.ownerUserId());
            if(owner==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Owner user not found");
            g.setOwner(owner);
            if (!members.exists(g.getId(), owner.getId())) {
                GroupMember gm = new GroupMember();
                gm.setGroup(g);
                gm.setUser(owner);
                gm.setId(new GroupMemberId(g.getId(), owner.getId()));
                members.save(gm);
            }
        }
        if(in.name()!=null) g.setName(in.name());

        if (cover != null && !cover.isEmpty()) {
            storage.deleteByUrl(g.getCoverImageUrl());
            String url = storage.save(cover, "group-covers", "group-" + g.getId(), req);
            g.setCoverImageUrl(url);
        }
        return toDto(groups.save(g));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        var g = groups.get(id);
        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }

        if (!perm.canManageGroup(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (g.getCoverImageUrl() != null && !g.getCoverImageUrl().isBlank()) {
            storage.deleteByUrl(g.getCoverImageUrl());
        }

        groups.delete(id);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/search")
    public List<GroupDto> searchMyGroups(@RequestParam("q") String q){
        Long me = sec.currentUserId();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");

        String query = (q == null) ? "" : q.trim();
        if (query.isEmpty()) return List.of(); // กันดึงทั้งก้อนถ้าไม่มี paging

        return groups.searchMyGroups(me, query)
                .stream()
                .map(this::toDto)
                .toList();
    }
    private GroupDto toDto(Group g){
        long memberCount = members.countMembers(g.getId());
        return new GroupDto(
                g.getId(),
                g.getOwner().getId(),
                g.getName(),
                g.getCoverImageUrl(),
                memberCount
        );
    }
}
