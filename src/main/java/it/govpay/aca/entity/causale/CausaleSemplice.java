package it.govpay.aca.entity.causale;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public class CausaleSemplice implements Causale {
	private static final long serialVersionUID = 1L;
	private String causale;

	@Override
	public String encode() throws UnsupportedEncodingException {
		if(this.causale == null) return null;
		return "01 " + Base64.getEncoder().encodeToString(this.causale.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String getSimple() throws UnsupportedEncodingException {
		return this.getCausale();
	}

	public void setCausale(String causale) {
		this.causale = causale;
	}

	public String getCausale() {
		return this.causale;
	}

	@Override
	public String toString() {
		return this.causale;
	}

}
