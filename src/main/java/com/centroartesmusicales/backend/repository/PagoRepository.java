package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.EstadoPago;
import com.centroartesmusicales.backend.model.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByAlumno_IdAndPeriodo(Long alumnoId, YearMonth periodo);

    boolean existsByAlumno_IdAndFechaLimite(Long alumnoId, LocalDate fechaLimite);

    Optional<Pago> findByAlumno_IdAndPeriodo(Long alumnoId, YearMonth periodo);

    Optional<Pago> findFirstByAlumno_IdOrderByPeriodoDesc(Long alumnoId);

    /** Borrado en cascada al eliminar un alumno por completo (ver AlumnoService#eliminar). */
    @Modifying
    @Query("DELETE FROM Pago p WHERE p.alumno.id = :alumnoId")
    void deleteByAlumnoId(@Param("alumnoId") Long alumnoId);

    @Query("SELECT p FROM Pago p WHERE "
            + "(:alumnoId IS NULL OR p.alumno.id = :alumnoId) AND "
            + "(:estado IS NULL OR p.estado = :estado) AND "
            + "(:periodo IS NULL OR p.periodo = :periodo)")
    Page<Pago> buscar(@Param("alumnoId") Long alumnoId,
                       @Param("estado") EstadoPago estado,
                       @Param("periodo") YearMonth periodo,
                       Pageable pageable);
}
