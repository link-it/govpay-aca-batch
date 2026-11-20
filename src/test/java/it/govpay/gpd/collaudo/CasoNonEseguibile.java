package it.govpay.gpd.collaudo;

public class CasoNonEseguibile extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CasoNonEseguibile(String motivo) {
		super(motivo);
	}
}
