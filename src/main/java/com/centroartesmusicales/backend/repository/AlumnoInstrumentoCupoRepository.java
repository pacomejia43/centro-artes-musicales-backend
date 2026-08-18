package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.AlumnoInstrumentoCupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlumnoInstrumentoCupoRepository extends JpaRepository<AlumnoInstrumentoCupo, Long> {

    List<AlumnoInstrumentoCupo> findByAlumno_IdOrderByInstrumento(Long alumnoId);

    void deleteByAlumno_Id(Long alumnoId);
}
