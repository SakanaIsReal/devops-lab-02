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
import com.smartsplit.smartsplitback.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GroupControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/groups";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Autowired JwtService jwtService;

    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository groupMembers;

    @MockitoBean
    FileStorageService storage; 

    long adminId;
    long ownerId;
    long newOwnerId;
    long memberId;
    long outsiderId;
    long groupId;
    String initialCover = "https://cdn.example/old.png";

    
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

        var newOwner = new User();
        newOwner.setEmail("newowner@example.com");
        newOwner.setUserName("NewOwner");
        newOwner.setPasswordHash("{noop}x");
        newOwner.setRole(Role.USER);
        newOwnerId = users.save(newOwner).getId();

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
        g.setCoverImageUrl(initialCover);
        groupId = groups.save(g).getId();

        // ใส่สมาชิก: owner + member
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

        // mock storage defaults
        when(storage.save(
                ArgumentMatchers.any(), // MultipartFile
                anyString(), anyString(),
                any(HttpServletRequest.class)
        )).thenReturn("https://cdn.example/new.png");

        when(storage.deleteByUrl(anyString())).thenReturn(true);
    }

    // -------------------- LIST --------------------

    @Test
    @DisplayName("GET /api/groups (ownerUserId=null) → ต้องล็อกอินและเห็นเฉพาะกลุ่มที่เราเป็น owner")
    void list_my_owned_groups() throws Exception {
        // กลุ่มของ ownerId ควรเห็นได้
        mvc.perform(get(BASE).with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUserId").value(ownerId));

        // ไม่ใส่ token → 401
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/groups?ownerUserId=X → Admin เท่านั้น")
    void list_by_owner_as_admin_only() throws Exception {
        // admin ขอ list ของ ownerId → 200
        mvc.perform(get(BASE).param("ownerUserId", String.valueOf(ownerId)).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUserId").value(ownerId));

        // user ธรรมดาขอลิสต์คนอื่น → 403
        mvc.perform(get(BASE).param("ownerUserId", String.valueOf(ownerId)).with(asUser(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/groups/mine → คืนกลุ่มที่เราเป็นสมาชิก (owner เองก็ต้องอยู่ใน list)")
    void list_mine_membership() throws Exception {
        // owner เป็นสมาชิกอยู่แล้ว → เห็น G1
        mvc.perform(get(BASE + "/mine").with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId));

        // member ก็เห็น G1
        mvc.perform(get(BASE + "/mine").with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId));
    }

    // -------------------- GET ONE --------------------

    @Test
    @DisplayName("GET /api/groups/{id} → เฉพาะสมาชิกกลุ่มเท่านั้น (member/owner=200, outsider=403), ไม่พบ=404")
    void get_group_member_only() throws Exception {
        // member ดูได้
        mvc.perform(get(BASE + "/{id}", groupId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.memberCount").value(2));

        // outsider → 403
        mvc.perform(get(BASE + "/{id}", groupId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());

        // ไม่พบ → 404 (ต้องเป็นสมาชิก/สิทธิ์พอถึงจะมาเจอ 404; ใช้ owner ซึ่งผ่าน @PreAuthorize)
        long notFoundGroup = 9_999_999L;
        mvc.perform(get(BASE + "/{id}", notFoundGroup).with(asUser(ownerId)))
                .andExpect(status().isNotFound());
    }

    // -------------------- CREATE (multipart/json) --------------------

    @Test
    @DisplayName("POST multipart /api/groups → owner สร้างกลุ่มของตัวเองได้ (201) + อัปโหลด cover")
    void create_multipart_owner_self() throws Exception {
        var groupJson = """
            {"ownerUserId": %d, "name": "G2"}
            """.formatted(ownerId).getBytes();

        var groupPart = new org.springframework.mock.web.MockMultipartFile(
                "group", "group.json", MediaType.APPLICATION_JSON_VALUE, groupJson);

        var cover = new org.springframework.mock.web.MockMultipartFile(
                "cover", "c.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1,2,3});

        mvc.perform(multipart(BASE)
                        .file(groupPart).file(cover)
                        .with(asUser(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(ownerId))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example/new.png"))
                .andExpect(jsonPath("$.memberCount").value(1)); // owner ถูกเพิ่มเป็นสมาชิก

        verify(storage, atLeastOnce()).save(any(), eq("group-covers"), startsWith("group-"), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("POST multipart /api/groups → user จะสร้างให้คนอื่นโดยไม่ใช่ admin → 403")
    void create_multipart_user_for_other_forbidden() throws Exception {
        var body = """
            {"ownerUserId": %d, "name": "Nope"}
            """.formatted(newOwnerId).getBytes();

        var groupPart = new org.springframework.mock.web.MockMultipartFile(
                "group", "group.json", MediaType.APPLICATION_JSON_VALUE, body);

        mvc.perform(multipart(BASE).file(groupPart).with(asUser(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST json /api/groups → admin สร้างให้คนอื่นได้ (201), owner ไม่พบ → 404, ไม่มี owner → 400")
    void create_json_admin_and_edge_cases() throws Exception {
        // admin สร้างให้ newOwner
        var ok = """
            {"ownerUserId": %d, "name": "AdminGroup", "coverImageUrl": "preset.png"}
            """.formatted(newOwnerId);

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(newOwnerId))
                .andExpect(jsonPath("$.name").value("AdminGroup"))
                .andExpect(jsonPath("$.coverImageUrl").value("preset.png"))
                .andExpect(jsonPath("$.memberCount").value(1));

        // owner ไม่พบ → 404
        var notFound = """
            {"ownerUserId": 999999, "name": "X"}
            """;
        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notFound))
                .andExpect(status().isNotFound());

        // ไม่มี ownerUserId → 400
        var body = om.writeValueAsString(Map.of("name", "NoOwner"));

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

    }

    // -------------------- UPDATE (json/multipart) --------------------

    @Test
    @DisplayName("PUT json /api/groups/{id} → owner (หรือ policy อนุญาต) แก้ชื่อ และโอน owner ได้ (owner ใหม่ถูกเพิ่มเป็นสมาชิก)")
    void update_json_change_name_and_owner() throws Exception {
        var body = """
            {"name": "G1-upd", "ownerUserId": %d}
            """.formatted(newOwnerId);

        mvc.perform(put(BASE + "/{id}", groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("G1-upd"))
                .andExpect(jsonPath("$.ownerUserId").value(newOwnerId));

        // owner ใหม่ถูกเพิ่มเป็นสมาชิกอัตโนมัติ
        assertThat(groupMembers.existsByGroup_IdAndUser_Id(groupId, newOwnerId)).isTrue();
    }

    @Test
    @DisplayName("PUT json /api/groups/{id} → เปลี่ยน owner เป็น user ที่ไม่มีอยู่ → 404")
    void update_json_owner_not_found() throws Exception {
        var body = """
            {"ownerUserId": 999999}
            """;
        mvc.perform(put(BASE + "/{id}", groupId)
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT multipart /api/groups/{id} → เปลี่ยนชื่อ + อัปโหลด cover ใหม่ (ลบของเก่าและบันทึกของใหม่)")
    void update_multipart_change_cover() throws Exception {
        var groupJson = """
            {"name": "G1-newname"}
            """.getBytes();

        var groupPart = new MockMultipartFile(
                "group", "group.json", MediaType.APPLICATION_JSON_VALUE, groupJson);

        var cover = new MockMultipartFile(
                "cover", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[]{9,9,9});

        mvc.perform(multipart(BASE + "/{id}", groupId)
                        .file(groupPart).file(cover)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("G1-newname"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example/new.png"));

        // ต้องลบรูปเดิม และบันทึกรูปใหม่
        verify(storage, atLeastOnce()).deleteByUrl(initialCover);
        verify(storage, atLeastOnce()).save(any(), eq("group-covers"), startsWith("group-"), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("PUT json /api/groups/{id} → outsider (ไม่ใช่ผู้จัดการกลุ่ม) โดน 403")
    void update_json_forbidden_for_outsider() throws Exception {
        var body = om.writeValueAsString(Map.of("name", "NoOwner"));

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

    }

    // -------------------- DELETE --------------------

    @Test
    @DisplayName("DELETE /api/groups/{id} → ผู้จัดการกลุ่มลบได้ (204) และไฟล์ cover ถูกลบ")
    void delete_group_ok() throws Exception {
        mvc.perform(delete(BASE + "/{id}", groupId).with(asUser(ownerId)))
                .andExpect(status().isNoContent());

        // ลบออกจากฐานข้อมูลแล้ว
        assertThat(groups.findById(groupId)).isEmpty();

        // ลบไฟล์ cover เดิม
        verify(storage, atLeastOnce()).deleteByUrl(initialCover);
    }

    @Test
    @DisplayName("DELETE /api/groups/{id} → ไม่พบกลุ่ม → 404")
    void delete_group_not_found() throws Exception {
        mvc.perform(delete(BASE + "/{id}", 999999L).with(asUser(ownerId)))
                .andExpect(status().isNotFound());
    }
}
