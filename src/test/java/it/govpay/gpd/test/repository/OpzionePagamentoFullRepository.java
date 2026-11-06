package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;

public interface OpzionePagamentoFullRepository extends JpaRepositoryImplementation<OpzionePagamentoFullEntity, Long> {

	OpzionePagamentoFullEntity findFirstByOrderByIdDesc();
}
