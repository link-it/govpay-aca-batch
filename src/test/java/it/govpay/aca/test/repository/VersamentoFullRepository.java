package it.govpay.aca.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.aca.test.entity.VersamentoFullEntity;

public interface VersamentoFullRepository extends JpaRepositoryImplementation<VersamentoFullEntity, Long>{

	public VersamentoFullEntity findFirstByOrderByIdDesc();
}
