import { useMemo } from 'react';
import { useUsersVM } from '@/viewmodels/useUsersVM';
import { SearchInput } from '@/components/SearchInput';
import { Table, type TableColumn } from '@/components/Table';
import { Pagination } from '@/components/Pagination';
import { Tag } from '@/components/Tag';
import type { UserDTO } from '@/models/UserDTO';
import { UserActionDrawer } from './UserActionDrawer';
import './UsersPage.css';

const formatDate = (value: string) => {
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  } catch {
    return value;
  }
};

const roleLabel: Record<UserDTO['role'], string> = {
  admin: '管理员',
  user: '普通用户'
};

const accountTypeLabel: Record<UserDTO['accountType'], string> = {
  normal: '正式账号',
  test: '测试账号'
};

const accountTypeTone: Record<UserDTO['accountType'], 'info' | 'warning'> = {
  normal: 'info',
  test: 'warning'
};

export const UsersPage = () => {
  const {
    items,
    page,
    pageSize,
    total,
    totalPages,
    searchTerm,
    setSearchTerm,
    applySearch,
    resetSearch,
    setPage,
    roleFilter,
    updateRoleFilter,
    accountTypeFilter,
    updateAccountTypeFilter,
    sortBy,
    updateSortBy,
    sortOrder,
    updateSortOrder,
    isLoading,
    error,
    refresh,
    selectedUser,
    isDrawerOpen,
    openDrawer,
    closeDrawer,
    handleRoleChange,
    handleAccountTypeChange,
    deleteUser,
    resetPassword,
    drawerError,
    isActionLoading,
    isDetailLoading,
    selectedUserDetail
  } = useUsersVM();

  const handleSoftDelete = () => {
    if (!selectedUser) return;
    const confirmed = window.confirm(`确认将 ${selectedUser.email} 标记为已删除？`);
    if (!confirmed) return;
    deleteUser(false);
  };

  const handleHardDelete = () => {
    if (!selectedUser) return;
    const first = window.confirm('该操作会永久删除用户，是否继续？');
    if (!first) return;
    const second = window.confirm('再次确认：删除后无法恢复，确定继续？');
    if (!second) return;
    deleteUser(true);
  };

  const columns: TableColumn<UserDTO>[] = useMemo(
    () => [
      {
        key: 'email',
        header: '邮箱',
        accessor: 'email'
      },
      {
        key: 'username',
        header: '用户名',
        render: item => item.username || '—'
      },
      {
        key: 'role',
        header: '角色',
        render: item => <Tag tone={item.role === 'admin' ? 'success' : 'info'} label={roleLabel[item.role]} />
      },
      {
        key: 'accountType',
        header: '账号类型',
        render: item => <Tag tone={accountTypeTone[item.accountType]} label={accountTypeLabel[item.accountType]} />
      },
      {
        key: 'status',
        header: '状态',
        render: item =>
          item.deletedAt ? <Tag tone="danger" label="已删除" /> : <Tag tone="success" label="正常" />
      },
      {
        key: 'createdAt',
        header: '创建时间',
        render: item => formatDate(item.createdAt)
      },
      {
        key: 'actions',
        header: '操作',
        render: item => (
          <button type="button" className="users-action" onClick={() => openDrawer(item)}>
            管理
          </button>
        )
      }
    ],
    [openDrawer]
  );

  return (
    <div className="users-root card">
      <div className="users-header">
        <div>
          <h2>用户管理</h2>
          <p>按条件检索 SonicWave 系统中的全部用户，并执行管理操作。</p>
        </div>
        <SearchInput
          value={searchTerm}
          onChange={setSearchTerm}
          onSubmit={applySearch}
          onReset={resetSearch}
          placeholder="请输入邮箱或用户名"
        />
      </div>

      <div className="users-toolbar">
        <label>
          角色
          <select value={roleFilter} onChange={event => updateRoleFilter(event.target.value as typeof roleFilter)}>
            <option value="all">全部</option>
            <option value="admin">管理员</option>
            <option value="user">普通用户</option>
          </select>
        </label>
        <label>
          账号类型
          <select
            value={accountTypeFilter}
            onChange={event => updateAccountTypeFilter(event.target.value as typeof accountTypeFilter)}
          >
            <option value="all">全部</option>
            <option value="normal">正式账号</option>
            <option value="test">测试账号</option>
          </select>
        </label>
        <label>
          排序字段
          <select value={sortBy} onChange={event => updateSortBy(event.target.value as typeof sortBy)}>
            <option value="createdAt">创建时间</option>
            <option value="email">邮箱</option>
            <option value="username">用户名</option>
            <option value="role">角色</option>
          </select>
        </label>
        <button
          type="button"
          className="users-sort-order"
          onClick={() => updateSortOrder(sortOrder === 'desc' ? 'asc' : 'desc')}
        >
          {sortOrder === 'desc' ? '降序' : '升序'}
        </button>
        <button type="button" className="users-refresh" onClick={refresh} disabled={isLoading}>
          刷新
        </button>
      </div>

      {error && <div className="users-error">{error}</div>}
      <Table columns={columns} data={items} isLoading={isLoading} emptyMessage="暂无符合条件的用户" />
      <div className="users-footer">
        <span className="users-meta">共 {total} 名用户</span>
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      <UserActionDrawer
        open={isDrawerOpen}
        user={selectedUser}
        detail={selectedUserDetail}
        onClose={closeDrawer}
        onChangeRole={handleRoleChange}
        onChangeAccountType={handleAccountTypeChange}
        onSoftDelete={handleSoftDelete}
        onHardDelete={handleHardDelete}
        isProcessing={isActionLoading}
        isDetailLoading={isDetailLoading}
        onResetPassword={resetPassword}
        error={drawerError}
      />
    </div>
  );
};
