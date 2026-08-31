  import jwt from "jsonwebtoken";
  import dotenv from "dotenv";
  dotenv.config();

  export function requireAuth(req, res, next) {
    const h = req.headers.authorization || "";
    const token = h.startsWith("Bearer ") ? h.slice(7) : null;

    if (!token) return res.status(401).json({ error: "Missing token" });

    try {
      const payload = jwt.verify(token, process.env.JWT_SECRET);
      req.user = { id: payload.sub, username: payload.username };
      next();
    } catch {
      return res.status(401).json({ error: "Invalid token" });
    }
  }
