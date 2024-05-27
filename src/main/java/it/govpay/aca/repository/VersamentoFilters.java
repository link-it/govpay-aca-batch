package it.govpay.aca.repository;

import java.time.OffsetDateTime;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoAcaEntity_;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VersamentoFilters {

	public static Specification<VersamentoAcaEntity> empty() {
		return (Root<VersamentoAcaEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> null; 
	}
	
	public static Specification<VersamentoAcaEntity> byDataUltimaModificaAcaDa(OffsetDateTime dataDa) {
		return (Root<VersamentoAcaEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
		cb.greaterThanOrEqualTo(root.get(VersamentoAcaEntity_.DATA_ULTIMA_MODIFICA_ACA),dataDa);
	}
}
