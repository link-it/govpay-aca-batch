package it.govpay.aca.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoAcaEntity.StatoVersamento;
import it.govpay.aca.utils.Utils;

@Mapper(componentModel = "spring")
public interface NewDebtPositionRequestMapper {
	
	@Mapping(target = "paFiscalCode", source = "codDominio")
	@Mapping(target = "entityFiscalCode", source = "debitoreIdentificativo")
	@Mapping(target = "entityFullName", source = "debitoreAnagrafica")
	@Mapping(target = "iuv", source = "iuvVersamento")
	@Mapping(target = "entityType", source = "debitoreTipo")
	@Mapping(target = "description", source = "causaleVersamento")
	@Mapping(target = "amount", source = "." , qualifiedByName = "amountMapper")
	@Mapping(target = "expirationDate", source = ".", qualifiedByName = "expirationDateMapper")
	public NewDebtPositionRequest versamentoAcaToNewDebtPositionRequest(VersamentoAcaEntity versamentoAcaEntity);

	
    @Named("amountMapper")
    public default Integer amountMapper(VersamentoAcaEntity versamentoAcaEntity) {
    	if(versamentoAcaEntity.getStatoVersamento().equals(StatoVersamento.NON_ESEGUITO)) {
			String printImporto = Utils.printImporto(BigDecimal.valueOf(versamentoAcaEntity.getImportoTotale()), true);
			return Integer.valueOf(printImporto);
		} else { // invio una chiusura di posizione o annullamento
			return Integer.valueOf(0);
		}
    }
    
    @Named("expirationDateMapper")
    public default OffsetDateTime expirationDateMapper(VersamentoAcaEntity versamentoAcaEntity) {
    	 // Otteniamo l'offset per il fuso orario di Roma
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(1, 0); // CET (Central European Time)
    	return Utils.calcolaDueDate(versamentoAcaEntity).atOffset(offset);
    }
}

/**
-fiscalCodePA: codice fiscale dell’Ente Creditore che ha creato la posizione debitoria;
-entityUniqueIdentifierType: tipologia del debitore (F=persona fisica, G=persona giuridica);
-entityUniqueIdentifierValue: codice fiscale del debitore;
-fullName: Nome e Cognome del debitore;
-IUV: identificativo univoco versamento;
-amount: importo (non è possibile censire una posizione con un importo uguale a zero);
-description: causale;
-dueDate: data di scadenza della posizione debitoria.
*/