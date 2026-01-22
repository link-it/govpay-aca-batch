package it.govpay.gpd.mapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.client.beans.PaymentOptionMetadataModel;
import it.govpay.gpd.client.beans.PaymentOptionModel;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.Stamp;
import it.govpay.gpd.client.beans.TransferMetadataModel;
import it.govpay.gpd.client.beans.TransferModel;
import it.govpay.gpd.client.beans.TransferModel.IdTransferEnum;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.MapEntry;
import it.govpay.gpd.entity.Metadata;
import it.govpay.gpd.entity.SingoloVersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.repository.SingoloVersamentoFilters;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.utils.Utils;

@Mapper(componentModel = "spring")
public abstract class PaymentPositionModelRequestMapper {

	ObjectMapper objectMapper;

	SingoloVersamentoGpdRepository singoloVersamentoGpdRepository; 

	@Value("${it.govpay.gpd.standIn.enabled:true}")
	Boolean standIn;

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void setSingoloVersamentoGpdRepository(SingoloVersamentoGpdRepository singoloVersamentoGpdRepository) {
		this.singoloVersamentoGpdRepository = singoloVersamentoGpdRepository;
	}

	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_IUPD, source = "versamentoGpdEntity", qualifiedByName = "mapIupd")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_PAY_STAND_IN, source = "standIn") // feature flag to enable a debt position in stand-in mode
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_TYPE, source = "versamentoGpdEntity.debitoreTipo")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_FISCAL_CODE, source = "versamentoGpdEntity.debitoreIdentificativo")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_FULL_NAME, source = "versamentoGpdEntity.debitoreAnagrafica")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_STREET_NAME, source = "versamentoGpdEntity.debitoreIndirizzo")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_CIVIC_NUMBER, source = "versamentoGpdEntity.debitoreCivico")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_POSTAL_CODE, source = "versamentoGpdEntity.debitoreCap")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_CITY, source = "versamentoGpdEntity.debitoreLocalita")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_PROVINCE, source = "versamentoGpdEntity.debitoreProvincia")
	//	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_REGION, source = "versamentoGpdEntity.debitoreRegione") // non c'e' nell'entity
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_COUNTRY, source = "versamentoGpdEntity.debitoreNazione")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_EMAIL, source = "versamentoGpdEntity.debitoreEmail")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_PHONE, source = "versamentoGpdEntity.debitoreCellulare")
	//@Mapping(target = PaymentPositionModel.JSON_PROPERTY_SWITCH_TO_EXPIRED, source = "switchToExpired") // feature flag to enable the debt position to expire after the due date   
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_COMPANY_NAME, source = "versamentoGpdEntity.ragioneSocialeDominio")
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_OFFICE_NAME, source = "versamentoGpdEntity" , qualifiedByName = "mapUoAnagrafica" )
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_VALIDITY_DATE, source = "versamentoGpdEntity", qualifiedByName = "mapValidityDate" ) // se impostata a null la PD viene pubblicata in automatico
	// @Mapping(target = PaymentPositionModel.JSON_PROPERTY_PAYMENT_DATE, source = "versamentoGpdEntity") attributo read only viene restituito dal servizio e ignorato in request 
	// @Mapping(target = PaymentPositionModel.JSON_PROPERTY_STATUS, source = "status")  attributo read only viene restituito dal servizio e ignorato in request 
	@Mapping(target = PaymentPositionModel.JSON_PROPERTY_PAYMENT_OPTION, source = "versamentoGpdEntity", qualifiedByName = "mapPaymentOption")
	public abstract PaymentPositionModel versamentoGpdToPaymentPositionModelBase(VersamentoGpdEntity versamentoGpdEntity, boolean standIn) throws IOException;


	public PaymentPositionModel versamentoGpdToPaymentPositionModel(VersamentoGpdEntity versamentoGpdEntity) throws IOException {
		boolean switchToExpired = false;
		PaymentPositionModel paymentPositionModel = this.versamentoGpdToPaymentPositionModelBase(versamentoGpdEntity, this.standIn);

		// switch to expired
		// feature flag to enable the debt position to expire after the due date   
		if(paymentPositionModel != null) {
			if(paymentPositionModel.getValidityDate() != null) {
				switchToExpired = true;
			}

			paymentPositionModel.setSwitchToExpired(switchToExpired);
		}


		return paymentPositionModel;
	}


	@Named("mapIupd")
	public String mapIupd(VersamentoGpdEntity versamentoGpdEntity) {
		return Utils.generaIupd(versamentoGpdEntity);
	}

	@Named("mapUoAnagrafica")
	public String mapUoAnagrafica(VersamentoGpdEntity versamentoGpdEntity) {
		if (versamentoGpdEntity == null || versamentoGpdEntity.getCodUo() == null) {
			return null;
		}

		if (!Costanti.EC.equals(versamentoGpdEntity.getCodUo())) {
			return versamentoGpdEntity.getUoDenominazione();
		}

		return null;
	}

	@Named("mapValidityDate")
	public OffsetDateTime mapValidityDate(VersamentoGpdEntity versamentoGpdEntity) {
		return leggiDueDate(versamentoGpdEntity, true);
	}

	@Named("mapPaymentOption")
	public List<PaymentOptionModel> mapPaymentOption(VersamentoGpdEntity versamentoGpdEntity) throws IOException {
		List<PaymentOptionModel> list = new ArrayList<>();
		list.add(versamentoGpdToPaymentOptionModel(versamentoGpdEntity));
		return list;
	}

	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_NAV, source = "versamentoGpdEntity.numeroAvviso")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_IUV, source = "versamentoGpdEntity.iuvVersamento")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_AMOUNT, source = "versamentoGpdEntity.importoTotale", qualifiedByName = "amountMapper")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_DESCRIPTION, source = "versamentoGpdEntity.causaleVersamento")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_IS_PARTIAL_PAYMENT, source = "versamentoGpdEntity", qualifiedByName = "mapPartialPayment")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_DUE_DATE, source = "versamentoGpdEntity", qualifiedByName = "mapDueDate") 
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_RETENTION_DATE, source = "versamentoGpdEntity", qualifiedByName = "mapRetentionDate") 
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_FEE, source = "versamentoGpdEntity", qualifiedByName = "mapFee") 
	// @Mapping(target = PaymentOptionModel.JSON_PROPERTY_NOTIFICATION_FEE, source = "versamentoGpdEntity", qualifiedByName = "mapNotificationFee") attributo readonly
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_TRANSFER, source = "versamentoGpdEntity", qualifiedByName = "mapTransfer")
	@Mapping(target = PaymentOptionModel.JSON_PROPERTY_PAYMENT_OPTION_METADATA, source = "versamentoGpdEntity", qualifiedByName = "mapMetadataVersamento")
	public abstract PaymentOptionModel versamentoGpdToPaymentOptionModel(VersamentoGpdEntity versamentoGpdEntity) throws IOException;

	@Named("amountMapper")
	public Long amountMapper(Double importo) {
		String printImporto = Utils.printImporto(BigDecimal.valueOf(importo), true);
		return Long.valueOf(printImporto);
	}

	@Named("mapPartialPayment")
	public Boolean mapPartialPayment(VersamentoGpdEntity versamentoGpdEntity) {
		return versamentoGpdEntity.getCodRata() != null;
	}

	@Named("mapDueDate")
	public OffsetDateTime mapDueDate(VersamentoGpdEntity versamentoGpdEntity) {
		return leggiDueDate(versamentoGpdEntity, false);
	}

	@Named("mapRetentionDate")
	public OffsetDateTime mapRetentionDate(VersamentoGpdEntity versamentoGpdEntity) {
		return leggiDueDate(versamentoGpdEntity, true);
	}


	private OffsetDateTime leggiDueDate(VersamentoGpdEntity versamentoGpdEntity, boolean allowNull) {
		// Otteniamo l'offset per il fuso orario di Roma
		ZoneOffset offset = ZoneOffset.ofHoursMinutes(1, 0); // CET (Central European Time)
		LocalDateTime dueDate = Utils.calcolaDueDate(versamentoGpdEntity, allowNull);
		if (dueDate == null) {
			return null;
		}

		return OffsetDateTime.of(dueDate, offset);
	}

	@Named("mapFee")
	public Long mapFee(VersamentoGpdEntity versamentoGpdEntity) {
		return null;
	}

	@Named("mapTransfer")
	public List<TransferModel> mapTransfer(VersamentoGpdEntity versamentoGpdEntity) throws IOException {
		List<TransferModel> list = new ArrayList<>();

		List<SingoloVersamentoGpdEntity> singoliVersamenti = this.singoloVersamentoGpdRepository.findAll(SingoloVersamentoFilters.byVersamentoId(versamentoGpdEntity.getId()));

		for (SingoloVersamentoGpdEntity singoloVersamento : singoliVersamenti) {
			list.add(singoloVersamentoGpdToTransferModel(singoloVersamento, versamentoGpdEntity));
		}

		return list;
	}

	@Named("mapMetadataVersamento")
	public List<PaymentOptionMetadataModel> mapMetadataVersamento(VersamentoGpdEntity versamentoGpdEntity) {
		return new ArrayList<>();
	}

	public TransferModel singoloVersamentoGpdToTransferModel(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, VersamentoGpdEntity versamentoGpdEntity) throws IOException {
		if ( singoloVersamentoGdpEntity == null || versamentoGpdEntity == null ) {
			return null;
		}

		TransferModel transferModel = new TransferModel();

		// TransferModel.JSON_PROPERTY_ID_TRANSFER
		mapIdTransfer(singoloVersamentoGdpEntity, transferModel);

		// TransferModel.JSON_PROPERTY_AMOUNT
		transferModel.setAmount(amountMapper(singoloVersamentoGdpEntity.getImportoSingoloVersamento()));

		// TransferModel.JSON_PROPERTY_ORGANIZATION_FISCAL_CODE
		transferModel.setOrganizationFiscalCode(getCodDominioSingoloVersamento(singoloVersamentoGdpEntity, versamentoGpdEntity));

		// TransferModel.JSON_PROPERTY_REMITTANCE_INFORMATION
		transferModel.setRemittanceInformation(getRemittanceInformation(singoloVersamentoGdpEntity, versamentoGpdEntity.getIuvVersamento()));


		// TransferModel.JSON_PROPERTY_CATEGORY (obbligatorio per tutti i transfer)
		transferModel.setCategory(getCategory(singoloVersamentoGdpEntity));

		// Bollo Telematico / IBAN
		if(singoloVersamentoGdpEntity.getTipoBollo() != null) {
			// TransferModel.JSON_PROPERTY_STAMP
			Stamp stamp = new Stamp();
			stamp.setHashDocument(singoloVersamentoGdpEntity.getHashDocumento());
			stamp.setProvincialResidence(singoloVersamentoGdpEntity.getProvinciaResidenza());
			stamp.setStampType(singoloVersamentoGdpEntity.getTipoBollo());
			transferModel.setStamp(stamp);
		} else {
			mapInformazioniEntrata(singoloVersamentoGdpEntity, transferModel);
		}

		// TransferModel.JSON_PROPERTY_TRANSFER_METADATA
		if(singoloVersamentoGdpEntity.getMetadata()!= null) {
			transferModel.setTransferMetadata(this.getTransferMetadata(singoloVersamentoGdpEntity.getMetadata()));
		}

		return transferModel;
	}


	private void mapInformazioniEntrata(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, TransferModel transferModel) {
		boolean postale = false;
		String ibanScelto = null;

		if(singoloVersamentoGdpEntity.getCodContabilita() != null) { // in questo caso ho le informazioni relative all'iban direttamente nel sv
			String ibanAccredito = singoloVersamentoGdpEntity.getIbanAccredito();
			String ibanAppoggio = singoloVersamentoGdpEntity.getIbanAppoggio();

			if(ibanAccredito != null) {
				ibanScelto = ibanAccredito;
				postale = singoloVersamentoGdpEntity.getIbanAccreditoPostale();
			} else if (ibanAppoggio != null) {
				ibanScelto = ibanAppoggio;
				postale = singoloVersamentoGdpEntity.getIbanAppoggioPostale();
			} else {
				// eccezione??
			}
		} else  {
			String tributoIbanAccredito = singoloVersamentoGdpEntity.getTributoIbanAccredito();
			String tributoIbanAppoggio = singoloVersamentoGdpEntity.getTributoIbanAppoggio();	

			if(tributoIbanAccredito != null) {
				ibanScelto = tributoIbanAccredito;
				postale = singoloVersamentoGdpEntity.getTributoIbanAccreditoPostale();
			} else if (tributoIbanAppoggio != null) {
				ibanScelto = tributoIbanAppoggio;
				postale = singoloVersamentoGdpEntity.getTributoIbanAppoggioPostale();
			} else {
				// eccezione??
			}
		}

		// TransferModel.JSON_PROPERTY_IBAN
		transferModel.setIban(ibanScelto);

		// TransferModel.JSON_PROPERTY_POSTAL_IBAN
		if(postale) {
			transferModel.setPostalIban(ibanScelto);
		}
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

	public String getCodDominioSingoloVersamento(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, VersamentoGpdEntity versamentoGpdEntity) {
		if(singoloVersamentoGdpEntity.getCodDominio() != null)
			return singoloVersamentoGdpEntity.getCodDominio();

		return versamentoGpdEntity.getCodDominio();
	}

	public String getRemittanceInformation(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, String iuv) {
		return Utils.buildCausaleSingoloVersamento(iuv, singoloVersamentoGdpEntity.getImportoSingoloVersamento(), singoloVersamentoGdpEntity.getDescrizione(), singoloVersamentoGdpEntity.getDescrizioneCausaleRPT());
	}

	public String getCategory(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity) {
		String tipoContabilita = singoloVersamentoGdpEntity.getTipoContabilita();

		if(tipoContabilita == null) {
			if(singoloVersamentoGdpEntity.getTributoTipoContabilita() != null) {
				tipoContabilita = singoloVersamentoGdpEntity.getTributoTipoContabilita(); 
			} else {
				tipoContabilita = singoloVersamentoGdpEntity.getTipoTributoTipoContabilita();
			}
		}

		String codContabilita = singoloVersamentoGdpEntity.getCodContabilita();

		if(codContabilita == null) {
			if(singoloVersamentoGdpEntity.getTributoCodContabilita() != null) {
				codContabilita = singoloVersamentoGdpEntity.getTributoCodContabilita(); 
			} else {
				codContabilita = singoloVersamentoGdpEntity.getTipoTributoCodContabilita();
			}
		}

		return tipoContabilita + "/" + codContabilita;
	}

	public List<TransferMetadataModel> getTransferMetadata(String metadataString) throws IOException {
		Metadata metadata = this.objectMapper.readValue(metadataString.getBytes(), Metadata.class);

		if(metadata != null) {
			List<MapEntry> value = metadata.getValue();
			if(value != null ) {
				List<TransferMetadataModel> toReturn = new ArrayList<>();

				for (MapEntry mapEntry : value) {
					TransferMetadataModel transferMetadataModel = new TransferMetadataModel();
					transferMetadataModel.setKey(mapEntry.getKey());
					transferMetadataModel.setValue(mapEntry.getValue());

					toReturn.add(transferMetadataModel);
				}

				return toReturn;
			}
		}

		return new ArrayList<>();

	}
}
