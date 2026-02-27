import express from "express";
import cors from "cors";
import axios from "axios";
import dotenv from "dotenv";
import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";
import { pool } from "./db.js";
import { requireAuth } from "./auth.js";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log("REQ:", req.method, req.url);
  next();
});

function requireAppSecret(req, res, next) {
  // Allowlist: rute care trebuie să meargă fără secret (pt test / monitorizare)
  if (req.path === "/health" || req.path === "/db-health") {
    return next();
  }

  const secret = req.headers["x-app-secret"];
  if (!process.env.APP_SECRET) return next(); 
  if (secret !== process.env.APP_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  next();
}

app.use(requireAppSecret);

app.get("/health", (req, res) => {
  res.json({ ok: true });
});

app.get("/db-health", async (req, res) => {
  try {
    const r = await pool.query("SELECT NOW() as now");
    res.json({ ok: true, now: r.rows[0].now });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ------------------------
// AUTH
// ------------------------
app.post("/auth/register", async (req, res) => {
  try {
    const { username, password } = req.body || {};
    if (!username || !password) {
      return res.status(400).json({ error: "username and password required" });
    }

    const hash = await bcrypt.hash(password, 10);

    const q = `
      INSERT INTO users (username, password_hash)
      VALUES ($1, $2)
      RETURNING id, username, created_at
    `;
    const r = await pool.query(q, [username, hash]);
    res.json({ user: r.rows[0] });
  } catch (e) {
    // username duplicate -> Postgres unique violation 23505
    if (e.code === "23505") {
      return res.status(409).json({ error: "username already exists" });
    }
    res.status(500).json({ error: e.message });
  }
});

app.post("/auth/login", async (req, res) => {
  try {
    const { username, password } = req.body || {};
    if (!username || !password) {
      return res.status(400).json({ error: "username and password required" });
    }

    const r = await pool.query(
      "SELECT id, username, password_hash FROM users WHERE username=$1",
      [username]
    );
    if (r.rowCount === 0) return res.status(401).json({ error: "invalid credentials" });

    const user = r.rows[0];
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return res.status(401).json({ error: "invalid credentials" });

    const token = jwt.sign(
      { username: user.username },
      process.env.JWT_SECRET,
      { subject: user.id, expiresIn: process.env.JWT_EXPIRES || "7d" }
    );

    res.json({ token });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ------------------------
// SENTIMENT (LLM -> JSON strict)
// ------------------------
async function classifyEmotion(userText) {
  const prompt = `
Return ONLY valid JSON (no markdown, no explanation).
Emotions: happy, sad, anxious, angry, neutral.
Rules:
- All values are integers 0..100
- Sum must be exactly 100
- dominant_emotion must be one of: happy|sad|anxious|angry|neutral
Text: ${JSON.stringify(userText)}
`;

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

  // încearcă parse JSON; dacă modelul mai “vorbește”, scoatem primul {...}
  let jsonText = raw;
  const m = raw.match(/\{[\s\S]*\}/);
  if (m) jsonText = m[0];

  const parsed = JSON.parse(jsonText);

  //minimal validation
  const keys = ["happy", "sad", "anxious", "angry", "neutral"];
  for (const k of keys) {
    const v = parsed[k];
    if (!Number.isInteger(v) || v < 0 || v > 100) throw new Error("Invalid emotion scores");
  }
  const sum = keys.reduce((acc, k) => acc + parsed[k], 0);
  if (sum !== 100) throw new Error("Emotion sum must be 100");
  if (!keys.includes(parsed.dominant_emotion)) throw new Error("Invalid dominant_emotion");

  return parsed;
}

// ------------------------
// CHAT (protected) + save mood_entries
// ------------------------
app.post("/chat", requireAuth, async (req, res) => {
  try {
    const userMessage = req.body?.message;
    if (!userMessage || typeof userMessage !== "string") {
      return res.status(400).json({ error: "Missing 'message' in body" });
    }

    //sentiment
    const emo = await classifyEmotion(userMessage);

    //save mood entry
    await pool.query(
      `INSERT INTO mood_entries
        (user_id, happy, sad, anxious, angry, neutral, dominant_emotion, text_excerpt)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [
        req.user.id,
        emo.happy, emo.sad, emo.anxious, emo.angry, emo.neutral,
        emo.dominant_emotion,
        userMessage.slice(0, 200)
      ]
    );

    //response from AI companion (support)
    const replyResp = await axios.post(
      "https://api.deepseek.com/chat/completions",
      {
        model: "deepseek-chat",
        messages: [
          {
            role: "system",
            content:
              "You are an empathetic mental health companion. You are not a licensed therapist. Provide supportive replies (max 70 words). If self-harm is mentioned, encourage contacting trusted people or professional help."
          },
          { role: "user", content: userMessage }
        ],
        temperature: 0.7
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.DEEPSEEK_API_KEY}`,
          "Content-Type": "application/json"
        }
      }
    );

    const reply = replyResp.data?.choices?.[0]?.message?.content ?? "(no reply)";
    res.json({ reply, emotion: emo });
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data;
    console.error("ERR:", data || err.message);

    res.status(status).json({
      error: data?.error?.message || err.message || "Server error"
    });
  }
});

const PORT = process.env.PORT || 3000;

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Backend running on http://0.0.0.0:${PORT}`);
});
