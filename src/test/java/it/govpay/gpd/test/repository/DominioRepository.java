package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.DominioEntity;

public interface DominioRepository extends JpaRepositoryImplementation<DominioEntity, Long>{

	public DominioEntity findByCodDominio(String codDominio);
}
