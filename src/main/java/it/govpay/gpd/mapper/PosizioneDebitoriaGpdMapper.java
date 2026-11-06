package it.govpay.gpd.mapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import it.govpay.gpd.client.v3.beans.DebtorModel;
import it.govpay.gpd.client.v3.beans.InstallmentModel;
import it.govpay.gpd.client.v3.beans.PaymentOptionModelV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.client.v3.beans.Stamp;
import it.govpay.gpd.client.v3.beans.TransferMetadataModel;
import it.govpay.gpd.client.v3.beans.TransferModel;
import it.govpay.gpd.client.v3.beans.TransferModel.IdTransferEnum;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.MapEntry;
import it.govpay.gpd.entity.Metadata;
import it.govpay.gpd.entity.OpzionePagamentoGpdEntity;
import it.govpay.gpd.entity.PendenzaGpdEntity;
import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.entity.SingoloVersamentoGpdEntity;
import it.govpay.gpd.repository.OpzionePagamentoGpdRepository;
import it.govpay.gpd.repository.PendenzaGpdRepository;
import it.govpay.gpd.repository.SingoloVersamentoFilters;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.utils.Utils;
import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.pendenze.service.CausaleVersamentoCodec;

/**
 * Mapping di una {@link PosizioneDebitoriaGpdEntity} (modello v3 di
 * govpay-common-pendenze) verso {@link PaymentPositionModelV3} (contratto GPD v3).
 * Stesso stile procedurale di {@link PaymentPositionModelRequestMapper} (repository
 * iniettati nel mapper stesso, metodi {@code @Named} che li interrogano) — qui serve
 * perche' una posizione ha piu' opzioni e ogni opzione piu' rate, a differenza del
 * legacy dove la cardinalita' e' sempre 1:1.
 */
@Mapper(componentModel = "spring")
public abstract class PosizioneDebitoriaGpdMapper {

	ObjectMapper objectMapper;

	OpzionePagamentoGpdRepository opzionePagamentoGpdRepository;

	PendenzaGpdRepository pendenzaGpdRepository;

	SingoloVersamentoGpdRepository singoloVersamentoGpdRepository;

	@Value("${it.govpay.gpd.standIn.enabled:true}")
	Boolean standIn;

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void setOpzionePagamentoGpdRepository(OpzionePagamentoGpdRepository opzionePagamentoGpdRepository) {
		this.opzionePagamentoGpdRepository = opzionePagamentoGpdRepository;
	}

	public void setPendenzaGpdRepository(PendenzaGpdRepository pendenzaGpdRepository) {
		this.pendenzaGpdRepository = pendenzaGpdRepository;
	}

	public void setSingoloVersamentoGpdRepository(SingoloVersamentoGpdRepository singoloVersamentoGpdRepository) {
		this.singoloVersamentoGpdRepository = singoloVersamentoGpdRepository;
	}

	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_IUPD, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapIupd")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_PAY_STAND_IN, source = "standIn")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_COMPANY_NAME, source = "posizioneDebitoriaGpdEntity.ragioneSocialeDominio")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_OFFICE_NAME, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapUoAnagrafica")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_PAYMENT_OPTION, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapPaymentOption")
	public abstract PaymentPositionModelV3 posizioneDebitoriaGpdToPaymentPositionModelV3Base(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity, boolean standIn) throws IOException;

	public PaymentPositionModelV3 posizioneDebitoriaGpdToPaymentPositionModelV3(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) throws IOException {
		return this.posizioneDebitoriaGpdToPaymentPositionModelV3Base(posizioneDebitoriaGpdEntity, this.standIn);
	}

	/**
	 * Payload per l'annullamento totale ({@code PUT ?toPublish=false}): a differenza
	 * del caricamento/aggiornamento, qui non ci sono opzioni DISPONIBILE da
	 * spedire (si arriva a questo metodo solo quando sono tutte ANNULLATA) - il contratto
	 * GPD v3 richiede comunque {@code paymentOption} non vuoto ({@code minItems: 1}),
	 * quindi si rimappano TUTTE le opzioni della posizione, senza i filtri di
	 * scadenza/validita' (qui non servono: la richiesta e' un puro cambio di stato verso
	 * DRAFT, non una pubblicazione).
	 */
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_IUPD, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapIupd")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_PAY_STAND_IN, source = "standIn")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_COMPANY_NAME, source = "posizioneDebitoriaGpdEntity.ragioneSocialeDominio")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_OFFICE_NAME, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapUoAnagrafica")
	@Mapping(target = PaymentPositionModelV3.JSON_PROPERTY_PAYMENT_OPTION, source = "posizioneDebitoriaGpdEntity", qualifiedByName = "mapPaymentOptionPerAnnullamento")
	public abstract PaymentPositionModelV3 posizioneDebitoriaGpdToPaymentPositionModelV3PerAnnullamentoBase(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity, boolean standIn) throws IOException;

	public PaymentPositionModelV3 posizioneDebitoriaGpdToPaymentPositionModelV3PerAnnullamento(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) throws IOException {
		return this.posizioneDebitoriaGpdToPaymentPositionModelV3PerAnnullamentoBase(posizioneDebitoriaGpdEntity, this.standIn);
	}

	@Named("mapPaymentOptionPerAnnullamento")
	public List<PaymentOptionModelV3> mapPaymentOptionPerAnnullamento(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) throws IOException {
		return mapPaymentOption(posizioneDebitoriaGpdEntity, false);
	}

	@Named("mapIupd")
	public String mapIupd(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) {
		return Utils.generaIupdPosizione(posizioneDebitoriaGpdEntity);
	}

	@Named("mapUoAnagrafica")
	public String mapUoAnagrafica(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) {
		if (posizioneDebitoriaGpdEntity == null || posizioneDebitoriaGpdEntity.getCodUo() == null) {
			return null;
		}

		if (!Costanti.EC.equals(posizioneDebitoriaGpdEntity.getCodUo())) {
			return posizioneDebitoriaGpdEntity.getUoDenominazione();
		}

		return null;
	}

	/**
	 * Una {@code PaymentOptionModelV3} per ogni opzione {@link StatoOpzionePagamento#DISPONIBILE}
	 * della posizione — le altre (ATTIVATA/ANNULLATA) non vengono mai spedite nel payload.
	 * Esclude le opzioni con tutte le rate scadute, o con una rata la cui due date precede
	 * la validity date dell'opzione (controlli pre-invio): un'eventuale mancanza di IUV,
	 * che invece blocca l'INTERA posizione, e'
	 * gia' stata verificata dal chiamante prima di arrivare qui (vedi
	 * {@code SendPosizioneDebitoriaToGpdProcessor}).
	 */
	@Named("mapPaymentOption")
	public List<PaymentOptionModelV3> mapPaymentOption(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) throws IOException {
		return mapPaymentOption(posizioneDebitoriaGpdEntity, true);
	}

	public List<PaymentOptionModelV3> mapPaymentOption(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity, boolean soloDisponibiliEValide) throws IOException {
		List<OpzionePagamentoGpdEntity> opzioni = soloDisponibiliEValide
				? this.opzionePagamentoGpdRepository.findByIdDocumentoAndStato(posizioneDebitoriaGpdEntity.getId(), StatoOpzionePagamento.DISPONIBILE)
				: this.opzionePagamentoGpdRepository.findByIdDocumento(posizioneDebitoriaGpdEntity.getId());

		List<PaymentOptionModelV3> list = new ArrayList<>();
		for (OpzionePagamentoGpdEntity opzione : opzioni) {
			List<PendenzaGpdEntity> rate = this.pendenzaGpdRepository.findByIdOpzionePagamentoOrderByNumeroRataAsc(opzione.getId());
			if (soloDisponibiliEValide && opzioneEsclusaPerScadenza(rate, opzione, posizioneDebitoriaGpdEntity)) {
				continue;
			}
			list.add(opzionePagamentoGpdToPaymentOptionModelV3(opzione, posizioneDebitoriaGpdEntity, rate));
		}
		return list;
	}

	/**
	 * @return {@code true} se l'opzione va esclusa dal payload: tutte le sue rate sono
	 *         scadute, oppure almeno una ha una due date antecedente alla validity date
	 *         dell'opzione (incongruenza di dati).
	 */
	private boolean opzioneEsclusaPerScadenza(List<PendenzaGpdEntity> rate, OpzionePagamentoGpdEntity opzione, PosizioneDebitoriaGpdEntity posizione) {
		LocalDateTime validityDate = Utils.calcolaValidityDatePosizione(posizione, opzione);
		boolean almenoUnaValida = false;
		for (PendenzaGpdEntity rata : rate) {
			LocalDateTime dueDate = Utils.calcolaDueDateRata(rata, opzione);
			if (validityDate != null && dueDate.isBefore(validityDate)) {
				return true;
			}
			if (Utils.isValidDueDate(rata, opzione)) {
				almenoUnaValida = true;
			}
		}
		return !almenoUnaValida;
	}

	public PaymentOptionModelV3 opzionePagamentoGpdToPaymentOptionModelV3(OpzionePagamentoGpdEntity opzione, PosizioneDebitoriaGpdEntity posizione, List<PendenzaGpdEntity> rate) throws IOException {
		PaymentOptionModelV3 paymentOption = new PaymentOptionModelV3();

		paymentOption.setDescription(posizione.getDescrizione());

		LocalDateTime validityDate = Utils.calcolaValidityDatePosizione(posizione, opzione);
		paymentOption.setValidityDate(Utils.toOffsetDateTimeCet(validityDate));
		paymentOption.setRetentionDate(Utils.toOffsetDateTimeCet(validityDate));

		// true solo se l'opzione ha una propria scadenza: si applica per-opzione, non
		// per-posizione, cosi' il modello CdS ENTRO/OLTRE (un'opzione per soglia, ognuna
		// con la propria data_scadenza) non ha bisogno di nessun caso speciale.
		paymentOption.setSwitchToExpired(opzione.getDataScadenza() != null);

		paymentOption.setDebtor(mapDebtor(posizione));

		boolean pianoRateale = opzione.getTipologia() == TipologiaOpzionePagamento.PIANO_RATEALE;

		List<InstallmentModel> installments = new ArrayList<>();
		for (PendenzaGpdEntity rata : rate) {
			installments.add(pendenzaGpdToInstallmentModel(rata, opzione, posizione, pianoRateale));
		}
		paymentOption.setInstallments(installments);

		return paymentOption;
	}

	/** GPD v3 ammette un solo debitore per opzione: soggetto di ordine 0. */
	public DebtorModel mapDebtor(PosizioneDebitoriaGpdEntity posizione) {
		DebtorModel debtor = new DebtorModel();
		if (posizione.getDebitoreTipo() != null) {
			debtor.setType(DebtorModel.TypeEnum.valueOf(posizione.getDebitoreTipo().name()));
		}
		debtor.setFiscalCode(posizione.getDebitoreIdentificativo());
		debtor.setFullName(posizione.getDebitoreAnagrafica());
		debtor.setStreetName(posizione.getDebitoreIndirizzo());
		debtor.setCivicNumber(posizione.getDebitoreCivico());
		debtor.setPostalCode(posizione.getDebitoreCap());
		debtor.setCity(posizione.getDebitoreLocalita());
		debtor.setProvince(posizione.getDebitoreProvincia());
		debtor.setCountry(posizione.getDebitoreNazione());
		debtor.setEmail(posizione.getDebitoreEmail());
		return debtor;
	}

	public InstallmentModel pendenzaGpdToInstallmentModel(PendenzaGpdEntity rata, OpzionePagamentoGpdEntity opzione,
			PosizioneDebitoriaGpdEntity posizione, boolean pianoRateale) throws IOException {
		InstallmentModel installment = new InstallmentModel();

		installment.setNav(rata.getNumeroAvviso());
		installment.setIuv(rata.getIuvVersamento());
		installment.setAmount(amountMapper(rata.getImportoTotale()));

		String descrizione = CausaleVersamentoCodec.decode(rata.getCausaleVersamento());
		if (descrizione == null) {
			descrizione = posizione.getDescrizione();
		}
		if (pianoRateale && rata.getNumeroRata() != null) {
			descrizione = (descrizione != null ? descrizione : "") + " - rata " + rata.getNumeroRata();
		}
		installment.setDescription(descrizione);

		LocalDateTime dueDate = Utils.calcolaDueDateRata(rata, opzione);
		installment.setDueDate(Utils.toOffsetDateTimeCet(dueDate));

		List<SingoloVersamentoGpdEntity> singoliVersamenti = this.singoloVersamentoGpdRepository
				.findAll(SingoloVersamentoFilters.byVersamentoId(rata.getId()));
		List<TransferModel> transfer = new ArrayList<>();
		for (SingoloVersamentoGpdEntity singoloVersamento : singoliVersamenti) {
			transfer.add(singoloVersamentoGpdToTransferModel(singoloVersamento, rata, posizione));
		}
		installment.setTransfer(transfer);

		return installment;
	}

	public Long amountMapper(Double importo) {
		String printImporto = Utils.printImporto(BigDecimal.valueOf(importo), true);
		return Long.valueOf(printImporto);
	}

	public TransferModel singoloVersamentoGpdToTransferModel(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity,
			PendenzaGpdEntity rata, PosizioneDebitoriaGpdEntity posizione) throws IOException {
		if (singoloVersamentoGdpEntity == null) {
			return null;
		}

		TransferModel transferModel = new TransferModel();

		mapIdTransfer(singoloVersamentoGdpEntity, transferModel);
		transferModel.setAmount(amountMapper(singoloVersamentoGdpEntity.getImportoSingoloVersamento()));
		transferModel.setOrganizationFiscalCode(Utils.getCodDominioSingoloVersamento(singoloVersamentoGdpEntity, posizione.getCodDominio()));
		transferModel.setRemittanceInformation(Utils.buildCausaleSingoloVersamento(rata.getIuvVersamento(),
				singoloVersamentoGdpEntity.getImportoSingoloVersamento(), singoloVersamentoGdpEntity.getDescrizione(),
				singoloVersamentoGdpEntity.getDescrizioneCausaleRPT()));
		transferModel.setCategory(Utils.getCategory(singoloVersamentoGdpEntity));

		if (singoloVersamentoGdpEntity.getTipoBollo() != null) {
			Stamp stamp = new Stamp();
			stamp.setHashDocument(singoloVersamentoGdpEntity.getHashDocumento());
			stamp.setProvincialResidence(singoloVersamentoGdpEntity.getProvinciaResidenza());
			stamp.setStampType(singoloVersamentoGdpEntity.getTipoBollo());
			transferModel.setStamp(stamp);
		} else {
			mapInformazioniEntrata(singoloVersamentoGdpEntity, transferModel);
		}

		if (singoloVersamentoGdpEntity.getMetadata() != null) {
			transferModel.setTransferMetadata(this.getTransferMetadata(singoloVersamentoGdpEntity.getMetadata()));
		}

		return transferModel;
	}

	private void mapIdTransfer(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, TransferModel transferModel) {
		switch (singoloVersamentoGdpEntity.getIndiceDati()) {
		case 1:
			transferModel.setIdTransfer(IdTransferEnum._1);
			break;
		case 2:
			transferModel.setIdTransfer(IdTransferEnum._2);
			break;
		case 3:
			transferModel.setIdTransfer(IdTransferEnum._3);
			break;
		case 4:
			transferModel.setIdTransfer(IdTransferEnum._4);
			break;
		case 5:
			transferModel.setIdTransfer(IdTransferEnum._5);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + singoloVersamentoGdpEntity.getIndiceDati());
		}
	}

	private void mapInformazioniEntrata(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, TransferModel transferModel) {
		boolean postale = false;
		String ibanScelto = null;

		if (singoloVersamentoGdpEntity.getCodContabilita() != null) {
			String ibanAccredito = singoloVersamentoGdpEntity.getIbanAccredito();
			String ibanAppoggio = singoloVersamentoGdpEntity.getIbanAppoggio();

			if (ibanAccredito != null) {
				ibanScelto = ibanAccredito;
				postale = singoloVersamentoGdpEntity.getIbanAccreditoPostale();
			} else if (ibanAppoggio != null) {
				ibanScelto = ibanAppoggio;
				postale = singoloVersamentoGdpEntity.getIbanAppoggioPostale();
			}
		} else {
			String tributoIbanAccredito = singoloVersamentoGdpEntity.getTributoIbanAccredito();
			String tributoIbanAppoggio = singoloVersamentoGdpEntity.getTributoIbanAppoggio();

			if (tributoIbanAccredito != null) {
				ibanScelto = tributoIbanAccredito;
				postale = singoloVersamentoGdpEntity.getTributoIbanAccreditoPostale();
			} else if (tributoIbanAppoggio != null) {
				ibanScelto = tributoIbanAppoggio;
				postale = singoloVersamentoGdpEntity.getTributoIbanAppoggioPostale();
			}
		}

		transferModel.setIban(ibanScelto);

		if (postale) {
			transferModel.setPostalIban(ibanScelto);
		}
	}

	public Set<TransferMetadataModel> getTransferMetadata(String metadataString) throws IOException {
		Metadata metadata;
		try {
			metadata = this.objectMapper.readValue(metadataString.getBytes(), Metadata.class);
		} catch (JacksonException e) {
			throw new IOException(e);
		}

		Set<TransferMetadataModel> toReturn = new LinkedHashSet<>();
		if (metadata != null) {
			List<MapEntry> value = metadata.getValue();
			if (value != null) {
				for (MapEntry mapEntry : value) {
					TransferMetadataModel transferMetadataModel = new TransferMetadataModel();
					transferMetadataModel.setKey(mapEntry.getKey());
					transferMetadataModel.setValue(mapEntry.getValue());
					toReturn.add(transferMetadataModel);
				}
			}
		}

		return toReturn;
	}
}
