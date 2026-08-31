import express from "express";
import cors from "cors";
import axios from "axios";
import dotenv from "dotenv";
import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";
import { pool } from "./db.js";
import { requireAuth } from "./auth.js";
import crypto from "crypto";
import nodemailer from "nodemailer";
import bodyParser from "body-parser";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());
app.use(bodyParser.urlencoded({ extended: true })); // pentru link browser reset parola

app.use((req, res, next) => {
  console.log("REQ:", req.method, req.url);
  next();
});

// ─── EMAIL ────────────────────────────────────────────────────────────────────

const mailer = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASS
  }
});

async function sendVerificationEmail(email, token) {
  const verifyUrl = `${process.env.APP_BASE_URL}/auth/verify-email?token=${token}`;
  await mailer.sendMail({
    from: process.env.EMAIL_USER,
    to: email,
    subject: "Verifica-ti contul",
    html: `
      <h2>Verifica-ti contul</h2>
      <p>Da click mai jos pentru a iti verifica contul:</p>
      <a href="${verifyUrl}">Verifica-ti contul</a>
    `
  });
}

async function sendResetEmail(email, token) {
  const resetUrl = `${process.env.APP_BASE_URL}/auth/reset-password-page?token=${token}`;
  await mailer.sendMail({
    from: process.env.EMAIL_USER,
    to: email,
    subject: "Resetare de parola",
    html: `
      <h2>Reseteaza parola</h2>
      <p>Da click mai jos pentru a iti reseta parola:</p>
      <a href="${resetUrl}">Resetare</a>
    `
  });
}

// trimite cod numeric de 6 cifre pentru resetarea PIN-ului din aplicatie
async function sendPinResetCodeEmail(email, code) {
  await mailer.sendMail({
    from: process.env.EMAIL_USER,
    to: email,
    subject: "Cod resetare PIN MindBuddy",
    html: `
      <h2>Resetare PIN</h2>
      <p>Codul tau de resetare este: <b style="font-size:22px">${code}</b></p>
      <p>Codul expira in 15 minute. Daca nu tu ai cerut resetarea, ignora acest email.</p>
    `
  });
}

// ─── MIDDLEWARE ───────────────────────────────────────────────────────────────

function requireAppSecret(req, res, next) {
  const publicPaths = [
    "/health",
    "/db-health",
    "/auth/register",
    "/auth/login",
    "/auth/verify-email",
    "/auth/forgot-password",
    "/auth/reset-password",
    "/auth/reset-password-page",
    "/auth/reset-password-html",
    "/auth/resend-verification",  
    "/safety/crisis-unlock/confirm",  // link-ul din email - acceseaza contactul de incredere
    "/safety/device-check"            // verificare device la app start, fara user logat
  ];
  // pin-reset NU e public - cere JWT (userul trebuie sa fie logat)

  if (publicPaths.includes(req.path)) return next();

  const secret = req.headers["x-app-secret"];
  if (!process.env.APP_SECRET) return next();
  if (secret !== process.env.APP_SECRET) {
    return res.status(401).send({ error: "Unauthorized" });
  }
  next();
}

app.use(requireAppSecret);

// ─── HEALTH ───────────────────────────────────────────────────────────────────

app.get("/health", (req, res) => res.json({ ok: true }));

app.get("/db-health", async (req, res) => {
  try {
    const r = await pool.query("SELECT NOW() as now");
    res.json({ ok: true, now: r.rows[0].now });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ─── AUTH ─────────────────────────────────────────────────────────────────────

app.post("/auth/register", async (req, res) => {
  try {
    const { username, email, password } = req.body || {};
    if (!username || !email || !password) {
      return res.status(400).send({ error: "Nume de utilizator, email si parola sunt obligatorii" });
    }

    const hash = await bcrypt.hash(password, 10);
    const verificationToken = crypto.randomBytes(32).toString("hex");

    const q = `
      INSERT INTO users (username, email, password_hash, is_verified, verification_token)
      VALUES ($1, $2, $3, FALSE, $4)
      RETURNING id, username, email, created_at
    `;
    const r = await pool.query(q, [
      username.trim(),
      email.toLowerCase().trim(),
      hash,
      verificationToken
    ]);

    await sendVerificationEmail(email.toLowerCase().trim(), verificationToken);

    res.json({
      message: "Cont creat. Te rog verifica email-ul inainte de logare.",
      user: r.rows[0]
    });
  } catch (e) {
    if (e.code === "23505") {
      return res.status(409).send({ error: "Numele de utilizator sau emailul deja exista" });
    }
    res.status(500).json({ error: e.message });
  }
});

app.get("/auth/verify-email", async (req, res) => {
  try {
    const token = req.query.token;
    if (!token) return res.status(400).send({ error: "missing token" });

    const r = await pool.query(
      `SELECT id FROM users WHERE verification_token = $1`,
      [token]
    );

    if (r.rowCount === 0) {
      return res.status(400).send({ error: "Token de verificare invalid sau expirat" });
    }

    await pool.query(
      `UPDATE users SET is_verified = TRUE, verification_token = NULL WHERE id = $1`,
      [r.rows[0].id]
    );

    res.send(`
      <h2>Email verificat cu succes</h2>
      <p>Te poti intoarce la aplicatie si sa te loghezi.</p>
    `);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/login", async (req, res) => {
  const t0 = Date.now();
  try {
    console.log("LOGIN start");
    const { identifier, password } = req.body || {};
    if (!identifier || !password) {
      return res.status(400).send({ error: "Identificatorul si parola sunt obligatorii" });
    }

    const value = identifier.trim();
    const r = await pool.query(
      `SELECT id, username, email, password_hash, is_verified
       FROM users
       WHERE LOWER(email) = LOWER($1) OR username = $1
       LIMIT 1`,
      [value]
    );
    console.log("after db:", Date.now() - t0, "ms");

    if (r.rowCount === 0) {
      return res.status(401).send({ error: "Caractere invalide" });
    }

    const user = r.rows[0];
    const ok = await bcrypt.compare(password, user.password_hash);
    console.log("after bcrypt:", Date.now() - t0, "ms");

    if (!ok) return res.status(401).send({ error: "Caractere invalide" });

    if (!user.is_verified) {
      return res.status(403).send({ error: "Te rog verifica email-ul inainte de logare" });
    }

    const token = jwt.sign(
      { username: user.username, email: user.email },
      process.env.JWT_SECRET,
      { subject: user.id, expiresIn: process.env.JWT_EXPIRES || "7d" }
    );
    console.log("after jwt:", Date.now() - t0, "ms");

    res.json({ token });
    console.log("LOGIN done:", Date.now() - t0, "ms");
  } catch (e) {
    console.error("LOGIN error:", e);
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/forgot-password", async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email) return res.status(400).send({ error: "Emailul e obligatoriu" });

    const normalizedEmail = email.toLowerCase().trim();
    const r = await pool.query(
      "SELECT id, email FROM users WHERE email = $1",
      [normalizedEmail]
    );

    if (r.rowCount > 0) {
      const resetToken = crypto.randomBytes(32).toString("hex");
      const expires = new Date(Date.now() + 1000 * 60 * 15);

      await pool.query(
        `UPDATE users SET reset_token = $1, reset_token_expires = $2 WHERE email = $3`,
        [resetToken, expires, normalizedEmail]
      );

      await sendResetEmail(normalizedEmail, resetToken);
    }

    res.json({ message: "Daca emailul exista, un link de resetare a fost trimis." });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/resend-verification", async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email) return res.status(400).json({ error: "Emailul e obligatoriu" });

    const normalizedEmail = email.toLowerCase().trim();
    const r = await pool.query(
      `SELECT id, email, is_verified FROM users WHERE email = $1`,
      [normalizedEmail]
    );

    if (r.rowCount === 0) {
      return res.json({ message: "Daca emailul exista si nu e verificat, un link nou a fost trimis." });
    }

    if (r.rows[0].is_verified) {
      return res.status(400).json({ error: "Contul tau a fost deja verificat. Te poti loga." });
    }

    const newToken = crypto.randomBytes(32).toString("hex");
    await pool.query(
      `UPDATE users SET verification_token = $1 WHERE id = $2`,
      [newToken, r.rows[0].id]
    );

    await sendVerificationEmail(normalizedEmail, newToken);
    res.json({ message: "Daca emailul exista si nu e verificat, un link nou a fost trimis." });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/reset-password", async (req, res) => {
  try {
    const { token, newPassword } = req.body || {};
    if (!token || !newPassword) {
      return res.status(400).send({ error: "Token si parola noua sunt necesare" });
    }

    const r = await pool.query(
      `SELECT id FROM users
       WHERE reset_token = $1
         AND reset_token_expires IS NOT NULL
         AND reset_token_expires > NOW()`,
      [token]
    );

    if (r.rowCount === 0) {
      return res.status(400).send({ error: "Token de reset invalid sau expirat" });
    }

    const hash = await bcrypt.hash(newPassword, 10);
    await pool.query(
      `UPDATE users SET password_hash = $1, reset_token = NULL, reset_token_expires = NULL WHERE id = $2`,
      [hash, r.rows[0].id]
    );

    res.json({ message: "Resetarea de parola a avut succes" });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/reset-password-html", async (req, res) => {
  try {
    const { token, newPassword } = req.body || {};
    if (!token || !newPassword) {
      return res.status(400).send("Token si parola noua sunt necesare");
    }

    const r = await pool.query(
      `SELECT id FROM users
       WHERE reset_token = $1
         AND reset_token_expires IS NOT NULL
         AND reset_token_expires > NOW()`,
      [token]
    );

    if (r.rowCount === 0) {
      return res.status(400).send("Token de reset invalid sau expirat");
    }

    const hash = await bcrypt.hash(newPassword, 10);
    await pool.query(
      `UPDATE users SET password_hash = $1, reset_token = NULL, reset_token_expires = NULL WHERE id = $2`,
      [hash, r.rows[0].id]
    );

    res.send(`
      <html>
        <body style="font-family: Arial; padding: 24px;">
          <h2>Resetarea de parola a avut succes</h2>
          <p>Te poti intoarce si loga in aplicatie.</p>
        </body>
      </html>
    `);
  } catch (e) {
    res.status(500).send(e.message);
  }
});

app.get("/auth/reset-password-page", (req, res) => {
  const token = req.query.token || "";
  res.send(`
    <html>
      <body style="font-family: Arial; padding: 24px;">
        <h2>Reseteaza parola</h2>
        <form method="POST" action="/auth/reset-password-html">
          <input type="hidden" name="token" value="${token}" />
          <input type="password" name="newPassword" placeholder="Parola noua" required />
          <button type="submit">Reseteaza parola</button>
        </form>
      </body>
    </html>
  `);
});

// ─── PIN RESET ────────────────────────────────────────────────────────────────

// userul logat cere un cod pe email pentru a-si reseta PIN-ul local
app.post("/auth/pin-reset/send-code", requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `SELECT email FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount === 0) {
      return res.status(404).json({ error: "Utilizator inexistent" });
    }

    // cod numeric de 6 cifre, mai usor de tastat decat un token hex
    const code = ("" + Math.floor(100000 + Math.random() * 900000));
    const expires = new Date(Date.now() + 1000 * 60 * 15); // 15 min

    await pool.query(
      `UPDATE users SET pin_reset_code = $1, pin_reset_expires = $2 WHERE id = $3`,
      [code, expires, req.user.id]
    );

    await sendPinResetCodeEmail(r.rows[0].email, code);
    res.json({ message: "Cod trimis pe email." });
  } catch (e) {
    console.error("ERR /auth/pin-reset/send-code:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// verifica cod + parola contului. daca e ok, app-ul sterge PIN-ul local
app.post("/auth/pin-reset/verify", requireAuth, async (req, res) => {
  try {
    const { code, password } = req.body || {};
    if (!code || !password) {
      return res.status(400).json({ error: "Cod si parola obligatorii" });
    }

    const r = await pool.query(
      `SELECT password_hash, pin_reset_code, pin_reset_expires
       FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount === 0) return res.status(404).json({ error: "Utilizator inexistent" });

    const u = r.rows[0];
    const ok = await bcrypt.compare(password, u.password_hash);
    if (!ok) return res.status(401).json({ error: "Parola incorecta" });

    if (!u.pin_reset_code || !u.pin_reset_expires) {
      return res.status(400).json({ error: "Nu exista o cerere activa de resetare" });
    }
    if (new Date(u.pin_reset_expires) < new Date()) {
      return res.status(400).json({ error: "Cod expirat. Cere unul nou." });
    }
    if (u.pin_reset_code !== String(code).trim()) {
      return res.status(400).json({ error: "Cod incorect" });
    }

    // invalideaza codul ca sa nu poata fi refolosit
    await pool.query(
      `UPDATE users SET pin_reset_code = NULL, pin_reset_expires = NULL WHERE id = $1`,
      [req.user.id]
    );

    res.json({ message: "Validat. Poti seta un PIN nou in aplicatie." });
  } catch (e) {
    console.error("ERR /auth/pin-reset/verify:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// ─── SAFETY ───────────────────────────────────────────────────────────────────

// calculeaza starea de safety a userului:
//   ok          - normal
//   concerning  - media sad pe ultimele 5 mesaje >= 50% (forteaza reminders din ora in ora)
//   crisis      - blocat (4+ mentiuni suicidale in ultimele 7 zile)
async function computeSafetyStatus(userId) {
  // criza: se uita direct in flag-ul din users
  const u = await pool.query(
    `SELECT crisis_locked FROM users WHERE id = $1`,
    [userId]
  );
  if (u.rowCount > 0 && u.rows[0].crisis_locked === true) {
    return { state: "crisis", forced_hourly: false };
  }

  // concerning: media sad pe ultimele 5 mesaje
  const m = await pool.query(
    `SELECT sad FROM mood_entries
     WHERE user_id = $1
     ORDER BY created_at DESC
     LIMIT 5`,
    [userId]
  );
  if (m.rowCount >= 3) {
    const avgSad = m.rows.reduce((a, r) => a + r.sad, 0) / m.rowCount;
    if (avgSad >= 50) {
      return { state: "concerning", forced_hourly: true };
    }
  }

  return { state: "ok", forced_hourly: false };
}

// numara mentiunile suicidale in ultimele 7 zile pentru un user
async function countRecentSuicidalMentions(userId) {
  const r = await pool.query(
    `SELECT COUNT(*)::int AS c FROM mood_entries
     WHERE user_id = $1
       AND suicidal_mention = TRUE
       AND created_at > NOW() - INTERVAL '7 days'`,
    [userId]
  );
  return r.rows[0].c;
}

// blocheaza userul si device-ul. device_id vine din headerul X-Device-Id
// si trimite imediat un mail de alerta catre contactul de incredere
async function lockUserCrisis(userId, deviceId) {
  await pool.query(
    `UPDATE users SET crisis_locked = TRUE, crisis_locked_at = NOW() WHERE id = $1`,
    [userId]
  );
  // device-ul e secundar - daca tabela nu are drepturi/lipseste, nu afecteaza blocarea principala
  if (deviceId) {
    try {
      await pool.query(
        `INSERT INTO device_crisis_locks (device_id, locked_at)
         VALUES ($1, NOW())
         ON CONFLICT (device_id) DO UPDATE SET locked_at = NOW(), unlocked_at = NULL`,
        [deviceId]
      );
    } catch (devErr) {
      console.error("device_crisis_locks insert failed (continuing):", devErr.message);
    }
  }

  // alerta automata catre contactul de incredere
  try {
    const r = await pool.query(
      `SELECT username, trusted_contact_email FROM users WHERE id = $1`,
      [userId]
    );
    const u = r.rows[0];
    if (u?.trusted_contact_email) {
      const name = u.username;
      await mailer.sendMail({
        from: process.env.EMAIL_USER,
        to: u.trusted_contact_email,
        subject: `${name} are nevoie de tine acum`,
        html: `
          <div style="font-family: Arial, sans-serif; max-width: 520px; line-height: 1.5;">
            <p>Buna,</p>

            <p><b>${name}</b> te-a desemnat persoana lui/ei de incredere in aplicatia MindBuddy.</p>

            <p>In ultimele zile a vorbit despre ganduri grele, iar aplicatia a intrat in modul de protectie - nu ii mai permite sa converseze cu chatbot-ul pana cand nu se asigura ca este in siguranta.</p>

            <p style="background:#fff3cd; padding:12px; border-left:4px solid #ffc107; border-radius:4px;">
              <b>Te rugam sa ii dai un telefon cat poti de repede.</b> Chiar si un mesaj scurt poate sa conteze.
              Nu trebuie sa stii exact ce sa spui - cel mai mult conteaza sa fii acolo si sa asculti.
            </p>

            <p>Daca simti ca situatia este grava chiar acum, suna la
            <b style="color:#c00;">112</b> sau la
            <b style="color:#c00;">TelVerde Antisuicid 0800 801 200</b>.</p>

            <p style="color:#666; font-size:13px; margin-top:24px;">
              — Aplicatia MindBuddy<br>
              <i>Acest email a fost trimis automat. Daca nu cunosti aceasta persoana, ignora-l.</i>
            </p>
          </div>
        `
      });
    }
  } catch (e) {
    console.error("crisis alert mail failed:", e.message);
  }
}

// middleware: refuza chat-ul daca userul nu a setat un contact de incredere
async function requireTrustedContact(req, res, next) {
  try {
    const r = await pool.query(
      `SELECT trusted_contact_email FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount === 0 || !r.rows[0].trusted_contact_email) {
      return res.status(428).json({
        error: "Adaugă un contact de încredere înainte să conversezi.",
        missing_trusted_contact: true
      });
    }
    next();
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
}

// middleware: blocheaza chat-ul (orice fel) daca userul e in criza
async function rejectIfCrisis(req, res, next) {
  try {
    const r = await pool.query(
      `SELECT crisis_locked FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount > 0 && r.rows[0].crisis_locked === true) {
      return res.status(423).json({
        error: "Conversațiile sunt suspendate. Te rugăm să cauți ajutor specializat.",
        crisis: true
      });
    }
    next();
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
}

// trimite emailul cu link de validare pentru contactul de incredere
async function sendCrisisUnlockEmail(toEmail, userName, token) {
  const url = `${process.env.APP_BASE_URL}/safety/crisis-unlock/confirm?token=${token}`;
  await mailer.sendMail({
    from: process.env.EMAIL_USER,
    to: toEmail,
    subject: "Cerere de validare - MindBuddy",
    html: `
      <h2>Cerere de validare pentru ${userName}</h2>
      <p>${userName} folosește aplicația MindBuddy si te-a desemnat drept contact de incredere.</p>
      <p>Aplicatia a detectat ca ${userName} ar putea avea nevoie de sprijin specializat si a intrat intr-un mod de protectie.</p>
      <p>Daca esti langa ${userName} si crezi ca este in regula sa continue sa foloseasca aplicatia, apasa link-ul de mai jos:</p>
      <p><a href="${url}" style="background:#5e6ce0;color:white;padding:12px 18px;border-radius:8px;text-decoration:none;">Confirm ca ${userName} este bine</a></p>
      <p>Linkul expira in 24 de ore. Daca nu cunosti aceasta persoana, ignora acest email.</p>
    `
  });
}

// ─── SENTIMENT ────────────────────────────────────────────────────────────────

// detectie keyword-based de safety net - daca DeepSeek-ul gresegte, prinde aici mesajele evidente
function looksSuicidal(text) {
  if (!text) return false;
  const t = String(text).toLowerCase();
  const patterns = [
    /vreau s[aă] mor\b/,
    /vreau s[aă] m[aă] sinucid/,
    /s[aă] m[aă] omor/,
    /s[aă]\-?mi iau via[tțţ]a/,
    /nu mai vreau s[aă] tr[aă]iesc/,
    /s[aă] m[aă] arunc(?: de| pe| in| din)/,
    /m[aă] sinucid/,
    /s[aă] termin cu (tot|toate|viata|via[tţ]a)/,
    /\bsuicid\b/,
    /kill myself/,
    /want to die/,
    /end my life/
  ];
  return patterns.some(p => p.test(t));
}

async function classifyEmotion(userText) {
  const prompt = `
Return ONLY valid JSON (no markdown, no explanation).
Emotions: happy, sad, anxious, angry, neutral.
Rules:
- All values are integers 0..100
- Sum must be exactly 100
- dominant_emotion must be one of: happy|sad|anxious|angry|neutral
- suicidal_mention: true if the text mentions suicide, self-harm, wanting to die, or thoughts of ending one's life. Otherwise false. Be careful with idioms like "mor de ras"/"die laughing" - those are NOT suicidal.
Text: ${JSON.stringify(userText)}
`;

  const keys = ["happy", "sad", "anxious", "angry", "neutral"];

  // fallback default - se foloseste daca DeepSeek crapa complet
  const fallback = {
    happy: 0, sad: 0, anxious: 0, angry: 0, neutral: 100,
    dominant_emotion: "neutral",
    suicidal_mention: looksSuicidal(userText)
  };

  try {
    const ds = await axios.post(
      "https://api.deepseek.com/chat/completions",
      {
        model: "deepseek-chat",
        messages: [
          { role: "system", content: "You are a strict JSON emotion classifier." },
          { role: "user", content: prompt }
        ],
        temperature: 0
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json"
        }
      }
    );

    const raw = ds.data?.choices?.[0]?.message?.content || "{}";
    let jsonText = raw;
    const m = raw.match(/\{[\s\S]*\}/);
    if (m) jsonText = m[0];

    const parsed = JSON.parse(jsonText);

    // sanitizare - tot ce nu e numar intreg 0..100 devine 0
    for (const k of keys) {
      let v = parsed[k];
      if (typeof v !== "number" || !Number.isFinite(v)) v = 0;
      v = Math.max(0, Math.min(100, Math.round(v)));
      parsed[k] = v;
    }

    // normalizare la suma 100 - daca DeepSeek a dat 98 sau 103, ajustam
    const sum = keys.reduce((acc, k) => acc + parsed[k], 0);
    if (sum === 0) {
      parsed.neutral = 100;
    } else if (sum !== 100) {
      let total = 0;
      for (let i = 0; i < keys.length - 1; i++) {
        parsed[keys[i]] = Math.round(parsed[keys[i]] * 100 / sum);
        total += parsed[keys[i]];
      }
      parsed[keys[keys.length - 1]] = Math.max(0, 100 - total);
    }

    // dominant_emotion - daca lipseste sau e invalid, se calculeaza aici
    if (!keys.includes(parsed.dominant_emotion)) {
      parsed.dominant_emotion = keys.reduce((a, b) =>
        parsed[a] >= parsed[b] ? a : b
      );
    }

    // suicidal_mention - DeepSeek sau keyword backup
    parsed.suicidal_mention = parsed.suicidal_mention === true || looksSuicidal(userText);

    return parsed;
  } catch (err) {
    console.error("classifyEmotion fallback:", err.message);
    return fallback;
  }
}

// ─── CONVERSAȚII ──────────────────────────────────────────────────────────────

// creaza conversatie noua
app.post("/conversations/new", requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `INSERT INTO conversations (user_id, title)
       VALUES ($1, 'Conversatie noua')
       RETURNING id, title, created_at`,
      [req.user.id]
    );
    res.json(r.rows[0]);
  } catch (e) {
    console.error("ERR /conversations/new:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// lista conversatii user
app.get("/conversations/list", requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `SELECT id, title, created_at, updated_at
       FROM conversations
       WHERE user_id = $1
       ORDER BY updated_at DESC NULLS LAST`,
      [req.user.id]
    );
    res.json(r.rows);
  } catch (e) {
    console.error("ERR /conversations/list:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// mesajele dintr-o conversatie
app.get("/conversations/:id/messages", requireAuth, async (req, res) => {
  try {
    const conv = await pool.query(
      `SELECT id FROM conversations WHERE id = $1 AND user_id = $2`,
      [req.params.id, req.user.id]
    );
    if (conv.rowCount === 0) {
      return res.status(404).json({ error: "Conversatia nu a fost gasita" });
    }

    const r = await pool.query(
      `SELECT id, role, content, created_at
       FROM messages
       WHERE conversation_id = $1
       ORDER BY created_at ASC`,
      [req.params.id]
    );
    res.json(r.rows);
  } catch (e) {
    console.error("ERR /conversations/:id/messages:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// trimite mesaj intr-o conversatie
app.post("/conversations/:id/send-message", requireAuth, rejectIfCrisis, requireTrustedContact, async (req, res) => {
  try {
    const { message } = req.body || {};
    if (!message || typeof message !== "string") {
      return res.status(400).json({ error: "Mesajul este obligatoriu" });
    }

    const conversationId = req.params.id;

    const conv = await pool.query(
      `SELECT id, title FROM conversations WHERE id = $1 AND user_id = $2`,
      [conversationId, req.user.id]
    );
    if (conv.rowCount === 0) {
      return res.status(404).json({ error: "Conversatia nu a fost gasita" });
    }

    // citeste istoricul complet al conversatiei pentru context
    const history = await pool.query(
      `SELECT role, content FROM messages
       WHERE conversation_id = $1
       ORDER BY created_at ASC`,
      [conversationId]
    );

    // clasifica emotia mesajului
    const emo = await classifyEmotion(message);

    // salveaza mood entry
    await pool.query(
      `INSERT INTO mood_entries
        (user_id, happy, sad, anxious, angry, neutral, dominant_emotion, text_excerpt, suicidal_mention)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [
        req.user.id,
        emo.happy, emo.sad, emo.anxious, emo.angry, emo.neutral,
        emo.dominant_emotion,
        message.slice(0, 200),
        emo.suicidal_mention === true
      ]
    );

    // verifica daca pragul de criza s-a atins
    let justLocked = false;
    if (emo.suicidal_mention === true) {
      try {
        const count = await countRecentSuicidalMentions(req.user.id);
        if (count >= 4) {
          const deviceId = req.headers["x-device-id"];
          await lockUserCrisis(req.user.id, deviceId);
          justLocked = true;
        }
      } catch (lockErr) {
        console.error("crisis lock failed:", lockErr.message);
      }
    }

    // daca tocmai a fost blocat userul, raspunde cu 423 ca app-ul sa intre pe CrisisLockedScreen
    if (justLocked) {
      return res.status(423).json({
        error: "Conversațiile sunt suspendate. Te rugăm să cauți ajutor specializat.",
        crisis: true
      });
    }

    // salveaza mesajul userului
    await pool.query(
      `INSERT INTO messages (conversation_id, role, content)
       VALUES ($1, 'user', $2)`,
      [conversationId, message]
    );

    // construieste contextul pentru DeepSeek cu tot istoricul
    const deepseekMessages = [
      {
        role: "system",
        content: "Ești un companion empatic pentru sănătate mintală. Nu ești terapeut licențiat. Răspunde ÎNTOTDEAUNA în limba română, indiferent de limba în care scrie utilizatorul. Oferă răspunsuri suportive și calde (maxim 80 de cuvinte). Dacă utilizatorul menționează automutilare sau gânduri de suicid, încurajează-l să contacteze persoane de încredere sau specialisti."
      },
      ...history.rows.map(row => ({
        role: row.role,
        content: row.content
      })),
      { role: "user", content: message }
    ];

    const replyResp = await axios.post(
      "https://api.deepseek.com/chat/completions",
      {
        model: "deepseek-chat",
        messages: deepseekMessages,
        temperature: 0.7
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json"
        }
      }
    );

    const reply = replyResp.data?.choices?.[0]?.message?.content ?? "(fara raspuns)";

    // salveaza raspunsul AI
    await pool.query(
      `INSERT INTO messages (conversation_id, role, content)
       VALUES ($1, 'assistant', $2)`,
      [conversationId, reply]
    );

    // daca e primul mesaj, seteaza titlul conversatiei
    const isFirstMessage = history.rows.length === 0;
    if (isFirstMessage) {
      const title = message.slice(0, 60) + (message.length > 60 ? "…" : "");
      await pool.query(
        `UPDATE conversations SET title = $1 WHERE id = $2`,
        [title, conversationId]
      );
    }

    // actualizeaza timestamp-ul conversatiei
    await pool.query(
      `UPDATE conversations SET updated_at = NOW() WHERE id = $1`,
      [conversationId]
    );

    // calculeaza starea de safety si o intoarce raspunsul ca app-ul sa reactioneze
    const safety = await computeSafetyStatus(req.user.id);
    res.json({ reply, emotion: emo, safety });
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data;
    console.error("ERR /send-message:", data || err.message);
    res.status(status).json({
      error: data?.error?.message || err.message || "Server error"
    });
  }
});

// mod privat - nu salveaza conversatia in DB, istoricul vine din request.
// singura exceptie e safety-ul: daca prinde un semnal suicidal, contorizeaza
// DOAR flag-ul (fara textul mesajului) ca pragul de criza sa functioneze si aici
app.post("/chat/private", requireAuth, rejectIfCrisis, requireTrustedContact, async (req, res) => {
  try {
    const { message, history } = req.body || {};
    if (!message || typeof message !== "string") {
      return res.status(400).json({ error: "Mesajul este obligatoriu" });
    }

    // history e trimis din app la fiecare mesaj - nu salveaza nimic
    const previousMessages = Array.isArray(history) ? history : [];

    // safety net si in privat: clasifica mesajul doar ca sa prinda semnalul suicidal.
    const emo = await classifyEmotion(message);

    let justLocked = false;
    if (emo.suicidal_mention === true) {
      try {
        // rand minimal in mood_entries: doar flag-ul de siguranta, fara excerpt.
        // emotiile raman neutre ca sa nu polueze media de "concerning"
        await pool.query(
          `INSERT INTO mood_entries
            (user_id, happy, sad, anxious, angry, neutral, dominant_emotion, text_excerpt, suicidal_mention)
           VALUES ($1, 0, 0, 0, 0, 100, 'neutral', NULL, TRUE)`,
          [req.user.id]
        );

        // acelasi prag ca in modul normal: 4+ mentiuni in ultimele 7 zile
        const count = await countRecentSuicidalMentions(req.user.id);
        if (count >= 4) {
          const deviceId = req.headers["x-device-id"];
          await lockUserCrisis(req.user.id, deviceId);
          justLocked = true;
        }
      } catch (lockErr) {
        console.error("crisis lock failed (private):", lockErr.message);
      }
    }

    if (justLocked) {
      return res.status(423).json({
        error: "Conversațiile sunt suspendate. Te rugăm să cauți ajutor specializat.",
        crisis: true
      });
    }

    const deepseekMessages = [
      {
        role: "system",
        content: "Ești un companion empatic pentru sănătate mintală. Nu ești terapeut licențiat. Răspunde ÎNTOTDEAUNA în limba română, indiferent de limba în care scrie utilizatorul. Oferă răspunsuri suportive și calde (maxim 80 de cuvinte). Dacă utilizatorul menționează automutilare sau gânduri de suicid, încurajează-l să contacteze persoane de încredere sau specialisti."
      },
      ...previousMessages,
      { role: "user", content: message }
    ];

    const replyResp = await axios.post(
      "https://api.deepseek.com/chat/completions",
      {
        model: "deepseek-chat",
        messages: deepseekMessages,
        temperature: 0.7
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json"
        }
      }
    );

    const reply = replyResp.data?.choices?.[0]?.message?.content ?? "(fara raspuns)";
    res.json({ reply });
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data;
    console.error("ERR /chat/private:", data || err.message);
    res.status(status).json({
      error: data?.error?.message || err.message || "Server error"
    });
  }
});

// sterge conversatie (mesajele se sterg automat prin CASCADE in DB)
app.delete("/conversations/:id", requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `DELETE FROM conversations WHERE id = $1 AND user_id = $2 RETURNING id`,
      [req.params.id, req.user.id]
    );
    if (r.rowCount === 0) {
      return res.status(404).json({ error: "Conversatia nu a fost gasita" });
    }
    res.json({ message: "Conversatie stearsa cu succes" });
  } catch (e) {
    console.error("ERR /conversations/:id DELETE:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// ─── SAFETY ENDPOINTS ─────────────────────────────────────────────────────────

// status curent de safety - apelat de app la startup si dupa fiecare mesaj
app.get("/safety/status", requireAuth, async (req, res) => {
  try {
    // citeste mai intai detaliile contului - daca asta merge, raspunde chiar daca
    // computeSafetyStatus crapa (ex. coloanele crisis_* lipsesc din migrare)
    const u = await pool.query(
      `SELECT crisis_locked_at, trusted_contact_email FROM users WHERE id = $1`,
      [req.user.id]
    );
    const email = u.rows[0]?.trusted_contact_email || null;

    let safety = { state: "ok", forced_hourly: false };
    try {
      safety = await computeSafetyStatus(req.user.id);
    } catch (innerErr) {
      console.error("computeSafetyStatus failed (continuing):", innerErr.message);
    }

    res.json({
      ...safety,
      locked_at: u.rows[0]?.crisis_locked_at || null,
      has_trusted_contact: !!email,
      trusted_contact_email: email
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// la app startup - verifica daca device-ul e blocat (chiar daca user nou pe acelasi device)
app.post("/safety/device-check", async (req, res) => {
  try {
    const { device_id } = req.body || {};
    if (!device_id) return res.json({ device_locked: false });

    const r = await pool.query(
      `SELECT locked_at, unlocked_at FROM device_crisis_locks WHERE device_id = $1`,
      [device_id]
    );
    if (r.rowCount === 0) return res.json({ device_locked: false });

    const row = r.rows[0];
    const locked = !row.unlocked_at; // daca nu are unlocked_at, e blocat
    res.json({ device_locked: locked, locked_at: row.locked_at });
  } catch (e) {
    // daca tabela nu exista sau nu avem drepturi, raspunde ca nu e blocat -
    // crisis-ul pe user este protectia primara, asta e doar un strat suplimentar
    console.error("device-check failed (defaulting to unlocked):", e.message);
    res.json({ device_locked: false });
  }
});

// seteaza email-ul contactului de incredere
app.post("/safety/set-trusted-contact", requireAuth, async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email || !email.includes("@")) {
      return res.status(400).json({ error: "Email invalid" });
    }
    await pool.query(
      `UPDATE users SET trusted_contact_email = $1 WHERE id = $2`,
      [email.toLowerCase().trim(), req.user.id]
    );
    res.json({ message: "Contact de incredere salvat" });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// userul cere link de deblocare pe email-ul contactului de incredere
app.post("/safety/crisis-unlock/request", requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `SELECT username, trusted_contact_email, crisis_locked, crisis_locked_at
       FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount === 0) return res.status(404).json({ error: "Utilizator inexistent" });

    const u = r.rows[0];
    if (!u.crisis_locked) return res.status(400).json({ error: "Nu esti in mod criza" });
    if (!u.trusted_contact_email) {
      return res.status(400).json({ error: "Nu ai un contact de incredere setat" });
    }

    // cooldown 24h obligatoriu
    const elapsed = Date.now() - new Date(u.crisis_locked_at).getTime();
    if (elapsed < 24 * 60 * 60 * 1000) {
      const hoursLeft = Math.ceil((24 * 60 * 60 * 1000 - elapsed) / (60 * 60 * 1000));
      return res.status(425).json({
        error: `Inca ${hoursLeft} ore de asteptare obligatorie inainte de a putea cere deblocare`
      });
    }

    const token = crypto.randomBytes(32).toString("hex");
    const expires = new Date(Date.now() + 24 * 60 * 60 * 1000); // 24h
    await pool.query(
      `UPDATE users SET crisis_unlock_token = $1, crisis_unlock_token_expires = $2 WHERE id = $3`,
      [token, expires, req.user.id]
    );

    await sendCrisisUnlockEmail(u.trusted_contact_email, u.username, token);
    res.json({ message: "Email trimis catre contactul de incredere" });
  } catch (e) {
    console.error("ERR /safety/crisis-unlock/request:", e.message);
    res.status(500).json({ error: e.message });
  }
});

// pagina pe care o vede contactul de incredere cand apasa link-ul din email
app.get("/safety/crisis-unlock/confirm", async (req, res) => {
  try {
    const token = req.query.token;
    if (!token) return res.status(400).send("Token lipsa");

    const r = await pool.query(
      `SELECT id, username FROM users
       WHERE crisis_unlock_token = $1
         AND crisis_unlock_token_expires > NOW()`,
      [token]
    );
    if (r.rowCount === 0) return res.status(400).send(`
      <html><body style="font-family:Arial;padding:24px;">
        <h2>Link invalid sau expirat</h2>
      </body></html>
    `);

    // deblocheaza userul si device-ul
    await pool.query(
      `UPDATE users
       SET crisis_locked = FALSE,
           crisis_unlocked_at = NOW(),
           crisis_unlock_token = NULL,
           crisis_unlock_token_expires = NULL,
           suicidal_count = 0
       WHERE id = $1`,
      [r.rows[0].id]
    );
    await pool.query(
      `UPDATE device_crisis_locks SET unlocked_at = NOW()
       WHERE device_id IN (
         SELECT device_id FROM device_crisis_locks
         WHERE unlocked_at IS NULL
         ORDER BY locked_at DESC LIMIT 1
       )`
    );

    // sterge si mood_entries cu suicidal_mention pentru ca contorul sa nu apara iar
    await pool.query(
      `UPDATE mood_entries SET suicidal_mention = FALSE
       WHERE user_id = $1 AND suicidal_mention = TRUE`,
      [r.rows[0].id]
    );

    res.send(`
      <html><body style="font-family:Arial;padding:24px;text-align:center;">
        <h2>Confirmare primită</h2>
        <p>Aplicația lui <b>${r.rows[0].username}</b> a fost deblocată. Mulțumim pentru sprijin.</p>
      </body></html>
    `);
  } catch (e) {
    res.status(500).send(e.message);
  }
});

// PHQ-2: 2 intrebari standard, scor 0..3 fiecare. Total < 3 = OK, deblocare
app.post("/safety/crisis-unlock/phq2", requireAuth, async (req, res) => {
  try {
    const { q1, q2 } = req.body || {};
    if (![0,1,2,3].includes(q1) || ![0,1,2,3].includes(q2)) {
      return res.status(400).json({ error: "Raspunsuri invalide (0-3)" });
    }

    const r = await pool.query(
      `SELECT crisis_locked, crisis_locked_at FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (r.rowCount === 0 || !r.rows[0].crisis_locked) {
      return res.status(400).json({ error: "Nu esti in mod criza" });
    }

    // cooldown 24h
    const elapsed = Date.now() - new Date(r.rows[0].crisis_locked_at).getTime();
    if (elapsed < 24 * 60 * 60 * 1000) {
      const hoursLeft = Math.ceil((24 * 60 * 60 * 1000 - elapsed) / (60 * 60 * 1000));
      return res.status(425).json({
        error: `Inca ${hoursLeft} ore inainte de a putea face testul`,
        hours_left: hoursLeft
      });
    }

    const score = q1 + q2;
    if (score >= 3) {
      // scor mare - nu deblocheaza, redirectioneaza la contact de incredere
      return res.status(403).json({
        error: "Scorul indica ca ai inca nevoie de sprijin. Foloseste contactul de incredere.",
        score
      });
    }

    // deblocam
    await pool.query(
      `UPDATE users
       SET crisis_locked = FALSE, crisis_unlocked_at = NOW(), suicidal_count = 0
       WHERE id = $1`,
      [req.user.id]
    );
    await pool.query(
      `UPDATE mood_entries SET suicidal_mention = FALSE
       WHERE user_id = $1 AND suicidal_mention = TRUE`,
      [req.user.id]
    );
    const deviceId = req.headers["x-device-id"];
    if (deviceId) {
      await pool.query(
        `UPDATE device_crisis_locks SET unlocked_at = NOW() WHERE device_id = $1`,
        [deviceId]
      );
    }

    res.json({ message: "Deblocat. Ai grija de tine.", score });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ─── MOOD ─────────────────────────────────────────────────────────────────────

app.get("/mood/entries", requireAuth, async (req, res) => {
  try {
    const userId = req.user.id;
    const { from, to } = req.query;

    if (!from || !to) {
      return res.status(400).send({ error: "from/to required" });
    }

    const q = `
      SELECT created_at, happy, sad, anxious, angry, neutral
      FROM mood_entries
      WHERE user_id = $1
        AND created_at >= $2
        AND created_at <  $3
      ORDER BY created_at ASC
    `;

    const { rows } = await pool.query(q, [userId, from, to]);

    const safe = rows.map(r => ({
      created_at: new Date(r.created_at).toISOString(),
      happy: r.happy,
      sad: r.sad,
      anxious: r.anxious,
      angry: r.angry,
      neutral: r.neutral
    }));

    return res.json(safe);
  } catch (e) {
    console.error(e);
    return res.status(500).send({ error: "server_error" });
  }
});

// ─── START ────────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 80;
app.listen(PORT, "0.0.0.0", () => {
  console.log(`Backend running on http://0.0.0.0:${PORT}`);
});