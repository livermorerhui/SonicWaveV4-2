import type { ErrorEnvelope } from '@/models/ErrorEnvelope';
import type { UserDTO, UserDetail } from '@/models/UserDTO';
import type { CustomerDTO, CustomerDetail } from '@/models/CustomerDTO';
import type { PaginatedResponse } from '@/models/PaginatedResponse';
import type { FeatureFlag, FeatureFlagSnapshot } from '@/models/FeatureFlag';
import type { DeviceDTO } from '@/models/Device';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export class ApiError extends Error {
  public status: number;
  public envelope?: ErrorEnvelope;

  constructor(status: number, message: string, envelope?: ErrorEnvelope) {
    super(message);
    this.status = status;
    this.envelope = envelope;
  }
}

interface RequestOptions extends RequestInit {
  token?: string | null;
}

const resolveUrl = (path: string) => {
  if (!API_BASE_URL) {
    return path;
  }
  return `${API_BASE_URL}${path}`;
};

const parseError = async (response: Response): Promise<ErrorEnvelope | undefined> => {
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    try {
      return (await response.json()) as ErrorEnvelope;
    } catch {
      return undefined;
    }
  }
  return undefined;
};

const request = async <T>(path: string, options: RequestOptions = {}): Promise<T> => {
  const headers = new Headers(options.headers || {});
  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`);
  }
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(resolveUrl(path), {
    ...options,
    headers,
    credentials: 'include'
  });

  if (!response.ok) {
    const envelope = await parseError(response);
    const message = envelope?.error?.message || `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message, envelope);
  }

  if (response.status === 204) {
    return null as T;
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return (await response.json()) as T;
  }

  return null as T;
};

const buildQueryString = (params: Record<string, string | number | boolean | undefined>) => {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.length > 0) {
      searchParams.set(key, String(value));
    }
  });
  const query = searchParams.toString();
  return query ? `?${query}` : '';
};

interface LoginPayload {
  email: string;
  password: string;
}

interface LoginResponse {
  message: string;
  accessToken: string;
  refreshToken?: string;
  username: string;
  userId: number;
  role?: string;
  accountType?: string;
}

type CurrentUserResponse = {
  user: {
    id: number;
    email: string;
    role: 'user' | 'admin';
    createdAt: string;
    username?: string;
    accountType: 'normal' | 'test';
    deletedAt?: string | null;
  };
};

export const login = (payload: LoginPayload) =>
  request<LoginResponse>('/api/v1/users/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });

export const getCurrentUser = (token: string) =>
  request<CurrentUserResponse>('/api/v1/users/me', {
    method: 'GET',
    token
  });

interface UserQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  role?: 'user' | 'admin' | 'all';
  accountType?: 'normal' | 'test' | 'all';
  sortBy?: 'createdAt' | 'email' | 'username' | 'role';
  sortOrder?: 'asc' | 'desc';
}

interface CustomerQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  sortBy?: 'createdAt' | 'name' | 'email';
  sortOrder?: 'asc' | 'desc';
}

interface DeviceQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  offlineAllowed?: boolean;
  onlyOnline?: boolean;
  onlineWindowSeconds?: number;
}

export const fetchUsers = (query: UserQuery, token: string) =>
  request<PaginatedResponse<UserDTO>>(`/api/admin/users${buildQueryString(query)}`, {
    method: 'GET',
    token
  });

export const fetchUserDetail = (id: number, token: string) =>
  request<UserDetail>(`/api/admin/users/${id}`, {
    method: 'GET',
    token
  });

export const fetchCustomers = (query: CustomerQuery, token: string) =>
  request<PaginatedResponse<CustomerDTO>>(`/api/admin/customers${buildQueryString(query)}`, {
    method: 'GET',
    token
  });

export const patchUserRole = (id: number, role: 'user' | 'admin', token: string) =>
  request<{ message: string; user: UserDTO }>(`/api/admin/users/${id}/role`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ role })
  });

export const patchUserAccountType = (id: number, accountType: 'normal' | 'test', token: string) =>
  request<{ message: string; user: UserDTO }>(`/api/admin/users/${id}/account-type`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ accountType })
  });

export const patchUserPassword = (id: number, payload: { password: string }, token: string) =>
  request<{ message: string }>(`/api/admin/users/${id}/password`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload)
  });

export const deleteUser = (id: number, force: boolean, token: string) =>
  request<{ message: string; force: boolean }>(
    `/api/admin/users/${id}${force ? buildQueryString({ force }) : ''}`,
    {
      method: 'DELETE',
      token
    }
  );

export const fetchCustomerDetail = (id: number, token: string) =>
  request<CustomerDetail>(`/api/admin/customers/${id}`, {
    method: 'GET',
    token
  });

export const patchCustomer = (
  id: number,
  payload: {
    name?: string | null;
    dateOfBirth?: string | null;
    gender?: string | null;
    phone?: string | null;
    email?: string | null;
    height?: number;
    weight?: number;
  },
  token: string
) =>
  request<{ message: string; customer: CustomerDetail }>(`/api/admin/customers/${id}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload)
  });

export const fetchFeatureFlags = (token: string) =>
  request<FeatureFlagSnapshot>('/api/admin/feature-flags', {
    method: 'GET',
    token
  });

export const patchOfflineModeFlag = (enabled: boolean, token: string, notifyOnline = false) =>
  request<{ message: string; featureFlag: FeatureFlag; notified: boolean }>(
    '/api/admin/feature-flags/offline-mode',
    {
      method: 'PATCH',
      token,
      body: JSON.stringify({ enabled, notifyOnline })
    }
  );

export const forceExitOfflineMode = (countdownSec: number, token: string) =>
  request<{ message: string; countdownSec: number }>(
    '/api/admin/feature-flags/offline-mode/force-exit',
    {
      method: 'POST',
      token,
      body: JSON.stringify({ countdownSec })
    }
  );

export const fetchDevices = (query: DeviceQuery, token: string) =>
  request<PaginatedResponse<DeviceDTO>>(`/api/admin/devices${buildQueryString(query)}`, {
    method: 'GET',
    token
  });

export const patchDeviceOfflinePermission = (
  deviceId: string,
  payload: { offlineAllowed: boolean; notifyOnline?: boolean },
  token: string
) =>
  request<{ message: string; device: DeviceDTO }>(`/api/admin/devices/${deviceId}/offline`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload)
  });

export const forceExitDeviceOffline = (deviceId: string, countdownSec: number, token: string) =>
  request<{ message: string; deviceId: string; countdownSec: number; device: DeviceDTO | null }>(
    `/api/admin/devices/${deviceId}/force-exit`,
    {
      method: 'POST',
      token,
      body: JSON.stringify({ countdownSec })
    }
  );
