package it.govpay.gpd.test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import it.govpay.pendenze.model.TipoSoggetto;

/** Fixture scrivibile su {@code soggetti_debitori} (modello v3). */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "soggetti_debitori")
public class SoggettoDebitoreFullEntity {

	@Id
	private Long id;

	@Column(name = "id_documento", nullable = false)
	private long idDocumento;

	@Column(name = "ordine", nullable = false)
	private Integer ordine;

	@Column(name = "tipo", nullable = false)
	@Enumerated(EnumType.STRING)
	private TipoSoggetto tipo;

	@Column(name = "identificativo", nullable = false)
	private String identificativo;

	@Column(name = "anagrafica")
	private String anagrafica;

	@Column(name = "indirizzo")
	private String indirizzo;

	@Column(name = "civico")
	private String civico;

	@Column(name = "cap")
	private String cap;

	@Column(name = "localita")
	private String localita;

	@Column(name = "provincia")
	private String provincia;

	@Column(name = "nazione")
	private String nazione;

	@Column(name = "email")
	private String email;

	@Column(name = "cellulare")
	private String cellulare;
}
