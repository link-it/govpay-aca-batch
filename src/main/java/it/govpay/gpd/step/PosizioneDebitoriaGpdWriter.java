package it.govpay.gpd.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.gpd.entity.PendenzaGpdEntity;
import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.repository.DocumentoRepository;
import it.govpay.gpd.repository.PendenzaGpdRepository;
import it.govpay.gpd.repository.VersamentoRepository;

/**
 * A differenza di {@link PendenzaWriter} (legacy, scrive la data di avvio del job), qui
 * si scrive il valore di {@code data_ultima_modifica_aca} LETTO dal reader, non
 * {@code now()}: una modifica arrivata durante l'elaborazione non viene cosi' persa (al
 * run successivo vale di nuovo {@code modifica > comunicazione}). Aggiorna sia
 * {@code documenti} che TUTTE le {@code versamenti} (rate) della posizione, qualunque sia
 * lo stato della loro opzione, nella stessa transazione.
 */
@Component
public class PosizioneDebitoriaGpdWriter implements ItemWriter<PosizioneDebitoriaGpdEntity> {

	private Logger logger = LoggerFactory.getLogger(PosizioneDebitoriaGpdWriter.class);

	private final DocumentoRepository documentoRepository;
	private final PendenzaGpdRepository pendenzaGpdRepository;
	private final VersamentoRepository versamentoRepository;

	public PosizioneDebitoriaGpdWriter(DocumentoRepository documentoRepository, PendenzaGpdRepository pendenzaGpdRepository,
			VersamentoRepository versamentoRepository) {
		this.documentoRepository = documentoRepository;
		this.pendenzaGpdRepository = pendenzaGpdRepository;
		this.versamentoRepository = versamentoRepository;
	}

	@Transactional
	@Override
	public void write(Chunk<? extends PosizioneDebitoriaGpdEntity> chunk) throws Exception {
		logger.info("Verra' effettuato il salvataggio di [{}] posizioni debitorie presenti nel chunk", chunk.size());

		for (PosizioneDebitoriaGpdEntity item : chunk) {
			var dataUltimaModificaAca = item.getDataUltimaModificaAca();

			logger.info("Aggiorno posizione debitoria [{},{}], imposto DataUltimaComunicazioneACA [{}]",
					item.getCodApplicazione(), item.getCodDocumento(), dataUltimaModificaAca);
			this.documentoRepository.updateDataUltimaComunicazioneAcaById(item.getId(), dataUltimaModificaAca);

			for (PendenzaGpdEntity rata : this.pendenzaGpdRepository.findByIdDocumento(item.getId())) {
				this.versamentoRepository.updateDataUltimaComunicazioneAcaById(rata.getId(), dataUltimaModificaAca);
			}

			logger.info("Posizione debitoria [{},{}] aggiornata", item.getCodApplicazione(), item.getCodDocumento());
		}
	}
}
