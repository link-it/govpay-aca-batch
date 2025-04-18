package it.govpay.gpd.repository;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import it.govpay.gpd.entity.VersamentoEntity;
import jakarta.transaction.Transactional;

public interface VersamentoRepository extends JpaRepositoryImplementation<VersamentoEntity, Long> {

	@Modifying
	@Transactional
	@Query("UPDATE VersamentoEntity v SET v.dataUltimaComunicazioneAca = :dataUltimaComunicazioneAca WHERE v.id = :id")
	int updateDataUltimaComunicazioneAcaById(@Param("id") Long id, @Param("dataUltimaComunicazioneAca") OffsetDateTime dataUltimaComunicazioneAca);
}
