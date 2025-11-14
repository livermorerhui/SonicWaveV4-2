import { useAuth } from '@/viewmodels/AuthProvider';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  fetchDevices,
  patchDeviceOfflinePermission,
  forceExitDeviceOffline
} from '@/services/api';
import type { DeviceDTO } from '@/models/Device';

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_ONLINE_WINDOW = 120; // 秒

type OfflineFilter = 'all' | 'allowed' | 'blocked';

export const useDevicesVM = () => {
  const { token, logout } = useAuth();
  const [items, setItems] = useState<DeviceDTO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [keyword, setKeyword] = useState('');
  const [offlineFilter, setOfflineFilter] = useState<OfflineFilter>('all');
  const [onlyOnline, setOnlyOnline] = useState(false);
  const [onlineWindowSeconds, setOnlineWindowSeconds] = useState(DEFAULT_ONLINE_WINDOW);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [actionDeviceId, setActionDeviceId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchDevices(
        {
          page,
          pageSize,
          keyword,
          offlineAllowed:
            offlineFilter === 'all' ? undefined : offlineFilter === 'allowed' ? true : false,
          onlyOnline,
          onlineWindowSeconds
        },
        token
      );
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        logout();
        return;
      }
      setError(err instanceof Error ? err.message : '加载设备列表失败');
    } finally {
      setIsLoading(false);
    }
  }, [token, page, pageSize, keyword, offlineFilter, onlyOnline, onlineWindowSeconds, logout]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!infoMessage) return;
    const timer = setTimeout(() => setInfoMessage(null), 4000);
    return () => clearTimeout(timer);
  }, [infoMessage]);

  const totalPages = useMemo(() => Math.max(1, Math.ceil(total / pageSize)), [total, pageSize]);

  const applySearch = useCallback(() => {
    setPage(1);
    setKeyword(searchTerm.trim());
  }, [searchTerm]);

  const resetSearch = useCallback(() => {
    setSearchTerm('');
    setKeyword('');
    setPage(1);
  }, []);

  const changeOfflineFilter = useCallback((value: OfflineFilter) => {
    setOfflineFilter(value);
    setPage(1);
  }, []);

  const toggleOnlineOnly = useCallback(() => {
    setOnlyOnline(prev => !prev);
    setPage(1);
  }, []);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  const updateOnlineWindow = useCallback((value: number) => {
    setOnlineWindowSeconds(value);
    setPage(1);
  }, []);

  const toggleOfflinePermission = useCallback(
    async (device: DeviceDTO) => {
      if (!token) return;
      setActionDeviceId(device.deviceId);
      setError(null);
      try {
        await patchDeviceOfflinePermission(
          device.deviceId,
          {
            offlineAllowed: !device.offlineAllowed,
            notifyOnline: true
          },
          token
        );
        setInfoMessage(!device.offlineAllowed ? '已允许该设备进入离线模式' : '已禁止该设备使用离线模式');
        await load();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setError(err instanceof Error ? err.message : '更新设备离线权限失败');
      } finally {
        setActionDeviceId(null);
      }
    },
    [token, load, logout]
  );

  const forceExitDevice = useCallback(
    async (device: DeviceDTO, countdownSec = 5) => {
      if (!token) return;
      setActionDeviceId(device.deviceId);
      setError(null);
      try {
        const response = await forceExitDeviceOffline(device.deviceId, countdownSec, token);
        setInfoMessage(`已下发 ${countdownSec}s 强制退出指令`);
        if (response.device) {
          setItems(prev =>
            prev.map(item =>
              item.deviceId === response.device!.deviceId ? response.device! : item
            )
          );
        }
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setError(err instanceof Error ? err.message : '强制退出指令发送失败');
      } finally {
        setActionDeviceId(null);
      }
    },
    [token, logout]
  );

  return {
    items,
    page,
    pageSize,
    total,
    totalPages,
    setPage,
    searchTerm,
    setSearchTerm,
    applySearch,
    resetSearch,
    offlineFilter,
    changeOfflineFilter,
    onlyOnline,
    toggleOnlineOnly,
    onlineWindowSeconds,
    updateOnlineWindow,
    isLoading,
    error,
    infoMessage,
    refresh,
    actionDeviceId,
    toggleOfflinePermission,
    forceExitDevice
  };
};
