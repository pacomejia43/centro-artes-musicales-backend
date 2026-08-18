package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.alumno.CupoInstrumentoRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.AlumnoInstrumentoCupoRepository;
import com.centroartesmusicales.backend.repository.AlumnoRepository;
import com.centroartesmusicales.backend.repository.ClaseRepository;
import com.centroartesmusicales.backend.repository.PagoRepository;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.SolicitudReagendacionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlumnoServiceTest {

    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlumnoInstrumentoCupoRepository cupoRepository;
    @Mock
    private ClaseRepository claseRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PagoTransaccionRepository pagoTransaccionRepository;
    @Mock
    private SolicitudReagendacionRepository solicitudReagendacionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AlumnoService alumnoService;
    private Alumno alumno;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-32b", 3_600_000L),
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap("", "", "Administrador")),
                "America/Mexico_City",
                new AppProperties.Clases(4, 4, 60),
                new AppProperties.Pagos(new BigDecimal("600.00"))
        );

        alumnoService = new AlumnoService(alumnoRepository, usuarioRepository, cupoRepository, claseRepository,
                pagoRepository, pagoTransaccionRepository, solicitudReagendacionRepository, passwordEncoder,
                appProperties);

        Usuario usuario = Usuario.builder().id(1L).email("alexandra1").nombre("Alexandra")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();
    }

    // ---------------------------------------------------------------- eliminar

    /**
     * No hay ON DELETE CASCADE en la base de datos (ver migraciones db/migration), así que este
     * orden es lo único que evita violar las FKs: solicitudes antes que sus clases, la
     * auto-referencia clase_original_id rota antes de borrar clases, transacciones antes que sus
     * pagos, y el alumno antes que su usuario (alumno.usuario_id apunta a usuario).
     */
    @Test
    void eliminar_borraTodoLoDependienteEnElOrdenCorrecto() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));

        alumnoService.eliminar(10L);

        InOrder orden = inOrder(solicitudReagendacionRepository, claseRepository, pagoTransaccionRepository,
                pagoRepository, cupoRepository, alumnoRepository, usuarioRepository);
        orden.verify(solicitudReagendacionRepository).deleteByAlumnoId(10L);
        orden.verify(claseRepository).desvincularOriginalesPorAlumno(10L);
        orden.verify(pagoTransaccionRepository).deleteByPagoAlumnoId(10L);
        orden.verify(pagoRepository).deleteByAlumnoId(10L);
        orden.verify(claseRepository).deleteByAlumnoId(10L);
        orden.verify(cupoRepository).deleteByAlumno_Id(10L);
        orden.verify(alumnoRepository).delete(alumno);
        orden.verify(usuarioRepository).deleteById(1L);
    }

    @Test
    void eliminar_fallaSiElAlumnoNoExisteYNoBorraNada() {
        when(alumnoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alumnoService.eliminar(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alumnoRepository, never()).delete(any());
        verify(usuarioRepository, never()).deleteById(any());
        verifyNoInteractions(claseRepository, pagoRepository, pagoTransaccionRepository,
                solicitudReagendacionRepository, cupoRepository);
    }

    // ---------------------------------------------------------------- actualizarCupos

    @Test
    void actualizarCupos_permiteVariosInstrumentosSiSumanElLimiteMensual() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));
        when(cupoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var items = List.of(
                new CupoInstrumentoRequest(Instrumento.PIANO, 2),
                new CupoInstrumentoRequest(Instrumento.CANTO, 2)
        );

        var resultado = alumnoService.actualizarCupos(10L, items);

        assertThat(resultado).hasSize(2);
        verify(cupoRepository).deleteByAlumno_Id(10L);
    }

    /**
     * El admin puede intercalar instrumentos en renglones separados (ej. para que
     * programarCiclo agende piano/canto/piano/canto en ese orden semanal) — deben sumarse en
     * vez de rechazarse, ya que alumno_instrumento_cupo tiene una fila única por instrumento.
     */
    @Test
    void actualizarCupos_sumaCantidadesCuandoElInstrumentoSeRepiteEnVariosRenglones() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));
        when(cupoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var items = List.of(
                new CupoInstrumentoRequest(Instrumento.PIANO, 1),
                new CupoInstrumentoRequest(Instrumento.CANTO, 1),
                new CupoInstrumentoRequest(Instrumento.PIANO, 1),
                new CupoInstrumentoRequest(Instrumento.CANTO, 1)
        );

        var resultado = alumnoService.actualizarCupos(10L, items);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).anySatisfy(c -> {
            assertThat(c.getInstrumento()).isEqualTo(Instrumento.PIANO);
            assertThat(c.getCupoMensual()).isEqualTo(2);
        });
        assertThat(resultado).anySatisfy(c -> {
            assertThat(c.getInstrumento()).isEqualTo(Instrumento.CANTO);
            assertThat(c.getCupoMensual()).isEqualTo(2);
        });
    }

    @Test
    void actualizarCupos_fallaSiLaSumaNoLlegaAlLimiteMensual() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));

        var items = List.of(new CupoInstrumentoRequest(Instrumento.PIANO, 2));

        assertThatThrownBy(() -> alumnoService.actualizarCupos(10L, items))
                .isInstanceOf(BusinessRuleException.class);
        verify(cupoRepository, never()).deleteByAlumno_Id(any());
    }

    @Test
    void actualizarCupos_fallaSiLaSumaExcedeElLimiteMensual() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));

        var items = List.of(
                new CupoInstrumentoRequest(Instrumento.PIANO, 3),
                new CupoInstrumentoRequest(Instrumento.CANTO, 3)
        );

        assertThatThrownBy(() -> alumnoService.actualizarCupos(10L, items))
                .isInstanceOf(BusinessRuleException.class);
        verify(cupoRepository, never()).deleteByAlumno_Id(any());
    }

    @Test
    void actualizarCupos_permiteListaVaciaParaVolverAlLimiteGlobal() {
        when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));
        when(cupoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = alumnoService.actualizarCupos(10L, List.of());

        assertThat(resultado).isEmpty();
        verify(cupoRepository).deleteByAlumno_Id(10L);
    }
}
