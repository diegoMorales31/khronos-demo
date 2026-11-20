package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.PasswordChangeRequestDTO;
import com.moga.khronosdemo.dtos.request.PasswordResetRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.PlanResponseDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.Role;

import java.util.List;

public interface UserService {

    UserResponseDTO createWorker(UserRequestDTO request, Long creatorId);

    // plan is for owners only, who get BASIC when it is null
    UserResponseDTO createUser(UserRequestDTO request, Role role, Plan plan, Long creatorId);

    UserResponseDTO findById(Long id);

    List<UserResponseDTO> findAll();

    List<UserResponseDTO> findByEstablishment(Long establishmentId);

    UserResponseDTO update(Long id, UserUpdateRequestDTO request);

    void changePassword(Long id, PasswordChangeRequestDTO request);

    void resetPassword(Long id, PasswordResetRequestDTO request);

    void delete(Long id);

    PlanResponseDTO findPlan(Long ownerId);

    PlanResponseDTO changePlan(Long ownerId, Plan plan);
}
