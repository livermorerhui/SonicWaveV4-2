import { useMemo } from 'react';
import { useCustomersVM } from '@/viewmodels/useCustomersVM';
import { SearchInput } from '@/components/SearchInput';
import { Table, type TableColumn } from '@/components/Table';
import { Pagination } from '@/components/Pagination';
import type { CustomerDTO } from '@/models/CustomerDTO';
import './CustomersPage.css';

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

export const CustomersPage = () => {
  const {
    items,
    searchTerm,
    setSearchTerm,
    applySearch,
    resetSearch,
    isLoading,
    error,
    page,
    totalPages,
    setPage,
    total,
    sortBy,
    updateSortBy,
    sortOrder,
    updateSortOrder,
    refresh
  } = useCustomersVM();

  const columns: TableColumn<CustomerDTO>[] = useMemo(
    () => [
      { key: 'name', header: '姓名', accessor: 'name' },
      { key: 'email', header: '邮箱', accessor: 'email' },
      { key: 'phone', header: '电话', render: item => item.phone || '—' },
      { key: 'createdAt', header: '创建时间', render: item => formatDate(item.createdAt) }
    ],
    []
  );

  return (
    <div className="customers-root card">
      <div className="customers-header">
        <div>
          <h2>客户管理</h2>
          <p>查看并检索所有与 SonicWave 业务关联的客户档案。</p>
        </div>
        <SearchInput
          value={searchTerm}
          onChange={setSearchTerm}
          onSubmit={applySearch}
          onReset={resetSearch}
          placeholder="请输入姓名、邮箱或电话"
        />
      </div>

      <div className="customers-toolbar">
        <label>
          排序字段
          <select value={sortBy} onChange={event => updateSortBy(event.target.value as typeof sortBy)}>
            <option value="createdAt">创建时间</option>
            <option value="name">姓名</option>
            <option value="email">邮箱</option>
          </select>
        </label>
        <button
          type="button"
          className="customers-sort-order"
          onClick={() => updateSortOrder(sortOrder === 'desc' ? 'asc' : 'desc')}
        >
          {sortOrder === 'desc' ? '降序' : '升序'}
        </button>
        <button type="button" className="customers-refresh" onClick={refresh} disabled={isLoading}>
          刷新
        </button>
      </div>

      {error && <div className="customers-error">{error}</div>}
      <Table columns={columns} data={items} isLoading={isLoading} emptyMessage="暂无符合条件的客户" />
      <div className="customers-footer">
        <span className="customers-meta">共 {total} 位客户</span>
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>
    </div>
  );
};
