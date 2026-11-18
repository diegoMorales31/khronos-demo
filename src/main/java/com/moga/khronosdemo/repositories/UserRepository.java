package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    // Signing in, and every request with a token, needs the account and, for a worker, whether they are
    // active. One query reads both; for an account that is not a worker, workerActive is null.
    @Query("""
            select u as account, w.isActive as workerActive
            from User u left join Worker w on w.userId = u.id
            where u.id = :id
            """)
    Optional<AccountWithStatus> findAccountById(@Param("id") Long id);

    @Query("""
            select u as account, w.isActive as workerActive
            from User u left join Worker w on w.userId = u.id
            where u.username = :username
            """)
    Optional<AccountWithStatus> findAccountByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    @Query("select w.user from Worker w where w.establishment.id = :establishmentId order by w.userId")
    List<User> findWorkersOfEstablishment(@Param("establishmentId") Long establishmentId);

    interface AccountWithStatus {

        User getAccount();

        Boolean getWorkerActive();
    }
}
