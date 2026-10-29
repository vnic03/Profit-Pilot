// An empty base uses the same origin in production and the Vite proxy locally.
const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export const API_BASE_URL = configuredBaseUrl.trim().replace(/\/+$/, "");
