package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.dto.alumno.ActualizarAlumnoRequest;
import com.centroartesmusicales.backend.dto.alumno.ActualizarPerfilRequest;
import com.centroartesmusicales.backend.dto.alumno.CrearAlumnoRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.AlumnoRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Alumno crear(CrearAlumnoRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Ya existe un usuario con ese correo");
        }

        Usuario usuario = Usuario.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nombre(request.nombre())
                .role(Role.ALUMNO)
                .enabled(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        Alumno alumno = Alumno.builder()
                .usuario(usuario)
                .telefono(request.telefono())
                .fechaNacimiento(request.fechaNacimiento())
                .fechaInscripcion(LocalDate.now())
                .fechaPrimeraClase(request.fechaPrimeraClase())
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
        if (request.googleDocsUrl() != null) {
            alumno.setGoogleDocsUrl(request.googleDocsUrl());
        }
        if (request.activo() != null) {
            alumno.setActivo(request.activo());
        }
        if (request.fechaPrimeraClase() != null) {
            alumno.setFechaPrimeraClase(request.fechaPrimeraClase());
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
        alumno.getUsuario().setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(alumno.getUsuario());
    }

    public String obtenerBitacora(Long alumnoId) {
        return obtenerPorId(alumnoId).getGoogleDocsUrl();
    }
}