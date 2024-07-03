package it.govpay.gpd.entity;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
	Metadata.JSON_PROPERTY_MAP_ENTRIES
})
public class Metadata {

	public static final String JSON_PROPERTY_MAP_ENTRIES = "mapEntries";
	private List<MapEntry> mapEntries = null;

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
}