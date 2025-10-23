import { useAuth } from '@/viewmodels/AuthProvider';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  fetchUsers,
  fetchUserDetail,
  patchUserRole,
  patchUserAccountType,
  patchUserPassword,
  deleteUser as deleteUserRequest
} from '@/services/api';
import type { AccountType, UserDTO, UserRole, UserDetail } from '@/models/UserDTO';

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = 'createdAt';

export const useUsersVM = () => {
  const { token, logout } = useAuth();
  const [items, setItems] = useState<UserDTO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState<'all' | UserRole>('all');
  const [accountTypeFilter, setAccountTypeFilter] = useState<'all' | AccountType>('all');
  const [sortBy, setSortBy] = useState<'createdAt' | 'email' | 'username' | 'role'>(DEFAULT_SORT);
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedUser, setSelectedUser] = useState<UserDTO | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [drawerError, setDrawerError] = useState<string | null>(null);
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchUsers(
        {
          page,
          pageSize,
          keyword,
          role: roleFilter,
          accountType: accountTypeFilter,
          sortBy,
          sortOrder
        },
        token
      );
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        logout();
        return;
      }
      setError(err instanceof Error ? err.message : '加载用户失败');
    } finally {
      setIsLoading(false);
    }
  }, [token, page, pageSize, keyword, roleFilter, accountTypeFilter, sortBy, sortOrder, logout]);

  useEffect(() => {
    load();
  }, [load]);

  const applySearch = useCallback(() => {
    setPage(1);
    setKeyword(searchTerm.trim());
  }, [searchTerm]);

  const resetSearch = useCallback(() => {
    setSearchTerm('');
    setKeyword('');
    setPage(1);
  }, []);

  const updateRoleFilter = useCallback((value: 'all' | UserRole) => {
    setRoleFilter(value);
    setPage(1);
  }, []);

  const updateAccountTypeFilter = useCallback((value: 'all' | AccountType) => {
    setAccountTypeFilter(value);
    setPage(1);
  }, []);

  const updateSortBy = useCallback((value: 'createdAt' | 'email' | 'username' | 'role') => {
    setSortBy(value);
    setPage(1);
  }, []);

  const updateSortOrder = useCallback((value: 'asc' | 'desc') => {
    setSortOrder(value);
    setPage(1);
  }, []);

  const loadDetail = useCallback(async (userId: number) => {
    if (!token) return;
    setIsDetailLoading(true);
    try {
      const detail = await fetchUserDetail(userId, token);
      setSelectedUserDetail(detail);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        logout();
        return;
      }
      setDrawerError(err instanceof Error ? err.message : '加载用户详情失败');
    } finally {
      setIsDetailLoading(false);
    }
  }, [token, logout]);

  const openDrawer = useCallback((user: UserDTO) => {
    setSelectedUser(user);
    setDrawerError(null);
    setIsDrawerOpen(true);
    setSelectedUserDetail(null);
    if (user?.id) {
      loadDetail(user.id);
    }
  }, [loadDetail]);

  const closeDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setSelectedUser(null);
    setDrawerError(null);
  }, []);

  const handleRoleChange = useCallback(
    async (nextRole: UserRole) => {
      if (!token || !selectedUser) {
        return;
      }
      setIsActionLoading(true);
      setDrawerError(null);
      try {
        const response = await patchUserRole(selectedUser.id, nextRole, token);
        setSelectedUser(response.user);
        await load();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '更新用户角色失败');
      } finally {
        setIsActionLoading(false);
      }
    },
    [token, selectedUser, load, logout]
  );

  const handleAccountTypeChange = useCallback(
    async (nextAccountType: AccountType) => {
      if (!token || !selectedUser) {
        return;
      }
      setIsActionLoading(true);
      setDrawerError(null);
      try {
        const response = await patchUserAccountType(selectedUser.id, nextAccountType, token);
        setSelectedUser(response.user);
        await load();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '更新账号类型失败');
      } finally {
        setIsActionLoading(false);
      }
    },
    [token, selectedUser, load, logout]
  );

  const deleteUser = useCallback(
    async (force: boolean) => {
      if (!token || !selectedUser) {
        return;
      }
      setIsActionLoading(true);
      setDrawerError(null);
      try {
        await deleteUserRequest(selectedUser.id, force, token);
        closeDrawer();
        await load();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '删除用户失败');
      } finally {
        setIsActionLoading(false);
      }
    },
    [token, selectedUser, closeDrawer, load, logout]
  );

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const resetPassword = useCallback(
    async (nextPassword: string) => {
      if (!token || !selectedUser) {
        return;
      }
      setIsActionLoading(true);
      setDrawerError(null);
      try {
        await patchUserPassword(selectedUser.id, { password: nextPassword }, token);
        await loadDetail(selectedUser.id);
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '重置密码失败');
      } finally {
        setIsActionLoading(false);
      }
    },
    [token, selectedUser, loadDetail, logout]
  );

  return useMemo(
    () => ({
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
      refresh: load,
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
    }),
    [
      items,
      page,
      pageSize,
      total,
      totalPages,
      searchTerm,
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
      load,
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
    ]
  );
};
