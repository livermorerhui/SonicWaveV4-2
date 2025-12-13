import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, getCurrentUser, login as loginRequest } from '@/services/api';
import type { UserDTO } from '@/models/UserDTO';

const ACCESS_TOKEN_KEY = 'sw-admin-access-token';
const REFRESH_TOKEN_KEY = 'sw-admin-refresh-token';

interface LoginFormPayload {
  email: string;
  password: string;
}

const mapUser = (payload: {
  id: number;
  email: string;
  role: 'user' | 'admin';
  createdAt: string;
  username?: string;
  accountType: 'normal' | 'test';
  deletedAt?: string | null;
}): UserDTO => ({
  id: payload.id,
  email: payload.email,
  role: payload.role,
  username: payload.username,
  accountType: payload.accountType,
  createdAt: payload.createdAt,
  updatedAt: payload.createdAt,
  deletedAt: payload.deletedAt ?? null
});

export const useAuthVM = () => {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(ACCESS_TOKEN_KEY));
  const [isLoading, setIsLoading] = useState(false);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const clearAuth = useCallback(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    setUser(null);
    setToken(null);
  }, []);

  const fetchProfile = useCallback(
    async (nextToken: string) => {
      setIsLoading(true);
      try {
        const response = await getCurrentUser(nextToken);
        const mapped = mapUser(response.user);
        setUser(mapped);
        setError(null);
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          clearAuth();
        }
        setError(err instanceof Error ? err.message : '加载用户信息失败');
      } finally {
        setIsLoading(false);
        setIsBootstrapping(false);
      }
    },
    [clearAuth]
  );

  useEffect(() => {
    const storedToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!storedToken) {
      setIsBootstrapping(false);
      return;
    }
    setToken(storedToken);
    fetchProfile(storedToken);
  }, [fetchProfile]);

  const login = useCallback(
    async (credentials: LoginFormPayload) => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await loginRequest(credentials);

        // 防御性检查：后端响应异常（例如返回了 HTML 或缺少 accessToken）
        if (!response || !response.accessToken) {
          throw new Error('登录响应异常：缺少 accessToken，请联系管理员检查后台服务配置');
        }

        localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
        if (response.refreshToken) {
          localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
        }
        setToken(response.accessToken);
        await fetchProfile(response.accessToken);
      } catch (err) {
        setError(err instanceof Error ? err.message : '登录失败');
        setIsLoading(false);
        throw err;
      }
    },
    [fetchProfile]
  );

  const logout = useCallback(() => {
    clearAuth();
    setIsBootstrapping(false);
  }, [clearAuth]);

  return useMemo(
    () => ({
      user,
      token,
      isLoading,
      isBootstrapping,
      error,
      login,
      logout
    }),
    [user, token, isLoading, isBootstrapping, error, login, logout]
  );
};
