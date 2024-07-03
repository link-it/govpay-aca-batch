package it.govpay.gpd.repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.entity.VersamentoGpdEntity_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VersamentoFilters {

	public static Specification<VersamentoGpdEntity> empty() {
		return (Root<VersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> null; 
	}
	
	public static Specification<VersamentoGpdEntity> byDataUltimaModificaAcaDa(OffsetDateTime dataDa) {
		return (Root<VersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
		cb.greaterThanOrEqualTo(root.get(VersamentoGpdEntity_.DATA_ULTIMA_MODIFICA_ACA),dataDa);
	}
	
	public static Specification<VersamentoGpdEntity> byDataUltimaModificaAcaGreaterThanDataUltimaComunicazioneAca() {
		return (Root<VersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
		cb.greaterThanOrEqualTo(root.get(VersamentoGpdEntity_.DATA_ULTIMA_MODIFICA_ACA), root.get(VersamentoGpdEntity_.DATA_ULTIMA_COMUNICAZIONE_ACA));
	}
	
	public static Specification<VersamentoGpdEntity> byDataUltimaComunicazioneAcaNull() {
		return (Root<VersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
		cb.isNull(root.get(VersamentoGpdEntity_.DATA_ULTIMA_COMUNICAZIONE_ACA));
	}
	
	public static Specification<VersamentoGpdEntity> byStato() {
		return (Root<VersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
	        List<String> stati = Arrays.asList(StatoVersamento.NON_ESEGUITO.name(), StatoVersamento.ANNULLATO.name());
	        return root.get(VersamentoGpdEntity_.STATO_VERSAMENTO).in(stati);
	    };
	}
	
	public static Specification<VersamentoGpdEntity> creaFiltriRicercaVersamentiDaSpedire(Integer numeroGiorni) {
		// filtro subito sugli stati
		Specification<VersamentoGpdEntity> spec = VersamentoFilters.byStato();
		
    	// filtro sulla data esecuzione
    	if(numeroGiorni != null) {
    		OffsetDateTime sogliaTemporale = OffsetDateTime.now().minusDays(numeroGiorni);
			spec = spec.and(VersamentoFilters.byDataUltimaModificaAcaDa(sogliaTemporale));
    	}
    	
    	// data ultima modifica Aca > data ultima spedizione
    	Specification<VersamentoGpdEntity> spec2 = VersamentoFilters.byDataUltimaModificaAcaGreaterThanDataUltimaComunicazioneAca();
    	// in or con data ultima spedizione null per i nuovi inserimenti
    	Specification<VersamentoGpdEntity> spec3 = VersamentoFilters.byDataUltimaComunicazioneAcaNull();
    	
    	spec = spec.and(spec2.or(spec3));
    	
		return spec;
	}
}
