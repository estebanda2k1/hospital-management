package com.hospital.service;

import com.hospital.dto.DoctorDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.repository.DoctorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService — Pruebas Unitarias")
class DoctorServiceTest {

    @Mock
    DoctorRepository doctorRepository;

    @Mock
    EntityManager entityManager;

    @InjectMocks
    DoctorService doctorService;

    private Doctor doctor1;
    private DoctorDTO doctorDTO;

    @BeforeEach
    void setUp() {
        // DoctorService usa @PersistenceContext, que Mockito no inyecta con @InjectMocks.
        // Se usa ReflectionTestUtils para inyectar el mock manualmente.
        ReflectionTestUtils.setField(doctorService, "entityManager", entityManager);

        doctor1 = new Doctor();
        doctor1.setId(1L);
        doctor1.setNombre("Carlos");
        doctor1.setApellido("López");
        doctor1.setEspecialidad("Cardiología");
        doctor1.setEmail("carlos@hospital.com");
        doctor1.setTelefono("0981234567");

        doctorDTO = new DoctorDTO();
        doctorDTO.setNombre("Carlos");
        doctorDTO.setApellido("López");
        doctorDTO.setEspecialidad("Cardiología");
        doctorDTO.setEmail("carlos@hospital.com");
        doctorDTO.setTelefono("0981234567");
    }

    // ─────────────────────────────────────────────────────────────
    // listarTodos()
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarTodos — happy path: retorna lista de doctores")
    void listarTodos_conDoctores_retornaLista() {
        when(doctorRepository.findAll()).thenReturn(List.of(doctor1));

        List<Doctor> resultado = doctorService.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Carlos");
        verify(doctorRepository, times(1)).findAll();
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorId(Long id)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId — happy path: retorna doctor existente")
    void buscarPorId_existente_retornaDoctor() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));

        Doctor resultado = doctorService.buscarPorId(1L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getEspecialidad()).isEqualTo("Cardiología");
    }

    @Test
    @DisplayName("buscarPorId — error: lanza ResourceNotFoundException si no existe")
    void buscarPorId_inexistente_lanzaResourceNotFoundException() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // crear(DoctorDTO dto)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear — happy path: guarda doctor con especialidad")
    void crear_conEspecialidad_retornaDoctor() {
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor1);

        Doctor resultado = doctorService.crear(doctorDTO);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getEspecialidad()).isEqualTo("Cardiología");
        verify(doctorRepository, times(1)).save(any(Doctor.class));
    }

    @Test
    @DisplayName("crear — BUG: permite especialidad null (sin @NotBlank en DTO ni NOT NULL en BD)")
    void crear_sinEspecialidad_permiteNull() {
        // BUG DOCUMENTADO: el DTO no tiene @NotBlank en especialidad y la columna
        // en la BD tampoco tiene NOT NULL. Se puede crear un doctor sin especialidad.
        Doctor doctorSinEspecialidad = new Doctor();
        doctorSinEspecialidad.setId(2L);
        doctorSinEspecialidad.setNombre("Ana");
        doctorSinEspecialidad.setApellido("Torres");
        doctorSinEspecialidad.setEspecialidad(null); // sin especialidad

        DoctorDTO dtoSinEspecialidad = new DoctorDTO();
        dtoSinEspecialidad.setNombre("Ana");
        dtoSinEspecialidad.setApellido("Torres");
        dtoSinEspecialidad.setEspecialidad(null); // null permitido

        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctorSinEspecialidad);

        Doctor resultado = doctorService.crear(dtoSinEspecialidad);

        // Se guarda con especialidad null — sin error
        assertThat(resultado.getEspecialidad()).isNull();
        // Hallazgo: se debe agregar @NotBlank en DoctorDTO.especialidad y NOT NULL en BD.
    }

    // ─────────────────────────────────────────────────────────────
    // actualizar(Long id, DoctorDTO dto)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar — happy path: actualiza campos del doctor")
    void actualizar_existente_actualizaCampos() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor1);

        DoctorDTO dtoActualizado = new DoctorDTO();
        dtoActualizado.setNombre("CarlosActualizado");
        dtoActualizado.setApellido("LópezActualizado");
        dtoActualizado.setEspecialidad("Neurología");

        Doctor resultado = doctorService.actualizar(1L, dtoActualizado);

        assertThat(resultado).isNotNull();
        verify(doctorRepository, times(1)).save(any(Doctor.class));
    }

    // ─────────────────────────────────────────────────────────────
    // eliminar(Long id)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar — BUG: elimina doctor sin verificar si tiene citas activas")
    void eliminar_sinVerificarCitasActivas_eliminaDirectamente() {
        doNothing().when(doctorRepository).deleteById(1L);

        doctorService.eliminar(1L);

        verify(doctorRepository, times(1)).deleteById(1L);
        verify(doctorRepository, never()).findById(anyLong());
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorEspecialidadInsegura(String especialidad) — SQL Injection
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorEspecialidadInsegura — documentar: construye query por concatenación")
    void buscarPorEspecialidadInsegura_concatenaStringDirectamente() {
        // BUG DOCUMENTADO: el service construye la query SQL concatenando el parámetro
        // directamente en el string, sin usar parámetros preparados.
        // Esto es una vulnerabilidad de SQL Injection (OWASP A03:2021).
        Query mockQuery = mock(Query.class);

        when(entityManager.createNativeQuery(
                anyString(),
                eq(Doctor.class)
        )).thenReturn(mockQuery);

        when(mockQuery.getResultList())
                .thenReturn(List.of(doctor1));

        List<Doctor> resultado =
                doctorService.buscarPorEspecialidadInsegura("Cardiología");

        verify(entityManager).createNativeQuery(
                contains("Cardiología"),
                eq(Doctor.class)
        );

        verify(mockQuery).getResultList();

        // Verificar que se usa createNativeQuery (query nativa = concatenación directa)
        // Hallazgo: SQL Injection — usar createNativeQuery con parámetros (:param) o JPQL.

        assertThat(resultado).isNotEmpty();
    }

    @Test
    @DisplayName("buscarPorEspecialidadInsegura — BUG: payload SQL Injection no es sanitizado")
    void buscarPorEspecialidadInsegura_conPayloadSQLi_noSanitiza() {
        String payloadSQLi = "' OR '1'='1";

        Query mockQuery = mock(Query.class);

        when(entityManager.createNativeQuery(
                anyString(),
                eq(Doctor.class)
        )).thenReturn(mockQuery);

        when(mockQuery.getResultList())
                .thenReturn(List.of(doctor1, new Doctor()));

        List<Doctor> resultado =
                doctorService.buscarPorEspecialidadInsegura(payloadSQLi);

        assertThat(resultado).hasSize(2);

        verify(entityManager).createNativeQuery(
                contains(payloadSQLi),
                eq(Doctor.class)
        );

        verify(mockQuery).getResultList();
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorEspecialidad(String especialidad) — versión segura
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorEspecialidad — versión segura: usa parámetros JPA (vs insegura)")
    void buscarPorEspecialidad_usaParametros_esSegura() {
        when(doctorRepository.findByEspecialidadContainingIgnoreCase("Cardiología"))
                .thenReturn(List.of(doctor1));

        List<Doctor> resultado = doctorService.buscarPorEspecialidad("Cardiología");

        // La versión segura usa findByEspecialidadContainingIgnoreCase (parámetros JPA)
        verify(doctorRepository)
                .findByEspecialidadContainingIgnoreCase("Cardiología");
        // Nunca llama a entityManager.createNativeQuery
        verify(entityManager, never()).createNativeQuery(anyString(),eq(Doctor.class));
        assertThat(resultado).hasSize(1);
    }

}
