package it.govpay.gpd.test.entity;

import java.time.OffsetDateTime;
import java.util.Set;

import it.govpay.gpd.entity.VersamentoGpdEntity.TIPO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "versamenti")
public class VersamentoFullEntity {

	@Id
	private Long id;
	
	@Column(name = "id_dominio", nullable = false)
	private long idDominio;
	
	@Column(name = "id_applicazione", nullable = false)
	private long idApplicazione;
	
	@Column(name = "id_uo")
	private Long idUo;
	
	@Column(name = "cod_versamento_ente" , nullable = false)
	private String codVersamentoEnte;
	
	@Column(name = "stato_versamento", nullable = false)
	@Enumerated(EnumType.STRING)
	private it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento statoVersamento;

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

	@Column(name = "debitore_indirizzo")
	private String debitoreIndirizzo;

	@Column(name = "debitore_civico")
	private String debitoreCivico;

	@Column(name = "debitore_cap")
	private String debitoreCap;

	@Column(name = "debitore_localita")
	private String debitoreLocalita;

	@Column(name = "debitore_provincia")
	private String debitoreProvincia;

	@Column(name = "debitore_nazione")
	private String debitoreNazione;

	@Column(name = "debitore_email")
	private String debitoreEmail;

	@Column(name = "debitore_telefono")
	private String debitoreTelefono;

	@Column(name = "debitore_cellulare")
	private String debitoreCellulare;

	@Column(name = "debitore_fax")
	private String debitoreFax;

	@Column(name = "debitore_tipo", nullable = false)
	@Enumerated(EnumType.STRING)
	private TIPO debitoreTipo;

	@Column(name = "debitore_anagrafica", nullable = false)
	private String debitoreAnagrafica;

	@Column(name = "iuv_versamento")
	private String iuvVersamento;
	
	@Column(name = "numero_avviso")
	private String numeroAvviso;
	
	@Column(name = "cod_rata")
	private String codRata;
	
	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
	
	@OneToMany(mappedBy = "versamento")
	private Set<SingoloVersamentoFullEntity> singoliVersamenti;
}
