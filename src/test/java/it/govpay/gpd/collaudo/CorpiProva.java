package it.govpay.gpd.collaudo;

import tools.jackson.databind.node.ObjectNode;

/** Accesso ai nodi del corpo di una posizione di prova con una sola opzione e una sola rata. */
final class CorpiProva {

	private CorpiProva() {
	}

	static ObjectNode opzione(ObjectNode corpo) {
		return (ObjectNode) corpo.path("paymentOption").get(0);
	}

	static ObjectNode rata(ObjectNode corpo) {
		return (ObjectNode) opzione(corpo).path("installments").get(0);
	}
}
