package com.clinic.booking.booking.service;

import com.clinic.booking.booking.dto.ProviderResponse;
import com.clinic.booking.booking.repository.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;

    public ProviderService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public List<ProviderResponse> listByAppointmentType(Long appointmentTypeId) {
        return providerRepository.findActiveByAppointmentTypeId(appointmentTypeId).stream()
                .map(ProviderResponse::from)
                .toList();
    }
}
