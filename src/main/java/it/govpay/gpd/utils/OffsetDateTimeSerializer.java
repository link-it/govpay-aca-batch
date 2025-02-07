package it.govpay.gpd.utils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import it.govpay.gpd.costanti.Costanti;


public class OffsetDateTimeSerializer extends StdScalarSerializer<OffsetDateTime> {

	private static final long serialVersionUID = 1L;

	private transient DateTimeFormatter formatter;

	public OffsetDateTimeSerializer() {
		this(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
	}
	
	public OffsetDateTimeSerializer(String format) {
		super(OffsetDateTime.class);
		this.formatter = DateTimeFormatter.ofPattern(format);
	}

	@Override
	public void serialize(OffsetDateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
		String dateTimeAsString = dateTime != null ? this.formatter.format(dateTime) : null;
		jsonGenerator.writeString(dateTimeAsString);
	}
}
