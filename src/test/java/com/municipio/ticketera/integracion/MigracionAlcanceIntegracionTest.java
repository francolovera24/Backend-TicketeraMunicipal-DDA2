package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigracionAlcanceIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void v9ConservaFilasFotosRelacionesYEstadosDeV8(boolean columnaExistente) {
        var fuente = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var esquema = "prueba_" + UUID.randomUUID().toString().replace("-", "");
        fuente.setSchema(esquema);
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .execute("CREATE SCHEMA " + esquema);
        Flyway.configure().dataSource(fuente).defaultSchema(esquema).schemas(esquema).target("8").load().migrate();
        var jdbc = new JdbcTemplate(fuente);
        UUID vecino = UUID.randomUUID();
        jdbc.update("INSERT INTO ciudadano(id,nombre,contacto) VALUES (?,?,?)", vecino, "Vecino de prueba", "migracion@example.com");
        UUID barrio = jdbc.queryForObject("SELECT id FROM barrio LIMIT 1", UUID.class);
        UUID cuadrilla = jdbc.queryForObject("SELECT id FROM cuadrilla LIMIT 1", UUID.class);
        UUID original = UUID.randomUUID();
        UUID duplicado = UUID.randomUUID();
        String alta = "INSERT INTO reclamo(id,tipo,titulo,descripcion,direccion,barrio_id,ciudadano_id,estado,"
                + "fecha_creacion,fecha_actualizacion,score_criticidad,urgente) VALUES (?, 'BACHEO', 'Pozo',"
                + "'Problema de prueba','Calle 123',?,?,?,now(),now(),35,false)";
        jdbc.update(alta, original, barrio, vecino, "ASIGNADO");
        jdbc.update("UPDATE reclamo SET cuadrilla_id=? WHERE id=?", cuadrilla, original);
        jdbc.update(alta, duplicado, barrio, vecino, "DUPLICADO");
        jdbc.update("UPDATE reclamo SET reclamo_original_id=? WHERE id=?", original, duplicado);
        jdbc.update("INSERT INTO reclamo_imagen(reclamo_id,media_type,contenido) VALUES (?, 'image/png', ?)",
                original, new byte[]{1, 2, 3});
        jdbc.update("UPDATE reclamo SET tiene_imagen=true WHERE id=?", original);
        if (columnaExistente) {
            jdbc.execute("ALTER TABLE reclamo ADD COLUMN alcance varchar(10)");
            jdbc.execute("ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_alcance CHECK (alcance IN ('PERSONAL','COLECTIVO'))");
            jdbc.update("UPDATE reclamo SET alcance='COLECTIVO' WHERE id=?", original);
        }
        var fotosAntes = jdbc.queryForList("SELECT * FROM reclamo_imagen");
        var antes = jdbc.queryForList("SELECT * FROM reclamo ORDER BY id");

        var flyway = Flyway.configure().dataSource(fuente).defaultSchema(esquema).schemas(esquema).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var despues = jdbc.queryForList("SELECT * FROM reclamo ORDER BY id");
        assertThat(despues).hasSize(antes.size());
        for (int i = 0; i < antes.size(); i++) {
            Map<String, Object> fila = despues.get(i);
            if (!columnaExistente) {
                assertThat(fila).containsEntry("alcance", null);
                fila.remove("alcance");
            }
            assertThat(fila).isEqualTo(antes.get(i));
        }
        assertThat(jdbc.queryForList("SELECT * FROM reclamo_imagen")).usingRecursiveComparison().isEqualTo(fotosAntes);
        jdbc.update("UPDATE reclamo SET alcance='COLECTIVO' WHERE id=?", original);
        assertThat(jdbc.queryForObject("SELECT alcance FROM reclamo WHERE id=?", String.class, original)).isEqualTo("COLECTIVO");
        assertThatThrownBy(() -> jdbc.update("UPDATE reclamo SET alcance='OTRO' WHERE id=?", original))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
