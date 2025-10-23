import { createContext, useContext, type ReactNode } from 'react';
import { useAuthVM } from './useAuthVM';

type AuthVM = ReturnType<typeof useAuthVM>;

export const AuthContext = createContext<AuthVM | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const vm = useAuthVM();
  return <AuthContext.Provider value={vm}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
};
