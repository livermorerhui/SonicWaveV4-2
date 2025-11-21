import { useEffect, useState } from 'react';
import { fetchFeatureFlags, patchOfflineModeFlag, forceExitOfflineMode } from '@/services/api';
import type { FeatureFlagSnapshot } from '@/models/FeatureFlag';
import { useAuth } from '@/viewmodels/AuthProvider';
import './FeatureFlagsPage.css';

export const FeatureFlagsPage = () => {
  const { token } = useAuth();
  const [snapshot, setSnapshot] = useState<FeatureFlagSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [forceExiting, setForceExiting] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const FORCE_COUNTDOWN = 5;

  useEffect(() => {
    if (!token) {
      return;
    }
    let mounted = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchFeatureFlags(token);
        if (!mounted) return;
        setSnapshot(data);
      } catch (err) {
        if (!mounted) return;
        setError(err instanceof Error ? err.message : '加载功能开关失败');
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, [token]);

  useEffect(() => {
    if (!infoMessage) return;
    const timer = setTimeout(() => setInfoMessage(null), 4000);
    return () => clearTimeout(timer);
  }, [infoMessage]);

  useEffect(() => {
    if (countdown === null) return;
    if (countdown <= 0) {
      setCountdown(null);
      setSnapshot(prev =>
        prev
          ? {
              offlineMode: {
                ...prev.offlineMode,
                enabled: false,
                updatedAt: new Date().toISOString()
              }
            }
          : prev
      );
      setInfoMessage('离线模式倒计时结束，已自动切换为关闭');
      return;
    }
    const timer = setTimeout(() => setCountdown(prev => (prev ?? 0) - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  useEffect(() => {
    if (!snapshot?.offlineMode.enabled) {
      setCountdown(null);
    }
  }, [snapshot?.offlineMode.enabled]);

  const handleToggle = async () => {
    if (!token || !snapshot) {
      return;
    }
    const nextEnabled = !snapshot.offlineMode.enabled;
    setSaving(true);
    setError(null);
    try {
      const response = await patchOfflineModeFlag(nextEnabled, token, true);
      setSnapshot({ offlineMode: response.featureFlag });
      setCountdown(null);
      setInfoMessage(nextEnabled ? '已启用离线模式并通知在线设备' : '已关闭离线模式，设备将在用户退出后生效');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新离线模式开关失败');
    } finally {
      setSaving(false);
    }
  };

  const handleForceExit = async () => {
    if (!token) {
      return;
    }
    setForceExiting(true);
    setError(null);
    try {
      await forceExitOfflineMode(FORCE_COUNTDOWN, token);
      setCountdown(FORCE_COUNTDOWN);
      setInfoMessage(`已广播强制退出指令，设备将在 ${FORCE_COUNTDOWN}s 内退出离线模式`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送强制退出指令失败');
    } finally {
      setForceExiting(false);
    }
  };

  const renderStatus = () => {
    if (loading) {
      return <span className="flag-status flag-status--loading">加载中...</span>;
    }
    if (!snapshot) {
      return <span className="flag-status flag-status--error">无法获取状态</span>;
    }
    return snapshot.offlineMode.enabled ? (
      <span className="flag-status flag-status--on">已启用</span>
    ) : (
      <span className="flag-status flag-status--off">已关闭</span>
    );
  };

  const updatedAt =
    snapshot?.offlineMode.updatedAt && !loading
      ? new Date(snapshot.offlineMode.updatedAt).toLocaleString()
      : '—';

  return (
    <div className="feature-flags-page">
      <header className="feature-flags-header">
        <div>
          <h1>功能总控</h1>
          <p>集中管理客户端离线能力，可实时对在线设备生效。</p>
        </div>
        {renderStatus()}
      </header>

      <section className="feature-card">
        <div className="feature-card__content">
          <h2>离线模式权限</h2>
          <p>允许 SonicWave 客户端在无法连接服务器时使用离线测试模式。</p>
          <ul>
            <li>启用后，设备启动时会向云端确认权限并自动进入离线模式。</li>
            <li>停用后，在线设备将在下一次同步时退出离线模式。</li>
            <li>如需紧急恢复在线，使用“强制退出”触发 10 秒倒计时并强制登出。</li>
          </ul>
          <div className="feature-card__meta">
            <span>最后更新：{updatedAt}</span>
            {snapshot?.offlineMode.updatedBy && (
              <span>操作者ID：{snapshot.offlineMode.updatedBy}</span>
            )}
            {countdown !== null && (
              <span className="force-countdown">倒计时：{countdown}s</span>
            )}
          </div>
        </div>
        <div className="feature-card__actions">
          <button
            className="toggle-button"
            onClick={handleToggle}
            disabled={loading || saving || !snapshot}
          >
            {saving ? '保存中...' : snapshot?.offlineMode.enabled ? '关闭离线模式' : '启用离线模式'}
          </button>
          <button
            className={`force-exit-button ${
              !snapshot?.offlineMode.enabled ? 'force-exit-button--disabled' : 'force-exit-button--active'
            }`}
            onClick={handleForceExit}
            disabled={!snapshot?.offlineMode.enabled || loading || forceExiting || countdown !== null}
          >
            {countdown !== null
              ? `倒计时 ${countdown}s`
              : forceExiting
                ? '指令发送中...'
                : '强制退出离线模式（5秒）'}
          </button>
        </div>
      </section>

      {infoMessage && <div className="feature-flags-feedback">{infoMessage}</div>}
      {error && <div className="feature-flags-error">{error}</div>}
    </div>
  );
};
