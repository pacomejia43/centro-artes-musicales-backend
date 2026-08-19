package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.repository.ClaseRepository;
import com.centroartesmusicales.backend.repository.ProfesorRepository;
import com.centroartesmusicales.backend.repository.SolicitudReagendacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for ProfesorService#eliminar — reassignment/nulling of a deleted profesor's clases. */
@ExtendWith(MockitoExtension.class)
class ProfesorServiceTest {

    @Mock
    private ProfesorRepository profesorRepository;
    @Mock
    private ClaseRepository claseRepository;
    @Mock
    private SolicitudReagendacionRepository solicitudReagendacionRepository;

    private ProfesorService profesorService;

    private Profesor profesor;
    private Profesor otroProfesor;

    @BeforeEach
    void setUp() {
        profesorService = new ProfesorService(profesorRepository, claseRepository, solicitudReagendacionRepository);
        profesor = Profesor.builder().id(20L).nombreCompleto("Profesor Uno").activo(true).build();
        otroProfesor = Profesor.builder().id(21L).nombreCompleto("Profesor Dos").activo(true).build();
    }

    @Test
    void eliminar_reasignaLasClasesAlReemplazoCuandoSeIndica() {
        when(profesorRepository.findById(20L)).thenReturn(Optional.of(profesor));
        when(profesorRepository.findById(21L)).thenReturn(Optional.of(otroProfesor));

        profesorService.eliminar(20L, 21L);

        verify(solicitudReagendacionRepository).desvincularProfesorPropuesto(20L);
        verify(claseRepository).reasignarProfesor(20L, otroProfesor);
        verify(claseRepository, never()).vaciarProfesor(any());
        verify(profesorRepository).delete(profesor);
    }

    @Test
    void eliminar_vaciaElProfesorDeLasClasesCuandoNoHayReemplazo() {
        when(profesorRepository.findById(20L)).thenReturn(Optional.of(profesor));

        profesorService.eliminar(20L, null);

        verify(solicitudReagendacionRepository).desvincularProfesorPropuesto(20L);
        verify(claseRepository).vaciarProfesor(20L);
        verify(claseRepository, never()).reasignarProfesor(any(), any());
        verify(profesorRepository).delete(profesor);
    }

    @Test
    void eliminar_fallaSiElReemplazoEsElMismoProfesorQueSeElimina() {
        when(profesorRepository.findById(20L)).thenReturn(Optional.of(profesor));

        assertThatThrownBy(() -> profesorService.eliminar(20L, 20L))
                .isInstanceOf(BusinessRuleException.class);

        verify(claseRepository, never()).reasignarProfesor(any(), any());
        verify(claseRepository, never()).vaciarProfesor(any());
        verify(profesorRepository, never()).delete(any());
    }

    @Test
    void eliminar_fallaSiElProfesorDeReemplazoNoExiste() {
        when(profesorRepository.findById(20L)).thenReturn(Optional.of(profesor));
        when(profesorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profesorService.eliminar(20L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(profesorRepository, never()).delete(any());
    }
}
