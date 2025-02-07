package it.govpay.gpd.utils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import it.govpay.gpd.costanti.Costanti;

public class OffsetDateTimeDeserializer extends StdScalarDeserializer<OffsetDateTime> {

	private static final long serialVersionUID = 1L;

	private transient DateTimeFormatter formatter;

	public OffsetDateTimeDeserializer() {
		this(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
	}

	public OffsetDateTimeDeserializer(String format) {
		super(OffsetDateTime.class);
		this.formatter = DateTimeFormatter.ofPattern(format,Locale.getDefault());
	}

	@Override
	public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		try {
			JsonToken currentToken = jsonParser.getCurrentToken();
			if (currentToken == JsonToken.VALUE_STRING) {
				return parseOffsetDateTime(jsonParser.getText(), this.formatter);
			} else {
				return null;
			}
		} catch (IOException | DateTimeParseException e) {
			throw new IOException(e);
		}
	}

	public OffsetDateTime parseOffsetDateTime(String value, DateTimeFormatter formatter)  {
		if (value != null && !value.trim().isEmpty()) {
			String dateString = value.trim();
			try {
				return OffsetDateTime.parse(dateString, formatter);
			}catch (DateTimeParseException e) {
				ZoneOffset offset = ZoneOffset.ofHoursMinutes(1, 0); // CET (Central European Time)
				LocalDateTime localDateTime = LocalDateTime.parse(dateString, formatter);
				if (localDateTime != null) {
					return OffsetDateTime.of(localDateTime, offset);
				}
			}
		}

		return null;
	}
}
