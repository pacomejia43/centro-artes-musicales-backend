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
import org.springframework.data.domain.Pageable;
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

    /** Shared by admin-create (POST /api/admin/alumnos) and self-registro (POST /api/auth/registro). */
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
                .activo(true)
                .build();

        return alumnoRepository.save(alumno);
    }

    public Page<Alumno> listar(Boolean activo, String nombre, Pageable pageable) {
        if (nombre != null && !nombre.isBlank()) {
            return alumnoRepository.findByUsuario_NombreContainingIgnoreCase(nombre, pageable);
        }
        if (activo != null) {
            return alumnoRepository.findByActivo(activo, pageable);
        }
        return alumnoRepository.findAll(pageable);
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
        return alumnoRepository.save(alumno);
    }

    @Transactional
    public Alumno actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request) {
        Alumno alumno = obtenerPorUsuarioId(usuarioId);
        aplicarCambiosComunes(alumno, request.nombre(), request.telefono(), request.fechaNacimiento());
        return alumnoRepository.save(alumno);
    }

    private void aplicarCambiosComunes(Alumno alumno, String nombre, String telefono, LocalDate fechaNacimiento) {
        if (nombre != null && !nombre.isBlank()) {
            alumno.getUsuario().setNombre(nombre);
        }
        if (telefono != null) {
            alumno.setTelefono(telefono);
        }
        if (fechaNacimiento != null) {
            alumno.setFechaNacimiento(fechaNacimiento);
        }
    }

    /** Baja lógica: el alumno deja de estar inscrito. No bloquea su login (podría seguir viendo su historial). */
    @Transactional
    public void desactivar(Long id) {
        Alumno alumno = obtenerPorId(id);
        alumno.setActivo(false);
        alumnoRepository.save(alumno);
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
