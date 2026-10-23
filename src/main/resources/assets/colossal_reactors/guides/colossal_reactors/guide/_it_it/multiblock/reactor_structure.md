---
navigation:
  parent: multiblock/multiblock-index.md
  title: Struttura del reattore
  icon: reactor_casing
  position: 10
item_ids:
  - colossal_reactors:reactor_casing
  - colossal_reactors:reactor_glass
  - colossal_reactors:reactor_rod
  - colossal_reactors:rod_controller
---

# Struttura del reattore

## Costruire il guscio

<Row gap="16" fullWidth={true}>
  <BlockImage id="reactor_casing" scale="4" />
  <BlockImage id="reactor_glass" scale="4" />
</Row>

- Usa **Reactor Casing** per pareti opache e **Reactor Glass** per facce trasparenti dove vuoi vedere dentro o far passare luce.

- L’interno deve formare una **scatola chiusa** con facce valide solo all’esterno. Lascia spazio per rod, fluidi, componenti interni e porte del casing prima di sigillare gli ultimi blocchi.

## Rod controller

<BlockImage id="rod_controller" scale="4" />

Il **Rod Controller** sta sullo **strato superiore del guscio** dove vuoi una **colonna di rod combustibile**. Ogni cella interna sotto di esso in quella colonna deve essere un blocco **Reactor Rod**—altrimenti la validazione fallisce. Serve a dichiarare posizionamento e geometria delle rod per il multiblocco (diverso dal blocco **Reactor Controller** su una faccia esterna, che gestisce la GUI).

## Fuel rod

<BlockImage id="reactor_rod" scale="4" />

- Le **Reactor Rod** contengono item di combustibile. Posizionale dove il progetto lo consente; adiacenza e layout cambiano **uscita RF** e **consumo combustibile** nelle letture del controller.

- Inserisci ed estrai combustibile tramite il **Reactor Controller** o automazione che punta agli slot fuel (vedi la pagina del controller).

## Heat sink (passivi)

Gli **heat sink** sono componenti termici **passivi** **dentro** il volume d’aria del reattore. Possono essere **blocchi solidi** o **volumi liquidi stazionari** che posizioni nel guscio (contano come riempimento interno per le regole di layout—non sono lo stesso circuito refrigerante attivo).

Cambiano **uscita RF** e **consumo combustibile** insieme alla definizione di refrigerante attivo; le stats di surriscaldamento entrano nel comportamento avanzato del reattore quando abilitato.

**Non** vengono consumati come il circuito refrigerante attivo.

## Refrigerante (attivo)

Il **refrigerante (attivo)** è il **fluido di lavoro** nel circuito: viene **consumato** mentre il reattore gira. Alcuni refrigeranti (es. acqua in modalità **vapore**) **deviano** l’RF potenziale in **fluido di scarico**—con abbastanza conversione, l’**RF può andare a zero** mentre il combustibile brucia ancora. I moltiplicatori datapack di **RF** e **consumo** si applicano sopra; altri refrigeranti possono solo scalare RF e uso fluido senza quella diversione. Se il circuito è **a secco**, l’**RF** può anche scendere finché non torna l’alimentazione.

Riempi o svuota tramite il **Reactor Controller** o porte **fluido-compatibili**, a seconda del build.
