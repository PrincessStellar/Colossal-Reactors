---
navigation:
  parent: turbine/turbine-index.md
  title: Turbine Power Port
  icon: turbine_power_port
  position: 36
item_ids:
  - colossal_reactors:turbine_power_port
  - colossal_reactors:turbine_high_cond_power_port
categories:
  - ports
---

# Turbine Power Port

<Row gap="16" fullWidth={true}>
  <BlockImage id="turbine_power_port" scale="4" />
  <BlockImage id="turbine_high_cond_power_port" scale="4" />
</Row>

Le power port inviano l’**RF** della turbina a cavi e macchine all’**esterno**. **Non c’è GUI**—collega la faccia esterna e leggi la potenza sulla tua rete.

## Turbine Power Port

La **Turbine Power Port** normale basta per la maggior parte dei modpack e dei tipici tassi RF.

## Turbine High Conduction Power Port

La variante **High Conduction** è per turbine **molto grandi** che producono enormi RF per tick. Memorizza e sposta quantità di energia molto più alte (regolabili nelle impostazioni del mod) e funziona meglio con mod che supportano trasferimenti enormi (ad esempio Draconic Evolution o Flux Networks se installati).

Si crafta da una **Turbine Power Port** più pezzi di alto livello—controlla **JEI**.

## Ridimensionamento automatico della capacità

Quando la turbina **rivalida**, ogni **power port** ridimensiona il buffer RF dall’uscita stimata della turbina:

- La dimensione obiettivo è circa **RF/t stimato × 10**, mai sotto **10.000 RF**.
- **Turbine Power Port** ha tetto a **2,1B RF**.
- La variante **High Conduction** ha tetto a **9,22×10¹⁸ RF** (buffer a 64 bit).

La capacità cresce subito quando l’uscita sale; si riduce solo se l’energia memorizzata entra ancora nel nuovo target.

## Entrambe le varianti

- Posiziona su una **faccia casing** con l’interno verso l’interno della turbina.

- Attacca cavi energetici o macchine all’**esterno**.

L’uscita dipende da vapore, coil e blade—vedi [Vapore, RF e coil](turbine_generation_and_coils.md).
