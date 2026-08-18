package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudReagendacionRepository extends JpaRepository<SolicitudReagendacion, Long> {

    boolean existsByClase_IdAndEstado(Long claseId, EstadoSolicitud estado);

    boolean existsByClase_IdOrClaseNueva_Id(Long claseId, Long claseNuevaId);

    Page<SolicitudReagendacion> findByClase_Alumno_Id(Long alumnoId, Pageable pageable);

    Page<SolicitudReagendacion> findByEstado(EstadoSolicitud estado, Pageable pageable);
}
