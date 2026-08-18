package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.dto.alumno.ActualizarAlumnoRequest;
import com.centroartesmusicales.backend.dto.alumno.ActualizarPerfilRequest;
import com.centroartesmusicales.backend.dto.alumno.CupoInstrumentoRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.AlumnoInstrumentoCupo;
import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.AlumnoInstrumentoCupoRepository;
import com.centroartesmusicales.backend.repository.AlumnoRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import com.centroartesmusicales.backend.security.PasswordCipherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlumnoInstrumentoCupoRepository cupoRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordCipherService passwordCipherService;

    /**
     * Común a la creación pública (registro con correo real) y la del admin (usuario/contraseña
     * asignados a mano) — cada controlador valida su propio DTO y pasa los campos ya extraídos,
     * para que las reglas de formato de "email" puedan diferir entre ambos casos sin duplicar
     * aquí la lógica de alta.
     */
    @Transactional
    public Alumno crear(String email, String password, String nombre, String telefono,
                         LocalDate fechaNacimiento, LocalDate fechaPrimeraClase, BigDecimal precioMensual) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Ya existe un usuario con ese correo o nombre de usuario");
        }

        Usuario usuario = Usuario.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .passwordVisible(passwordCipherService.encriptar(password))
                .nombre(nombre)
                .role(Role.ALUMNO)
                .enabled(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        Alumno alumno = Alumno.builder()
                .usuario(usuario)
                .telefono(telefono)
                .fechaNacimiento(fechaNacimiento)
                .fechaInscripcion(LocalDate.now())
                .fechaPrimeraClase(fechaPrimeraClase)
                .precioMensual(precioMensual)
                .activo(true)
                .build();

        return alumnoRepository.save(alumno);
    }

    public Page<Alumno> listar(Boolean activo, String nombre, Pageable pageable) {
        Pageable pageableCorregido = pageable;
        if (pageable.getSort().isSorted()) {
            Sort sortCorregido = Sort.unsorted();
            for (Sort.Order order : pageable.getSort()) {
                String property = order.getProperty();
                if ("nombre".equals(property)) {
                    property = "usuario.nombre";
                }
                sortCorregido = sortCorregido.and(Sort.by(order.getDirection(), property));
            }
            pageableCorregido = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortCorregido);
        }

        if (nombre != null && !nombre.isBlank()) {
            return alumnoRepository.findByUsuario_NombreContainingIgnoreCase(nombre, pageableCorregido);
        }
        if (activo != null) {
            return alumnoRepository.findByActivo(activo, pageableCorregido);
        }
        return alumnoRepository.findAll(pageableCorregido);
    }

    public Alumno obtenerPorId(Long id) {
        return alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno no encontrado: " + id));
    }

    public Alumno obtenerPorUsuarioId(Long usuarioId) {
        return alumnoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de alumno no encontrado"));
    }

    @Transactional
    public Alumno actualizar(Long id, ActualizarAlumnoRequest request) {
        Alumno alumno = obtenerPorId(id);
        aplicarCambiosComunes(alumno, request.nombre(), request.telefono(), request.fechaNacimiento());
        if (request.email() != null && !request.email().isBlank()) {
            String nuevoEmail = request.email().trim();
            Usuario usuario = alumno.getUsuario();
            if (!nuevoEmail.equals(usuario.getEmail())) {
                if (usuarioRepository.existsByEmail(nuevoEmail)) {
                    throw new BusinessRuleException("Ya existe un usuario con ese correo o nombre de usuario");
                }
                usuario.setEmail(nuevoEmail);
                usuarioRepository.save(usuario);
            }
        }
        if (request.googleDocsUrl1() != null) {
            alumno.setGoogleDocsUrl1(request.googleDocsUrl1());
        }
        if (request.instrumentoBitacora1() != null) {
            alumno.setInstrumentoBitacora1(request.instrumentoBitacora1());
        }
        if (request.googleDocsUrl2() != null) {
            alumno.setGoogleDocsUrl2(request.googleDocsUrl2());
        }
        if (request.instrumentoBitacora2() != null) {
            alumno.setInstrumentoBitacora2(request.instrumentoBitacora2());
        }
        if (request.activo() != null) {
            alumno.setActivo(request.activo());
        }
        if (request.fechaPrimeraClase() != null) {
            alumno.setFechaPrimeraClase(request.fechaPrimeraClase());
        }
        if (request.precioMensual() != null) {
            alumno.setPrecioMensual(request.precioMensual());
        }
        Alumno guardado = alumnoRepository.save(alumno);
        alumnoRepository.flush(); // Fuerza la escritura inmediata en la base de datos (ver desactivar())
        return guardado;
    }

    @Transactional
    public Alumno actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request) {
        Alumno alumno = obtenerPorUsuarioId(usuarioId);
        aplicarCambiosComunes(alumno, request.nombre(), request.telefono(), request.fechaNacimiento());
        Alumno guardado = alumnoRepository.save(alumno);
        alumnoRepository.flush();
        return guardado;
    }

    /**
     * El nombre vive en Usuario, no en Alumno (relación @OneToOne perezosa) — se guarda
     * explícitamente en su propio repositorio en vez de confiar solo en el dirty-checking
     * de Hibernate, para que "editar alumno" nunca deje el nombre sin persistir.
     */
    private void aplicarCambiosComunes(Alumno alumno, String nombre, String telefono, LocalDate fechaNacimiento) {
        if (nombre != null && !nombre.isBlank()) {
            Usuario usuario = alumno.getUsuario();
            usuario.setNombre(nombre);
            usuarioRepository.save(usuario);
        }
        if (telefono != null) {
            alumno.setTelefono(telefono);
        }
        if (fechaNacimiento != null) {
            alumno.setFechaNacimiento(fechaNacimiento);
        }
    }

    @Transactional
    public void desactivar(Long id) {
        Alumno alumno = obtenerPorId(id);
        alumno.setActivo(false);
        alumnoRepository.save(alumno);
        alumnoRepository.flush(); // Fuerza la escritura inmediata en la base de datos
    }

    @Transactional
    public void resetPassword(Long id, String nuevaPassword) {
        Alumno alumno = obtenerPorId(id);
        Usuario usuario = alumno.getUsuario();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setPasswordVisible(passwordCipherService.encriptar(nuevaPassword));
        usuarioRepository.save(usuario);
    }

    /** Null si el alumno todavía no tiene ninguna contraseña capturada desde que existe esta función. */
    public String obtenerPasswordVisible(Long id) {
        Alumno alumno = obtenerPorId(id);
        String cifrado = alumno.getUsuario().getPasswordVisible();
        return cifrado != null ? passwordCipherService.desencriptar(cifrado) : null;
    }

    public List<AlumnoInstrumentoCupo> obtenerCupos(Long alumnoId) {
        return cupoRepository.findByAlumno_IdOrderByInstrumento(alumnoId);
    }

    /**
     * Reemplaza de un solo golpe todos los cupos por instrumento del alumno (ver
     * AlumnoInstrumentoCupo) — una lista vacía los borra y el alumno vuelve a regirse por el
     * límite mensual global (ClaseService#verificarCupoMensual).
     */
    @Transactional
    public List<AlumnoInstrumentoCupo> actualizarCupos(Long alumnoId, List<CupoInstrumentoRequest> items) {
        Alumno alumno = obtenerPorId(alumnoId);

        Set<Instrumento> vistos = new HashSet<>();
        for (CupoInstrumentoRequest item : items) {
            if (!vistos.add(item.instrumento())) {
                throw new BusinessRuleException("El instrumento " + item.instrumento() + " está repetido");
            }
        }

        cupoRepository.deleteByAlumno_Id(alumnoId);
        cupoRepository.flush();

        List<AlumnoInstrumentoCupo> nuevos = items.stream()
                .map(item -> AlumnoInstrumentoCupo.builder()
                        .alumno(alumno)
                        .instrumento(item.instrumento())
                        .cupoMensual(item.cupoMensual())
                        .build())
                .toList();
        return cupoRepository.saveAll(nuevos);
    }
}