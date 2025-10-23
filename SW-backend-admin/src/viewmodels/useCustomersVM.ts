import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, fetchCustomers } from '@/services/api';
import { useAuth } from '@/viewmodels/AuthProvider';
import type { CustomerDTO } from '@/models/CustomerDTO';

const DEFAULT_PAGE_SIZE = 20;

export const useCustomersVM = () => {
  const { token, logout } = useAuth();
  const [items, setItems] = useState<CustomerDTO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [keyword, setKeyword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sortBy, setSortBy] = useState<'createdAt' | 'name' | 'email'>('createdAt');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchCustomers({ page, pageSize, keyword, sortBy, sortOrder }, token);
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        logout();
        return;
      }
      setError(err instanceof Error ? err.message : '加载客户失败');
    } finally {
      setIsLoading(false);
    }
  }, [token, page, pageSize, keyword, sortBy, sortOrder, logout]);

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

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const updateSortBy = useCallback((value: 'createdAt' | 'name' | 'email') => {
    setSortBy(value);
    setPage(1);
  }, []);

  const updateSortOrder = useCallback((value: 'asc' | 'desc') => {
    setSortOrder(value);
    setPage(1);
  }, []);

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
      sortBy,
      updateSortBy,
      sortOrder,
      updateSortOrder,
      isLoading,
      error,
      refresh: load
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
      sortBy,
      updateSortBy,
      sortOrder,
      updateSortOrder,
      isLoading,
      error,
      load
    ]
  );
};
