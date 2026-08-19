package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudReagendacionRepository extends JpaRepository<SolicitudReagendacion, Long> {

    boolean existsByClase_IdAndEstado(Long claseId, EstadoSolicitud estado);

    boolean existsByClase_IdOrClaseNueva_Id(Long claseId, Long claseNuevaId);

    Page<SolicitudReagendacion> findByClase_Alumno_Id(Long alumnoId, Pageable pageable);

    Page<SolicitudReagendacion> findByEstado(EstadoSolicitud estado, Pageable pageable);

    /** Borrado en cascada al eliminar un alumno por completo (ver AlumnoService#eliminar). */
    @Modifying
    @Query("DELETE FROM SolicitudReagendacion s WHERE s.clase.alumno.id = :alumnoId OR s.claseNueva.alumno.id = :alumnoId")
    void deleteByAlumnoId(@Param("alumnoId") Long alumnoId);

    /** Desvincula al profesor propuesto de cualquier solicitud al eliminar ese profesor —
     *  equivale a "mantener el mismo profesor que la clase original" (ver ProfesorService#eliminar). */
    @Modifying
    @Query("UPDATE SolicitudReagendacion s SET s.profesorPropuesto = NULL WHERE s.profesorPropuesto.id = :profesorId")
    void desvincularProfesorPropuesto(@Param("profesorId") Long profesorId);
}
