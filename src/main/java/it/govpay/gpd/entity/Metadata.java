package it.govpay.gpd.entity;


import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
	Metadata.JSON_PROPERTY_MAP_ENTRIES
})
public class Metadata {
  
  public static final String JSON_PROPERTY_MAP_ENTRIES = "mapEntries";
  private List<MapEntry> mapEntries = null;
  
  public Metadata mapEntries(List<MapEntry> mapEntries) {

		this.mapEntries = mapEntries;
		return this;
	}

	/**
	 * Get mapEntries
	 * @return mapEntries
	 **/
	@javax.annotation.Nullable
	@JsonProperty(JSON_PROPERTY_MAP_ENTRIES)
	@JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

	public List<MapEntry> getValue() {
		return mapEntries;
	}


	@JsonProperty(JSON_PROPERTY_MAP_ENTRIES)
	@JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
	public void setValue(List<MapEntry> mapEntries) {
		this.mapEntries = mapEntries;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Metadata mapEntry = (Metadata) o;
		return Objects.equals(this.mapEntries, mapEntry.mapEntries);
	}

	@Override
	public int hashCode() {
		return Objects.hash(mapEntries);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class MapEntry {\n");
		sb.append("    mapEntries: ").append(toIndentedString(mapEntries)).append("\n");
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



