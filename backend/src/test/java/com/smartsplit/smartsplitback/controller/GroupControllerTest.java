package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.GroupDto;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.security.SecurityFacade;
import com.smartsplit.smartsplitback.service.FileStorageService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(controllers = GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GroupControllerTest.MethodSecurityTestConfig.class, GroupControllerTest.MethodSecurityExceptionAdvice.class})
class GroupControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @RestControllerAdvice
    static class MethodSecurityExceptionAdvice {
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

    @MockitoBean GroupService groups;
    @MockitoBean UserService users;
    @MockitoBean GroupMemberService members;
    @MockitoBean SecurityFacade sec;
    @MockitoBean FileStorageService storage;

    private Group group(Long id, long ownerId, String name, String cover) {
        Group g = new Group();
        var u = new User();
        u.setId(ownerId);
        g.setId(id);
        g.setOwner(u);
        g.setName(name);
        g.setCoverImageUrl(cover);
        return g;
    }

    private User user(long id) {
        var u = new User();
        u.setId(id);
        return u;
    }

    // ---------- LIST ----------
    @Test
    @DisplayName("GET /api/groups?ownerUserId (ADMIN only) -> 200 when admin, 403 otherwise")
    void list_by_owner_requires_admin() throws Exception {
        when(perm.isAdmin()).thenReturn(false);
        mockMvc.perform(get("/api/groups").param("ownerUserId", "9"))
                .andExpect(status().isForbidden());

        when(perm.isAdmin()).thenReturn(true);
        when(groups.listByOwner(9L)).thenReturn(List.of());
        mockMvc.perform(get("/api/groups").param("ownerUserId", "9"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/groups (no ownerUserId) -> 401 when sec.currentUserId=null; 200 when logged in")
    void list_mine_as_owner() throws Exception {
       
        when(sec.currentUserId()).thenReturn(null);
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        when(sec.currentUserId()).thenReturn(77L);
        when(groups.listByOwner(77L)).thenReturn(List.of());
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- LIST /mine ----------
    @Test
    @WithMockUser
    @DisplayName("GET /api/groups/mine -> 401 when sec.currentUserId=null; 200 when logged in")
    void listMine() throws Exception {
        when(sec.currentUserId()).thenReturn(null);
        mockMvc.perform(get("/api/groups/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        when(sec.currentUserId()).thenReturn(77L);
        when(groups.listByMember(77L)).thenReturn(List.of());
        mockMvc.perform(get("/api/groups/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- GET /{id} (must be member) ----------
    @Test
    @WithMockUser
    @DisplayName("GET /api/groups/{id} -> 403 when not a member; 404 when not found; 200 when OK")
    void get_group_authorization_and_notfound() throws Exception {
        when(groups.get(55L)).thenReturn(group(55L, 9L, "Trip", null));
        when(perm.isGroupMember(55L)).thenReturn(false);
        mockMvc.perform(get("/api/groups/55"))
                .andExpect(status().isForbidden());

        when(perm.isGroupMember(55L)).thenReturn(true);
        when(groups.get(55L)).thenReturn(null);
        mockMvc.perform(get("/api/groups/55"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        var g = group(55L, 9L, "Trip", null);
        when(groups.get(55L)).thenReturn(g);
        when(members.countMembers(55L)).thenReturn(3L);
        mockMvc.perform(get("/api/groups/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.ownerUserId").value(9))
                .andExpect(jsonPath("$.name").value("Trip"))
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    // ---------- POST create (JSON) ----------
    @Test
    @WithMockUser
    @DisplayName("POST /api/groups (JSON) -> 401 when sec.currentUserId=null; 403 when not admin and owner!=me; 201 when ok")
    void create_json_authz() throws Exception {
        var in = new GroupDto(null, 50L, "Team", null, 0L);
        var body = objectMapper.writeValueAsString(in);

        when(sec.currentUserId()).thenReturn(null);
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        when(sec.currentUserId()).thenReturn(77L);
        when(perm.isAdmin()).thenReturn(false);
        // กันหลุดไป 404
        when(users.get(50L)).thenReturn(user(50L));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        when(sec.currentUserId()).thenReturn(50L);
        when(perm.isAdmin()).thenReturn(false);
        when(users.get(50L)).thenReturn(user(50L));
        when(groups.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            if (g.getId()==null) g.setId(101L);
            return g;
        });
        when(members.exists(101L, 50L)).thenReturn(true);
        when(members.countMembers(101L)).thenReturn(1L);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.ownerUserId").value(50))
                .andExpect(jsonPath("$.name").value("Team"));
    }

    // ---------- POST create (multipart) ----------
    @Test
    @WithMockUser
    @DisplayName("POST /api/groups (multipart) -> 201 when owner=self and cover uploaded; 403 when owner!=self && !admin")
    void create_multipart_authz_and_cover() throws Exception {
        when(sec.currentUserId()).thenReturn(77L);
        when(perm.isAdmin()).thenReturn(false);

        MockMultipartFile groupPart = new MockMultipartFile(
                "group", "", "application/json",
                objectMapper.writeValueAsBytes(new GroupDto(null, 50L, "Team", null, 0L))
        );
        MockMultipartFile cover = new MockMultipartFile("cover", "a.png", "image/png", new byte[]{1,2});

        // ป้องกันหลุดไป 404
        when(users.get(50L)).thenReturn(user(50L));

        mockMvc.perform(multipart("/api/groups").file(groupPart).file(cover))
                .andExpect(status().isForbidden());

        when(sec.currentUserId()).thenReturn(50L);
        when(users.get(50L)).thenReturn(user(50L));
        when(groups.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            if (g.getId()==null) g.setId(201L);
            return g;
        });
        when(storage.save(any(), eq("group-covers"), eq("group-201"), any())).thenReturn("http://files/group-201.png");
        when(members.exists(201L, 50L)).thenReturn(true);
        when(members.countMembers(201L)).thenReturn(1L);

        mockMvc.perform(multipart("/api/groups").file(groupPart).file(cover))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.ownerUserId").value(50))
                .andExpect(jsonPath("$.name").value("Team"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("group-201.png")));
    }

    // ---------- PUT update (JSON) requires canManageGroup ----------
    @Test
    @DisplayName("PUT /api/groups/{id} (JSON) -> 403 when cannot manage; 404 when not found; 200 when ok")
    void update_json_authz_and_notfound() throws Exception {
        var in = new GroupDto(null, null, "NewName", null, 0L);
        var body = objectMapper.writeValueAsString(in);

        when(perm.canManageGroup(55L)).thenReturn(false);
        mockMvc.perform(put("/api/groups/55").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        when(perm.canManageGroup(55L)).thenReturn(true);
        when(groups.get(55L)).thenReturn(null);
        mockMvc.perform(put("/api/groups/55").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        var g = group(55L, 9L, "Old", null);
        when(groups.get(55L)).thenReturn(g);
        when(groups.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.countMembers(55L)).thenReturn(3L);

        mockMvc.perform(put("/api/groups/55").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    // ---------- PUT update (multipart) requires canManageGroup ----------
    @Test
    @DisplayName("PUT /api/groups/{id} (multipart) -> 403 when cannot manage; 404 when not found; 200 when ok")
    void update_multipart_authz_and_notfound() throws Exception {
        var groupPart = new MockMultipartFile("group", "", "application/json",
                objectMapper.writeValueAsBytes(new GroupDto(null, null, "Newer", null, 0L)));
        var cover = new MockMultipartFile("cover", "b.png", "image/png", new byte[]{1,2});

        when(perm.canManageGroup(55L)).thenReturn(false);
        mockMvc.perform(multipart("/api/groups/55").file(groupPart).file(cover).with(req -> {
                    req.setMethod("PUT"); return req;
                }))
                .andExpect(status().isForbidden());

        when(perm.canManageGroup(55L)).thenReturn(true);
        when(groups.get(55L)).thenReturn(null);
        mockMvc.perform(multipart("/api/groups/55").file(groupPart).file(cover).with(req -> {
                    req.setMethod("PUT"); return req;
                }))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        var g = group(55L, 9L, "Old", "http://old");
        when(groups.get(55L)).thenReturn(g);
        // ไม่ stub deleteByUrl เพราะไม่ใช่ void (กัน Mockito error)
        when(storage.save(any(), eq("group-covers"), eq("group-55"), any())).thenReturn("http://new");
        when(groups.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.countMembers(55L)).thenReturn(4L);

        mockMvc.perform(multipart("/api/groups/55").file(groupPart).file(cover).with(req -> {
                    req.setMethod("PUT"); return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Newer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http://new")));
    }

    // ---------- DELETE requires canManageGroup ----------
    @Test
    @WithMockUser
    @DisplayName("DELETE /api/groups/{id} -> 403 when cannot manage; 404 when not found; 204 when ok")
    void delete_authz_and_notfound() throws Exception {

        when(groups.get(55L)).thenReturn(group(55L, 9L, "X", "http://old"));
        when(perm.canManageGroup(55L)).thenReturn(false);

        mockMvc.perform(delete("/api/groups/55"))
                .andExpect(status().isForbidden());


        when(groups.get(55L)).thenReturn(null);
        when(perm.canManageGroup(55L)).thenReturn(true);

        mockMvc.perform(delete("/api/groups/55"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Group not found"));

        when(groups.get(55L)).thenReturn(group(55L, 9L, "X", "http://old"));
        when(perm.canManageGroup(55L)).thenReturn(true);

        mockMvc.perform(delete("/api/groups/55"))
                .andExpect(status().isNoContent());
    }

}
