# City Explorer Challenge

City Explorer Challenge is a dynamic Android adventure application designed to encourage urban exploration. It generates personalized walking challenges, tracks your progress via real-time GPS, and synchronizes your achievements with a remote cloud server.

## Key Features

### Authentication & Sync
- **Secure Login/Register**: Seamlessly create an account or log in to access your data.
- **Cloud Synchronization**: All challenge history and current "In Progress" missions are automatically synced with a remote API.
- **Persistent Adventure**: Start a challenge on one device and resume it on another; your active mission state is preserved.

### Smart Exploration
- **Dynamic Challenge Generation**: Uses the **Overpass API** (OpenStreetMap) to find interesting locations near you (Parks, Historic Sites, Cafes, etc.).
- **Intelligent Recommendations**: A built-in algorithm analyzes your habits. If you haven't visited enough parks, it will prioritize them in your recommendations!
- **Interactive Maps**: Powered by **osmdroid**, featuring real-time location tracking and custom red-arrow directional markers.
- **Proximity Validation**: Challenges can only be completed when you are within **20 meters** of the target.

### Progress Tracking
- **Comprehensive History**: View a list of all your successful explorations and missions you've "Gave Up" on.
- **Detailed Statistics**: Track your total completed challenges and see which categories you explore the most.

---

## Building and Running

### Prerequisites
- **Android Studio Ladybug (2024.2.1)** or higher.
- **JDK 17** (Ensure your JAVA_HOME and Gradle settings are set to 17).
- **Physical Device or Emulator** with Google Play Services (for location) and internet access.

### Configuration (API Keys)
The project uses the **Secrets Gradle Plugin** to keep keys secure. You must add your keys to a `local.properties` file in the project root:

```properties
# root/local.properties
GEOAPIFY_API_KEY=your_geoapify_api_key_here
AUTH_API_URL=https://your-api-server.com
```

1.  **Geoapify**: Sign up at [geoapify.com](https://www.geoapify.com/) to get a routing key.
2.  **Auth API**: Point this to your backend server (see backend specification below).

### Build Steps
1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Wait for **Gradle Sync** to finish.
4.  Run the `app` module on your target device.

---

## Backend Server & API

The City Explorer backend is a **Node.js/Express** server that handles authentication, user data persistence, and challenge history synchronization.

### Server Location
The server code is located in the `./server` directory of the project.

### Prerequisites
- **Node.js 18+** and **npm**
- **Docker** and **Docker Compose** (recommended for easy setup)
- **PostgreSQL 15** (if running without Docker)

### Configuration (Environment Variables)

The server uses environment variables for configuration. In development with Docker Compose, these are automatically set. For manual setup, create a `.env` file in the `./server` directory:

```env
PORT=3000
DB_USER=postgres
DB_PASSWORD=postgres
DB_HOST=localhost
DB_PORT=5432
DB_NAME=city_explorer
JWT_SECRET=your-secure-jwt-secret-key
```

### Running with Docker Compose (Recommended)

Docker Compose will spin up both the PostgreSQL database and the Node.js server:

```bash
# From the project root
docker-compose up
```

The server will be accessible at `http://localhost:3000`.

**What Docker Compose does:**
- Launches a PostgreSQL 15 database (`postgres:15-alpine`)
- Initializes the `city_explorer` database with required tables
- Builds and runs the Express server in a container
- Sets up networking between services
- Mounts a persistent volume for database data

### Running Manually (Without Docker)

If you prefer to run without Docker:

1. **Start PostgreSQL** on your system (ensure it's running on port 5432 by default)

2. **Install dependencies** and start the server:
   ```bash
   cd server
   npm install
   npm run dev
   ```

The server will initialize the database schema automatically on first run.

### API Endpoints

#### **POST /register**
Create a new user account.

**Request Body:**
```json
{
  "username": "john_explorer",
  "password": "secure_password"
}
```

**Response (201):**
```json
{
  "status": "success",
  "username": "john_explorer"
}
```

#### **POST /login**
Authenticate a user and receive a JWT token.

**Request Body:**
```json
{
  "username": "john_explorer",
  "password": "secure_password"
}
```

**Response (200):**
```json
{
  "status": "success",
  "username": "john_explorer",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Note:** The returned token is valid for **7 days** and must be included in subsequent authenticated requests.

#### **GET /history**
Fetch challenge history and active mission for a user.

**Query Parameters:**
- `username` (required): The username to retrieve history for

**Response (200):**
```json
{
  "history": [
    {
      "name": "Central Park",
      "category": "Parks",
      "lat": 40.7829,
      "lon": -73.9654,
      "timestamp": 1718700000,
      "distance": "0.5 km",
      "status": "Completed"
    }
  ],
  "active": {
    "name": "Library Building",
    "category": "Historic Sites",
    "start_lat": 40.7530,
    "start_lon": -73.9823,
    "target_lat": 40.7533,
    "target_lon": -73.9830,
    "distance": "0.2 km",
    "isActive": true
  }
}
```

#### **POST /history**
Update user's challenge history and active mission (syncs data from mobile app).

**Request Body:**
```json
{
  "username": "john_explorer",
  "history": [
    {
      "name": "Central Park",
      "category": "Parks",
      "lat": 40.7829,
      "lon": -73.9654,
      "timestamp": 1718700000,
      "distance": "0.5 km",
      "status": "Completed"
    }
  ],
  "active": {
    "name": "Brooklyn Bridge",
    "category": "Landmarks",
    "start_lat": 40.7061,
    "start_lon": -73.9969,
    "target_lat": 40.7061,
    "target_lon": -73.9969,
    "distance": "1.2 km",
    "isActive": true
  }
}
```

**Response (200):**
```json
{
  "status": "success"
}
```

#### **GET /health**
Health check endpoint to verify the server is running.

**Response (200):**
```json
{
  "status": "ok"
}
```

### Database Schema

The server automatically creates three tables:

1. **users** - Stores user credentials (with bcrypt-hashed passwords)
2. **challenge_history** - Stores completed and abandoned challenges
3. **active_challenge** - Tracks the user's current in-progress mission

All tables are set up automatically on server startup.

### API Integration in Android App

The Android app communicates with this backend via the `AUTH_API_URL` configured in `local.properties`. The app:

1. **Registers/Logs in** via `/register` and `/login` endpoints
2. **Syncs data** by calling `/history` (GET to fetch, POST to update)
3. **Persists JWT tokens** locally for session management
4. **Automatically pulls full data** on login to restore user state

---

## Fragment Breakdown

### 1. Login Fragment (`LoginFragment`)
The entry point of the app. It handles user authentication.
- **Features**: Registration for new users (clears local state) and Login for existing users (triggers a full remote data pull).

### 2. Main Screen Fragment (`MainScreenFragment`)
The central dashboard.
- **Features**: Displays the current active challenge card, your progress stats for today, and quick-access buttons for the Map, History, and Statistics.

### 3. Proposals Fragment (`ProposalsFragment`)
The "Smart Recommendation" engine UI.
- **Features**: Shows 10 personalized challenge cards. Each card explains *why* it was selected (e.g., "Least visited category") and shows the distance from your current location.

### 4. Map Fragment (`MapFragment`)
The core gameplay screen.
- **Features**: Interactive OSM map with your target marker and a red walking route calculated via **Geoapify**. Includes a "Check Completion" button that validates your GPS proximity.

### 5. Challenge Details Fragment (`ChallengeDetailsFragment`)
Deep dive into the mission.
- **Features**: Shows technical details about the target location, including GPS coordinates and dynamic reasoning for why this specific location is worth visiting.

### 6. History Fragment (`HistoryFragment`)
Your personal archive.
- **Features**: A scrollable list of past adventures. It distinguishes between **Completed** missions (Green) and **Gave Up** missions (Red).

### 7. Statistics Fragment (`StatisticsFragment`)
Data-driven insights.
- **Features**: Aggregates all persistent data to show your lifetime achievements, focusing strictly on verified completed challenges.

---

## Tech Stack
- **Language**: Kotlin (Android) / TypeScript (Backend)
- **Architecture**: MVVM (ViewModel, LiveData) on Android
- **Networking**: HttpURLConnection & Coroutines (Android) / Express.js (Backend)
- **Navigation**: Jetpack Navigation Component
- **Maps**: osmdroid
- **Routing**: Geoapify Routing API
- **Data**: Overpass API (OSM Query Language)
- **Backend Database**: PostgreSQL 15
- **Authentication**: JWT (7-day expiry)
- **Password Security**: bcrypt
- **Containerization**: Docker & Docker Compose