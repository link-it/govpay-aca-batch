package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity;

public interface SingoloVersamentoFullRepository extends JpaRepositoryImplementation<SingoloVersamentoFullEntity, Long>{

	public SingoloVersamentoFullEntity findFirstByOrderByIdDesc();
}
