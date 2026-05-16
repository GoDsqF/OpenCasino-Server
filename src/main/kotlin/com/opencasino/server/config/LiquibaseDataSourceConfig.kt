package com.opencasino.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(prefix = "spring.liquibase", name = ["enabled"], matchIfMissing = true)
@ConditionalOnExpression(
    "'\${spring.liquibase.url:}'.isEmpty() and '\${spring.r2dbc.url:}'.startsWith('r2dbc:postgresql:')"
)
class LiquibaseDataSourceConfig {

    @Bean
    @LiquibaseDataSource
    fun liquibaseDataSource(
        @Value("\${spring.r2dbc.url}") r2dbcUrl: String,
        @Value("\${spring.r2dbc.username}") username: String,
        @Value("\${spring.r2dbc.password:}") password: String,
    ): DataSource {
        val jdbcUrl = "jdbc:" + r2dbcUrl.removePrefix("r2dbc:")
        return DriverManagerDataSource(jdbcUrl, username, password)
    }
}
