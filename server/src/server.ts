import express, { Request, Response, NextFunction } from "express";
import cors from "cors";
import dotenv from "dotenv";
import { Pool } from "pg";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

const pool = new Pool({
  user: process.env.DB_USER || "postgres",
  password: process.env.DB_PASSWORD || "postgres",
  host: process.env.DB_HOST || "localhost",
  port: parseInt(process.env.DB_PORT ?? "5432", 10),
  database: process.env.DB_NAME || "city_explorer",
});

app.use(cors());
app.use(express.json());

const JWT_SECRET: string = process.env.JWT_SECRET || "your-secret-key";

const verifyToken = (req: Request, res: Response, next: NextFunction) => {
  const token = req.headers.authorization?.split(" ")[1];
  if (!token) {
    res.status(401).json({
      status: "error",
      message: "No token provided",
    });
    return;
  }
  try {
    const decoded = jwt.verify(token, JWT_SECRET) as any;
    (req as any).userId = decoded.userId;
    (req as any).username = decoded.username;
    next();
  } catch (error) {
    res.status(401).json({
      status: "error",
      message: "Invalid token",
    });
  }
};

async function initializeDatabase() {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(255) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    await pool.query(`
      CREATE TABLE IF NOT EXISTS challenge_history (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name VARCHAR(255) NOT NULL,
        category VARCHAR(255) NOT NULL,
        lat DECIMAL(10, 6) NOT NULL,
        lon DECIMAL(10, 6) NOT NULL,
        timestamp BIGINT NOT NULL,
        distance VARCHAR(50) NOT NULL,
        status VARCHAR(50) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    await pool.query(`
      CREATE TABLE IF NOT EXISTS active_challenge (
        id SERIAL PRIMARY KEY,
        user_id INTEGER UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name VARCHAR(255) NOT NULL,
        category VARCHAR(255) NOT NULL,
        start_lat DECIMAL(10, 6) NOT NULL,
        start_lon DECIMAL(10, 6) NOT NULL,
        target_lat DECIMAL(10, 6) NOT NULL,
        target_lon DECIMAL(10, 6) NOT NULL,
        distance VARCHAR(50) NOT NULL,
        is_active BOOLEAN DEFAULT true,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    console.log("Database tables initialized");
  } catch (error) {
    console.error("Error initializing database:", error);
  }
}

app.post("/register", async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      res.status(400).json({
        status: "error",
        message: "Username and password are required",
      });
      return;
    }

    const userExists = await pool.query("SELECT id FROM users WHERE username = $1", [username]);

    if (userExists.rows.length > 0) {
      res.status(400).json({
        status: "error",
        message: "Username already exists",
      });
      return;
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const result = await pool.query("INSERT INTO users (username, password) VALUES ($1, $2) RETURNING id, username", [username, hashedPassword]);

    const user = result.rows[0];

    res.status(201).json({
      status: "success",
      username: user.username,
    });
  } catch (error) {
    console.error("Register error:", error);
    res.status(500).json({
      status: "error",
      message: "Internal server error",
    });
  }
});

app.post("/login", async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      res.status(400).json({
        status: "error",
        message: "Username and password are required",
      });
      return;
    }

    const result = await pool.query("SELECT id, username, password FROM users WHERE username = $1", [username]);

    if (result.rows.length === 0) {
      res.status(401).json({
        status: "error",
        message: "Invalid credentials",
      });
      return;
    }

    const user = result.rows[0];
    const passwordMatch = await bcrypt.compare(password, user.password);

    if (!passwordMatch) {
      res.status(401).json({
        status: "error",
        message: "Invalid credentials",
      });
      return;
    }

    const token = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET, { expiresIn: "7d" });

    res.status(200).json({
      status: "success",
      username: user.username,
      token: token,
    });
  } catch (error) {
    console.error("Login error:", error);
    res.status(500).json({
      status: "error",
      message: "Internal server error",
    });
  }
});

app.get("/history", async (req: Request, res: Response) => {
  try {
    const { username } = req.query;

    if (!username || typeof username !== "string") {
      res.status(400).json({
        status: "error",
        message: "Username query parameter is required",
      });
      return;
    }

    const userResult = await pool.query("SELECT id FROM users WHERE username = $1", [username]);

    if (userResult.rows.length === 0) {
      res.status(404).json({
        status: "error",
        message: "User not found",
      });
      return;
    }

    const userId = userResult.rows[0].id;

    const historyResult = await pool.query(
      `SELECT name, category, lat, lon, timestamp, distance, status 
       FROM challenge_history 
       WHERE user_id = $1 
       ORDER BY timestamp DESC`,
      [userId],
    );

    const history = historyResult.rows.map((row) => ({
      name: row.name,
      category: row.category,
      lat: parseFloat(row.lat),
      lon: parseFloat(row.lon),
      timestamp: parseInt(row.timestamp),
      distance: row.distance,
      status: row.status,
    }));

    const activeResult = await pool.query(
      `SELECT name, category, start_lat, start_lon, target_lat, target_lon, distance, is_active 
       FROM active_challenge 
       WHERE user_id = $1 AND is_active = true`,
      [userId],
    );

    const active =
      activeResult.rows.length > 0
        ? {
            name: activeResult.rows[0].name,
            category: activeResult.rows[0].category,
            start_lat: parseFloat(activeResult.rows[0].start_lat),
            start_lon: parseFloat(activeResult.rows[0].start_lon),
            target_lat: parseFloat(activeResult.rows[0].target_lat),
            target_lon: parseFloat(activeResult.rows[0].target_lon),
            distance: activeResult.rows[0].distance,
            isActive: activeResult.rows[0].is_active,
          }
        : null;

    res.status(200).json({
      history: history,
      active: active,
    });
  } catch (error) {
    console.error("Get history error:", error);
    res.status(500).json({
      status: "error",
      message: "Internal server error",
    });
  }
});

app.post("/history", async (req: Request, res: Response) => {
  try {
    const { username, history, active } = req.body;

    if (!username) {
      res.status(400).json({
        status: "error",
        message: "Username is required",
      });
      return;
    }

    const userResult = await pool.query("SELECT id FROM users WHERE username = $1", [username]);

    if (userResult.rows.length === 0) {
      res.status(404).json({
        status: "error",
        message: "User not found",
      });
      return;
    }

    const userId = userResult.rows[0].id;

    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query("DELETE FROM challenge_history WHERE user_id = $1", [userId]);

      if (Array.isArray(history) && history.length > 0) {
        const historyInsertQuery = `
          INSERT INTO challenge_history (user_id, name, category, lat, lon, timestamp, distance, status)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        `;

        for (const entry of history) {
          await client.query(historyInsertQuery, [userId, entry.name, entry.category, entry.lat, entry.lon, entry.timestamp, entry.distance, entry.status]);
        }
      }

      if (active && active.name) {
        const existingActive = await client.query("SELECT id FROM active_challenge WHERE user_id = $1", [userId]);

        if (existingActive.rows.length > 0) {
          await client.query(
            `UPDATE active_challenge 
             SET name = $1, category = $2, start_lat = $3, start_lon = $4, 
                 target_lat = $5, target_lon = $6, distance = $7, is_active = $8,
                 updated_at = CURRENT_TIMESTAMP
             WHERE user_id = $9`,
            [active.name, active.category, active.start_lat, active.start_lon, active.target_lat, active.target_lon, active.distance, active.isActive !== false, userId],
          );
        } else {
          // Insert new
          await client.query(
            `INSERT INTO active_challenge (user_id, name, category, start_lat, start_lon, target_lat, target_lon, distance, is_active)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
            [userId, active.name, active.category, active.start_lat, active.start_lon, active.target_lat, active.target_lon, active.distance, active.isActive !== false],
          );
        }
      } else {
        // delete if exists
        await client.query("DELETE FROM active_challenge WHERE user_id = $1", [userId]);
      }

      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }

    res.status(200).json({
      status: "success",
    });
  } catch (error) {
    console.error("Post history error:", error);
    res.status(500).json({
      status: "error",
      message: "Internal server error",
    });
  }
});

// Health check
app.get("/health", (req: Request, res: Response) => {
  res.status(200).json({ status: "ok" });
});

// Initialize database and start server
initializeDatabase().then(() => {
  app.listen(port, () => {
    console.log(`Server running at http://localhost:${port}`);
  });
});

export default app;
