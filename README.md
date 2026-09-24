# Citiverse per Android

App Android nativa (Kotlin + Jetpack Compose) per [citiverse.it](https://citiverse.it), il forum NodeBB 4.x
"città dei social liberi". Usa solo le API ufficiali di NodeBB, senza plugin sul server e senza Google Play Services.

## Scaricare l'APK

Ogni push viene compilato da GitHub Actions (`.github/workflows/android.yml`):

- **Releases** del repository → release `apk-<nome-branch>` (sostituita a ogni build) → file `citiverse-N.apk`.
  È il modo più comodo dal telefono: si scarica senza fare login su GitHub.
- In alternativa: scheda **Actions** → ultima esecuzione → artifact `citiverse-apk-N` (zip, serve il login).

Per installarlo: apri il file `.apk` sul telefono e consenti l'installazione da "origini sconosciute" per il browser
o il file manager. Le build successive si installano sopra la precedente (stessa firma, `versionCode` crescente).

## Cosa c'è

**Tappa 1 – forum**
- Login con l'account del sito (sessione NodeBB via cookie + token CSRF).
- Feed a card: riga *comunità · autore · tempo*, titolo, miniatura o riquadro col colore/immagine della comunità,
  barra con pillola dei voti (arancione `#ff4500` su, viola `#6a5cff` giù), commenti, condividi.
- Sezioni **Recenti / Popolari / Più votati** a pillole (con periodo: sempre, oggi, settimana, mese) e fila
  **"Le tue comunità"** (categorie seguite) oppure **"Comunità più attive"**.
- **Trova una comunità**: ricerca senza accenti per nome, percorso e descrizione, ordinamento.
- Pagina comunità: intestazione col colore della categoria, *Segui*, chip **"Segui dal fediverso"** con
  l'indirizzo `@categoria@citiverse.it` (tocco = copia), sottocategorie compatte, nuova discussione.
- Discussione leggibile: primo post più grande, chip **Autore** sulle risposte di chi ha aperto la discussione,
  chip **🌐 istanza** per chi scrive da altre istanze del fediverso, voti per ogni post, risposta (anche a un
  post specifico), segui la discussione, link interni aperti nell'app.
- Tema chiaro/scuro (automatico o scelto nelle impostazioni).

**Tappa 2 – notifiche senza Google**
- **In tempo reale**: servizio in primo piano con notifica fissa silenziosa (canale separato che si può
  nascondere) che tiene aperto il WebSocket di NodeBB (socket.io, Engine.IO v4) come fanno Signal/Molly.
  - keepalive allineato al server: è NodeBB che manda il *ping* (ogni 25 s su citiverse.it, timeout 20 s,
    letti dall'handshake); se non arriva entro `pingInterval + pingTimeout` la connessione è considerata morta;
  - riconnessione con attese crescenti (2 s, 4 s, 8 s… fino a 5 min, con un po' di casualità), immediata
    quando torna la rete; allarme ogni 15 minuti che funziona anche in Doze;
  - alla riconnessione recupera le notifiche arrivate nel frattempo (senza doppioni).
- **Ogni 15 minuti**: modalità a basso consumo con WorkManager, senza connessione fissa.
- Canali separati: Risposte, Menzioni, Voti, Chat, Nuovi follower, Altre, Connessione in background.
- Tocco sulla notifica → apre la discussione (e il post) nell'app, e segna la notifica come letta.
- Richiesta di esclusione dall'ottimizzazione batteria e pagina di aiuto per Xiaomi, Samsung, Huawei/Honor,
  OPPO/Realme/OnePlus, vivo.
- I testi delle notifiche (`[[notifications:...]]`) sono tradotti con i file di lingua italiani del forum.

ntfy / UnifiedPush / FCM potranno essere aggiunti in futuro come alternativa al WebSocket.

## API usate

| Cosa | Endpoint |
|---|---|
| Sessione e token CSRF | `GET /api/config` |
| Login | `POST /api/v3/utilities/login` |
| Feed | `GET /api/recent`, `/api/popular`, `/api/top` (`?page=N&term=…`) |
| Comunità | `GET /api/v3/categories`, `GET /api/category/{id}?page=N`, `GET /api/user/{slug}/categories` |
| Segui comunità | `PUT /api/v3/categories/{cid}/watch` |
| Discussione | `GET /api/topic/{tid}?page=N`, `PUT/DELETE /api/v3/topics/{tid}/follow` |
| Risposta / nuova discussione | `POST /api/v3/topics/{tid}`, `POST /api/v3/topics` |
| Voti | `GET /api/v3/posts/{pid}`, `PUT/DELETE /api/v3/posts/{pid}/vote` |
| Notifiche | `GET /api/notifications`, socket.io `event:new_notification` |

Gli id di discussioni, post e utenti possono essere numeri (contenuti locali) oppure URL/UUID (contenuti del
fediverso): l'app li tratta sempre come stringhe.

## Compilare in locale

Serve JDK 17 e l'Android SDK (platform 35):

```bash
./gradlew assembleRelease          # APK in app/build/outputs/apk/release/
./gradlew testReleaseUnitTest      # test JVM (protocollo socket.io, traduzioni, formattazione)
```

## Firma

Senza configurazione l'APK è firmato con `app/dev.keystore`, una chiave **di sviluppo** (password `android`)
tenuta nel repository apposta, così ogni build si installa sopra la precedente. Non protegge nulla: per una
distribuzione pubblica crea una chiave tua e mettila nei *secrets* del repository:

- `RELEASE_KEYSTORE_BASE64` – il file `.jks` codificato in base64
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

(Cambiando chiave bisogna disinstallare una volta la versione firmata con quella di sviluppo.)

## Account di test

Per le prove automatiche si possono usare le variabili d'ambiente `CITIVERSE_USERNAME` / `CITIVERSE_PASSWORD`.
Non vanno mai scritte nel codice o nei commit.
