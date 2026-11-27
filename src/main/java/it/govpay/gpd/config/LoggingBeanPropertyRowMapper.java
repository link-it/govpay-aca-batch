package it.govpay.gpd.config;

import java.beans.PropertyDescriptor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import it.govpay.common.utils.DateTimePatterns;
import it.govpay.gpd.entity.VersamentoGpdEntity;

/**
 * Row mapper dei reader JDBC, con log delle righe lette.
 *
 * <p>Le colonne temporali mappate su {@code OffsetDateTime} sono lette come ora locale
 * {@value DateTimePatterns#DEFAULT_TIME_ZONE}.
 */
public class LoggingBeanPropertyRowMapper<T> extends BeanPropertyRowMapper<T> {

	private static final ZoneId FUSO_DATABASE = ZoneId.of(DateTimePatterns.DEFAULT_TIME_ZONE);

	public LoggingBeanPropertyRowMapper(Class<T> mappedClass) {
		super(mappedClass);
	}

	@Override
	protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
		if (pd.getPropertyType() == OffsetDateTime.class) {
			LocalDateTime locale = rs.getObject(index, LocalDateTime.class);
			return locale == null ? null : locale.atZone(FUSO_DATABASE).toOffsetDateTime();
		}
		return super.getColumnValue(rs, index, pd);
	}

	@Override
	public T mapRow(ResultSet rs, int rowNum) throws SQLException {
		LoggerFactory.getLogger(getClass()).debug("Inizio mapping per rowNum {}. ResultSet metaData: {}", rowNum, rs.getMetaData());
		try {
			T result = super.mapRow(rs, rowNum);
			if (result instanceof VersamentoGpdEntity versamentoGpdEntity) {
				LoggerFactory.getLogger(getClass()).debug("Pendenza letta: id[{},{}], dataUltimaModificaACA={}", versamentoGpdEntity.getCodApplicazione(), versamentoGpdEntity.getCodVersamentoEnte(),
						versamentoGpdEntity.getDataUltimaModificaAca());
			} else {
				LoggerFactory.getLogger(getClass()).trace("Mapping riga {}: {}", rowNum, result);
			}
			return result;
		} catch (SQLException e) {
			LoggerFactory.getLogger(getClass()).error("Errore nel mapping della row {}: {}", rowNum, e.getMessage(), e);
			throw e;
		}
	}
}
