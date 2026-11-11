package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.dtos.response.EmailResponseDTO;
import com.moga.khronosdemo.entities.EmailLog;
import com.moga.khronosdemo.entities.enums.EmailStatus;
import com.moga.khronosdemo.exceptions.EmailDeliveryException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.EmailLogMapper;
import com.moga.khronosdemo.repositories.EmailLogRepository;
import com.moga.khronosdemo.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String RESEND_BASE_URL = "https://api.resend.com";

    private final EmailLogRepository emailLogRepository;
    private final EmailLogMapper emailLogMapper;
    private final RestClient restClient;
    private final String from;

    public EmailServiceImpl(EmailLogRepository emailLogRepository, EmailLogMapper emailLogMapper,
                            @Value("${resend.api-key}") String apiKey,
                            @Value("${resend.from}") String from,
                            @Value("${resend.base-url:" + RESEND_BASE_URL + "}") String baseUrl) {
        this.emailLogRepository = emailLogRepository;
        this.emailLogMapper = emailLogMapper;
        this.from = from;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public EmailResponseDTO send(EmailRequestDTO request) {
        EmailLog emailLog = new EmailLog(request.to(), request.subject(), EmailStatus.FAILED);
        try {
            ResendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmailRequest(from, List.of(request.to()), request.subject(), request.html()))
                    .retrieve()
                    .body(ResendEmailResponse.class);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setResendId(response != null ? response.id() : null);
            return emailLogMapper.toResponse(emailLogRepository.save(emailLog));
        } catch (RestClientResponseException ex) {
            String reason = "Resend rejected the email (" + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString();
            throw fail(emailLog, reason);
        } catch (RestClientException ex) {
            throw fail(emailLog, "Could not reach Resend: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmailResponseDTO findById(Long id) {
        return emailLogRepository.findById(id)
                .map(emailLogMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("EmailLog", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmailResponseDTO> findAll() {
        return emailLogRepository.findAll().stream().map(emailLogMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmailResponseDTO> findByRecipient(String recipient) {
        return emailLogRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream()
                .map(emailLogMapper::toResponse)
                .toList();
    }

    private EmailDeliveryException fail(EmailLog emailLog, String reason) {
        log.warn("Email to {} failed: {}", emailLog.getRecipient(), reason);
        emailLog.setErrorMessage(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        emailLogRepository.save(emailLog);
        return new EmailDeliveryException(reason);
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
    }

    private record ResendEmailResponse(String id) {
    }
}
