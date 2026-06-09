import express, { Request, Response } from "express";
import cors from "cors";
import dotenv from "dotenv";
import { Pool } from "pg";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

// Database connection pool
const pool = new Pool({
  user: process.env.DB_USER || "postgres",
  password: process.env.DB_PASSWORD || "postgres",
  host: process.env.DB_HOST || "localhost",
  port: parseInt(process.env.DB_PORT || "5432"),
  database: process.env.DB_NAME || "city_explorer",
});

app.use(cors());
app.use(express.json());

const JWT_SECRET = process.env.JWT_SECRET;

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
      message: "User registered successfully",
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
        message: "Invalid username or password",
      });
      return;
    }

    const user = result.rows[0];
    const passwordMatch = await bcrypt.compare(password, user.password);

    if (!passwordMatch) {
      res.status(401).json({
        status: "error",
        message: "Invalid username or password",
      });
      return;
    }

    const token = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET, { expiresIn: "24h" });

    res.status(200).json({
      status: "success",
      message: "Login successful",
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

app.get("/health", (req: Request, res: Response) => {
  res.status(200).json({ status: "ok" });
});

initializeDatabase().then(() => {
  app.listen(port, () => {
    console.log(`Server running at http://localhost:${port}`);
  });
});
