export type UserRole = 'user' | 'admin';
export type AccountType = 'normal' | 'test';

export interface UserDTO {
  id: number;
  email: string;
  role: UserRole;
  accountType: AccountType;
  username?: string;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
}

export interface UserDetail extends UserDTO {
  passwordHash?: string;
}
