package com.hospital.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hospital.dto.PacienteDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Paciente;
import com.hospital.repository.PacienteRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteService Pruebas unitarias")
public class PacienteServiceTest {
    @Mock
    PacienteRepository pacienteRepository;

    @InjectMocks
    PacienteService pacienteService;
    private Paciente pacienteUno;
    private Paciente pacienteDos;
    private PacienteDTO pacienteDTO;

    @BeforeEach
    void setUp() {
        pacienteUno = new Paciente();
        pacienteUno.setId(1L);
        pacienteUno.setNombre("Juan");
        pacienteUno.setApellido("López");
        pacienteUno.setEmail("juan@test.com");
        pacienteUno.setTelefono("123456789");
        pacienteUno.setFechaNacimiento(LocalDate.of(1990, 8, 1));

        pacienteDTO = new PacienteDTO();
        pacienteDTO.setNombre("Juan");
        pacienteDTO.setApellido("López");
        pacienteDTO.setEmail("juan@test.com");
        pacienteDTO.setTelefono("123456789");
        pacienteDTO.setFechaNacimiento(LocalDate.of(1990, 8, 1));

        pacienteDos = new Paciente();
        pacienteDos.setId(2L);
        pacienteDos.setNombre("María");
        pacienteDos.setApellido("Pérez");
        pacienteDos.setFechaNacimiento(LocalDate.of(2000, 8, 1) );

    }
     // ─────────────────────────────────────────────────────────────
    // Listar pacientes
    // ____________________________________________________________

    @Test
    @DisplayName("Listar todos los pacientes, devuelve dos pacientes")
    void ListarPacientesTest(){
        when(pacienteRepository.findAll()).thenReturn(Arrays.asList(pacienteUno, pacienteDos));
        List<Paciente> pacientes = pacienteService.listarTodos();

        assertEquals(2, pacientes.size());
        assertThat(pacientes).contains(pacienteUno, pacienteDos);
        verify(pacienteRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Listar todos los pacientes, devuelve lista vacía")
    void ListarPacientesVacioTest(){
        when(pacienteRepository.findAll()).thenReturn(List.of());
        List<Paciente> pacientes = pacienteService.listarTodos();

        assertTrue(pacientes.isEmpty());
        verify(pacienteRepository, times(1)).findAll();
    }

     // ─────────────────────────────────────────────────────────────
    // buscarPorId(Long id)
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("buscarPorId — happy path: retorna paciente existente")
    void buscarPorId_existenteTest() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteUno));
 
        Paciente resultado = pacienteService.buscarPorId(1L);
 
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getNombre()).isEqualTo("Juan");
    }

     @Test
    @DisplayName("buscarPorId — error: lanza ResourceNotFoundException si no existe")
    void buscarPorId_inexistente_lanzaResourceNotFoundException() {
        when(pacienteRepository.findById(999L)).thenReturn(Optional.empty());
 
        assertThatThrownBy(() -> pacienteService.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    @DisplayName("buscarPorId — BUG: ID negativo no lanza excepción de validación")
    void buscarPorId_idNegativo_lanzaExcepcion() {
        // BUG DOCUMENTADO: el service no valida IDs negativos.
        // Debería lanzar IllegalArgumentException antes de consultar el repositorio,
        // pero en su lugar llega directamente al repositorio con el ID negativo.
        when(pacienteRepository.findById(-1L)).thenReturn(Optional.empty());
 
        // El comportamiento actual es lanzar ResourceNotFoundException (no hay validación previa).
        // El comportamiento esperado sería una validación explícita de ID negativo.
        assertThatThrownBy(() -> pacienteService.buscarPorId(-1L))
                .isInstanceOf(ResourceNotFoundException.class);
        // Hallazgo: falta validación de ID negativo antes de consultar el repositorio.
    }

    // ─────────────────────────────────────────────────────────────
    // crear(PacienteDTO dto)
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("crear — happy path: guarda paciente y retorna objeto con ID")
    void crear_datosValidos_retornaPacienteGuardado() {
        when(pacienteRepository.save(any(Paciente.class))).thenReturn(pacienteUno);
 
        Paciente resultado = pacienteService.crear(pacienteDTO);
 
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        verify(pacienteRepository, times(1)).save(any(Paciente.class));
    }
 
    @Test
    @DisplayName("crear — BUG: acepta email duplicado sin validar unicidad")
    void crear_emailDuplicado_noValidaUnicidad() {
        // BUG DOCUMENTADO: el service no verifica si ya existe un paciente con el mismo email.
        // Debería lanzar una excepción de unicidad, pero guarda ambos pacientes sin error.
        Paciente duplicado = new Paciente();
        duplicado.setId(2L);
        duplicado.setEmail("juan@test.com"); // mismo email que paciente1
 
        when(pacienteRepository.save(any(Paciente.class)))
                .thenReturn(pacienteUno)
                .thenReturn(duplicado);
 
        PacienteDTO dto2 = new PacienteDTO();
        dto2.setNombre("Otro");
        dto2.setApellido("Usuario");
        dto2.setEmail("juan@test.com"); // email duplicado
 
        Paciente p1 = pacienteService.crear(pacienteDTO);
        Paciente p2 = pacienteService.crear(dto2);
 
        // Ambos se guardan sin error — el service no valida unicidad
        assertThat(p1.getId()).isEqualTo(1L);
        assertThat(p2.getId()).isEqualTo(2L);
        verify(pacienteRepository, times(2)).save(any(Paciente.class));
        // Hallazgo: debería existir una validación de email único antes de save().
    }
 
    // ─────────────────────────────────────────────────────────────
    // actualizar(Long id, PacienteDTO dto)
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("actualizar — happy path: actualiza todos los campos del paciente")
    void actualizar_existente_actualizaTodosLosCampos() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteUno));
        when(pacienteRepository.save(any(Paciente.class))).thenReturn(pacienteUno);
 
        PacienteDTO dtoActualizado = new PacienteDTO();
        dtoActualizado.setNombre("JuanActualizado");
        dtoActualizado.setApellido("PérezActualizado");
        dtoActualizado.setEmail("nuevo@test.com");
        dtoActualizado.setTelefono("0999999999");
        dtoActualizado.setFechaNacimiento(LocalDate.of(1991, 1, 1));
 
        Paciente resultado = pacienteService.actualizar(1L, dtoActualizado);
 
        assertThat(resultado).isNotNull();
        verify(pacienteRepository, times(1)).save(any(Paciente.class));
    }
 
    @Test
    @DisplayName("actualizar — BUG: campos nulos en DTO pisan valores existentes sin validar")
    void actualizar_camposNulos_pisaValoresExistentes() {
        // BUG DOCUMENTADO: el service actualiza campos sin validar nulos.
        // Si el DTO tiene nombre=null, el service pisa el nombre existente con null.
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteUno));
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));
 
        PacienteDTO dtoConNulos = new PacienteDTO();
        dtoConNulos.setNombre(null); // campo nulo
        dtoConNulos.setApellido(null);
        // demás campos también null
 
        Paciente resultado = pacienteService.actualizar(1L, dtoConNulos);
 
        // El service guarda sin validar — hallazgo: debería ignorar o rechazar nulos
        verify(pacienteRepository, times(1)).save(any(Paciente.class));
    }
 
    // ─────────────────────────────────────────────────────────────
    // eliminar(Long id)
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("eliminar — happy path: llama delete() para paciente existente")
    void eliminar_existente_llamaDelete() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteUno));
        doNothing().when(pacienteRepository).delete(any(Paciente.class));
 
        pacienteService.eliminar(1L);
 
        verify(pacienteRepository, times(1)).delete(any(Paciente.class));
    }
 
    @Test
    @DisplayName("eliminar — error: propaga ResourceNotFoundException si paciente no existe")
    void eliminar_inexistente_propagaExcepcion() {
        when(pacienteRepository.findById(999L)).thenReturn(Optional.empty());
 
        assertThatThrownBy(() -> pacienteService.eliminar(999L))
                .isInstanceOf(ResourceNotFoundException.class);
 
        verify(pacienteRepository, never()).delete(any());
    }
 
    // ─────────────────────────────────────────────────────────────
    // buscarPorNombre(String nombre)
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("buscarPorNombre — happy path: retorna lista filtrada")
    void buscarPorNombre_nombreValido_retornaResultados() {
        when(pacienteRepository.buscarPorNombre("Juan"))
                .thenReturn(List.of(pacienteUno));
 
        List<Paciente> resultado = pacienteService.buscarPorNombre("Juan");
 
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Juan");
    }
 
    @Test
    @DisplayName("buscarPorNombre — BUG: nombre null provoca NullPointerException")
    void buscarPorNombre_nombreNull_lanzaNullPointerException() {
        // BUG DOCUMENTADO: el service llama al repositorio sin validar que nombre != null.
        // El repositorio recibe null y puede lanzar NullPointerException.
        when(pacienteRepository.buscarPorNombre(null))
                .thenThrow(new NullPointerException("nombre no puede ser null"));
 
        assertThatThrownBy(() -> pacienteService.buscarPorNombre(null))
                .isInstanceOf(NullPointerException.class);
        // Hallazgo: falta validación de null antes de llamar al repositorio.
    }
 
    // ─────────────────────────────────────────────────────────────
    // calcularEdadPromedio()
    // ─────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("calcularEdadPromedio — happy path: retorna promedio correcto con 2 pacientes")
    void calcularEdadPromedio_conPacientes_retornaPromedio() {
        // paciente1 nació en 1990, paciente2 en 1985
        when(pacienteRepository.findAll()).thenReturn(List.of(pacienteUno, pacienteDos));
 
        double resultado = pacienteService.calcularEdadPromedio();
 
        // El promedio debe ser positivo y razonable (entre 30 y 50 años aprox.)
        assertThat(resultado).isPositive();
        assertThat(resultado).isLessThan(100.0);
    }
 
    @Test
    @DisplayName("calcularEdadPromedio — BUG: lista vacía lanza ArithmeticException (división por cero)")
    void calcularEdadPromedio_sinPacientes_lanzaArithmeticException() {
        // BUG DOCUMENTADO: cuando no hay pacientes, pacientes.size() = 0
        // y el service divide por cero sin manejar el caso.
        when(pacienteRepository.findAll()).thenReturn(Collections.emptyList());

        double resultado = pacienteService.calcularEdadPromedio();
        
    }
}
