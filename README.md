# MindBuddy

MindBuddy este o aplicație de companion pentru sănătate mintală. Are un chatbot empatic care răspunde în limba română, ține un istoric al conversațiilor, urmărește starea emoțională a utilizatorului în timp și include un mecanism de siguranță care intervine când apar semnale repetate de risc.

Proiectul a fost realizat ca lucrare de licență și are două componente: o aplicație Android și un backend propriu care se ocupă de conturi, conversații și de comunicarea cu modelul de limbaj.

## Despre aplicație

Ideea de la care am pornit a fost una simplă: un loc unde cineva poate să vorbească atunci când nu are cu cine, fără să fie judecat. Chatbot-ul nu înlocuiește un specialist și nu pretinde că o face — răspunsurile sunt scurte, calde și încearcă să încurajeze persoana să caute ajutor real când e cazul.

Pe lângă partea de conversație, aplicația clasifică emoția fiecărui mesaj și construiește în timp un grafic al stării emoționale. Dacă apar în mod repetat mesaje cu gânduri grele, aplicația intră într-un mod de protecție: oprește conversațiile și anunță automat o persoană de încredere aleasă de utilizator.

## Arhitectură

Aplicația e împărțită în trei părți care comunică între ele:

- **Aplicația Android** (Kotlin + Jetpack Compose) — interfața cu care interacționează utilizatorul.
- **Backend Node.js** (Express) — expune un API REST, gestionează conturile, conversațiile și logica de siguranță.
- **PostgreSQL** — baza de date unde se salvează utilizatorii, conversațiile, mesajele și înregistrările de stare emoțională.

Pentru generarea răspunsurilor și pentru clasificarea emoțiilor, backend-ul folosește API-ul **DeepSeek**. Aplicația Android nu vorbește niciodată direct cu modelul — totul trece prin backend, care ține cheia de acces și aplică regulile de siguranță.

```
Android (Compose)  ──►  Backend (Express)  ──►  PostgreSQL
                              │
                              └──►  DeepSeek API (chat + clasificare emoții)
```

## Structura proiectului

```
proiect/
├── backend/                 # API Node.js + Express
│   ├── index.js             # toate rutele si logica
│   ├── auth.js              # middleware JWT
│   ├── db.js                # pool-ul de conexiuni PostgreSQL
│   ├── package.json
│   └── .env.example         # model de configurare (fara valori reale)
├── Mental_Healt_ChatBot/    # aplicatia Android (Kotlin + Compose)
│   └── app/src/main/...
└── DB/
    └── schema.sql           # structura bazei de date

```

## Tehnologii

Backend: Node.js, Express, PostgreSQL (`pg`), JSON Web Tokens, bcrypt pentru parole, Nodemailer pentru email-uri (verificare cont, resetare parolă, alerte), DeepSeek API.

Android: Kotlin, Jetpack Compose, Material 3, Retrofit + OkHttp pentru rețea, Coroutines, EncryptedSharedPreferences pentru date sensibile, Biometric + WorkManager pentru blocarea aplicației și remindere, Vico pentru graficul de emoții.

## Cerințe

- Node.js 18 sau mai nou
- PostgreSQL 14 sau mai nou
- Android Studio (cu Android SDK, API minim 29) și JDK 11
- Un cont DeepSeek cu o cheie de API

## Configurare backend

1. Intri în folderul backend și instalezi dependențele:

   ```bash
   cd backend
   npm install
   ```

2. Creezi baza de date și rulezi schema:

   ```bash
   psql -U <utilizator> -d <baza_de_date> -f ../DB/schema.sql
   ```

   (sau deschizi `DB/schema.sql` în pgAdmin și dai Run pe o bază goală)

3. Faci o copie după `.env.example`, o redenumești `.env` și completezi valorile tale:

   ```bash
   cp .env.example .env
   ```

   În `.env` pui cheia DeepSeek, datele de conectare la PostgreSQL, secretul pentru JWT și contul de email de pe care se trimit mesajele. Fișierul `.env` nu se urcă niciodată în repository.

4. Pornești serverul:

   ```bash
   node index.js
   ```

   Backend-ul ascultă pe portul din `PORT` (implicit 80) și pe toate interfețele, ca să poată fi accesat și de pe telefon.

## Configurare aplicație Android

1. Deschizi folderul `Mental_Healt_ChatBot` în Android Studio și lași Gradle să sincronizeze.

2. În `app/src/main/java/com/example/mental_healt_chatbot/RetrofitClient.kt` setezi `BASE_URL` către backend-ul tău. În funcție de cum testezi:

   - emulator Android: `http://10.0.2.2:<port>`
   - telefon fizic în aceeași rețea: `http://<ip-ul-calculatorului>:<port>`
   - acces din afara rețelei: un tunel public (de exemplu ngrok)

3. Rulezi aplicația pe un emulator sau pe un telefon cu Android 10 (API 29) sau mai nou.

## Funcționalități

**Cont și autentificare.** Înregistrare cu verificare pe email, autentificare cu username sau email, resetare de parolă. Parolele sunt stocate doar ca hash (bcrypt), iar sesiunea e ținută cu JWT.

**Conversații.** Utilizatorul poate porni conversații noi, le poate relua din istoric și le poate șterge. Fiecare răspuns vine de la chatbot prin backend, cu tot contextul conversației trimis modelului.

**Mod privat.** O conversație care nu se salvează nicăieri — istoricul există doar pe durata sesiunii. Singura excepție e plasa de siguranță: dacă apare un semnal de risc, se reține doar acel semnal, fără textul mesajului.

**Urmărirea stării emoționale.** Fiecare mesaj este clasificat (bucurie, tristețe, anxietate, furie, neutru), iar pe baza acestor valori aplicația desenează un grafic al stării emoționale pe intervalul ales.

**Blocare cu PIN.** Aplicația se poate bloca cu un PIN propriu (și amprentă/față, dacă telefonul suportă), ca să nu poată citi nimeni conversațiile private. PIN-ul stă criptat pe telefon, nu pe server.

**Remindere.** Notificări locale la o frecvență aleasă de utilizator: din oră în oră, de patru ori pe zi, de două ori pe zi sau o dată pe zi.

**Mecanism de siguranță.** Dacă mesajele cu gânduri suicidale se repetă peste un prag, aplicația intră în modul de criză: oprește conversațiile și trimite automat un email persoanei de încredere desemnate. Blocarea se reține și la nivel de dispozitiv, ca să țină chiar dacă se șterge și se recreează contul. Înainte de a putea conversa, utilizatorul trebuie oricum să aibă setat un contact de încredere.

## Notă

MindBuddy nu este un dispozitiv medical și nu înlocuiește consultul unui specialist. În situații de urgență, numărul 112 și TelVerde Antisuicid (0800 801 200) sunt disponibile non-stop.

## Autor

Vlad Tamășilă — Lucrare de licență, Facultatea de Informatică, Universitatea de Vest din Timișoara.
