// src/main/java/com/smartsplit/smartsplitback/controller/UserController.java
package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.UserDto;
import com.smartsplit.smartsplitback.service.FileStorageService;
import com.smartsplit.smartsplitback.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService svc;
    private final FileStorageService storage;

    public UserController(UserService svc, FileStorageService storage) {
        this.svc = svc;
        this.storage = storage;
    }

    @GetMapping
    public List<UserDto> list() {
        return svc.list().stream().map(UserController::toDto).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        var u = svc.get(id);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return toDto(u);
    }

    @Operation(summary = "Create user (multipart)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(
            @Parameter(
                    description = "User JSON payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class)))
            @RequestPart("user") UserDto in,

            @Parameter(description = "Avatar image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,

            @Parameter(description = "QR image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "qr", required = false) MultipartFile qr,

            HttpServletRequest req) {


        var u = new User();
        u.setEmail(in.email());
        u.setUserName(in.userName());
        u.setPhone(in.phone());
        u = svc.create(u);


        if (avatar != null && !avatar.isEmpty()) {
            String url = storage.save(avatar, "avatars", "user-" + u.getId(), req);
            u.setAvatarUrl(url);
        }
        if (qr != null && !qr.isEmpty()) {
            String url = storage.save(qr, "qrcodes", "qr-" + u.getId(), req);
            u.setQrCodeUrl(url);
        }


        u = svc.update(u);
        return toDto(u);
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createJson(@RequestBody UserDto in) {
        var u = new User();
        u.setEmail(in.email());
        u.setUserName(in.userName());
        u.setPhone(in.phone());
        u.setAvatarUrl(in.avatarUrl());
        u.setQrCodeUrl(in.qrCodeUrl());
        return toDto(svc.create(u));
    }


    @PreAuthorize("@perm.isAdmin() or @perm.isSelf(#id)")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserDto updateJson(@PathVariable Long id, @RequestBody UserDto in) {
        var u = svc.get(id);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

        if (in.email()     != null) u.setEmail(in.email());
        if (in.userName()  != null) u.setUserName(in.userName());
        if (in.phone()     != null) u.setPhone(in.phone());
        if (in.avatarUrl() != null) u.setAvatarUrl(in.avatarUrl());
        if (in.qrCodeUrl() != null) u.setQrCodeUrl(in.qrCodeUrl());

        return toDto(svc.update(u));
    }

    @Operation(summary = "Update user (multipart)")
    @PreAuthorize("@perm.isAdmin() or @perm.isSelf(#id)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto update(
            @PathVariable Long id,

            @Parameter(
                    description = "User JSON payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class)))
            @RequestPart("user") UserDto in,

            @Parameter(description = "Avatar image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,

            @Parameter(description = "QR image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "qr", required = false) MultipartFile qr,

            HttpServletRequest req) {

        var u = svc.get(id);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");


        if (in.email()    != null) u.setEmail(in.email());
        if (in.userName() != null) u.setUserName(in.userName());
        if (in.phone()    != null) u.setPhone(in.phone());


        if (avatar != null && !avatar.isEmpty()) {
            storage.deleteByUrl(u.getAvatarUrl());
            String url = storage.save(avatar, "avatars", "user-" + u.getId(), req);
            u.setAvatarUrl(url);
        }
        if (qr != null && !qr.isEmpty()) {
            storage.deleteByUrl(u.getQrCodeUrl());
            String url = storage.save(qr, "qrcodes", "qr-" + u.getId(), req);
            u.setQrCodeUrl(url);
        }

        return toDto(svc.update(u));
    }


    @PreAuthorize("@perm.isAdmin() or @perm.isSelf(#id)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        var u = svc.get(id);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

        storage.deleteByUrl(u.getAvatarUrl());
        storage.deleteByUrl(u.getQrCodeUrl());

        svc.delete(id);
    }


    private static UserDto toDto(User u) {
        return new UserDto(
                u.getId(),
                u.getEmail(),
                u.getUserName(),
                u.getPhone(),
                u.getAvatarUrl(),
                u.getQrCodeUrl()
        );
    }
}
