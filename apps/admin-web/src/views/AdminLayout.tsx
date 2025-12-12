import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/viewmodels/AuthProvider';
import './AdminLayout.css';

export const AdminLayout = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const roleLabel = user?.role === 'admin' ? '管理员' : '普通用户';

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true, state: { from: location.pathname } });
  };

  return (
    <div className="app-shell">
      <header className="admin-header">
        <div className="admin-brand">
          <span className="brand-accent">SonicWave</span>
          <span className="brand-subtitle">管理控制台</span>
        </div>
        <nav className="admin-nav">
          <NavLink className="nav-link" to="/users">
            用户管理
          </NavLink>
          <NavLink className="nav-link" to="/customers">
            客户管理
          </NavLink>
          <NavLink className="nav-link" to="/devices">
            设备控制
          </NavLink>
          <NavLink className="nav-link" to="/feature-flags">
            功能开关
          </NavLink>
          <NavLink className="nav-link" to="/music">
            音乐管理
          </NavLink>
        </nav>
        <div className="admin-session">
          <div className="session-user">
            <span className="session-email">{user?.email}</span>
            <span className="session-role">{roleLabel}</span>
          </div>
          <button className="logout-button" onClick={handleLogout}>
            退出登录
          </button>
        </div>
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
};
