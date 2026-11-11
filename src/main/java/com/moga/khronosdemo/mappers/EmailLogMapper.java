package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.response.EmailResponseDTO;
import com.moga.khronosdemo.entities.EmailLog;
import org.springframework.stereotype.Component;

@Component
public class EmailLogMapper {

    public EmailResponseDTO toResponse(EmailLog emailLog) {
        if (emailLog == null) {
            return null;
        }
        return new EmailResponseDTO(
                emailLog.getId(),
                emailLog.getRecipient(),
                emailLog.getSubject(),
                emailLog.getStatus(),
                emailLog.getResendId(),
                emailLog.getErrorMessage(),
                emailLog.getCreatedAt()
        );
    }
}
