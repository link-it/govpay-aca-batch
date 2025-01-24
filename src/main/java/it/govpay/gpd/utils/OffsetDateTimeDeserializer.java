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

public class OffsetDateTimeDeserializer extends StdScalarDeserializer<OffsetDateTime> {

	private static final long serialVersionUID = 1L;
	
	private transient DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Utils.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);
	
	private transient DateTimeFormatter formatterMillis = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]",
            Locale.getDefault());
	
//	private transient DateTimeFormatter formatterMillis = DateTimeFormatter.ofPattern(Utils.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSSSSSSSS);
	
	public OffsetDateTimeDeserializer() {
        super(OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        try {
            JsonToken currentToken = jsonParser.getCurrentToken();
            if (currentToken == JsonToken.VALUE_STRING) {
            	return tryParseOffsetDateTime(jsonParser);
            } else {
            	return null;
            }
        } catch (IOException | DateTimeParseException e) {
        	throw new IOException(e);
        }
    }

	private OffsetDateTime tryParseOffsetDateTime(JsonParser jsonParser) throws IOException {
//		try {
			return parseOffsetDateTime(jsonParser.getText(), this.formatterMillis);
//		}catch (DateTimeParseException e) {
//			return parseOffsetDateTime(jsonParser.getText(), this.formatterMillis);	
//		}
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
