package it.govpay.gpd.collaudo;

import tools.jackson.databind.node.ObjectNode;

public record Posizione(String nome, String iupd, String nav, String iuv, ObjectNode corpo) {
}
