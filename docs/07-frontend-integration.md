# Frontend Integration Guide

The frontend (`aviation-careers-hub-live`) uses **TanStack Start** (React + SSR) with TypeScript. Currently it uses mock/demo data in `AppContext.tsx`. This guide replaces all mock data with real API calls to the backend.

---

## Step 1 — Add the API URL to the frontend `.env`

In the `aviation-careers-hub-live/` folder, create a `.env` file (or `.env.local`):

```env
VITE_API_URL=http://localhost:5000/api
```

For production deployment, change this to your deployed backend URL:
```env
VITE_API_URL=https://api.recruitment.caa.co.ug/api
```

> The `VITE_` prefix makes this variable accessible in the browser. Do NOT put secrets (JWT_SECRET, DB_PASSWORD, etc.) with this prefix.

---

## Step 2 — Create the API client

Create the file `src/lib/api/client.ts` in the frontend:

```typescript
// src/lib/api/client.ts
// Central API client — all backend calls go through here.

const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:5000/api";

// Access token is stored in memory (safer than localStorage for XSS).
// Falls back to localStorage so it survives page refresh.
let _token: string | null = localStorage.getItem("caa_token");

export function setToken(t: string | null) {
  _token = t;
  if (t) localStorage.setItem("caa_token", t);
  else localStorage.removeItem("caa_token");
}

export function getToken() { return _token; }

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  isMultipart = false
): Promise<T> {
  const headers: Record<string, string> = {};
  if (_token) headers["Authorization"] = `Bearer ${_token}`;
  if (!isMultipart) headers["Content-Type"] = "application/json";

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    credentials: "include", // send caa_refresh cookie automatically
    body: isMultipart
      ? (body as FormData)
      : body !== undefined
      ? JSON.stringify(body)
      : undefined,
  });

  // Auto-refresh on 401
  if (res.status === 401 && path !== "/auth/refresh-token" && path !== "/auth/login") {
    const refreshed = await tryRefresh();
    if (refreshed) {
      headers["Authorization"] = `Bearer ${_token}`;
      const retry = await fetch(`${BASE}${path}`, {
        method,
        headers,
        credentials: "include",
        body: isMultipart
          ? (body as FormData)
          : body !== undefined
          ? JSON.stringify(body)
          : undefined,
      });
      if (!retry.ok) throw await apiError(retry);
      return retry.json();
    }
    setToken(null);
    window.location.href = "/login";
    throw new Error("Session expired");
  }

  if (!res.ok) throw await apiError(res);
  return res.json();
}

async function tryRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/auth/refresh-token`, {
      method: "POST",
      credentials: "include",
    });
    if (!res.ok) return false;
    const json = await res.json();
    if (json.success && json.data?.token) {
      setToken(json.data.token);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

async function apiError(res: Response): Promise<Error> {
  try {
    const json = await res.json();
    return new Error(json.error ?? `HTTP ${res.status}`);
  } catch {
    return new Error(`HTTP ${res.status}`);
  }
}

const get  = <T>(path: string) => request<T>("GET", path);
const post = <T>(path: string, body?: unknown) => request<T>("POST", path, body);
const put  = <T>(path: string, body?: unknown) => request<T>("PUT", path, body);
const del  = <T>(path: string) => request<T>("DELETE", path);
const upload = <T>(path: string, form: FormData) => request<T>("POST", path, form, true);

// ── Auth ─────────────────────────────────────────────────────────────────────
export const auth = {
  register: (data: { email: string; password: string; firstName: string; lastName: string; accountType: string; employeeNumber?: string }) =>
    post<{ success: boolean; data: UserResponse }>("/auth/register", data),

  login: (email: string, password: string) =>
    post<{ success: boolean; data: UserResponse }>("/auth/login", { email, password }),

  me: () =>
    get<{ success: boolean; data: UserResponse }>("/auth/me"),

  updateProfile: (data: { firstName?: string; lastName?: string; email?: string }) =>
    put<{ success: boolean; data: UserResponse }>("/auth/profile", data),

  logout: () =>
    post<{ success: boolean; data: { message: string } }>("/auth/logout"),
};

// ── Jobs ─────────────────────────────────────────────────────────────────────
export const jobs = {
  list: () =>
    get<{ success: boolean; data: Job[]; total: number }>("/jobs"),

  get: (id: number) =>
    get<{ success: boolean; data: Job }>(`/jobs/${id}`),

  create: (data: Partial<Job>) =>
    post<{ success: boolean; data: Job }>("/jobs", data),

  update: (id: number, data: Partial<Job>) =>
    put<{ success: boolean; data: Job }>(`/jobs/${id}`, data),

  delete: (id: number) =>
    del<{ success: boolean; data: { message: string } }>(`/jobs/${id}`),
};

// ── Applications ──────────────────────────────────────────────────────────────
export const applications = {
  list: (params?: { jobId?: number; status?: string; fromDate?: string; toDate?: string; email?: string }) => {
    const qs = params ? "?" + new URLSearchParams(Object.entries(params).filter(([, v]) => v != null) as [string, string][]).toString() : "";
    return get<{ success: boolean; data: Application[]; total: number }>(`/applications${qs}`);
  },

  submit: (data: { jobId: number; completion?: number; cgpa?: number; university?: string; screeningAnswers?: Record<string, string> }) =>
    post<{ success: boolean; data: Application }>("/applications", data),

  updateStatus: (id: number, status: string, notifyEmail?: string, notifyMessage?: string) =>
    put<{ success: boolean; data: Application }>(`/applications/${id}/status`, { status, notifyEmail, notifyMessage }),

  bulkStatus: (updates: { id: number; status: string }[]) =>
    put<{ success: boolean; data: { updated: number } }>("/applications/bulk-status", { updates }),

  withdraw: (id: number) =>
    del<{ success: boolean; data: { message: string } }>(`/applications/${id}`),

  exportUrl: (params?: { jobId?: number; status?: string }) => {
    const qs = params ? "?" + new URLSearchParams(Object.entries(params).filter(([, v]) => v != null) as [string, string][]).toString() : "";
    return `${BASE}/applications/export${qs}`;
  },
};

// ── CV ────────────────────────────────────────────────────────────────────────
export const cv = {
  get: () =>
    get<{ success: boolean; data: CvProfile }>("/cv"),

  save: (data: Partial<CvProfile>) =>
    put<{ success: boolean; data: CvProfile }>("/cv", data),

  upload: (file: File, type: "photo" | "document") => {
    const form = new FormData();
    form.append("file", file);
    form.append("type", type);
    return upload<{ success: boolean; data: { url: string; publicId: string; format: string; bytes: number } }>("/cv/upload", form);
  },

  getByEmail: (email: string) =>
    get<{ success: boolean; data: CvProfile | null }>(`/cv/by-email/${encodeURIComponent(email)}`),
};

// ── Settings ──────────────────────────────────────────────────────────────────
export const settings = {
  get: () =>
    get<{ success: boolean; data: PortalSettings }>("/settings"),

  update: (data: Partial<PortalSettings>) =>
    put<{ success: boolean; data: PortalSettings }>("/settings", data),
};

// ── Notifications ─────────────────────────────────────────────────────────────
export const notifications = {
  list: () =>
    get<{ success: boolean; data: Notification[]; total: number }>("/notifications"),

  markRead: (id: number) =>
    put<{ success: boolean; data: { id: number; isRead: boolean } }>(`/notifications/${id}/read`),
};

// ── Analytics ─────────────────────────────────────────────────────────────────
export const analyticsApi = {
  track: (type: string, jobId?: number, jobTitle?: string, query?: string) =>
    post<{ success: boolean }>("/analytics/event", { type, jobId, jobTitle, query }),

  summary: (days = 30) =>
    get<{ success: boolean; data: AnalyticsSummary }>(`/analytics?days=${days}`),
};

// ── Types ─────────────────────────────────────────────────────────────────────
export interface UserResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  accountType: "external" | "internal" | "admin";
  effectiveType: string;
  adminRole: "super" | "hr" | "recruiter" | null;
  employeeNumber: string | null;
  token: string;
}

export interface Job {
  id: number;
  abbr: string;
  title: string;
  dept: string;
  deptKey: string;
  location: string;
  salary: string;
  salaryBand: string;
  type: string;
  closes: string;
  closesAt: string;
  visibility: string;
  minAge: number;
  requiredExperience: number;
  requiredQualification: string;
  description: string;
  featured: boolean;
}

export interface Application {
  id: number;
  jobId: number;
  abbr: string;
  title: string;
  dept: string;
  date: string;
  status: string;
  completion: number;
  candidateName: string;
  candidateEmail: string;
  cgpa: number | null;
  university: string | null;
}

export interface CvProfile {
  personal: Record<string, string>;
  highestLevel: string | null;
  qualifications: unknown[];
  skills: string[];
  experience: unknown[];
  referees: unknown[];
  nextOfKin: Record<string, string>;
  photoFile: string | null;
}

export interface PortalSettings {
  minAgeThreshold: number;
  allowExternalInternalJobs: boolean;
  orgName: string;
  sessionTimeoutMinutes: number;
  emailSenderName: string;
  closingSoonDays: number;
  maxApplicationsPerCandidate: number;
  notifTemplates: { shortlist: string; decline: string; interview: string; offer: string };
}

export interface Notification {
  id: number;
  recipientEmail: string;
  title: string;
  message: string;
  read: boolean;
  type: string;
  at: string;
}

export interface AnalyticsSummary {
  events: unknown[];
  summary: Record<string, number>;
  topJobs: unknown[];
  topSearches: unknown[];
  dailyCounts: { date: string; count: number }[];
}
```

---

## Step 3 — Update AppContext to use real API

Open `src/context/AppContext.tsx` and replace the mock `signIn` function with a real API call.

Find the `signIn` function and replace it with this pattern:

```typescript
// At the top of AppContext.tsx, add:
import { auth, setToken, applications, jobs as jobsApi } from "@/lib/api/client";

// Replace the signIn function:
const signIn = async (email: string, password: string) => {
  const result = await auth.login(email, password);
  if (result.success) {
    const { token, ...userInfo } = result.data;
    setToken(token);
    setCurrentUser(userInfo); // update your user state
    return result.data;
  }
  throw new Error("Login failed");
};

// Replace the signOut function:
const signOut = async () => {
  await auth.logout();
  setToken(null);
  setCurrentUser(null);
};
```

---

## Step 4 — Replace mock job data

In any component that currently shows hardcoded jobs, use the API instead:

```typescript
import { jobs } from "@/lib/api/client";
import { useEffect, useState } from "react";

function VacanciesPage() {
  const [jobList, setJobList] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    jobs.list()
      .then(res => setJobList(res.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  // ... render jobList
}
```

---

## Step 5 — Handle CORS

The backend already has CORS configured. Make sure `FRONTEND_URL` in the backend `.env` exactly matches your frontend development URL:

```env
# In caa-recruitment-backend/.env
FRONTEND_URL=http://localhost:3000
```

If your frontend runs on a different port (e.g., 5173 for Vite dev), update it:
```env
FRONTEND_URL=http://localhost:5173
```

Restart the backend after changing `.env`.

---

## Step 6 — Handle the refresh token cookie

The `caa_refresh` cookie is `httpOnly` and `SameSite=Strict`. For it to be sent cross-origin in development:

1. Backend CORS config already includes `credentials: true` — ✅ done
2. Frontend fetch calls must include `credentials: 'include'` — ✅ already in the `client.ts` above
3. Both must be on the same hostname (localhost) — ✅ in development

In **production**, the frontend and backend must either:
- Share the same domain (e.g., `caa.co.ug` and `api.caa.co.ug`)  
- Or use `SameSite=None; Secure` cookies (requires HTTPS on both)

---

## Step 7 — Test the integration

1. Start the backend: `npm run dev` (in `caa-recruitment-backend/`)
2. Start the frontend: `npm run dev` (in `aviation-careers-hub-live/`)
3. Open the frontend in the browser
4. Open DevTools → Network tab
5. Try to log in — you should see requests going to `localhost:5000/api/auth/login`
6. After login, navigate to vacancies — requests should go to `localhost:5000/api/jobs`

---

## Checklist — Integration Complete When:

- [ ] Login calls `POST /api/auth/login` and stores the token
- [ ] The `caa_refresh` cookie appears in DevTools → Application → Cookies
- [ ] Job list loads from `GET /api/jobs`
- [ ] Applying for a job calls `POST /api/applications`
- [ ] CV save calls `PUT /api/cv`
- [ ] File upload calls `POST /api/cv/upload` and returns a Cloudinary URL
- [ ] Notifications load from `GET /api/notifications`
- [ ] Admin dashboard loads from `GET /api/applications` with admin token
- [ ] Logout calls `POST /api/auth/logout` and clears the cookie
- [ ] After token expires (2h), the client auto-refreshes via `POST /api/auth/refresh-token`
