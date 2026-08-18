import axios from "axios";

/**
 * The single HTTP client for ScoreGrid.
 *
 * Everything goes through the API gateway — the frontend never holds a URL for
 * an individual service. That is the whole point of having a gateway.
 */
export const TOKEN_STORAGE_KEY = "scoregrid.token";
export const USER_STORAGE_KEY = "scoregrid.user";
export const AUTH_EXPIRED_EVENT = "scoregrid.auth-expired";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // An expired or missing token means one thing: log in again.
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }
    return Promise.reject(error);
  },
);

/** The error envelope every ScoreGrid service returns — docs/contracts.md */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export function toApiError(error: unknown): ApiError | null {
  if (axios.isAxiosError(error) && error.response?.data) {
    return error.response.data as ApiError;
  }
  return null;
}
