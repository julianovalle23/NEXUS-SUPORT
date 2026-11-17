package com.nexus.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Inicializa la base antes de que Hibernate valide el esquema:
 * <ul>
 *   <li>Base vacía (sin la tabla incidente): carga database/init/01..04 (esquema y datos iniciales).</li>
 *   <li>Siempre: aplica los cambios idempotentes de database/init/05-mejoras.sql.</li>
 * </ul>
 */
@Configuration
public class InicializacionBaseDatosConfig {

    private static final List<String> SCRIPTS_INICIALES =
            List.of("01-schema.sql", "02-catalogos.sql", "03-articulos.sql", "04-incidentes.sql");
    private static final List<String> SCRIPTS_CAMBIOS = List.of("05-mejoras.sql");

    @Bean
    DataSourceScriptDatabaseInitializer inicializadorBaseDatos(
            DataSource dataSource, @Value("${nexus.bd.scripts:file:../database/init/}") String ubicacion) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(SCRIPTS_INICIALES.stream().map(ubicacion::concat).toList());
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setEncoding(StandardCharsets.UTF_8);
        return new DataSourceScriptDatabaseInitializer(dataSource, settings) {
            @Override
            public boolean initializeDatabase() {
                boolean inicial = !esquemaCreado(dataSource) && super.initializeDatabase();
                aplicarCambios(dataSource, ubicacion);
                return inicial;
            }
        };
    }

    private static void aplicarCambios(DataSource dataSource, String ubicacion) {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        SCRIPTS_CAMBIOS.forEach(s -> populator.addScript(loader.getResource(ubicacion + s)));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private static boolean esquemaCreado(DataSource dataSource) {
        try (Connection conexion = dataSource.getConnection();
             ResultSet tablas = conexion.getMetaData().getTables(null, null, "incidente", new String[]{"TABLE"})) {
            return tablas.next();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo verificar el esquema de la base de datos", e);
        }
    }
}
