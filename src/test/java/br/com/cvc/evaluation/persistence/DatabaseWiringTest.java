package br.com.cvc.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Prova que a persistência está ligada ponta a ponta: DataSource apontando para um
 * PostgreSQL real (Dev Services, ADR-001) e Flyway executando na subida.
 *
 * <p>Ainda não há entidades JPA — elas chegam junto com a primeira migração (M1.2),
 * então este teste não injeta {@code EntityManagerFactory}: sem entidades o Quarkus
 * não cria unidade de persistência.</p>
 *
 * <p>Sem Docker disponível o Dev Services não sobe e o teste falha na inicialização:
 * é intencional, para não rodar contra um banco diferente do de produção.</p>
 */
@QuarkusTest
class DatabaseWiringTest {
    @Inject
    AgroalDataSource dataSource;

    @Test
    void connectsToPostgres() throws SQLException {
        try (var connection = dataSource.getConnection();
                        var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                        "select current_database(), current_setting('server_version_num')")) {

            assertTrue(result.next(), "a consulta deve devolver uma linha");
            assertNotNull(result.getString(1), "o database atual deve estar definido");
            assertTrue(result.getInt(2) >= 160000,
                            "esperado PostgreSQL 16, veio " + result.getString(2));
        }
    }

    @Test
    void flywayRanAtStartup() throws SQLException {
        try (var connection = dataSource.getConnection();
                        var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                        "select to_regclass('public.flyway_schema_history') is not null")) {

            assertTrue(result.next());
            assertTrue(result.getBoolean(1),
                            "a tabela flyway_schema_history deve existir depois do migrate-at-start");
        }
    }

    @Test
    void hibernateDoesNotManageTheSchema() throws SQLException {
        // generation=none: nada além do controle do Flyway deve existir no schema.
        try (var connection = dataSource.getConnection();
                        var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                        "select count(*) from information_schema.tables "
                                                        + "where table_schema = 'public' and table_name not like 'flyway%'")) {

            assertTrue(result.next());
            assertEquals(0, result.getInt(1),
                            "nenhuma tabela de aplicação deve existir antes da primeira migração");
        }
    }
}
