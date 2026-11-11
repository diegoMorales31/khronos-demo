package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.dtos.response.EmailResponseDTO;

import java.util.List;

public interface EmailService {

    EmailResponseDTO send(EmailRequestDTO request);

    EmailResponseDTO findById(Long id);

    List<EmailResponseDTO> findAll();

    List<EmailResponseDTO> findByRecipient(String recipient);
}
