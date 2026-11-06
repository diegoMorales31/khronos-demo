package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ChangePasswordRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;

import java.util.List;

public interface UserService {

    UserResponseDTO create(UserRequestDTO request);

    UserResponseDTO createOwner(UserRequestDTO request);

    UserResponseDTO findById(Long id);

    List<UserResponseDTO> findAll();

    UserResponseDTO update(Long id, UserUpdateRequestDTO request);

    void changePassword(Long id, ChangePasswordRequestDTO request);

    void delete(Long id);
}
