package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GroupMemberControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/groups/{groupId}/members";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Autowired JwtService jwtService;

    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository groupMembers;

    long adminId;
    long ownerId;
    long memberId;
    long outsiderId;
    long groupId;


    private String jwtFor(long uid, int roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtService.CLAIM_UID, uid);
        claims.put(JwtService.CLAIM_ROLE, roleCode);
        String subject = "uid:" + uid; 
        return jwtService.generate(subject, claims, 3600); 
    }
    private RequestPostProcessor asAdmin(long id) {
        return req -> { req.addHeader("Authorization", "Bearer " + jwtFor(id, JwtService.ROLE_ADMIN)); return req; };
    }
    private RequestPostProcessor asUser(long id) {
        return req -> { req.addHeader("Authorization", "Bearer " + jwtFor(id, JwtService.ROLE_USER)); return req; };
    }

    @BeforeEach
    void setUp() {
        groupMembers.deleteAll();
        groups.deleteAll();
        users.deleteAll();

        
        var admin = new User();
        admin.setEmail("admin@example.com");
        admin.setUserName("Admin");
        admin.setPasswordHash("{noop}x");
        admin.setRole(Role.ADMIN);
        adminId = users.save(admin).getId();

        var owner = new User();
        owner.setEmail("owner@example.com");
        owner.setUserName("Owner");
        owner.setPasswordHash("{noop}x");
        owner.setRole(Role.USER);
        ownerId = users.save(owner).getId();

        var member = new User();
        member.setEmail("member@example.com");
        member.setUserName("Member");
        member.setPasswordHash("{noop}x");
        member.setRole(Role.USER);
        memberId = users.save(member).getId();

        var outsider = new User();
        outsider.setEmail("out@example.com");
        outsider.setUserName("Outsider");
        outsider.setPasswordHash("{noop}x");
        outsider.setRole(Role.USER);
        outsiderId = users.save(outsider).getId();

        // group: owner เป็นเจ้าของ
        var g = new Group();
        g.setName("G1");
        g.setOwner(owner);    
        groupId = groups.save(g).getId();

        
        var ownerMem = new GroupMember();
        ownerMem.setGroup(g);
        ownerMem.setUser(owner);
        ownerMem.setId(new GroupMemberId(groupId, ownerId));
        groupMembers.save(ownerMem);

        var memberMem = new GroupMember();
        memberMem.setGroup(g);
        memberMem.setUser(member);
        memberMem.setId(new GroupMemberId(groupId, memberId));
        groupMembers.save(memberMem);

        
    }

    // -------------------- LIST (GET) --------------------

    @Test
    @DisplayName("GET /api/groups/{id}/members → สมาชิกในกลุ่มเท่านั้นที่ดูได้ (owner/member ได้, outsider 403)")
    void list_members_authz() throws Exception {
        // owner → 200 และมีสมาชิกอย่างน้อย owner + member
        mvc.perform(get(BASE, groupId).with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId));

        // member → 200
        mvc.perform(get(BASE, groupId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId));

        // outsider (ไม่ใช่สมาชิก) → 403
        mvc.perform(get(BASE, groupId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    // -------------------- ADD (POST) --------------------

    @Test
    @DisplayName("POST /api/groups/{id}/members → owner เพิ่มสมาชิกใหม่ได้ (201), ซ้ำแล้ว 409, member ปกติ 403")
    void add_member_flow() throws Exception {
        // 1) member (ไม่ใช่คนจัดการ) พยายามเพิ่ม → 403
        var addOutsiderJson = """
            {"userId": %d}
            """.formatted(outsiderId);

        mvc.perform(post(BASE, groupId)
                        .with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutsiderJson))
                .andExpect(status().isForbidden());

        // 2) owner เพิ่ม outsider เข้า group → 201 CREATED
        mvc.perform(post(BASE, groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutsiderJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.userId").value(outsiderId));

        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, outsiderId)).isTrue();

        // 3) owner เพิ่มซ้ำคนเดิม → 409 CONFLICT
        mvc.perform(post(BASE, groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutsiderJson))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/groups/{id}/members → body ไม่มี userId ให้ 400; user ไม่พบให้ 404")
    void add_member_invalid_inputs() throws Exception {
        // ไม่มี userId → 400 (Controller เช็คเอง)
        mvc.perform(post(BASE, groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // user ไม่พบ → 404
        long notExistUser = 9_999_999L;
        var body = """
            {"userId": %d}
            """.formatted(notExistUser);

        mvc.perform(post(BASE, groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // -------------------- REMOVE (DELETE) --------------------

    @Test
    @DisplayName("DELETE /api/groups/{id}/members/{userId} → owner ลบสมาชิกได้ (204) และถ้าสมาชิกไม่อยู่แล้ว → 404")
    void remove_member_ok_and_notfound() throws Exception {
        // ลบ member เดิม → 204
        mvc.perform(delete(BASE + "/{userId}", groupId, memberId)
                        .with(asUser(ownerId)))
                .andExpect(status().isNoContent());

        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, memberId)).isFalse();

        // ลบซ้ำอีกครั้ง (ไม่มีแล้ว) → 404
        mvc.perform(delete(BASE + "/{userId}", groupId, memberId)
                        .with(asUser(ownerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/groups/{id}/members/{userId} → member ปกติลบใครไม่ได้ (403)")
    void remove_member_forbidden_for_normal_member() throws Exception {
        mvc.perform(delete(BASE + "/{userId}", groupId, outsiderId)
                        .with(asUser(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/groups/{id}/members/{userId} → เจ้าของกลุ่มลบตัวเองไม่ได้ (400)")
    void remove_owner_is_bad_request() throws Exception {
        mvc.perform(delete(BASE + "/{userId}", groupId, ownerId)
                        .with(asUser(ownerId)))
                .andExpect(status().isBadRequest());
    }
    @Test
    @DisplayName("DELETE /api/groups/{id}/members/me → สมาชิกธรรมดาออกจากกลุ่มตัวเองได้ (204)")
    void leave_group_as_member_ok() throws Exception {
        // ก่อนออก ต้องมีสถานะเป็นสมาชิก
        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, memberId)).isTrue();

        mvc.perform(delete(BASE + "/me", groupId).with(asUser(memberId)))
                .andExpect(status().isNoContent());

        // ออกจากกลุ่มแล้วต้องไม่มี membership
        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, memberId)).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/groups/{id}/members/me → owner ห้ามออกเอง (400)")
    void leave_group_as_owner_bad_request() throws Exception {
        mvc.perform(delete(BASE + "/me", groupId).with(asUser(ownerId)))
                .andExpect(status().isBadRequest());
        // ยังคงเป็นสมาชิก (และเป็น owner)
        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, ownerId)).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/groups/{id}/members/me → คนที่ไม่ใช่สมาชิก ห้ามเรียก (403 จาก @PreAuthorize)")
    void leave_group_as_outsider_forbidden() throws Exception {
        mvc.perform(delete(BASE + "/me", groupId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

}
