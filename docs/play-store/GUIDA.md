# Pubblicare Citiverse su Google Play – guida passo passo

Sviluppatore: **Le Alternative** · Nome del pacchetto: **`it.citiverse.app`** (non si potrà più cambiare)

## Già pronto nel repository

| Cosa | Dove |
|---|---|
| App conforme alle regole 2026: Android 16 (API 36), segnala/blocca, regolamento al login, nessun permesso "a rischio" | codice |
| File **AAB** per il Play Store, firmato con la tua chiave | ogni build: artifact `citiverse-aab-N`; nelle versioni (tag `v…`) anche in release |
| Titolo, descrizione breve e lunga, novità della versione | `fastlane/metadata/android/it-IT/*.txt` |
| Icona 512×512 e immagine in evidenza 1024×500 | `fastlane/metadata/android/it-IT/images/` |
| Informativa privacy (da pubblicare online) | `docs/play-store/privacy-policy.md` |
| Risposte ai questionari della Console | più sotto, in questa guida |

---

## Passo 1 – Scegli il tipo di account (decisione importante)

| | **Organizzazione** (consigliato se Le Alternative è un ente: associazione, impresa, P.IVA) | **Personale** |
|---|---|---|
| Cosa serve | il **numero D-U-N-S** di Le Alternative (gratuito, lo rilascia Dun & Bradstreet; se non ce l'hai si chiede dalla Console e arriva in 1–3 settimane) | un documento d'identità |
| Nome mostrato sullo Store | Le Alternative | quello che scegli (es. "Le Alternative"), ma Google verifica il tuo nome |
| Prima di andare in pubblico | **puoi pubblicare subito** | **test chiuso obbligatorio: almeno 12 tester per 14 giorni di fila**, che usino davvero l'app |

Se hai il D-U-N-S o puoi aspettare un paio di settimane, scegli **Organizzazione**: eviti i 12 tester.

## Passo 2 – Crea l'account sviluppatore

1. Vai su **https://play.google.com/console/signup** con l'account Google che vuoi usare. Conviene un account
   dell'associazione (es. `android@…`), non quello personale.
2. Scegli il tipo (Passo 1) e compila i dati. **Nome sviluppatore**: `Le Alternative`.
3. Paga la quota una tantum di **25 $** con una carta.
4. Completa la **verifica dell'identità**: documento, e per le organizzazioni anche il D-U-N-S. Può richiedere qualche giorno.
5. Serve un telefono Android con l'app Play Console per confermare il dispositivo: te lo chiede lei.

## Passo 3 – Pagina sviluppatore con il logo di Le Alternative (facoltativa, ma bella)

Console → **Pagina sviluppatore**:
- **Icona**: il logo rosa, **esattamente 512×512 px**, PNG. Il tuo file è più grande: ridimensionalo (anche con
  Anteprima/Paint, oppure caricalo nel repository e lo preparo io).
- **Immagine di intestazione**: 4096×2304 px, facoltativa. Se vuoi la preparo io con il logo.
- Sito: `https://www.lealternative.net` (o quello che preferisci) · breve descrizione di Le Alternative.

## Passo 4 – Pubblica l'informativa privacy

1. Apri `docs/play-store/privacy-policy.md` e sostituisci i campi tra `[QUADRE]`: **email di contatto**, sito, data.
2. Pubblicala come pagina pubblica, per esempio su lealternative.net o come pagina su citiverse.it.
3. Tieni l'indirizzo (URL): va nella Console al Passo 7.

Verifica anche che su citiverse.it gli utenti possano **eliminare il proprio account**
(Pannello di amministrazione → Impostazioni → Utenti → consentire l'eliminazione dell'account). Google lo chiede.

## Passo 5 – Crea un account per i revisori di Google

Google prova l'app prima di approvarla e deve poter accedere. Su citiverse.it crea un utente apposta, per esempio
`revisore-google`, **senza verifica in due passaggi**, con una password lunga. Tieni nome utente e password
per il Passo 7 ("Accesso all'app"). **Non** usare il tuo account.

## Passo 6 – Crea l'app nella Console

Console → **Crea app**:
- Nome: `Citiverse` · Lingua predefinita: **Italiano – it-IT**
- App o gioco: **App** · Gratuita o a pagamento: **Gratuita** (non si potrà più rendere a pagamento)
- Spunta le dichiarazioni (norme e leggi USA sull'esportazione) → **Crea app**

## Passo 7 – "Contenuti dell'app" (menu a sinistra: Norme e programmi → Contenuti dell'app)

Compila ogni riquadro. Le risposte:

**Norme sulla privacy** → incolla l'URL del Passo 4.

**Accesso all'app** → "Tutte le funzionalità, o alcune, sono limitate" → **Aggiungi istruzioni**:
- Nome: `Accesso revisori` · Nome utente e password: quelli del Passo 5
- Altre informazioni: `Tocca "Accedi" in alto a destra nella home e inserisci le credenziali. L'account non ha la verifica in due passaggi.`

**Annunci** → **No, la mia app non contiene annunci.**

**Classificazione dei contenuti** → email, categoria **"Social o comunicazione"** (o la voce più vicina che vedi). Rispondi:
- Violenza, sesso, linguaggio volgare, droghe, gioco d'azzardo **come contenuti dell'app stessa**: No.
- **Gli utenti possono interagire o scambiarsi contenuti?** **Sì**
- Condivisione della posizione con altri utenti: No · Acquisti digitali: No · È un browser web o motore di ricerca: No
- Il risultato sarà probabilmente "PEGI 12" o simile, per via dei contenuti degli utenti: va bene.

**Pubblico di destinazione** → fasce d'età: **18 anni e oltre** (è la scelta più semplice: niente regole per i
minori). "L'app potrebbe attrarre i bambini?" → **No**.

**App di notizie** → **No**.

**Sicurezza dei dati** (la parte più lunga):
1. "L'app raccoglie o condivide dati utente?" → **Sì**
2. "Tutti i dati sono criptati in transito?" → **Sì**
3. "Metodi di creazione account": **Nome utente e password** (più "Altro": verifica in due passaggi facoltativa).
   Link per eliminare l'account: `https://citiverse.it/me/edit` (vedi Passo 4).
4. Tipi di dati, per ciascuno: **raccolto: sì · condiviso: no · trattamento temporaneo: no · facoltativo**
   (tranne dove indicato):
   - **Informazioni personali → ID utente** (il nome utente). Obbligatorio. Finalità: *Funzionalità dell'app*, *Gestione dell'account*.
   - **Informazioni personali → Indirizzo email**: solo se l'utente accede con l'email invece del nome utente. Finalità: *Gestione dell'account*.
   - **Attività nelle app → Altri contenuti generati dagli utenti** (post, risposte, segnalazioni). Finalità: *Funzionalità dell'app*.
   - **Attività nelle app → Interazioni con l'app** (voti, comunità seguite, utenti bloccati). Finalità: *Funzionalità dell'app*.
   - Tutto il resto (posizione, contatti, foto, dati finanziari, identificativi del dispositivo, log degli arresti anomali, analisi): **non raccolto**.
   - "Condiviso" è **No** perché i dati vanno a citiverse.it, cioè al servizio stesso dell'app. Se citiverse.it
     **non** è gestito da Le Alternative, dimmelo: cambia qualche risposta.

**App governative** → No · **Funzionalità finanziarie** → nessuna · **Salute** → nessuna.

**Autorizzazioni per servizi in primo piano** (compare dopo il primo caricamento dell'AAB):
- Tipo: **Uso speciale / Altro (specialUse)**
- Descrizione (incollala):
  > Citiverse non usa Google Play Services né Firebase Cloud Messaging. Per consegnare in tempo reale le notifiche
  > del forum (risposte, menzioni, messaggi) l'app mantiene una connessione WebSocket persistente con il server
  > citiverse.it tramite un servizio in primo piano, con una notifica fissa silenziosa. L'utente lo attiva e
  > disattiva in Impostazioni → Notifiche, e può scegliere in alternativa un controllo ogni 15 minuti con
  > WorkManager, senza servizio in primo piano.
- **Video**: registra 30–60 secondi con la registrazione schermo del telefono. Apri l'app → Impostazioni →
  Notifiche "In tempo reale" → si vede la notifica fissa → da un altro account rispondi a un tuo post → arriva la
  notifica. Caricalo su YouTube come **"Non in elenco"** e incolla il link.

## Passo 8 – Scheda dello Store (Crescita → Presenza nello Store → Scheda principale dello Store)

- **Nome app**, **Descrizione breve**, **Descrizione completa**: copia i file in `fastlane/metadata/android/it-IT/`
  (`title.txt`, `short_description.txt`, `full_description.txt`).
- **Icona dell'app**: `images/icon.png` · **Grafica di presentazione**: `images/featureGraphic.png`
- **Screenshot del telefono**: da 2 a 8. **Attenzione**: Google accetta al massimo il rapporto **2:1**. Gli
  screenshot dei telefoni moderni (come quelli che mi hai mandato, 713×1600) sono più lunghi e vengono **rifiutati**.
  Fai 4–6 screenshot (feed "Seguite", una discussione, una comunità, l'editor, le impostazioni notifiche), caricali
  nel repository in `fastlane/metadata/android/it-IT/raw/` e li preparo io in formato 1080×1920, con sfondo e
  didascalie.
- **Categoria**: **Social** · **Tag**: forum, comunità, social network
- **Dettagli di contatto**: email (obbligatoria, sarà pubblica), sito `https://citiverse.it`

## Passo 9 – Carica la prima versione

1. **Crea la versione su GitHub** (lo posso fare io se me lo chiedi): serve un tag, per esempio `v1.0.0`. Da
   GitHub: **Releases → Draft a new release → Choose a tag** → scrivi `v1.0.0` → **Create new tag** →
   **Publish release**. Il workflow compila e allega alla release sia `citiverse-N.apk` sia **`citiverse-N.aab`**.
   Scarica il file **.aab**.
2. Nella Console: **Test → Test chiuso** (account personale) oppure **Produzione** (organizzazione) → **Crea nuova release**.
3. **Firma dell'app di Google Play**: la prima volta te la propone. Scegli **"Usa la chiave generata da Google"**
   (è l'impostazione predefinita). La tua chiave `citiverse-release.jks` diventa la **chiave di caricamento**:
   continua a usarla per tutti gli aggiornamenti. Conseguenza: l'app scaricata dal Play Store e l'APK di GitHub
   hanno firme diverse, quindi chi passa dall'una all'altro deve disinstallare una volta.
4. Carica il file `.aab` → Nome della release: `1.0` → Note: copia `changelogs/default.txt` → **Avanti** → **Salva** →
   **Invia per la revisione**.

## Passo 10 – Test chiuso (solo account personale)

1. Test chiuso → **Tester** → crea una lista email con almeno **12 persone** (meglio 15–20, qualcuno si perde).
2. Manda loro il **link di partecipazione**: devono accettare, installare dal Play Store e **usare davvero l'app**
   per 14 giorni. Google controlla che l'abbiano aperta.
3. Dopo 14 giorni: **Dashboard → Richiedi l'accesso alla produzione** e rispondi alle domande sul test.

## Passo 11 – Revisione e pubblicazione

La prima revisione di solito richiede **da qualche giorno a 1–2 settimane**. Se Google rifiuta qualcosa, arriva
un'email con il motivo: mandamela e la sistemiamo.

## Aggiornamenti automatici (da configurare una volta)

Dopo questa configurazione non devi più scaricare né caricare niente:
- **ogni modifica al codice** (push sul branch principale) arriva da sola nel **Test interno** in pochi minuti;
- per mandarla **ai tester o a tutti**: GitHub → **Actions** → **Build APK** → **Run workflow** → scegli
  `alpha` (test chiuso) o `production` → **Run workflow**. Per la produzione Google fa comunque la sua revisione.
- Note di rilascio: `fastlane/metadata/android/it-IT/changelogs/default.txt`. Per una build specifica si può
  creare `changelogs/<numero build>.txt`.

### A. Collega GitHub a Google Cloud senza chiavi (Cloud Shell, 5 minuti)

Si usa la **federazione delle identità per i workload**: nessun file di chiavi. A ogni build GitHub presenta a
Google un "tesserino" firmato che vale solo per il repository `myonlyalphanumeric/ctvs-android`, e Google risponde
con un permesso che scade dopo un'ora. È il metodo consigliato da Google, e funziona anche se la tua organizzazione
blocca le chiavi JSON (regola `iam.disableServiceAccountKeyCreation`).

Serve l'account di servizio `github-play` (già creato: Google Cloud → IAM e amministrazione → Account di servizio).

1. In Google Cloud, con il progetto `citiverse-play` selezionato in alto, apri la **Cloud Shell**: icona `>_` in alto a destra.
2. Incolla questi comandi, tutti insieme, e premi Invio:

```bash
PROJECT_ID=$(gcloud config get-value project)
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
REPO=myonlyalphanumeric/ctvs-android
SA="github-play@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud services enable androidpublisher.googleapis.com iamcredentials.googleapis.com sts.googleapis.com

gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc github \
  --location=global --workload-identity-pool=github --display-name="GitHub" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${REPO}'"

gcloud iam service-accounts add-iam-policy-binding "$SA" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/${REPO}"

echo "GCP_WIF_PROVIDER = projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/providers/github"
echo "GCP_SERVICE_ACCOUNT = ${SA}"
```

Cosa fanno:
- **Le prime quattro righe** salvano in variabili i dati usati dopo: `gcloud config get-value project` legge il
  progetto aperto, `gcloud projects describe … --format='value(projectNumber)'` ne ricava il numero (serve negli
  indirizzi di Google), `REPO` è il tuo repository, `SA` è l'email dell'account di servizio.
- **`gcloud services enable …`** attiva tre API: quella di Google Play (`androidpublisher`), quella che genera i
  permessi temporanei (`iamcredentials`) e quella che scambia il tesserino di GitHub con uno di Google (`sts`).
- **`workload-identity-pools create github`** crea un "gruppo di identità esterne" chiamato `github`.
  `--location=global` perché questi oggetti non stanno in una regione, `--display-name` è solo il nome visibile.
- **`providers create-oidc github`** dice a Google di fidarsi dei tesserini firmati da GitHub:
  - `--issuer-uri` è l'indirizzo con cui GitHub firma i tesserini;
  - `--attribute-mapping` indica quali dati del tesserino leggere (chi è e da quale repository arriva);
  - `--attribute-condition` è il lucchetto: **accetta solo tesserini del tuo repository**. Gli altri repository
    di GitHub vengono rifiutati.
- **`add-iam-policy-binding … roles/iam.workloadIdentityUser`** permette **solo** ai workflow del tuo repository
  di agire come l'account di servizio `github-play`.
- **Le due righe `echo`** stampano i due valori da copiare in GitHub (passo C).

Se un comando dice che la cosa "esiste già" (per esempio se lo rilanci due volte), va bene: passa al successivo.
Se in fondo non vedi le due righe `GCP_…`, mandami lo screenshot.

### B. Dai i permessi nella Play Console
1. Play Console → **Utenti e autorizzazioni** (menu a sinistra, nella pagina di tutte le app) → **Invita nuovi utenti**.
2. Email: quella di `GCP_SERVICE_ACCOUNT` (es. `github-play@citiverse-play.iam.gserviceaccount.com`).
3. Scheda **Autorizzazioni app** → **Aggiungi app** → **Citiverse** → spunta:
   **Rilasciare app in produzione, escludere dispositivi e usare Play App Signing**,
   **Rilasciare nei canali di test**, **Gestire i canali di test e modificare gli elenchi di tester**.
   Nient'altro (niente dati finanziari, niente amministrazione).
4. **Invita utente** → conferma. I permessi possono metterci qualche minuto ad attivarsi.

### C. Metti i due valori in GitHub
Non sono segreti (da soli non danno accesso a niente), quindi vanno tra le **variabili**, non tra i secrets.
GitHub → repository → **Settings → Secrets and variables → Actions** → scheda **Variables** → **New repository variable**, due volte:
- Name `GCP_WIF_PROVIDER` → Value: la riga `projects/…/providers/github` stampata dalla Cloud Shell
- Name `GCP_SERVICE_ACCOUNT` → Value: l'email dell'account di servizio

Finché le variabili non ci sono, la build funziona come prima e salta solo la pubblicazione.

## Aggiornamenti senza automazione (se non configuri la parte sopra)

Scarica l'artifact `citiverse-aab-N` dall'ultima build su GitHub (Actions), estrai il file `.aab` e caricalo nella
Console come nuova release. Il numero di versione cresce da solo.
