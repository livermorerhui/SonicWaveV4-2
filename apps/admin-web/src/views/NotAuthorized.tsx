import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/viewmodels/AuthProvider';
import './NotAuthorized.css';

export const NotAuthorized = () => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleBack = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="not-authorized-shell">
      <div className="not-authorized-card">
        <h2>访问受限</h2>
        <p>此控制台仅向 SonicWave 管理员开放。</p>
        <button onClick={handleBack}>返回登录页</button>
      </div>
    </div>
  );
};
