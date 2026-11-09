package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ChangePasswordRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.UserMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final EstablishmentRepository establishmentRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, WorkerRepository workerRepository,
                           EstablishmentRepository establishmentRepository, UserMapper userMapper,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.workerRepository = workerRepository;
        this.establishmentRepository = establishmentRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponseDTO createUser(UserRequestDTO request, Role role) {
        return createWithRole(request, role);
    }

    @Override
    public UserResponseDTO createWorker(UserRequestDTO request) {
        return createWithRole(request, Role.WORKER);
    }

    private UserResponseDTO createWithRole(UserRequestDTO request, Role role) {

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("Username already in use");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already in use");
        }

        if (request.phoneNumber() != null && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessRuleException("Phone number already in use");
        }

        User user = userMapper.toEntity(request, role);

        return userMapper.toResponse(userRepository.save(user));

    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Override
    public UserResponseDTO update(Long id, UserUpdateRequestDTO request) {
        User user = getUser(id);
        if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new BusinessRuleException("Username already in use");
        }
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new BusinessRuleException("Email already in use");
        }
        if (request.phoneNumber() != null && userRepository.existsByPhoneNumberAndIdNot(request.phoneNumber(), id)) {
            throw new BusinessRuleException("Phone number already in use");
        }
        userMapper.updateEntity(user, request);
        return userMapper.toResponse(user);
    }

    @Override
    public void changePassword(Long id, ChangePasswordRequestDTO request) {

        User user = getUser(id);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));

    }

    @Override
    public void delete(Long id) {

        User user = getUser(id);
        if (user.getRole() == Role.PLATFORM_ADMIN) {
            throw new BusinessRuleException("Platform admins cannot be deleted through the API");
        }

        if (user.getRole() == Role.OWNER) {
            establishmentRepository.deleteAll(establishmentRepository.findByOwner_Id(id));
        }

        workerRepository.findById(id).ifPresentOrElse(
                workerRepository::delete,
                () -> userRepository.delete(user)
        );

    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
