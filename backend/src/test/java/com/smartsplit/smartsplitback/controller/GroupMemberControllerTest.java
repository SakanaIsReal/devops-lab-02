package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.GroupMember;
import com.smartsplit.smartsplitback.model.GroupMemberId;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.GroupMemberDto;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.security.SecurityFacade;
import com.smartsplit.smartsplitback.service.GroupMemberService;
import com.smartsplit.smartsplitback.service.GroupService;
import com.smartsplit.smartsplitback.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(controllers = GroupMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GroupMemberControllerTest.MethodSecurityConfig.class, GroupMemberControllerTest.MethodSecurityAdvice.class})
class GroupMemberControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @RestControllerAdvice
    static class MethodSecurityAdvice {
        @ExceptionHandler(AuthorizationDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handle() {}
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    @MockitoBean GroupMemberService members;
    @MockitoBean GroupService groups;
    @MockitoBean UserService users;
    @MockitoBean SecurityFacade security;

    private Group group(long id, long ownerId, String name) {
        Group g = new Group();
        g.setId(id);
        var u = new User(); u.setId(ownerId);
        g.setOwner(u);
        g.setName(name);
        return g;
    }

    private User user(long id) { var u = new User(); u.setId(id); return u; }

    private GroupMember gm(long gid, long uid) {
        GroupMember m = new GroupMember();
        m.setId(new GroupMemberId(gid, uid));
        m.setGroup(group(gid, 999L, "X"));
        m.setUser(user(uid));
        return m;
    }

    // ---------- GET list ----------
    @Test
    @DisplayName("GET /api/groups/{gid}/members -> 403 เมื่อไม่ใช่สมาชิก, 404 เมื่อไม่มีกลุ่ม, 200 เมื่อสำเร็จ")
    void list_authz_and_notfound_and_ok() throws Exception {
        // ไม่ใช่สมาชิก
        when(perm.isGroupMember(55L)).thenReturn(false);
        mockMvc.perform(get("/api/groups/55/members"))
                .andExpect(status().isForbidden());

        // เป็นสมาชิก แต่ไม่มีกลุ่ม
        when(perm.isGroupMember(55L)).thenReturn(true);
        when(groups.get(55L)).thenReturn(null);
        mockMvc.perform(get("/api/groups/55/members"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        // ปกติ: 200 + ลิสต์
        when(groups.get(55L)).thenReturn(group(55L, 7L, "Trip"));
        when(members.listByGroup(55L)).thenReturn(List.of(gm(55L, 10L), gm(55L, 11L)));

        mockMvc.perform(get("/api/groups/55/members"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].groupId").value(55))
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[1].userId").value(11));
    }

    // ---------- POST add ----------
    @Test
    @DisplayName("POST /api/groups/{gid}/members -> 403 เมื่อไม่มีสิทธิ์, 404 group/user, 409 duplicate, 400 validation, 201 OK")
    void add_authz_and_errors_and_ok() throws Exception {
        // 403 ไม่มีสิทธิ์
        when(perm.canManageMembers(55L)).thenReturn(false);
        var body = objectMapper.writeValueAsString(new GroupMemberDto(55L, 99L));
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // เปิดสิทธิ์
        when(perm.canManageMembers(55L)).thenReturn(true);

        // 400 userId null
        var bad = objectMapper.writeValueAsString(new GroupMemberDto(55L, null));
        when(groups.get(55L)).thenReturn(group(55L, 7L, "Trip")); // กัน 404 กลุ่ม
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("userId is required"));

        // 404 group not found
        when(groups.get(55L)).thenReturn(null);
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        // 404 user not found
        when(groups.get(55L)).thenReturn(group(55L, 7L, "Trip"));
        when(users.get(99L)).thenReturn(null);
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        // 409 already a member
        when(users.get(99L)).thenReturn(user(99L));
        when(members.exists(55L, 99L)).thenReturn(true);
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(status().reason("Already a member"));

        // 201 created
        when(members.exists(55L, 99L)).thenReturn(false);
        mockMvc.perform(post("/api/groups/55/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(55))
                .andExpect(jsonPath("$.userId").value(99));
    }

    // ---------- DELETE remove ----------
    @Test
    @DisplayName("DELETE /api/groups/{gid}/members/{uid} -> 403 เมื่อไม่มีสิทธิ์, 404 เมื่อไม่พบความเป็นสมาชิก, 204 เมื่อสำเร็จ")
    void remove_authz_notfound_ok() throws Exception {
        // 403
        when(perm.canManageMembers(55L)).thenReturn(false);
        mockMvc.perform(delete("/api/groups/55/members/99"))
                .andExpect(status().isForbidden());

        // 404
        when(perm.canManageMembers(55L)).thenReturn(true);
        when(members.exists(55L, 99L)).thenReturn(false);
        mockMvc.perform(delete("/api/groups/55/members/99"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Membership not found"));

        // 204
        when(members.exists(55L, 99L)).thenReturn(true);
        mockMvc.perform(delete("/api/groups/55/members/99"))
                .andExpect(status().isNoContent());
    }

    // ---------- DELETE /me (ใหม่) ----------
    @Test
    @DisplayName("DELETE /api/groups/{gid}/members/me -> 204 เมื่อสมาชิกธรรมดาออกจากกลุ่มได้")
    void leave_me_ok_for_member() throws Exception {
        long gid = 77L;
        long currentUid = 100L;

        // ผ่าน @PreAuthorize
        when(perm.isGroupMember(gid)).thenReturn(true);

        // ensureGroup OK
        when(groups.get(gid)).thenReturn(group(gid, 999L, "Trip"));

        // current user id
        when(security.currentUserId()).thenReturn(currentUid);

        // เป็นสมาชิกอยู่
        when(members.exists(gid, currentUid)).thenReturn(true);

        mockMvc.perform(delete("/api/groups/{gid}/members/me", gid))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/groups/{gid}/members/me -> 400 เมื่อ owner พยายามออกเอง")
    void leave_me_bad_request_for_owner() throws Exception {
        long gid = 77L;
        long ownerUid = 7L;

        when(perm.isGroupMember(gid)).thenReturn(true);
        when(groups.get(gid)).thenReturn(group(gid, ownerUid, "Trip"));
        when(security.currentUserId()).thenReturn(ownerUid);
        when(members.exists(gid, ownerUid)).thenReturn(true);

        // Service ปกติจะเช็คและโยน 400 เอง; mock ให้สอดคล้อง
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot remove themselves from the group"))
                .when(members).delete(gid, ownerUid);

        mockMvc.perform(delete("/api/groups/{gid}/members/me", gid))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Owner cannot remove themselves from the group"));
    }

    @Test
    @DisplayName("DELETE /api/groups/{gid}/members/me -> 403 เมื่อคนที่ไม่ใช่สมาชิกพยายามออก")
    void leave_me_forbidden_for_outsider() throws Exception {
        long gid = 77L;
        when(perm.isGroupMember(gid)).thenReturn(false);

        mockMvc.perform(delete("/api/groups/{gid}/members/me", gid))
                .andExpect(status().isForbidden());
    }
}
