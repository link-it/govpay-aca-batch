package it.govpay.aca.test.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import it.govpay.aca.entity.VersamentoAcaEntity.TIPO;
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
@Table(name = "versamenti")
public class VersamentoFullEntity {

	@Id
	private Long id;
	
	@Column(name = "id_dominio", nullable = false)
	private long idDominio;
	
	@Column(name = "id_applicazione", nullable = false)
	private long idApplicazione;
	
	@Column(name = "cod_versamento_ente" , nullable = false)
	private String codVersamentoEnte;
	
	@Column(name = "stato_versamento", nullable = false)
	@Enumerated(EnumType.STRING)
	private it.govpay.aca.entity.VersamentoAcaEntity.StatoVersamento statoVersamento;

	@Column(name = "importo_totale", nullable = false)
	private Double importoTotale;

	@Column(name = "data_creazione", nullable = false)
	private OffsetDateTime dataCreazione;
	
	@Column(name = "data_validita")
	private OffsetDateTime dataValidita;
	
	@Column(name = "data_scadenza")
	private OffsetDateTime dataScadenza;
	
	@Column(name = "causale_versamento")
	private String causaleVersamento;

	@Column(name = "debitore_identificativo", nullable = false)
	private String debitoreIdentificativo;

	@Column(name = "debitore_tipo", nullable = false)
	@Enumerated(EnumType.STRING)
	private TIPO debitoreTipo;
	
	@Column(name = "debitore_anagrafica", nullable = false)
	private String debitoreAnagrafica;

	@Column(name = "iuv_versamento")
	private String iuvVersamento;
	
	@Column(name = "numero_avviso")
	private String numeroAvviso;
	
	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;
	
	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
