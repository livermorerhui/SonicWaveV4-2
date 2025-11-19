import { useEffect, useMemo, useState } from 'react';
import type { AccountType, UserDTO, UserRole, UserDetail } from '@/models/UserDTO';
import './UserActionDrawer.css';

interface UserActionDrawerProps {
  open: boolean;
  user: UserDTO | null;
  detail: UserDetail | null;
  onClose: () => void;
  onChangeRole: (role: UserRole) => void;
  onChangeAccountType: (accountType: AccountType) => void;
  onSoftDelete: () => void;
  onHardDelete: () => void;
  isProcessing: boolean;
  isDetailLoading: boolean;
  onResetPassword: (password: string) => void;
  error?: string | null;
}

const roleOptions: Array<{ value: UserRole; label: string }> = [
  { value: 'admin', label: '管理员' },
  { value: 'user', label: '普通用户' }
];

const accountTypeOptions: Array<{ value: AccountType; label: string }> = [
  { value: 'normal', label: '正式账号' },
  { value: 'test', label: '测试账号' }
];

export const UserActionDrawer = ({
  open,
  user,
  detail,
  onClose,
  onChangeRole,
  onChangeAccountType,
  onSoftDelete,
  onHardDelete,
  isProcessing,
  isDetailLoading,
  onResetPassword,
  error
}: UserActionDrawerProps) => {
  const [nextRole, setNextRole] = useState<UserRole>('user');
  const [nextAccountType, setNextAccountType] = useState<AccountType>('normal');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);

  useEffect(() => {
    if (open && user) {
      setNextRole(user.role);
      setNextAccountType(user.accountType);
      setNewPassword('');
      setConfirmPassword('');
      setPasswordError(null);
    }
  }, [open, user]);

  const formattedCreatedAt = useMemo(() => {
    if (!user) return '';
    try {
      return new Intl.DateTimeFormat('zh-CN', {
        dateStyle: 'medium',
        timeStyle: 'short'
      }).format(new Date(user.createdAt));
    } catch {
      return user.createdAt;
    }
  }, [user]);

  return (
    <div className={`drawer-overlay ${open ? 'is-open' : ''}`} onClick={onClose}>
      <div className="drawer-panel" onClick={event => event.stopPropagation()}>
        <header className="drawer-header">
          <div>
            <h3>用户设置</h3>
            {user && <p>{user.email}</p>}
          </div>
          <button className="drawer-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>
        {user && (
          <div className="drawer-body">
            <section className="drawer-section">
              <span className="drawer-label">基本信息</span>
              {isDetailLoading ? (
                <span className="drawer-value">加载中...</span>
              ) : detail ? (
                <div className="drawer-grid">
                  <div>
                    <span className="drawer-sub-label">用户名</span>
                    <span className="drawer-value">{detail.username || '—'}</span>
                  </div>
                  <div>
                    <span className="drawer-sub-label">邮箱</span>
                    <span className="drawer-value">{detail.email}</span>
                  </div>
                  <div>
                    <span className="drawer-sub-label">账号类型</span>
                    <span className="drawer-value">{detail.accountType === 'test' ? '测试账号' : '正式账号'}</span>
                  </div>
                </div>
              ) : (
                <span className="drawer-value">未加载到用户详情</span>
              )}
            </section>
            <section className="drawer-section">
              <span className="drawer-label">创建时间</span>
              <span className="drawer-value">{formattedCreatedAt}</span>
            </section>
            {user.deletedAt && (
              <section className="drawer-section warning">
                <span className="drawer-label">当前状态</span>
                <span className="drawer-value">已标记删除</span>
              </section>
            )}
            <section className="drawer-section">
              <label className="drawer-field">
                <span>角色</span>
                <select value={nextRole} onChange={event => setNextRole(event.target.value as UserRole)}>
                  {roleOptions.map(option => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="drawer-primary"
                onClick={() => onChangeRole(nextRole)}
                disabled={isProcessing}
              >
                保存角色
              </button>
            </section>
            <section className="drawer-section">
              <label className="drawer-field">
                <span>账号类型</span>
                <select value={nextAccountType} onChange={event => setNextAccountType(event.target.value as AccountType)}>
                  {accountTypeOptions.map(option => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="drawer-primary"
                onClick={() => onChangeAccountType(nextAccountType)}
                disabled={isProcessing}
              >
                保存账号类型
              </button>
            </section>
            <section className="drawer-section">
              <span className="drawer-label">密码管理</span>
              <div className="drawer-field">
                <span>新密码</span>
                <input
                  type="password"
                  value={newPassword}
                  onChange={event => setNewPassword(event.target.value)}
                  placeholder="请输入新密码"
                  disabled={isProcessing}
                />
              </div>
              <div className="drawer-field">
                <span>确认新密码</span>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={event => setConfirmPassword(event.target.value)}
                  placeholder="请再次输入新密码"
                  disabled={isProcessing}
                />
              </div>
              {passwordError && <div className="drawer-error">{passwordError}</div>}
              <button
                type="button"
                className="drawer-primary"
                onClick={async () => {
                  if (newPassword.trim().length < 8) {
                    setPasswordError('密码长度至少 8 位');
                    return;
                  }
                  if (newPassword !== confirmPassword) {
                    setPasswordError('两次输入的密码不一致');
                    return;
                  }
                  setPasswordError(null);
                  await onResetPassword(newPassword.trim());
                  setNewPassword('');
                  setConfirmPassword('');
                }}
                disabled={isProcessing}
              >
                重置密码
              </button>
            </section>
            <section className="drawer-section danger">
              <span className="drawer-label">危险操作</span>
              <div className="drawer-danger-actions">
                <button type="button" className="drawer-secondary" onClick={onSoftDelete} disabled={isProcessing}>
                  软删除用户
                </button>
                <button type="button" className="drawer-danger" onClick={onHardDelete} disabled={isProcessing}>
                  强制删除
                </button>
              </div>
              <p className="drawer-hint">软删除仅做标记，可在数据库中恢复；强制删除将永久移除。</p>
            </section>
            {error && <div className="drawer-error">{error}</div>}
          </div>
        )}
      </div>
    </div>
  );
};
