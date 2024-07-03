package it.govpay.gpd.entity;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
@JsonPropertyOrder({
	MapEntry.JSON_PROPERTY_KEY,
	MapEntry.JSON_PROPERTY_VALUE
})
public class MapEntry {

	public static final String JSON_PROPERTY_KEY = "key";
	private String key;

	public static final String JSON_PROPERTY_VALUE = "value";
	private String value;

	public MapEntry() {
		// donothing
	}

	/**
	 * Get key
	 * @return key
	 **/
	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_KEY)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getKey() {
		return key;
	}


	@JsonProperty(JSON_PROPERTY_KEY)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setKey(String key) {
		this.key = key;
	}

	/**
	 * Get value
	 * @return value
	 **/
	@javax.annotation.Nullable
	@JsonProperty(JSON_PROPERTY_VALUE)
	@JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

	public String getValue() {
		return value;
	}


	@JsonProperty(JSON_PROPERTY_VALUE)
	@JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
	public void setValue(String value) {
		this.value = value;
	}
}