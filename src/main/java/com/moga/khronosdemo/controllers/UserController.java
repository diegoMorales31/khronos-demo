package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.PasswordChangeRequestDTO;
import com.moga.khronosdemo.dtos.request.PasswordResetRequestDTO;
import com.moga.khronosdemo.dtos.request.PlanRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.PlanResponseDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.Role;
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

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN')")
    // plan is only for owners, and BASIC when left out
    public UserResponseDTO createUser(@Valid @RequestBody UserRequestDTO request, @RequestParam Role role,
                                      @RequestParam(required = false) Plan plan,
                                      @AuthenticationPrincipal SecurityUser principal) {
        return userService.createUser(request, role, plan, principal.getId());
    }

    @PostMapping("/create/worker")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'OWNER')")
    public UserResponseDTO createWorker(@Valid @RequestBody UserRequestDTO request,
                                        @AuthenticationPrincipal SecurityUser principal) {
        return userService.createWorker(request, principal.getId());
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<UserResponseDTO> findAll() {
        return userService.findAll();
    }

    // The accounts of everyone who works at the establishment, instead of one request per worker
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<UserResponseDTO> findByEstablishment(@PathVariable Long establishmentId) {
        return userService.findByEstablishment(establishmentId);
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
    public void changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequestDTO request) {
        userService.changePassword(id, request);
    }

    // Admins can reset anyone but another admin; owners only the workers of their establishments
    @PutMapping("/{id}/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("(hasRole('PLATFORM_ADMIN') and !@access.isPlatformAdmin(#id)) or (hasRole('OWNER') and @access.ownsWorker(#id))")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequestDTO request) {
        userService.resetPassword(id, request);
    }

    // The owner's plan, its worker limit and how many active workers they have now
    @GetMapping("/{id}/plan")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('OWNER') and @access.isSelf(#id))")
    public PlanResponseDTO findPlan(@PathVariable Long id) {
        return userService.findPlan(id);
    }

    // Only the platform admin changes plans; it applies from the owner's next request
    @PutMapping("/{id}/plan")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlanResponseDTO changePlan(@PathVariable Long id, @Valid @RequestBody PlanRequestDTO request) {
        return userService.changePlan(id, request.plan());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
