# Frontend Duplication Guide

You have one frontend: `aviation-careers-hub-main` on your Desktop.

The plan:
- **`aviation-careers-hub-main`** → becomes the **prototype** (stays with mock data, left untouched)
- **`aviation-careers-hub-live`** → the **live version** (copy of the original, connected to the real backend)

---

## Step 1 — Duplicate the Frontend Folder

Open PowerShell (Win+X → Terminal) and run:

```powershell
# Navigate to the Desktop
cd "$env:USERPROFILE\Desktop"

# Copy the folder (excludes node_modules and dist to keep it fast)
robocopy "aviation-careers-hub-main" "aviation-careers-hub-live" /E /XD "node_modules" "dist" ".netlify" /XF "*.lock"

# Verify the copy was created
ls aviation-careers-hub-live
```

> `robocopy /E` copies everything including subdirectories. `/XD` excludes the listed directories.
> If robocopy is not available, use: `xcopy "aviation-careers-hub-main" "aviation-careers-hub-live\" /E /I /H /Y /EXCLUDE:exclude.txt`

---

## Step 2 — Rename the live project

Open `aviation-careers-hub-live/package.json` and change the name field:

```json
{
  "name": "caa-recruitment-live"
}
```

---

## Step 3 — Install dependencies for the live version

```powershell
cd "$env:USERPROFILE\Desktop\aviation-careers-hub-live"
npm install
```

---

## Step 4 — Add environment file to live version

Create `aviation-careers-hub-live/.env` (or `.env.local`):

```env
VITE_API_URL=http://localhost:5000/api
```

---

## Step 5 — Add the API client to the live version

Copy the `client.ts` file from `docs/07-frontend-integration.md` into:
```
aviation-careers-hub-live/src/lib/api/client.ts
```

(Create the `api/` folder under `src/lib/` if it doesn't exist.)

---

## Step 6 — Run both versions simultaneously

You can run both frontend versions at the same time on different ports.

**Terminal 1 — Backend:**
```powershell
cd "$env:USERPROFILE\Desktop\caa-recruitment-backend"
npm run dev
# Runs on http://localhost:5000
```

**Terminal 2 — Live Frontend:**
```powershell
cd "$env:USERPROFILE\Desktop\aviation-careers-hub-live"
npm run dev
# Runs on http://localhost:3000 (or 5173 with Vite)
```

**Terminal 3 — Prototype Frontend (optional):**
```powershell
cd "$env:USERPROFILE\Desktop\aviation-careers-hub-main"
npm run dev -- --port 3001
# Runs on http://localhost:3001 so it doesn't conflict
```

---

## Folder Summary

```
Desktop/
├── caa-recruitment-backend/      ← Your backend API (Node.js/Express)
├── aviation-careers-hub-live/    ← Live frontend (connected to backend)
└── aviation-careers-hub-main/    ← Prototype frontend (mock data, reference copy)
```

---

## What to Change in the Live Version vs. Prototype

| Feature | Prototype (aviation-careers-hub-main) | Live (aviation-careers-hub-live) |
|---------|--------------------------------------|----------------------------------|
| Login | Uses `ADMIN_DEMO`, `CANDIDATE_DEMO` shortcuts | Calls `POST /api/auth/login` |
| Jobs | Hardcoded in AppContext | Fetches from `GET /api/jobs` |
| Applications | Mock state in AppContext | Fetches from `GET /api/applications` |
| File uploads | Simulated | Real Cloudinary upload via `POST /api/cv/upload` |
| Auth tokens | None | JWT stored in memory + refresh cookie |
| Navigation after login | Instant | Waits for API response |

---

## Keeping the Prototype Safe

The prototype (`aviation-careers-hub-main`) should never be connected to the live backend. To make this permanent, add a comment at the top of its `src/context/AppContext.tsx`:

```typescript
// PROTOTYPE VERSION — uses mock data only.
// Do not add real API calls here. See aviation-careers-hub-live for the live version.
```

You can also lock it from accidental changes by making it read-only:
```powershell
# Make all files in the prototype read-only (PowerShell)
Get-ChildItem -Path "$env:USERPROFILE\Desktop\aviation-careers-hub-main" -Recurse -File |
  Where-Object { $_.DirectoryName -notmatch "node_modules" } |
  Set-ItemProperty -Name IsReadOnly -Value $true
```

To undo:
```powershell
Get-ChildItem -Path "$env:USERPROFILE\Desktop\aviation-careers-hub-main" -Recurse -File |
  Set-ItemProperty -Name IsReadOnly -Value $false
```

---

## Deployment (When Ready to Go Live)

### Backend — Deploy to a VPS or cloud server

**Option A: Ubuntu VPS (DigitalOcean/AWS/Azure/Hetzner)**

1. SSH into your server
2. Install Node.js 20:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
   sudo apt-get install -y nodejs
   ```
3. Clone your code and `npm install --production`
4. Set up `.env` with production values
5. Use **PM2** to keep it running:
   ```bash
   npm install -g pm2
   pm2 start index.js --name caa-api
   pm2 save
   pm2 startup
   ```
6. Set up **Nginx** as a reverse proxy on port 80/443
7. Add an SSL certificate with **Certbot** (free)

**Option B: Railway.app (easiest — no server management)**

1. Push your backend code to a GitHub repository
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Add all environment variables in Railway's dashboard
4. Railway gives you a public URL like `https://caa-api.railway.app`

### Frontend — Deploy to Netlify

The frontend already has `netlify.toml` configured.

1. Push `aviation-careers-hub-live` to a GitHub repository
2. Go to [netlify.com](https://www.netlify.com) → New site from Git
3. Select your repository
4. Set environment variable: `VITE_API_URL=https://your-backend-url/api`
5. Deploy

---

## Production Checklist

### Backend
- [ ] `NODE_ENV=production` in `.env`
- [ ] `FRONTEND_URL` set to your production frontend URL
- [ ] `JWT_SECRET` and `JWT_REFRESH_SECRET` are long random strings (not defaults)
- [ ] Database uses a dedicated MySQL user (not root)
- [ ] HTTPS only (no HTTP in production)
- [ ] `npm run start` (not `npm run dev` — no nodemon in production)
- [ ] PM2 or equivalent process manager
- [ ] Log rotation configured

### Frontend  
- [ ] `VITE_API_URL` points to production backend
- [ ] All demo/mock login shortcuts removed from `AppContext.tsx`
- [ ] Error boundaries added for API failures
- [ ] Loading states added for all API calls
