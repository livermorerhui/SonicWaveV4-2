import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/viewmodels/AuthProvider';
import { ApiError } from '@/services/api';
import './LoginPage.css';

export const LoginPage = () => {
  const { login, isLoading, error } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setLocalError(null);

    try {
      await login({ email, password });
      navigate('/users', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setLocalError(err.message);
      } else if (err instanceof Error) {
        setLocalError(err.message);
      } else {
        setLocalError('登录失败，请稍后再试');
      }
    }
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-header">
          <h1>SonicWave 管理后台</h1>
          <p>请使用管理员账号登录以继续操作。</p>
        </div>
        <label className="login-field">
          <span>邮箱</span>
          <input
            type="email"
            value={email}
            onChange={event => setEmail(event.target.value)}
            placeholder="admin@sonicwave.example"
            required
          />
        </label>
        <label className="login-field">
          <span>密码</span>
          <input
            type="password"
            value={password}
            onChange={event => setPassword(event.target.value)}
            placeholder="••••••••"
            required
          />
        </label>
        {(localError || error) && <div className="login-error">{localError || error}</div>}
        <button type="submit" className="login-submit" disabled={isLoading}>
          {isLoading ? '正在登录...' : '登录'}
        </button>
      </form>
    </div>
  );
};
