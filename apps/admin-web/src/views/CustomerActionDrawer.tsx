import { useEffect, useMemo, useState } from 'react';
import type { CustomerDTO, CustomerDetail } from '@/models/CustomerDTO';
import './UserActionDrawer.css';

interface CustomerActionDrawerProps {
  open: boolean;
  customer: CustomerDTO | null;
  detail: CustomerDetail | null;
  onClose: () => void;
  onSave: (payload: Partial<CustomerDetail>) => void;
  isProcessing: boolean;
  isDetailLoading: boolean;
  error?: string | null;
}

const toInputValue = (value: string | null | undefined) => (value ?? '');
const toNumberInputValue = (value: number | null | undefined) => (value ?? '');

export const CustomerActionDrawer = ({
  open,
  customer,
  detail,
  onClose,
  onSave,
  isProcessing,
  isDetailLoading,
  error
}: CustomerActionDrawerProps) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [height, setHeight] = useState<string>('');
  const [weight, setWeight] = useState<string>('');
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    if (open && customer) {
      const source = detail ?? customer;
      setName(source.name ?? '');
      setEmail(source.email ?? '');
      setPhone(source.phone ?? '');
      setGender(source.gender ?? '');
      setDateOfBirth(source.dateOfBirth ?? '');
      setHeight(source.height !== null && source.height !== undefined ? String(source.height) : '');
      setWeight(source.weight !== null && source.weight !== undefined ? String(source.weight) : '');
      setLocalError(null);
    }
  }, [open, customer, detail]);

  const formattedUpdatedAt = useMemo(() => {
    if (!detail) return '';
    try {
      return new Intl.DateTimeFormat('zh-CN', {
        dateStyle: 'medium',
        timeStyle: 'short'
      }).format(new Date(detail.updatedAt));
    } catch {
      return detail.updatedAt;
    }
  }, [detail]);

  const handleSave = () => {
    if (!customer) return;
    const changes: Partial<CustomerDetail> = {};

    const trimOrNull = (value: string) => {
      const trimmed = value.trim();
      return trimmed === '' ? null : trimmed;
    };

    const parseOptionalNumber = (value: string) => {
      const trimmed = value.trim();
      if (trimmed === '') return null;
      const numeric = Number(trimmed);
      return Number.isNaN(numeric) ? null : numeric;
    };

    const base = detail ?? customer;

    const normalizedName = trimOrNull(name);
    if ((base.name ?? null) !== normalizedName) {
      if (normalizedName === null) {
        setLocalError('客户名称不能为空');
        return;
      }
      changes.name = normalizedName;
    }

    const trimmedEmail = email.trim();
    if ((base.email ?? '') !== trimmedEmail) {
      if (!trimmedEmail) {
        setLocalError('邮箱不能为空');
        return;
      }
      changes.email = trimmedEmail;
    }

    const normalizedPhone = trimOrNull(phone);
    if ((base.phone ?? null) !== normalizedPhone) {
      changes.phone = normalizedPhone;
    }

    const normalizedGender = trimOrNull(gender);
    if ((base.gender ?? null) !== normalizedGender) {
      changes.gender = normalizedGender;
    }

    const normalizedDob = dateOfBirth.trim();
    if ((base.dateOfBirth ?? '') !== normalizedDob) {
      changes.dateOfBirth = normalizedDob === '' ? null : normalizedDob;
    }

    const parsedHeight = parseOptionalNumber(height);
    if (parsedHeight !== (base.height ?? null)) {
      changes.height = parsedHeight;
    }

    const parsedWeight = parseOptionalNumber(weight);
    if (parsedWeight !== (base.weight ?? null)) {
      changes.weight = parsedWeight;
    }

    if (Object.keys(changes).length === 0) {
      setLocalError('暂无可保存的更新内容');
      return;
    }

    setLocalError(null);
    onSave(changes);
  };

  return (
    <div className={`drawer-overlay ${open ? 'is-open' : ''}`} onClick={onClose}>
      <div className="drawer-panel" onClick={event => event.stopPropagation()}>
        <header className="drawer-header">
          <div>
            <h3>客户详情</h3>
            {customer && <p>{customer.email}</p>}
          </div>
          <button className="drawer-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>
        {customer && (
          <div className="drawer-body">
            <section className="drawer-section">
              <span className="drawer-label">基础信息</span>
              {isDetailLoading ? (
                <span className="drawer-value">加载中...</span>
              ) : (
                <div className="drawer-grid">
                  <div>
                    <span className="drawer-sub-label">客户名称</span>
                    <span className="drawer-value">{detail?.name || customer.name}</span>
                  </div>
                  <div>
                    <span className="drawer-sub-label">最新修改</span>
                    <span className="drawer-value">{formattedUpdatedAt || '—'}</span>
                  </div>
                </div>
              )}
            </section>
            <section className="drawer-section">
              <label className="drawer-field">
                <span>客户名称</span>
                <input value={name} onChange={event => setName(event.target.value)} disabled={isProcessing} />
              </label>
              <label className="drawer-field">
                <span>邮箱</span>
                <input value={email} onChange={event => setEmail(event.target.value)} disabled={isProcessing} />
              </label>
              <label className="drawer-field">
                <span>电话</span>
                <input value={toInputValue(phone)} onChange={event => setPhone(event.target.value)} disabled={isProcessing} />
              </label>
              <label className="drawer-field">
                <span>性别</span>
                <input value={toInputValue(gender)} onChange={event => setGender(event.target.value)} disabled={isProcessing} />
              </label>
              <label className="drawer-field">
                <span>出生日期</span>
                <input
                  type="date"
                  value={toInputValue(dateOfBirth)}
                  onChange={event => setDateOfBirth(event.target.value)}
                  disabled={isProcessing}
                />
              </label>
              <div className="drawer-grid">
                <label className="drawer-field">
                  <span>身高 (cm)</span>
                  <input
                    type="number"
                  value={height}
                  onChange={event => setHeight(event.target.value)}
                  disabled={isProcessing}
                />
              </label>
              <label className="drawer-field">
                  <span>体重 (kg)</span>
                  <input
                    type="number"
                    value={weight}
                    onChange={event => setWeight(event.target.value)}
                    disabled={isProcessing}
                  />
                </label>
              </div>
              <button type="button" className="drawer-primary" onClick={handleSave} disabled={isProcessing}>
                保存更改
              </button>
            </section>
            {localError && <div className="drawer-error">{localError}</div>}
            {error && <div className="drawer-error">{error}</div>}
          </div>
        )}
      </div>
    </div>
  );
};
