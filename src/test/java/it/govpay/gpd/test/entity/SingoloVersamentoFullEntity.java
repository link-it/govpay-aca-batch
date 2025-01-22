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

@Setter
@Getter	
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "singoli_versamenti")
public class SingoloVersamentoFullEntity {
	
	public enum StatoSingoloVersamento {
		ESEGUITO,
		NON_ESEGUITO;
	}

	@Id
	private Long id;

	@Column(name = "cod_singolo_versamento_ente", nullable = false)
	private String codSingoloVersamentoEnte;
	
	@Column(name = "stato_singolo_versamento", nullable = false)
	@Enumerated(EnumType.STRING)
	private StatoSingoloVersamento statoSingoloVersamento;
	
	@Column(name = "importo_singolo_versamento", nullable = false)
	private Double importoSingoloVersamento;

	@Column(name = "id_dominio")
	private Long idDominio;
	
	@Column(name = "descrizione")
	private String descrizione;
	
	@Column(name = "descrizione_causale_rpt")
	private String descrizioneCausaleRPT;
	
	@Column(name = "tipo_contabilita")
	private String tipoContabilita;
	
	@Column(name = "codice_contabilita")
	private String codContabilita;
	
	@Column(name = "indice_dati")
	private Integer indiceDati;
	
	@Column(name = "tipo_bollo")
	private String tipoBollo;
	
	@Column(name = "hash_documento")
	private String hashDocumento;
	
	@Column(name = "provincia_residenza")
	private String provinciaResidenza;
	
	@Column(name = "id_tributo")
	private Long idTributo;
	
	@Column(name = "id_iban_accredito")
	private Long idIbanAccredito;
	
	@Column(name = "id_iban_appoggio")
	private Long idIbanAppoggio;
	
	@Column(name = "id_versamento", nullable = false)
	private long idVersamento;
	
	@Column(name = "metadata")
	private String metadata;
		
}


