package com.hospital.service;

import com.hospital.dto.CitaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Cita;
import com.hospital.model.Doctor;
import com.hospital.model.Paciente;
import com.hospital.repository.CitaRepository;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CitaService — Pruebas Unitarias")
class CitaServiceTest {

    @Mock
    CitaRepository citaRepository;

    @Mock
    DoctorRepository doctorRepository;

    @InjectMocks
    CitaService citaService;

    private Doctor doctor1;
    private Paciente paciente1;
    private Cita cita1;
    private CitaDTO citaDTO;
    private LocalDateTime fechaHora;

    @BeforeEach
    void setUp() {
        fechaHora = LocalDateTime.of(2026, 8, 15, 10, 0);

        doctor1 = new Doctor();
        doctor1.setId(1L);
        doctor1.setNombre("Carlos");
        doctor1.setApellido("López");
        doctor1.setEspecialidad("Cardiología");

        paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setNombre("Juan");
        paciente1.setApellido("Pérez");

        cita1 = new Cita();
        cita1.setId(1L);
        cita1.setPaciente(paciente1);
        cita1.setDoctor(doctor1);
        cita1.setFechaHora(fechaHora);
        cita1.setMotivo("Chequeo general");
        cita1.setEstado("PROGRAMADA");

        citaDTO = new CitaDTO();
        citaDTO.setPacienteId(1L);
        citaDTO.setDoctorId(1L);
        citaDTO.setFechaHora(fechaHora);
        citaDTO.setMotivo("Chequeo general");
        citaDTO.setEstado("PROGRAMADA");
    }

    // ─────────────────────────────────────────────────────────────
    // listarTodas()
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarTodas — happy path: retorna todas las citas")
    void listarTodas_retornaTodasLasCitas() {
        when(citaRepository.findAll()).thenReturn(List.of(cita1));

        List<Cita> resultado = citaService.listarTodas();

        assertThat(resultado).hasSize(1);
        verify(citaRepository, times(1)).findAll();
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorId(Long id)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId — happy path: retorna cita existente")
    void buscarPorId_existente_retornaCita() {
        when(citaRepository.findById(1L)).thenReturn(Optional.of(cita1));

        Cita resultado = citaService.buscarPorId(1L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getMotivo()).isEqualTo("Chequeo general");
    }

    @Test
    @DisplayName("buscarPorId — error: lanza ResourceNotFoundException si no existe")
    void buscarPorId_inexistente_lanzaResourceNotFoundException() {
        when(citaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citaService.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // crear(CitaDTO dto)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear — happy path: guarda cita cuando doctor existe")
    void crear_doctorExistente_guardaCita() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        when(citaRepository.save(any(Cita.class))).thenReturn(cita1);

        Cita resultado = citaService.crear(citaDTO);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        verify(citaRepository, times(1)).save(any(Cita.class));
    }

    @Test
    @DisplayName("crear — BUG: guarda cita con pacienteId inexistente sin verificar")
    void crear_sinVerificarPaciente_guardaConIdInexistente() {
        // BUG DOCUMENTADO: el service solo valida que el doctor exista.
        // No verifica si pacienteId corresponde a un paciente real en PacienteRepository.
        // Se puede crear una cita con un pacienteId ficticio.
        CitaDTO dtoConPacienteFicticio = new CitaDTO();
        dtoConPacienteFicticio.setPacienteId(9999L); // ID que no existe
        dtoConPacienteFicticio.setDoctorId(1L);
        dtoConPacienteFicticio.setFechaHora(fechaHora);
        dtoConPacienteFicticio.setMotivo("Consulta");

        Cita citaConPacienteFicticio = new Cita();
        citaConPacienteFicticio.setId(2L);
        // paciente queda como referencia por ID, sin validar existencia

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        when(citaRepository.save(any(Cita.class))).thenReturn(citaConPacienteFicticio);

        // El service no lanza excepción aunque el paciente no existe
        assertThatCode(() -> citaService.crear(dtoConPacienteFicticio))
                .doesNotThrowAnyException();
        // Hallazgo: falta verificar pacienteId en PacienteRepository antes de guardar.
    }

    @Test
    @DisplayName("crear — BUG: permite double-booking (mismo doctor, misma fechaHora)")
    void crear_sinValidarDobleBooking_permiteDuplicado() {
        // BUG DOCUMENTADO: no existe lógica de doble booking.
        // Se pueden crear dos citas con el mismo doctor y la misma fechaHora.
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));

        Cita cita2 = new Cita();
        cita2.setId(2L);
        cita2.setDoctor(doctor1);
        cita2.setFechaHora(fechaHora); // misma hora
        cita2.setMotivo("Segunda consulta");

        when(citaRepository.save(any(Cita.class)))
                .thenReturn(cita1)
                .thenReturn(cita2);

        CitaDTO dto2 = new CitaDTO();
        dto2.setPacienteId(2L);
        dto2.setDoctorId(1L); // mismo doctor
        dto2.setFechaHora(fechaHora); // misma hora
        dto2.setMotivo("Segunda consulta");

        Cita c1 = citaService.crear(citaDTO);
        Cita c2 = citaService.crear(dto2); // double-booking sin error

        assertThat(c1.getId()).isEqualTo(1L);
        assertThat(c2.getId()).isEqualTo(2L);
        verify(citaRepository, times(2)).save(any(Cita.class));
        // Hallazgo: falta validar que el doctor no tenga cita en la misma fechaHora.
    }

    @Test
    @DisplayName("crear — error: lanza ResourceNotFoundException si doctor no existe")
    void crear_doctorInexistente_lanzaResourceNotFoundException() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        CitaDTO dtoSinDoctor = new CitaDTO();
        dtoSinDoctor.setDoctorId(999L);
        dtoSinDoctor.setPacienteId(1L);
        dtoSinDoctor.setFechaHora(fechaHora);

        assertThatThrownBy(() -> citaService.crear(dtoSinDoctor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // actualizar(Long id, CitaDTO dto)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar — happy path: cambia estado de la cita")
    void actualizar_cambiarEstado_guardaCambio() {
        when(citaRepository.findById(1L)).thenReturn(Optional.of(cita1));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        CitaDTO dtoActualizado = new CitaDTO();
        dtoActualizado.setPacienteId(1L);
        dtoActualizado.setDoctorId(1L);
        dtoActualizado.setFechaHora(fechaHora);
        dtoActualizado.setMotivo("Chequeo general");
        dtoActualizado.setEstado("COMPLETADA"); // nuevo estado

        Cita resultado = citaService.actualizar(1L, dtoActualizado);

        assertThat(resultado).isNotNull();
        verify(citaRepository, times(1)).save(any(Cita.class));
    }

    // ─────────────────────────────────────────────────────────────
    // listarPorPaciente / listarPorDoctor / listarPorEstado
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarPorPaciente — happy path: retorna citas filtradas por pacienteId")
    void listarPorPaciente_retornaCitasFiltradas() {
        when(citaRepository.findByPacienteId(1L)).thenReturn(List.of(cita1));

        List<Cita> resultado = citaService.listarPorPaciente(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getPaciente().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listarPorDoctor — happy path: llama findByDoctorId (N+1 documentado)")
    void listarPorDoctor_llamaFindByDoctorId() {
        when(citaRepository.findByDoctorId(1L)).thenReturn(List.of(cita1));

        List<Cita> resultado = citaService.listarPorDoctor(1L);

        assertThat(resultado).hasSize(1);
        verify(citaRepository, times(1)).findByDoctorId(1L);
        // Hallazgo N+1: CitaRepository.findByDoctorId carga las entidades relacionadas
        // sin JOIN FETCH, provocando N+1 queries adicionales para cada cita.
        // Solución: usar @EntityGraph o JOIN FETCH en la query.
    }

    // ─────────────────────────────────────────────────────────────
    // listarPorRangoFechas(LocalDateTime inicio, LocalDateTime fin)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarPorRangoFechas — BUG: fechas invertidas no se validan")
    void listarPorRangoFechas_fechasInvertidas_noValida() {
        // BUG DOCUMENTADO: el service no valida que inicio < fin.
        // Cuando inicio es posterior a fin, el repositorio retorna lista vacía
        // (o comportamiento indefinido según la BD) sin lanzar error de negocio.
        LocalDateTime inicio = LocalDateTime.of(2026, 12, 31, 0, 0); // posterior
        LocalDateTime fin    = LocalDateTime.of(2026, 1, 1, 0, 0);   // anterior

        when(citaRepository.findByFechaHoraBetween(inicio, fin)).thenReturn(List.of());

        // El service llama al repo sin validar que inicio < fin
        assertThatCode(() -> citaService.listarPorRangoFechas(inicio, fin))
                .doesNotThrowAnyException();

        verify(citaRepository, times(1)).findByFechaHoraBetween(inicio, fin);
        // Hallazgo: debe validar inicio.isBefore(fin) antes de consultar el repositorio.
    }
}
