package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.controller.ManejadorDeErrores;
import com.municipio.ticketera.controller.ReclamoController;
import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.TransicionInvalidaException;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** Logica real de asignacion y contrato HTTP, con repositorios y transporte simulados. */
class AsignacionCuadrillasTest {

    private Reclamo reclamo;
    private Cuadrilla cuadrilla;
    private ReclamoRepository reclamos;
    private CuadrillaRepository cuadrillas;
    private Broker broker;
    private SvcCuadrillas svcCuadrillas;
    private SvcReclamos svcReclamos;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        cuadrilla = new Cuadrilla("Bacheo 1", TipoDeReclamo.BACHEO);
        ReflectionTestUtils.setField(cuadrilla, "id", UUID.randomUUID());
        reclamos = mock(ReclamoRepository.class);
        cuadrillas = mock(CuadrillaRepository.class);
        broker = mock(Broker.class);
        when(reclamos.findById(reclamo.getId())).thenReturn(Optional.of(reclamo));
        when(cuadrillas.buscarConBloqueo(cuadrilla.getId())).thenReturn(Optional.of(cuadrilla));
        when(cuadrillas.findFirstByEspecialidadAndDisponibleTrueOrderByNombreAsc(TipoDeReclamo.BACHEO))
                .thenAnswer(inv -> cuadrilla.isDisponible() ? Optional.of(cuadrilla) : Optional.empty());
        svcCuadrillas = new SvcCuadrillas(cuadrillas, reclamos, broker);
        svcReclamos = new SvcReclamos(reclamos, mock(CiudadanoRepository.class), mock(SvcBarrios.class),
                mock(GeoClient.class), broker, mock(TransactionTemplate.class), List.of());
        // Contrato MVC sin filtros de seguridad; sus pruebas existentes verifican roles y JWT.
        mvc = MockMvcBuilders.standaloneSetup(new ReclamoController(svcReclamos, svcCuadrillas))
                .setControllerAdvice(new ManejadorDeErrores()).build();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void estadoAsignadoDevuelve409YElReclamoSigueAsignable(boolean automatica) throws Exception {
        mvc.perform(put("/reclamos/{id}/estado", reclamo.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ASIGNADO\"}"))
                .andExpect(status().isConflict());
        assertThat(reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(reclamo.getCuadrilla()).isNull();
        assertThat(cuadrilla.isDisponible()).isTrue();
        verifyNoInteractions(broker);

        if (automatica) {
            assertThat(svcCuadrillas.asignar(reclamo)).isTrue();
        } else {
            mvc.perform(put("/reclamos/{id}/asignar-cuadrilla", reclamo.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cuadrillaId\":\"" + cuadrilla.getId() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cuadrillaId").value(cuadrilla.getId().toString()));
        }
        assertThat(reclamo.getEstado()).isEqualTo(Estado.ASIGNADO);
        assertThat(reclamo.getCuadrilla()).isSameAs(cuadrilla);
        assertThat(cuadrilla.isDisponible()).isFalse();
        ArgumentCaptor<Evento> evento = ArgumentCaptor.forClass(Evento.class);
        verify(broker).publicar(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.RECLAMO_ASIGNADO);
    }

    @Test
    void cuadrillaOcupadaNoCambiaElReclamo() {
        cuadrilla.marcarOcupada();
        assertThatThrownBy(() -> svcCuadrillas.asignarCuadrilla(reclamo.getId(), cuadrilla.getId()))
                .isInstanceOf(ConflictoException.class);
        assertThat(reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(reclamo.getCuadrilla()).isNull();
        verifyNoInteractions(broker);
    }

    @Test
    void otraEspecialidadNoCambiaElReclamoNiOcupaLaCuadrilla() {
        Cuadrilla otra = new Cuadrilla("Arbolado 1", TipoDeReclamo.ARBOLADO);
        UUID id = UUID.randomUUID();
        when(cuadrillas.buscarConBloqueo(id)).thenReturn(Optional.of(otra));
        assertThatThrownBy(() -> svcCuadrillas.asignarCuadrilla(reclamo.getId(), id))
                .isInstanceOf(ConflictoException.class);
        assertThat(reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(reclamo.getCuadrilla()).isNull();
        assertThat(otra.isDisponible()).isTrue();
        verifyNoInteractions(broker);
    }

    @Test
    void sinCuadrillasLibresSiguePendiente() {
        cuadrilla.marcarOcupada();
        assertThat(svcCuadrillas.asignar(reclamo)).isFalse();
        assertThat(reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(reclamo.getCuadrilla()).isNull();
        verifyNoInteractions(broker);
    }

    @Test
    void unReclamoFinalNoOcupaUnaCuadrilla() {
        reclamo.cambiarEstado(Estado.RECHAZADO);
        assertThatThrownBy(() -> svcCuadrillas.asignarCuadrilla(reclamo.getId(), cuadrilla.getId()))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(reclamo.getEstado()).isEqualTo(Estado.RECHAZADO);
        assertThat(reclamo.getCuadrilla()).isNull();
        assertThat(cuadrilla.isDisponible()).isTrue();
        verifyNoInteractions(broker);
    }

    @Test
    void resolverLiberaYReasignaLaMismaCuadrillaAlPendiente() {
        assertThat(svcCuadrillas.asignar(reclamo)).isTrue();
        Reclamo pendiente = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Otro pozo");
        when(reclamos.findFirstByTipoAndEstadoInAndCuadrillaIsNullOrderByFechaCreacionAsc(
                TipoDeReclamo.BACHEO, Estado.PENDIENTES_DE_ASIGNACION)).thenReturn(Optional.of(pendiente));

        svcReclamos.cambiarEstado(reclamo.getId(), Estado.EN_PROCESO);
        svcReclamos.cambiarEstado(reclamo.getId(), Estado.RESUELTO);
        svcCuadrillas.actualizar(Evento.de(TipoEvento.RECLAMO_RESUELTO,
                reclamo.getId(), reclamo.getBarrio().getNombre()));

        assertThat(reclamo.getEstado()).isEqualTo(Estado.RESUELTO);
        assertThat(pendiente.getEstado()).isEqualTo(Estado.ASIGNADO);
        assertThat(pendiente.getCuadrilla()).isSameAs(cuadrilla);
        assertThat(cuadrilla.isDisponible()).isFalse();
        ArgumentCaptor<Evento> eventos = ArgumentCaptor.forClass(Evento.class);
        verify(broker, times(4)).publicar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(Evento::tipo).containsExactly(
                TipoEvento.RECLAMO_ASIGNADO, TipoEvento.RECLAMO_ESTADO_CAMBIADO,
                TipoEvento.RECLAMO_RESUELTO, TipoEvento.RECLAMO_ASIGNADO);
    }
}
