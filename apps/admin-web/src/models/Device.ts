export interface DeviceDTO {
  deviceId: string;
  firstSeenAt: string;
  lastSeenAt: string;
  lastUserId: string | null;
  lastUserEmail: string | null;
  lastUserName: string | null;
  lastIp: string | null;
  deviceModel: string | null;
  osVersion: string | null;
  appVersion: string | null;
  offlineAllowed: boolean;
  metadata?: Record<string, unknown> | null;
  updatedAt: string;
  isOnline?: boolean;
}

export interface DeviceListResponse {
  items: DeviceDTO[];
  page: number;
  pageSize: number;
  total: number;
}
