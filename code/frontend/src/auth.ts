import { createAuthClient } from '@neon/auth';

const authUrl = import.meta.env.VITE_NEON_AUTH_URL || 'https://ep-rapid-firefly-aousvely.neonauth.c-2.ap-southeast-1.aws.neon.tech/wastelens/auth';

export const neonAuthClient = createAuthClient(authUrl);

export interface AuthUser {
  id: string;
  email: string;
}

// In-memory token and session cache
let inMemoryToken: string | null = null;
let inMemoryUser: AuthUser | null = null;

async function fetchJwtFromAuthServer(): Promise<string | null> {
  try {
    const res = await fetch(`${authUrl}/token`, {
      method: 'GET',
      credentials: 'include',
    });
    if (res.ok) {
      const data = await res.json();
      if (data?.token) {
        return data.token;
      }
    }
  } catch (ignored) {}
  return null;
}

export async function signIn(email: string, password: string): Promise<AuthUser> {
  const result = await neonAuthClient.signIn.email({
    email,
    password,
  });

  if (result.error) {
    throw new Error(result.error.message || 'Authentication failed');
  }

  const token = await fetchJwtFromAuthServer();
  if (!token) {
    throw new Error('Failed to retrieve verified session token from auth service');
  }

  inMemoryToken = token;
  inMemoryUser = {
    id: result.data.user.id,
    email: result.data.user.email,
  };

  return inMemoryUser;
}

export async function signOut(): Promise<void> {
  inMemoryToken = null;
  inMemoryUser = null;
  try {
    await neonAuthClient.signOut();
  } catch (ignored) {
    // Session is cleared in memory
  }
}

export async function getJwtToken(): Promise<string | null> {
  if (inMemoryToken) {
    return inMemoryToken;
  }
  const token = await fetchJwtFromAuthServer();
  if (token) {
    inMemoryToken = token;
    return inMemoryToken;
  }
  return null;
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  if (inMemoryUser) {
    return inMemoryUser;
  }
  try {
    const sessionRes = await neonAuthClient.getSession();
    if (sessionRes.data?.user) {
      inMemoryUser = {
        id: sessionRes.data.user.id,
        email: sessionRes.data.user.email,
      };
      await getJwtToken();
      return inMemoryUser;
    }
  } catch (e) {
    // Not logged in
  }
  return null;
}
