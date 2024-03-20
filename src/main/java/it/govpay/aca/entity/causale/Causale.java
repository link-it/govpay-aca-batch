package it.govpay.aca.entity.causale;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;

public interface Causale extends Serializable {
	public String encode() throws UnsupportedEncodingException;
	public String getSimple() throws UnsupportedEncodingException;
}