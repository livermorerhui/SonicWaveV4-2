import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, fetchCustomers, fetchCustomerDetail, patchCustomer } from '@/services/api';
import { useAuth } from '@/viewmodels/AuthProvider';
import type { CustomerDTO, CustomerDetail } from '@/models/CustomerDTO';

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

  const [selectedCustomer, setSelectedCustomer] = useState<CustomerDTO | null>(null);
  const [selectedCustomerDetail, setSelectedCustomerDetail] = useState<CustomerDetail | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [drawerError, setDrawerError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

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

  const loadDetail = useCallback(
    async (customerId: number) => {
      if (!token) return;
      setIsDetailLoading(true);
      try {
        const detail = await fetchCustomerDetail(customerId, token);
        setSelectedCustomerDetail(detail);
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '加载客户详情失败');
      } finally {
        setIsDetailLoading(false);
      }
    },
    [token, logout]
  );

  const openDrawer = useCallback(
    (customer: CustomerDTO) => {
      setSelectedCustomer(customer);
      setDrawerError(null);
      setIsDrawerOpen(true);
      setSelectedCustomerDetail(null);
      loadDetail(customer.id);
    },
    [loadDetail]
  );

  const closeDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setSelectedCustomer(null);
    setSelectedCustomerDetail(null);
    setDrawerError(null);
  }, []);

  const saveCustomer = useCallback(
    async (payload: Partial<CustomerDetail>) => {
      if (!token || !selectedCustomer) {
        return;
      }
      setIsSaving(true);
      setDrawerError(null);
      try {
        const response = await patchCustomer(selectedCustomer.id, payload, token);
        setSelectedCustomerDetail(response.customer);
        await load();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          logout();
          return;
        }
        setDrawerError(err instanceof Error ? err.message : '更新客户信息失败');
      } finally {
        setIsSaving(false);
      }
    },
    [token, selectedCustomer, load, logout]
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
      sortBy,
      updateSortBy,
      sortOrder,
      updateSortOrder,
      isLoading,
      error,
      refresh: load,
      selectedCustomer,
      selectedCustomerDetail,
      isDrawerOpen,
      openDrawer,
      closeDrawer,
      saveCustomer,
      drawerError,
      isDetailLoading,
      isSaving
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
      load,
      selectedCustomer,
      selectedCustomerDetail,
      isDrawerOpen,
      openDrawer,
      closeDrawer,
      saveCustomer,
      drawerError,
      isDetailLoading,
      isSaving
    ]
  );
};
