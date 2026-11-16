package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.DebitoreFullEntity;

public interface DebitoreFullRepository extends JpaRepositoryImplementation<DebitoreFullEntity, Long> {

	DebitoreFullEntity findFirstByOrderByIdDesc();
}
