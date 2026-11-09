package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ChangePasswordRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.entities.enums.Role;

import java.util.List;

public interface UserService {

    UserResponseDTO createWorker(UserRequestDTO request);

    UserResponseDTO createUser(UserRequestDTO request, Role role);

    UserResponseDTO findById(Long id);

    List<UserResponseDTO> findAll();

    UserResponseDTO update(Long id, UserUpdateRequestDTO request);

    void changePassword(Long id, ChangePasswordRequestDTO request);

    void delete(Long id);
}
