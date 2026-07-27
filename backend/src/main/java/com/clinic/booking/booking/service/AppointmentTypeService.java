package com.clinic.booking.booking.service;

import com.clinic.booking.booking.dto.AppointmentTypeResponse;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppointmentTypeService {

    private final AppointmentTypeRepository appointmentTypeRepository;

    public AppointmentTypeService(AppointmentTypeRepository appointmentTypeRepository) {
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    public List<AppointmentTypeResponse> listActive() {
        return appointmentTypeRepository.findByActiveTrueOrderById().stream()
                .map(AppointmentTypeResponse::from)
                .toList();
    }
}
