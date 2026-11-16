package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.DocumentoFullEntity;

public interface DocumentoFullRepository extends JpaRepositoryImplementation<DocumentoFullEntity, Long> {

	DocumentoFullEntity findFirstByOrderByIdDesc();
}
