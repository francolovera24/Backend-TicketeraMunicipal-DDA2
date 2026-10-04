package com.municipio.ticketera.integracion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

/** Entorno Compose aislado; la clave externa solo se transmite por entorno. */
final class ComposePruebas implements AutoCloseable {
    private final Path raiz = Path.of(System.getProperty("user.dir"));
    private final String archivo;
    private final String proyecto = "ticketera-it-" + UUID.randomUUID().toString().substring(0, 8);
    private final Path env;
    private final Map<String, String> variables;
    private boolean cerrado;

    ComposePruebas(String archivo) throws IOException {
        this(archivo, Map.of());
    }

    ComposePruebas(String archivo, Map<String, String> opciones) throws IOException {
        this.archivo = archivo;
        variables = new HashMap<>(Map.ofEntries(Map.entry("APP_PORT", "0"), Map.entry("IA_PORT", "0"),
                Map.entry("POSTGRES_PORT", "0"), Map.entry("RABBITMQ_PORT", "0"),
                Map.entry("RABBITMQ_ADMIN_PORT", "0"), Map.entry("REDIS_PORT", "0"),
                Map.entry("POSTGRES_DB", "ticketera"), Map.entry("POSTGRES_USER", "ticketera"),
                Map.entry("POSTGRES_PASSWORD", "clave-local-compose"), Map.entry("RABBITMQ_USER", "ticketera"),
                Map.entry("RABBITMQ_PASSWORD", "clave-local-compose"), Map.entry("IA_GENERADOR", "stub"),
                Map.entry("GEO_HABILITADO", "false"), Map.entry("LLM_API_KEY", ""),
                Map.entry("DUPLICADOS_HABILITADO", "true"),
                Map.entry("JWT_SECRET", "test-compose-separado-0123456789-abcdef")));
        variables.putAll(opciones);
        env = raiz.resolve("target/" + proyecto + ".env");
        Files.createDirectories(env.getParent());
        Files.write(env, variables.entrySet().stream()
                .map(v -> v.getKey() + "=" + (v.getKey().equals("LLM_API_KEY") ? "" : v.getValue())).toList());
    }

    void arrancar() throws IOException {
        ejecutar("up", "--build", "--detach", "--wait", "--wait-timeout", "180");
    }

    String ejecutar(String... argumentos) throws IOException {
        List<String> comando = new ArrayList<>(List.of(System.getProperty("docker.bin", "docker"), "compose",
                "--project-name", proyecto, "--env-file", env.toString(), "--file", archivo));
        comando.addAll(List.of(argumentos));
        Path log = raiz.resolve("target/" + proyecto + "-" + UUID.randomUUID() + ".log");
        ProcessBuilder builder = new ProcessBuilder(comando).directory(raiz.toFile()).redirectError(log.toFile());
        builder.environment().putAll(variables);
        Path docker = Path.of(comando.get(0));
        if (docker.getParent() != null) {
            String clavePath = builder.environment().keySet().stream()
                    .filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
            builder.environment().put(clavePath, docker.toAbsolutePath().getParent() + File.pathSeparator
                    + builder.environment().getOrDefault(clavePath, ""));
        }
        // Los builds pueden exceder el buffer de un pipe; su salida se lee desde archivo.
        Path salida = Path.of(log + ".out");
        Process proceso = builder.redirectOutput(salida.toFile()).start();
        try {
            if (!proceso.waitFor(15, TimeUnit.MINUTES)) {
                proceso.destroyForcibly();
                throw new IOException("Compose excedio el tiempo limite; logs en " + log);
            }
        } catch (InterruptedException ex) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Verificacion de Compose interrumpida", ex);
        }
        if (proceso.exitValue() != 0) {
            throw new IOException("Compose " + argumentos[0] + " fallo; logs en " + log + " y " + salida);
        }
        return Files.readString(salida);
    }

    TestRestTemplate cliente(String servicio, int puerto) throws IOException {
        String direccion = ejecutar("port", servicio, Integer.toString(puerto)).strip();
        int publicado = Integer.parseInt(direccion.substring(direccion.lastIndexOf(':') + 1));
        return new TestRestTemplate(new RestTemplateBuilder().rootUri("http://localhost:" + publicado)
                .setConnectTimeout(java.time.Duration.ofSeconds(5)).setReadTimeout(java.time.Duration.ofSeconds(60)));
    }

    @Override
    public void close() throws IOException {
        if (cerrado) return;
        try {
            Files.writeString(raiz.resolve("target/" + proyecto + "-contenedores.log"), ejecutar("logs", "--no-color"));
        } finally {
            ejecutar("down", "--volumes", "--remove-orphans");
            cerrado = true;
        }
    }
}
