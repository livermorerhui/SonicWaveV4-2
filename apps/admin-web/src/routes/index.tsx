import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/viewmodels/AuthProvider';
import { LoginPage } from '@/views/LoginPage';
import { UsersPage } from '@/views/UsersPage';
import { CustomersPage } from '@/views/CustomersPage';
import { NotAuthorized } from '@/views/NotAuthorized';
import { AdminLayout } from '@/views/AdminLayout';
import { LoadingScreen } from '@/views/LoadingScreen';
import { MusicAdminPage } from '@/views/MusicAdminPage';
import { FeatureFlagsPage } from '@/views/FeatureFlagsPage';
import { DevicesPage } from '@/views/DevicesPage';

const ProtectedRoute = () => {
  const { user, isBootstrapping } = useAuth();

  if (isBootstrapping) {
    return <LoadingScreen message="正在准备管理工作台..." />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.role !== 'admin') {
    return <Navigate to="/not-authorized" replace />;
  }

  return <Outlet />;
};

export const AppRoutes = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AdminLayout />}>
          <Route index element={<Navigate to="/users" replace />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/feature-flags" element={<FeatureFlagsPage />} />
          <Route path="/devices" element={<DevicesPage />} />
          <Route path="/music" element={<MusicAdminPage />} />
        </Route>
      </Route>
      <Route path="/not-authorized" element={<NotAuthorized />} />
      <Route path="*" element={<Navigate to="/users" replace />} />
    </Routes>
  </BrowserRouter>
);
