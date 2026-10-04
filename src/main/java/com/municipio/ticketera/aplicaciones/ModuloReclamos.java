package com.municipio.ticketera.aplicaciones;

import com.municipio.ticketera.config.Permisos;
import com.municipio.ticketera.config.WebServiceConfig;
import com.municipio.ticketera.controller.AuthController;
import com.municipio.ticketera.controller.CiudadanoController;
import com.municipio.ticketera.controller.CuadrillaController;
import com.municipio.ticketera.controller.ReclamoController;
import com.municipio.ticketera.controller.SoapReclamos;
import com.municipio.ticketera.messaging.ConsumidorCuadrillas;
import com.municipio.ticketera.patterns.factory.ReclamoFactory;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.GeoClient;
import com.municipio.ticketera.service.SvcAuth;
import com.municipio.ticketera.service.SvcCiudadanos;
import com.municipio.ticketera.service.SvcCuadrillas;
import com.municipio.ticketera.service.SvcReclamos;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Operaciones de reclamos, ciudadanos y cuadrillas, con autenticacion y SOAP. */
@EnableJpaRepositories(basePackageClasses = ReclamoRepository.class)
@ComponentScan(basePackageClasses = ReclamoFactory.class)
@Import({SvcReclamos.class, SvcCuadrillas.class, SvcCiudadanos.class, SvcAuth.class, GeoClient.class,
        Permisos.class, WebServiceConfig.class, ReclamoController.class, CuadrillaController.class,
        CiudadanoController.class, AuthController.class, SoapReclamos.class, ConsumidorCuadrillas.class})
public class ModuloReclamos {
}
