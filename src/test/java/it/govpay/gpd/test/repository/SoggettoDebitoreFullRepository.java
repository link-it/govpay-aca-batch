package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.SoggettoDebitoreFullEntity;

public interface SoggettoDebitoreFullRepository extends JpaRepositoryImplementation<SoggettoDebitoreFullEntity, Long> {

	SoggettoDebitoreFullEntity findFirstByOrderByIdDesc();
}
