package it.govpay.gpd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity;

@DisplayName("Lettura JDBC delle date in ora Europe/Rome")
class LoggingBeanPropertyRowMapperTest {

	/**
	 * Riga con la sola data di ultima modifica. Letta come {@code OffsetDateTime} il
	 * driver restituisce l'ora della colonna marcata UTC, come quello di PostgreSQL.
	 */
	private static ResultSet riga(LocalDateTime colonna) throws SQLException {
		ResultSetMetaData metaData = mock(ResultSetMetaData.class);
		when(metaData.getColumnCount()).thenReturn(1);
		when(metaData.getColumnLabel(1)).thenReturn("data_ultima_modifica_aca");
		ResultSet rs = mock(ResultSet.class);
		when(rs.getMetaData()).thenReturn(metaData);
		when(rs.getObject(1, LocalDateTime.class)).thenReturn(colonna);
		lenient().when(rs.getObject(1, OffsetDateTime.class))
				.thenReturn(colonna == null ? null : colonna.atOffset(ZoneOffset.UTC));
		return rs;
	}

	@ParameterizedTest(name = "{0} letta come {1}")
	@CsvSource({
			"2026-01-15T12:00:00,     2026-01-15T12:00:00+01:00",
			"2026-07-15T12:00:00,     2026-07-15T12:00:00+02:00",
			"2026-07-15T00:00:00.123, 2026-07-15T00:00:00.123+02:00"
	})
	@DisplayName("Posizione debitoria: la colonna e' ora italiana, d'inverno e d'estate")
	void posizioneDebitoria(LocalDateTime colonna, OffsetDateTime attesa) throws SQLException {
		PosizioneDebitoriaGpdEntity letta = new LoggingBeanPropertyRowMapper<>(PosizioneDebitoriaGpdEntity.class)
				.mapRow(riga(colonna), 0);

		assertEquals(attesa, letta.getDataUltimaModificaAca());
	}

	@Test
	@DisplayName("Pendenza legacy: stessa regola di lettura")
	void pendenza() throws SQLException {
		VersamentoGpdEntity letta = new LoggingBeanPropertyRowMapper<>(VersamentoGpdEntity.class)
				.mapRow(riga(LocalDateTime.parse("2026-07-15T12:00:00")), 0);

		assertEquals(OffsetDateTime.parse("2026-07-15T12:00:00+02:00"), letta.getDataUltimaModificaAca());
	}

	@Test
	@DisplayName("Data assente: resta nulla")
	void dataAssente() throws SQLException {
		PosizioneDebitoriaGpdEntity letta = new LoggingBeanPropertyRowMapper<>(PosizioneDebitoriaGpdEntity.class)
				.mapRow(riga(null), 0);

		assertNull(letta.getDataUltimaModificaAca());
	}
}
