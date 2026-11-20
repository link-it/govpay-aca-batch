package it.govpay.gpd.collaudo;

public interface Caso {

	String id();

	String versione();

	String gruppo();

	String descrizione();

	void esegui(ContestoCaso contesto);

	interface Corpo {
		void esegui(ContestoCaso contesto);
	}

	static Caso di(String id, String gruppo, String descrizione, Corpo corpo) {
		return new Caso() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public String versione() {
				return "1";
			}

			@Override
			public String gruppo() {
				return gruppo;
			}

			@Override
			public String descrizione() {
				return descrizione;
			}

			@Override
			public void esegui(ContestoCaso contesto) {
				corpo.esegui(contesto);
			}
		};
	}
}
