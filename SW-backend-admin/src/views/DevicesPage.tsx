import { useMemo } from 'react';
import { SearchInput } from '@/components/SearchInput';
import { Table, type TableColumn } from '@/components/Table';
import { Pagination } from '@/components/Pagination';
import { Tag } from '@/components/Tag';
import type { DeviceDTO } from '@/models/Device';
import { useDevicesVM } from '@/viewmodels/useDevicesVM';
import './DevicesPage.css';

const formatDateTime = (value: string) => {
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  } catch {
    return value;
  }
};

export const DevicesPage = () => {
  const {
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
  } = useDevicesVM();

  const isDeviceOnline = (item: DeviceDTO) => {
    const diff = Date.now() - new Date(item.lastSeenAt).getTime();
    return diff <= onlineWindowSeconds * 1000;
  };

  const columns: TableColumn<DeviceDTO>[] = useMemo(
    () => [
      {
        key: 'deviceId',
        header: '设备标识',
        render: item => <code className="devices-code">{item.deviceId}</code>
      },
      {
        key: 'lastUserId',
        header: '最近用户',
        render: item =>
          item.lastUserId ? (
            <div className="devices-user">
              <span>{item.lastUserName || '未命名'}</span>
              <span className="devices-user-email">{item.lastUserEmail || '无邮箱'}</span>
              <span className="devices-user-id">ID: {item.lastUserId}</span>
            </div>
          ) : (
            '—'
          )
      },
      {
        key: 'lastSeenAt',
        header: '最近在线',
        render: item => (
          <div className="devices-last-seen">
            <span>{formatDateTime(item.lastSeenAt)}</span>
            <Tag tone={isDeviceOnline(item) ? 'success' : 'warning'} label={isDeviceOnline(item) ? '在线' : '离线'} />
          </div>
        )
      },
      {
        key: 'deviceInfo',
        header: '设备信息',
        render: item => (
          <div className="devices-meta">
            <span>{item.deviceModel || '未知型号'}</span>
            <span>Android {item.osVersion || '未知版本'}</span>
            <span>App {item.appVersion || '未知版本'}</span>
            <span>IP：{item.lastIp || '—'}</span>
          </div>
        )
      },
      {
        key: 'offlineAllowed',
        header: '离线权限',
        render: item => (
          <Tag tone={item.offlineAllowed ? 'success' : 'danger'} label={item.offlineAllowed ? '允许' : '禁止'} />
        )
      },
      {
        key: 'actions',
        header: '操作',
        render: item => (
          <div className="devices-actions">
            <button
              type="button"
              onClick={() => toggleOfflinePermission(item)}
              disabled={isLoading || actionDeviceId === item.deviceId}
            >
              {item.offlineAllowed ? '禁止离线' : '允许离线'}
            </button>
            <button
              type="button"
              className="devices-force"
              onClick={() => forceExitDevice(item)}
              disabled={!item.offlineAllowed || isLoading || actionDeviceId === item.deviceId}
            >
              强制退出
            </button>
          </div>
        )
      }
    ],
    [actionDeviceId, forceExitDevice, isLoading, toggleOfflinePermission]
  );

  return (
    <div className="devices-root card">
      <div className="devices-header">
        <div>
          <h2>设备控制台</h2>
          <p>查看在线设备、离线权限，并可针对单台设备发出控制指令。</p>
        </div>
        <SearchInput
          value={searchTerm}
          onChange={setSearchTerm}
          onSubmit={applySearch}
          onReset={resetSearch}
          placeholder="输入设备ID、用户或IP"
        />
      </div>

      <div className="devices-toolbar">
        <label>
          离线权限
          <select value={offlineFilter} onChange={event => changeOfflineFilter(event.target.value as typeof offlineFilter)}>
            <option value="all">全部</option>
            <option value="allowed">允许</option>
            <option value="blocked">禁止</option>
          </select>
        </label>
        <label className="devices-checkbox">
          <input type="checkbox" checked={onlyOnline} onChange={toggleOnlineOnly} />
          仅显示在线设备
        </label>
        <label>
          在线窗口
          <select
            value={onlineWindowSeconds}
            onChange={event => updateOnlineWindow(Number(event.target.value))}
          >
            <option value={60}>1 分钟</option>
            <option value={120}>2 分钟</option>
            <option value={300}>5 分钟</option>
          </select>
        </label>
        <button type="button" className="devices-refresh" onClick={refresh} disabled={isLoading}>
          刷新
        </button>
      </div>

      {infoMessage && <div className="devices-info">{infoMessage}</div>}
      {error && <div className="devices-error">{error}</div>}

      <Table columns={columns} data={items} isLoading={isLoading} emptyMessage="暂无设备数据" />

      <div className="devices-footer">
        <span>共 {total} 台设备</span>
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>
    </div>
  );
};
