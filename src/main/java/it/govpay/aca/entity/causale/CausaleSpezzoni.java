package it.govpay.aca.entity.causale;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;


public class CausaleSpezzoni implements Causale {
	private static final long serialVersionUID = 1L;
	private List<String> spezzoni;

	@Override
	public String encode() throws UnsupportedEncodingException {
		if(this.spezzoni == null) return null;
		String encoded = "02";
		for(String spezzone : this.spezzoni) {
			encoded += " " + Base64.getEncoder().encodeToString(spezzone.getBytes(StandardCharsets.UTF_8));
		}
		return encoded;
	}

	@Override
	public String getSimple() throws UnsupportedEncodingException {
		if(this.spezzoni != null && !this.spezzoni.isEmpty())
			return this.spezzoni.get(0);

		return "";
	}

	public void setSpezzoni(List<String> spezzoni) {
		this.spezzoni = spezzoni;
	}

	public List<String> getSpezzoni() {
		return this.spezzoni;
	}

	@Override
	public String toString() {
		return StringUtils.join(this.spezzoni, "; ");
	}

}
