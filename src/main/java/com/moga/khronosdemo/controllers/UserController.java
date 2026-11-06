package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.ChangePasswordRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponseDTO me(@AuthenticationPrincipal SecurityUser principal) {
        return userService.findById(principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OWNER')")
    public UserResponseDTO create(@Valid @RequestBody UserRequestDTO request) {
        return userService.create(request);
    }

    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public UserResponseDTO createOwner(@Valid @RequestBody UserRequestDTO request) {
        return userService.createOwner(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<UserResponseDTO> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#id) or @access.ownsWorker(#id)")
    public UserResponseDTO findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#id) or @access.ownsWorker(#id)")
    public UserResponseDTO update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequestDTO request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.isSelf(#id)")
    public void changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequestDTO request) {
        userService.changePassword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#id)")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
