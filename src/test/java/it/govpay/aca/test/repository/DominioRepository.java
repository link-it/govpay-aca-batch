package it.govpay.aca.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.aca.test.entity.DominioEntity;

public interface DominioRepository extends JpaRepositoryImplementation<DominioEntity, Long>{

	public DominioEntity findByCodDominio(String codDominio);
}
