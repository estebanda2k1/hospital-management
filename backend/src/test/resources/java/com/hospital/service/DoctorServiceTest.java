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
        // BUG DOCUMENTADO: el service llama directamente a deleteById() sin verificar
        // si el doctor tiene citas activas. Esto puede dejar citas huérfanas o causar
        // errores de FK si la BD tiene restricciones.
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        doNothing().when(doctorRepository).delete(any(Doctor.class));

        doctorService.eliminar(1L);

        // Se elimina sin ninguna verificación de citas activas
        verify(doctorRepository, times(1)).delete(any(Doctor.class));
        // Hallazgo: antes de eliminar, se debe verificar que el doctor no tenga citas activas.
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
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of(doctor1));

        List<Doctor> resultado = doctorService.buscarPorEspecialidadInsegura("Cardiología");

        // Verificar que se usa createNativeQuery (query nativa = concatenación directa)
        verify(entityManager, times(1)).createNativeQuery(anyString());
        assertThat(resultado).isNotEmpty();
        // Hallazgo: SQL Injection — usar createNativeQuery con parámetros (:param) o JPQL.
    }

    @Test
    @DisplayName("buscarPorEspecialidadInsegura — BUG: payload SQL Injection no es sanitizado")
    void buscarPorEspecialidadInsegura_conPayloadSQLi_noSanitiza() {
        // BUG DOCUMENTADO: un payload de SQL Injection pasa sin error ni sanitización.
        // En producción, esto puede exponer toda la tabla de doctores o modificar datos.
        String payloadSQLi = "' OR '1'='1";

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of(doctor1, new Doctor())); // retorna más de lo esperado

        // La inyección pasa sin lanzar excepción
        assertThatCode(() -> doctorService.buscarPorEspecialidadInsegura(payloadSQLi))
                .doesNotThrowAnyException();

        // Verificar que la query se construyó con el payload sin escapar
        verify(entityManager).createNativeQuery(contains(payloadSQLi));
        // Hallazgo CRÍTICO: SQL Injection confirmado en buscarPorEspecialidadInsegura().
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
        verify(doctorRepository, times(1))
                .findByEspecialidadContainingIgnoreCase("Cardiología");
        // Nunca llama a entityManager.createNativeQuery
        verify(entityManager, never()).createNativeQuery(anyString());
        assertThat(resultado).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorNombreCompleto(String nombre, String apellido)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorNombreCompleto — BUG: no valida nombre/apellido vacíos")
    void buscarPorNombreCompleto_parametrosVacios_noValida() {
        // BUG DOCUMENTADO: el service no valida que nombre y apellido no sean vacíos.
        // Se puede buscar con strings vacíos, lo que retorna todos los doctores.
        when(doctorRepository.findByNombreContainingIgnoreCaseAndApellidoContainingIgnoreCase("", ""))
                .thenReturn(List.of(doctor1));

        List<Doctor> resultado = doctorService.buscarPorNombreCompleto("", "");

        assertThat(resultado).isNotEmpty();
        // Hallazgo: debería validar que al menos uno de los parámetros tenga contenido.
    }
}
