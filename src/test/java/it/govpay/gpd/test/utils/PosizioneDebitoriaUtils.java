package it.govpay.gpd.test.utils;

import java.time.OffsetDateTime;

import it.govpay.common.entity.DominioEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.entity.VersamentoGpdEntity.TIPO;
import it.govpay.gpd.test.costanti.Costanti;
import it.govpay.gpd.test.entity.ApplicazioneEntity;
import it.govpay.gpd.test.entity.DocumentoFullEntity;
import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;
import it.govpay.gpd.test.entity.PdDebitoreFullEntity;
import it.govpay.gpd.test.entity.DebitoreFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DocumentoFullRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.OpzionePagamentoFullRepository;
import it.govpay.gpd.test.repository.DebitoreFullRepository;
import it.govpay.gpd.test.repository.PdDebitoreFullRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;
import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipoSoggetto;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;

/**
 * Fixture per il modello v3 (posizione/opzione/rata), stesso stile di
 * {@link VersamentoUtils} per il modello legacy. Dominio/applicazione sono quelli gia'
 * seminati da {@code data.sql} ({@code Costanti.CODAPPLICAZIONE}/{@code CODDOMINIO}).
 */
public class PosizioneDebitoriaUtils {

	private PosizioneDebitoriaUtils() {}

	public static DocumentoFullEntity creaDocumento(DocumentoFullRepository documentoFullRepository,
			ApplicazioneRepository applicazioneRepository, DominioRepository dominioRepository,
			String codDocumento, OffsetDateTime dataUltimaComunicazioneAca) {
		ApplicazioneEntity applicazione = applicazioneRepository.findByCodApplicazione(Costanti.CODAPPLICAZIONE);
		DominioEntity dominio = dominioRepository.findByCodDominio(Costanti.CODDOMINIO);

		OffsetDateTime now = OffsetDateTime.now();
		DocumentoFullEntity ultimo = documentoFullRepository.findFirstByOrderByIdDesc();
		Long id = ultimo != null ? ultimo.getId() + 1 : 1L;
		DocumentoFullEntity documento = DocumentoFullEntity.builder()
				.id(id)
				.idApplicazione(applicazione.getId())
				.idDominio(dominio.getId())
				.codDocumento(codDocumento)
				.descrizione("Pagamento " + codDocumento)
				.dataCreazione(now)
				.dataUltimoAggiornamento(now)
				.dataUltimaModificaAca(now)
				.dataUltimaComunicazioneAca(dataUltimaComunicazioneAca)
				.build();
		return documentoFullRepository.save(documento);
	}

	/** Primo debitore della posizione, con i dati di prova standard. */
	public static PdDebitoreFullEntity creaDebitore(DebitoreFullRepository debitoreFullRepository,
			PdDebitoreFullRepository pdDebitoreFullRepository, Long idDocumento) {
		return creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, idDocumento, 0,
				Costanti.DEBITORE_CF, Costanti.DEBITORE_ANAGRAFICA);
	}

	/**
	 * Un debitore della posizione: una riga in {@code debitori} (anagrafica) e una in
	 * {@code pd_debitori} (legame con la posizione, in ordine di {@code posizione}).
	 */
	public static PdDebitoreFullEntity creaDebitore(DebitoreFullRepository debitoreFullRepository,
			PdDebitoreFullRepository pdDebitoreFullRepository, Long idDocumento, int posizione,
			String identificativo, String anagrafica) {
		DebitoreFullEntity ultimoDebitore = debitoreFullRepository.findFirstByOrderByIdDesc();
		DebitoreFullEntity debitore = debitoreFullRepository.save(DebitoreFullEntity.builder()
				.id(ultimoDebitore != null ? ultimoDebitore.getId() + 1 : 1L)
				.tipo(TipoSoggetto.F)
				.identificativo(identificativo)
				.anagrafica(anagrafica)
				.build());

		PdDebitoreFullEntity ultimoPdDebitore = pdDebitoreFullRepository.findFirstByOrderByIdDesc();
		return pdDebitoreFullRepository.save(PdDebitoreFullEntity.builder()
				.id(ultimoPdDebitore != null ? ultimoPdDebitore.getId() + 1 : 1L)
				.idDocumento(idDocumento)
				.idDebitore(debitore.getId())
				.posizione(posizione)
				.build());
	}

	public static OpzionePagamentoFullEntity creaOpzione(OpzionePagamentoFullRepository opzionePagamentoFullRepository,
			Long idPdDebitore, TipologiaOpzionePagamento tipologia, StatoOpzionePagamento stato) {
		OffsetDateTime now = OffsetDateTime.now();
		OpzionePagamentoFullEntity ultimo = opzionePagamentoFullRepository.findFirstByOrderByIdDesc();
		Long id = ultimo != null ? ultimo.getId() + 1 : 1L;
		OpzionePagamentoFullEntity opzione = OpzionePagamentoFullEntity.builder()
				.id(id)
				.idPdDebitore(idPdDebitore)
				.tipologia(tipologia)
				.stato(stato)
				.dataCreazione(now)
				.dataUltimoAggiornamento(now)
				.build();
		return opzionePagamentoFullRepository.save(opzione);
	}

	public static VersamentoFullEntity creaRata(VersamentoFullRepository versamentoFullRepository,
			ApplicazioneRepository applicazioneRepository, DominioRepository dominioRepository,
			Long idOpzionePagamento, String codVersamentoEnte, int numeroRata, double importoTotale, StatoVersamento stato) {
		ApplicazioneEntity applicazione = applicazioneRepository.findByCodApplicazione(Costanti.CODAPPLICAZIONE);
		DominioEntity dominio = dominioRepository.findByCodDominio(Costanti.CODDOMINIO);

		OffsetDateTime now = OffsetDateTime.now();
		String numeroAvviso = VersamentoUtils.generaNumeroAvviso();
		String iuvVersamento = VersamentoUtils.getIuvFromNumeroAvviso(numeroAvviso);

		Long id = VersamentoUtils.getNextVersamentoId(versamentoFullRepository);
		VersamentoFullEntity rata = VersamentoFullEntity.builder()
				.id(id)
				.idApplicazione(applicazione.getId())
				.idDominio(dominio.getId())
				.codVersamentoEnte(codVersamentoEnte)
				.statoVersamento(stato)
				.importoTotale(importoTotale)
				.dataCreazione(now)
				.debitoreAnagrafica(Costanti.DEBITORE_ANAGRAFICA)
				.debitoreIdentificativo(Costanti.DEBITORE_CF)
				.debitoreTipo(TIPO.F)
				.numeroAvviso(numeroAvviso)
				.iuvVersamento(iuvVersamento)
				.idOpzionePagamento(idOpzionePagamento)
				.numeroRata(numeroRata)
				.dataUltimaModificaAca(now)
				.build();
		return versamentoFullRepository.save(rata);
	}
}
