import { api } from "@/lib/api";
import type { Role } from "@/auth/AuthContext";

/** docs/contracts.md#auth-service — 201 returns the profile, never a token. */
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  roles: Role[];
}

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
}

export async function register(input: RegisterInput): Promise<UserProfile> {
  const { data } = await api.post<UserProfile>("/api/auth/register", input);
  return data;
}
