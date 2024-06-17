package it.govpay.gpd.entity;


import java.util.Objects;

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

	public MapEntry key(String key) {

		this.key = key;
		return this;
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


	public MapEntry value(String value) {

		this.value = value;
		return this;
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


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MapEntry mapEntry = (MapEntry) o;
		return Objects.equals(this.key, mapEntry.key) &&
				Objects.equals(this.value, mapEntry.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, value);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class MapEntry {\n");
		sb.append("    key: ").append(toIndentedString(key)).append("\n");
		sb.append("    value: ").append(toIndentedString(value)).append("\n");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Convert the given object to string with each line indented by 4 spaces
	 * (except the first line).
	 */
	private String toIndentedString(Object o) {
		if (o == null) {
			return "null";
		}
		return o.toString().replace("\n", "\n    ");
	}
}



