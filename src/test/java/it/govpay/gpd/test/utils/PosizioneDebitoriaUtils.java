package it.govpay.gpd.test.utils;

import java.time.OffsetDateTime;

import it.govpay.common.entity.DominioEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.entity.VersamentoGpdEntity.TIPO;
import it.govpay.gpd.test.costanti.Costanti;
import it.govpay.gpd.test.entity.ApplicazioneEntity;
import it.govpay.gpd.test.entity.DocumentoFullEntity;
import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;
import it.govpay.gpd.test.entity.SoggettoDebitoreFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DocumentoFullRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.OpzionePagamentoFullRepository;
import it.govpay.gpd.test.repository.SoggettoDebitoreFullRepository;
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

	public static SoggettoDebitoreFullEntity creaSoggettoDebitoreOrdineZero(SoggettoDebitoreFullRepository soggettoDebitoreFullRepository, Long idDocumento) {
		SoggettoDebitoreFullEntity ultimo = soggettoDebitoreFullRepository.findFirstByOrderByIdDesc();
		Long id = ultimo != null ? ultimo.getId() + 1 : 1L;
		SoggettoDebitoreFullEntity soggetto = SoggettoDebitoreFullEntity.builder()
				.id(id)
				.idDocumento(idDocumento)
				.ordine(0)
				.tipo(TipoSoggetto.F)
				.identificativo(Costanti.DEBITORE_CF)
				.anagrafica(Costanti.DEBITORE_ANAGRAFICA)
				.build();
		return soggettoDebitoreFullRepository.save(soggetto);
	}

	public static OpzionePagamentoFullEntity creaOpzione(OpzionePagamentoFullRepository opzionePagamentoFullRepository,
			Long idDocumento, TipologiaOpzionePagamento tipologia, StatoOpzionePagamento stato) {
		OffsetDateTime now = OffsetDateTime.now();
		OpzionePagamentoFullEntity ultimo = opzionePagamentoFullRepository.findFirstByOrderByIdDesc();
		Long id = ultimo != null ? ultimo.getId() + 1 : 1L;
		OpzionePagamentoFullEntity opzione = OpzionePagamentoFullEntity.builder()
				.id(id)
				.idDocumento(idDocumento)
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
