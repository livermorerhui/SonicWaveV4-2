import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { UserActionDrawer } from './UserActionDrawer';

describe('UserActionDrawer', () => {
  const baseUser = {
    id: 1,
    email: 'demo@example.com',
    role: 'user' as const,
    accountType: 'normal' as const,
    createdAt: '2025-01-01T00:00:00.000Z',
    updatedAt: '2025-01-01T00:00:00.000Z',
    deletedAt: null
  };

  it('renders details when open', () => {
    render(
      <UserActionDrawer
        open
        user={baseUser}
        onClose={vi.fn()}
        onChangeRole={vi.fn()}
        onChangeAccountType={vi.fn()}
        onSoftDelete={vi.fn()}
        onHardDelete={vi.fn()}
        isProcessing={false}
        error={null}
      />
    );

    expect(screen.getByText('用户设置')).toBeInTheDocument();
    expect(screen.getByDisplayValue('普通用户')).toBeInTheDocument();
  });

  it('invokes callbacks', () => {
    const onClose = vi.fn();
    render(
      <UserActionDrawer
        open
        user={baseUser}
        onClose={onClose}
        onChangeRole={vi.fn()}
        onChangeAccountType={vi.fn()}
        onSoftDelete={vi.fn()}
        onHardDelete={vi.fn()}
        isProcessing={false}
        error={null}
      />
    );

    fireEvent.click(screen.getByLabelText('关闭'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
