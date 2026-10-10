// NEXT_PUBLIC_API_URL must be the browser-reachable API origin in deployments.
// Keep local development convenient while normalizing accidental trailing slashes.
const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL?.trim();

export const API_BASE_URL = (configuredApiUrl || 'http://localhost:8081').replace(/\/+$/, '');
