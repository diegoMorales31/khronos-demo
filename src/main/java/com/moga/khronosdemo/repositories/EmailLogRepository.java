package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findByRecipientOrderByCreatedAtDesc(String recipient);
}
