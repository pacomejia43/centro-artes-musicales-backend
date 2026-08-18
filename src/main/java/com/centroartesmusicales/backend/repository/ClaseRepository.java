package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.Clase;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.model.Instrumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClaseRepository extends JpaRepository<Clase, Long> {

    @Query("SELECT COUNT(c) FROM Clase c WHERE c.alumno.id = :alumnoId AND c.estado IN :estados "
            + "AND c.fechaHora >= :inicio AND c.fechaHora < :fin "
            + "AND (:excludeId IS NULL OR c.id <> :excludeId)")
    long countOcupadasEnRango(@Param("alumnoId") Long alumnoId,
                               @Param("estados") List<EstadoClase> estados,
                               @Param("inicio") LocalDateTime inicio,
                               @Param("fin") LocalDateTime fin,
                               @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(c) FROM Clase c WHERE c.alumno.id = :alumnoId AND c.instrumento = :instrumento "
            + "AND c.estado IN :estados AND c.fechaHora >= :inicio AND c.fechaHora < :fin "
            + "AND (:excludeId IS NULL OR c.id <> :excludeId)")
    long countOcupadasEnRangoPorInstrumento(@Param("alumnoId") Long alumnoId,
                                             @Param("instrumento") Instrumento instrumento,
                                             @Param("estados") List<EstadoClase> estados,
                                             @Param("inicio") LocalDateTime inicio,
                                             @Param("fin") LocalDateTime fin,
                                             @Param("excludeId") Long excludeId);

    /**
     * Coarse same-day candidates for either the target profesor or the target alumno, in an
     * occupying estado. Exact interval-overlap comparison happens in ClaseService (Java, not
     * JPQL date arithmetic) — the candidate set for one professor/student in one day is always
     * small.
     */
    @Query("SELECT c FROM Clase c WHERE (c.profesor.id = :profesorId OR c.alumno.id = :alumnoId) "
            + "AND c.estado IN :estados AND c.fechaHora >= :desde AND c.fechaHora < :hasta "
            + "AND (:excludeId IS NULL OR c.id <> :excludeId)")
    List<Clase> findCandidatosConflicto(@Param("profesorId") Long profesorId,
                                         @Param("alumnoId") Long alumnoId,
                                         @Param("estados") List<EstadoClase> estados,
                                         @Param("desde") LocalDateTime desde,
                                         @Param("hasta") LocalDateTime hasta,
                                         @Param("excludeId") Long excludeId);

    /** Clases activas (no reagendadas/canceladas) desde una fecha, ascendente — para resolver el
     * estado actual del ciclo de un alumno, incluyendo cualquier reagendo ya aprobado. */
    @Query("SELECT c FROM Clase c WHERE c.alumno.id = :alumnoId AND c.estado IN :estados "
            + "AND c.fechaHora >= :desde ORDER BY c.fechaHora ASC")
    List<Clase> findActivasDesde(@Param("alumnoId") Long alumnoId,
                                  @Param("estados") List<EstadoClase> estados,
                                  @Param("desde") LocalDateTime desde);

    /** Antes de eliminar: si otra clase apunta a esta como su origen (reagendo), no se puede borrar. */
    boolean existsByClaseOriginal_Id(Long claseOriginalId);

    /** Rompe la cadena de auto-referencia (reagendos) de un alumno antes de borrar sus clases. */
    @Modifying
    @Query("UPDATE Clase c SET c.claseOriginal = NULL WHERE c.alumno.id = :alumnoId")
    void desvincularOriginalesPorAlumno(@Param("alumnoId") Long alumnoId);

    /** Borrado en cascada al eliminar un alumno por completo (ver AlumnoService#eliminar). */
    @Modifying
    @Query("DELETE FROM Clase c WHERE c.alumno.id = :alumnoId")
    void deleteByAlumnoId(@Param("alumnoId") Long alumnoId);

    @Query("SELECT c FROM Clase c WHERE "
            + "(:alumnoId IS NULL OR c.alumno.id = :alumnoId) AND "
            + "(:profesorId IS NULL OR c.profesor.id = :profesorId) AND "
            + "(:estado IS NULL OR c.estado = :estado) AND "
            + "(:desde IS NULL OR c.fechaHora >= :desde) AND "
            + "(:hasta IS NULL OR c.fechaHora < :hasta)")
    Page<Clase> buscar(@Param("alumnoId") Long alumnoId,
                        @Param("profesorId") Long profesorId,
                        @Param("estado") EstadoClase estado,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        Pageable pageable);
}
