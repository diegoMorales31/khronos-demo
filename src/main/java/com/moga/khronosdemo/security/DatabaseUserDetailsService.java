package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.UserRepository.AccountWithStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Runs on every request with a token, so each lookup is a single query (see UserRepository.findAccountById)
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findAccountByUsername(username)
                .map(DatabaseUserDetailsService::toSecurityUser)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public Optional<SecurityUser> loadUserById(Long id) {
        return userRepository.findAccountById(id).map(DatabaseUserDetailsService::toSecurityUser);
    }

    // A worker can only use their account while active; any other account always can
    private static SecurityUser toSecurityUser(AccountWithStatus found) {
        User user = found.getAccount();
        boolean enabled = !Boolean.FALSE.equals(found.getWorkerActive());
        return new SecurityUser(user.getId(), user.getUsername(), user.getPassword(), user.getRole(), enabled,
                user.getCredentialsChangedAt(), user.getPlan());
    }
}
