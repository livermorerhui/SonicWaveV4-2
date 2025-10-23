import type { ReactNode } from 'react';
import './Table.css';

export interface TableColumn<T> {
  key: string;
  header: string;
  render?: (item: T) => ReactNode;
  accessor?: keyof T;
}

interface TableProps<T> {
  columns: TableColumn<T>[];
  data: T[];
  isLoading?: boolean;
  emptyMessage?: string;
}

export function Table<T extends Record<string, unknown>>({
  columns,
  data,
  isLoading = false,
  emptyMessage = '暂无数据'
}: TableProps<T>) {
  const columnSignature = columns.map(column => column.key).join('-');

  return (
    <div className="table-container">
      <table className="table-root">
        <thead>
          <tr>
            {columns.map(column => (
              <th key={column.key}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                加载中…
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((item, index) => {
              const record = item as Record<string, unknown>;
              const rowKey =
                record.id !== undefined && record.id !== null ? String(record.id) : `${columnSignature}-${index}`;
              return (
                <tr key={rowKey}>
                  {columns.map(column => (
                    <td key={`${column.key}-${index}`}>
                      {column.render
                        ? column.render(item)
                        : column.accessor
                          ? (item[column.accessor as keyof T] as ReactNode)
                          : null}
                    </td>
                  ))}
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
