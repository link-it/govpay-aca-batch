package it.govpay.gpd.repository;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.gpd.entity.SingoloVersamentoGpdEntity;
import it.govpay.gpd.entity.SingoloVersamentoGpdEntity_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SingoloVersamentoFilters {

	public static Specification<SingoloVersamentoGpdEntity> byVersamentoId(Long idVersamento) {
		return (Root<SingoloVersamentoGpdEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
		cb.equal(root.get(SingoloVersamentoGpdEntity_.ID_VERSAMENTO),idVersamento);
	}

}
