package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.dto.profesor.ActualizarProfesorRequest;
import com.centroartesmusicales.backend.dto.profesor.CrearProfesorRequest;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.repository.ProfesorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfesorService {

    private final ProfesorRepository profesorRepository;

    @Transactional
    public Profesor crear(CrearProfesorRequest request) {
        Profesor profesor = Profesor.builder()
                .nombreCompleto(request.nombreCompleto())
                .email(request.email())
                .telefono(request.telefono())
                .especialidades(request.especialidades())
                .activo(true)
                .build();
        return profesorRepository.save(profesor);
    }

    public Page<Profesor> listar(Boolean activo, Pageable pageable) {
        if (activo != null) {
            return profesorRepository.findByActivo(activo, pageable);
        }
        return profesorRepository.findAll(pageable);
    }

    public Profesor obtenerPorId(Long id) {
        return profesorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado: " + id));
    }

    @Transactional
    public Profesor actualizar(Long id, ActualizarProfesorRequest request) {
        Profesor profesor = obtenerPorId(id);
        if (request.nombreCompleto() != null && !request.nombreCompleto().isBlank()) {
            profesor.setNombreCompleto(request.nombreCompleto());
        }
        if (request.email() != null) {
            profesor.setEmail(request.email());
        }
        if (request.telefono() != null) {
            profesor.setTelefono(request.telefono());
        }
        if (request.especialidades() != null && !request.especialidades().isEmpty()) {
            profesor.setEspecialidades(request.especialidades());
        }
        if (request.activo() != null) {
            profesor.setActivo(request.activo());
        }
        Profesor guardado = profesorRepository.save(profesor);
        profesorRepository.flush(); // Fuerza la escritura inmediata en la base de datos (ver desactivar())
        return guardado;
    }

    @Transactional
    public void desactivar(Long id) {
        Profesor profesor = obtenerPorId(id);
        profesor.setActivo(false);
        profesorRepository.save(profesor);
        profesorRepository.flush(); // Fuerza la escritura inmediata en la base de datos
    }
}