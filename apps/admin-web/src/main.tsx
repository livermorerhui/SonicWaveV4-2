import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppRoutes } from '@/routes';
import { AuthProvider } from '@/viewmodels/AuthProvider';
import '@/styles/base.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  </React.StrictMode>
);
