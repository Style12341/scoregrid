import { api } from "@/lib/api";

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  roles: string[];
}

export interface UsersPage {
  items: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function listUsers(page: number, size: number): Promise<UsersPage> {
  const { data } = await api.get<UsersPage>("/api/users", { params: { page, size } });
  return data;
}
