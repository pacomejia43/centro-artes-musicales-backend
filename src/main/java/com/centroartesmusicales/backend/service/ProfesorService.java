package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.dto.profesor.ActualizarProfesorRequest;
import com.centroartesmusicales.backend.dto.profesor.CrearProfesorRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.repository.ClaseRepository;
import com.centroartesmusicales.backend.repository.ProfesorRepository;
import com.centroartesmusicales.backend.repository.SolicitudReagendacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfesorService {

    private final ProfesorRepository profesorRepository;
    private final ClaseRepository claseRepository;
    private final SolicitudReagendacionRepository solicitudReagendacionRepository;

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
        profesorRepository.flush(); // Fuerza la escritura inmediata en la base de datos
        return guardado;
    }

    /**
     * Borra al profesor de forma permanente. No hay ON DELETE CASCADE para clase.profesor_id ni
     * para solicitud_reagendacion.profesor_propuesto_id (ver migraciones db/migration), así que antes
     * de borrar:
     * 1) si se indicó reemplazoId, todas sus clases se reasignan a ese otro profesor; si no, quedan
     *    sin profesor asignado (columna nullable desde V13) a la espera de que el admin les asigne uno,
     * 2) cualquier solicitud de reagendación pendiente que lo proponía como profesor se desvincula
     *    (vuelve a "mismo profesor que la clase original").
     * Para solo ocultarlo sin tocar su historial, usar actualizar() con activo=false.
     */
    @Transactional
    public void eliminar(Long id, Long reemplazoId) {
        Profesor profesor = obtenerPorId(id);

        Profesor reemplazo = null;
        if (reemplazoId != null) {
            if (reemplazoId.equals(id)) {
                throw new BusinessRuleException("El profesor de reemplazo debe ser distinto al que se está eliminando");
            }
            reemplazo = obtenerPorId(reemplazoId);
        }

        solicitudReagendacionRepository.desvincularProfesorPropuesto(id);
        if (reemplazo != null) {
            claseRepository.reasignarProfesor(id, reemplazo);
        } else {
            claseRepository.vaciarProfesor(id);
        }

        profesorRepository.delete(profesor);
    }
}