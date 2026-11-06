package it.govpay.gpd.repository;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import it.govpay.gpd.entity.DocumentoEntity;
import jakarta.transaction.Transactional;

public interface DocumentoRepository extends JpaRepositoryImplementation<DocumentoEntity, Long> {

	@Modifying
	@Transactional
	@Query("UPDATE DocumentoEntity d SET d.dataUltimaComunicazioneAca = :dataUltimaComunicazioneAca WHERE d.id = :id")
	int updateDataUltimaComunicazioneAcaById(@Param("id") Long id, @Param("dataUltimaComunicazioneAca") OffsetDateTime dataUltimaComunicazioneAca);
}
