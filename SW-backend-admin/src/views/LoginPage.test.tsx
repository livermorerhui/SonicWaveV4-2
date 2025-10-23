import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '@/viewmodels/AuthProvider';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
  it('renders login form', () => {
    const mockAuth = {
      user: null,
      token: null,
      isLoading: false,
      isBootstrapping: false,
      error: null,
      login: vi.fn(),
      logout: vi.fn()
    };

    render(
      <AuthContext.Provider value={mockAuth}>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByText('SonicWave 管理后台')).toBeInTheDocument();
    expect(screen.getByLabelText('邮箱')).toBeInTheDocument();
    expect(screen.getByLabelText('密码')).toBeInTheDocument();
  });
});
