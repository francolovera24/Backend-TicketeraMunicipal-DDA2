package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.repository.CuadrillaRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Observer: se suscribe al Broker y reacciona cuando cambia el estado de un
 * reclamo, sin que Svc_Reclamos (el emisor) conozca esta clase directamente.
 */
@Service
public class SvcCuadrillas implements Observador {

    private final CuadrillaRepository repo;
    private final Broker broker;

    public SvcCuadrillas(CuadrillaRepository repo, Broker broker) {
        this.repo = repo;
        this.broker = broker;
    }

    @PostConstruct
    public void suscribirseAlBroker() {
        broker.suscribir(this);
    }

    @Override
    public void actualizar(Evento evento) {
        if (evento.getTipo() == Evento.Tipo.RECLAMO_CREADO) {
            // en una version mas completa, ac\u00e1 se buscar\u00eda una cuadrilla
            // disponible y se le asignar\u00eda el reclamo automaticamente
        }
    }

    public void asignar(Long reclamoId, Long cuadrillaId) {
        Cuadrilla cuadrilla = repo.findById(cuadrillaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuadrilla no encontrada: " + cuadrillaId));
        cuadrilla.marcarOcupada();
        repo.save(cuadrilla);
    }

    public List<Cuadrilla> buscarDisponibles(String especialidad) {
        return repo.findByEspecialidadAndDisponibleTrue(especialidad);
    }
}
