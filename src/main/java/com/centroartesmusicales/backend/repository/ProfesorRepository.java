package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Profesor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfesorRepository extends JpaRepository<Profesor, Long> {

    Page<Profesor> findByActivo(boolean activo, Pageable pageable);

    Page<Profesor> findByEspecialidadesContaining(Instrumento instrumento, Pageable pageable);
}
