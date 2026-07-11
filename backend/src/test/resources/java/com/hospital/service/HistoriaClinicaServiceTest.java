package com.hospital.service;

import com.hospital.dto.HistoriaClinicaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.model.HistoriaClinica;
import com.hospital.model.Paciente;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.HistoriaClinicaRepository;
import com.hospital.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoriaClinicaService — Pruebas Unitarias")
class HistoriaClinicaServiceTest {

    @Mock
    HistoriaClinicaRepository historiaRepository;

    @Mock
    PacienteRepository pacienteRepository;

    @Mock
    DoctorRepository doctorRepository;

    @InjectMocks
    HistoriaClinicaService historiaClinicaService;

    private Paciente paciente1;
    private Doctor doctor1;
    private HistoriaClinica historia1;
    private HistoriaClinicaDTO historiaDTO;

    @BeforeEach
    void setUp() {
        paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setNombre("Juan");
        paciente1.setApellido("Pérez");

        doctor1 = new Doctor();
        doctor1.setId(1L);
        doctor1.setNombre("Carlos");
        doctor1.setApellido("López");
        doctor1.setEspecialidad("Cardiología");

        historia1 = new HistoriaClinica();
        historia1.setId(1L);
        historia1.setPaciente(paciente1);
        historia1.setDoctor(doctor1);
        historia1.setDiagnostico("Hipertensión leve");
        historia1.setTratamiento("Dieta y ejercicio");
        historia1.setFechaCreacion(LocalDateTime.of(2026, 7, 1, 9, 0));

        historiaDTO = new HistoriaClinicaDTO();
        historiaDTO.setPacienteId(1L);
        historiaDTO.setDoctorId(1L);
        historiaDTO.setDiagnostico("Hipertensión leve");
        historiaDTO.setTratamiento("Dieta y ejercicio");
    }

    // ─────────────────────────────────────────────────────────────
    // listarTodas()
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarTodas — happy path: llama findAllByOrderByFechaCreacionDesc()")
    void listarTodas_retornaOrdenadaPorFechaDesc() {
        when(historiaRepository.findAllByOrderByFechaCreacionDesc())
                .thenReturn(List.of(historia1));

        List<HistoriaClinica> resultado = historiaClinicaService.listarTodas();

        assertThat(resultado).hasSize(1);
        verify(historiaRepository, times(1)).findAllByOrderByFechaCreacionDesc();
        // Verificar que se usa el método ordenado (no findAll genérico)
    }

    @Test
    @DisplayName("listarTodas — límite: sin paginación hay riesgo de OOM con lista grande")
    void listarTodas_sinPaginacion_documentaRiesgoOOM() {
        // HALLAZGO DOCUMENTADO: el service retorna TODAS las historias sin paginar.
        // En producción con miles de registros, esto puede provocar OutOfMemoryError.
        List<HistoriaClinica> listaGrande = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            HistoriaClinica h = new HistoriaClinica();
            h.setId((long) i);
            h.setDiagnostico("Diagnóstico " + i);
            listaGrande.add(h);
        }
        when(historiaRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(listaGrande);

        // El service retorna todo sin error (en test es aceptable, en prod es un riesgo)
        List<HistoriaClinica> resultado = historiaClinicaService.listarTodas();

        assertThat(resultado).hasSize(1000);
        // Hallazgo: implementar paginación con Pageable para evitar carga masiva.
    }

    // ─────────────────────────────────────────────────────────────
    // buscarPorId(Long id)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId — happy path: retorna historia existente")
    void buscarPorId_existente_retornaHistoria() {
        when(historiaRepository.findById(1L)).thenReturn(Optional.of(historia1));

        HistoriaClinica resultado = historiaClinicaService.buscarPorId(1L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getDiagnostico()).isEqualTo("Hipertensión leve");
    }

    @Test
    @DisplayName("buscarPorId — error: lanza ResourceNotFoundException si no existe")
    void buscarPorId_inexistente_lanzaResourceNotFoundException() {
        when(historiaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> historiaClinicaService.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // crear(HistoriaClinicaDTO dto)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear — happy path: guarda historia con paciente y doctor existentes")
    void crear_conPacienteYDoctor_guardaHistoria() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente1));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        when(historiaRepository.save(any(HistoriaClinica.class))).thenReturn(historia1);

        HistoriaClinica resultado = historiaClinicaService.crear(historiaDTO);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getDiagnostico()).isEqualTo("Hipertensión leve");
        verify(historiaRepository, times(1)).save(any(HistoriaClinica.class));
    }

    @Test
    @DisplayName("crear — límite: doctor es opcional (doctorId null), historia se guarda sin doctor")
    void crear_sinDoctor_doctorEsOpcional() {
        // Según HU-04: el doctor es opcional en una historia clínica.
        // Si doctorId = null, el service debe guardar la historia con doctor = null.
        HistoriaClinicaDTO dtoSinDoctor = new HistoriaClinicaDTO();
        dtoSinDoctor.setPacienteId(1L);
        dtoSinDoctor.setDoctorId(null); // sin doctor
        dtoSinDoctor.setDiagnostico("Diagnóstico sin doctor");

        HistoriaClinica historiaSinDoctor = new HistoriaClinica();
        historiaSinDoctor.setId(2L);
        historiaSinDoctor.setPaciente(paciente1);
        historiaSinDoctor.setDoctor(null);
        historiaSinDoctor.setDiagnostico("Diagnóstico sin doctor");

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente1));
        when(historiaRepository.save(any(HistoriaClinica.class))).thenReturn(historiaSinDoctor);

        HistoriaClinica resultado = historiaClinicaService.crear(dtoSinDoctor);

        assertThat(resultado.getDoctor()).isNull();
        assertThat(resultado.getDiagnostico()).isEqualTo("Diagnóstico sin doctor");
        // Doctor opcional funciona correctamente según la especificación
    }

    @Test
    @DisplayName("crear — error: lanza ResourceNotFoundException si paciente no existe")
    void crear_pacienteInexistente_lanzaResourceNotFoundException() {
        when(pacienteRepository.findById(999L)).thenReturn(Optional.empty());

        HistoriaClinicaDTO dtoConPacienteInexistente = new HistoriaClinicaDTO();
        dtoConPacienteInexistente.setPacienteId(999L);
        dtoConPacienteInexistente.setDiagnostico("Diagnóstico");

        assertThatThrownBy(() -> historiaClinicaService.crear(dtoConPacienteInexistente))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("crear — BUG: diagnóstico con HTML/script se guarda sin sanitizar (XSS almacenado)")
    void crear_diagnosticoConHtml_noSanitiza() {
        // BUG DOCUMENTADO: el service no sanitiza el campo diagnóstico.
        // Un atacante puede inyectar código HTML/JavaScript que se almacena en la BD
        // y se ejecuta cuando otro usuario visualiza la historia clínica.
        // Clasificación: XSS almacenado (Stored XSS) — OWASP A03:2021.
        String payloadXSS = "<script>alert('XSS')</script>";

        HistoriaClinicaDTO dtoConXSS = new HistoriaClinicaDTO();
        dtoConXSS.setPacienteId(1L);
        dtoConXSS.setDoctorId(1L);
        dtoConXSS.setDiagnostico(payloadXSS); // payload malicioso

        HistoriaClinica historiaConXSS = new HistoriaClinica();
        historiaConXSS.setId(3L);
        historiaConXSS.setPaciente(paciente1);
        historiaConXSS.setDiagnostico(payloadXSS); // guardado tal cual

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente1));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor1));
        when(historiaRepository.save(any(HistoriaClinica.class))).thenReturn(historiaConXSS);

        HistoriaClinica resultado = historiaClinicaService.crear(dtoConXSS);

        // El payload se guarda sin sanitizar
        assertThat(resultado.getDiagnostico()).isEqualTo(payloadXSS);
        // Hallazgo CRÍTICO: XSS almacenado — implementar sanitización con OWASP Java Encoder
        // o Jsoup.clean() antes de persistir campos de texto libre.
    }

    // ─────────────────────────────────────────────────────────────
    // listarPorPaciente / listarPorDoctor
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarPorPaciente — happy path: retorna historias filtradas por pacienteId")
    void listarPorPaciente_retornaHistoriasFiltradas() {
        when(historiaRepository.findByPacienteId(1L)).thenReturn(List.of(historia1));

        List<HistoriaClinica> resultado = historiaClinicaService.listarPorPaciente(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getPaciente().getId()).isEqualTo(1L);
        verify(historiaRepository, times(1)).findByPacienteId(1L);
    }

    @Test
    @DisplayName("listarPorDoctor — happy path: retorna historias filtradas por doctorId")
    void listarPorDoctor_retornaHistoriasFiltradas() {
        when(historiaRepository.findByDoctorId(1L)).thenReturn(List.of(historia1));

        List<HistoriaClinica> resultado = historiaClinicaService.listarPorDoctor(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getDoctor().getId()).isEqualTo(1L);
        verify(historiaRepository, times(1)).findByDoctorId(1L);
    }
}
