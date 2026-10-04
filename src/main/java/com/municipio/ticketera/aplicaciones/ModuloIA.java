package com.municipio.ticketera.aplicaciones;

import com.municipio.ticketera.controller.ResumenZonaController;
import com.municipio.ticketera.messaging.ConsumidorIA;
import com.municipio.ticketera.patterns.strategy.CriticidadStrategy;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.repository.UsuarioRepository;
import com.municipio.ticketera.service.CacheResumenes;
import com.municipio.ticketera.service.ComparadorDeReclamosStub;
import com.municipio.ticketera.service.DetectorDeDuplicados;
import com.municipio.ticketera.service.GeneradorDeResumenStub;
import com.municipio.ticketera.service.LlmClient;
import com.municipio.ticketera.service.SvcIA;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Resumenes, criticidad y duplicados sobre el esquema compartido. */
@EnableJpaRepositories(basePackageClasses = ReclamoRepository.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {CiudadanoRepository.class, CuadrillaRepository.class, UsuarioRepository.class}))
@ComponentScan(basePackageClasses = CriticidadStrategy.class)
@Import({SvcIA.class, ResumenZonaController.class, ConsumidorIA.class, CacheResumenes.class,
        DetectorDeDuplicados.class, GeneradorDeResumenStub.class, ComparadorDeReclamosStub.class, LlmClient.class})
public class ModuloIA {
}
