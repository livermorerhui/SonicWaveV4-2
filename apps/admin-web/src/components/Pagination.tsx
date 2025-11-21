import './Pagination.css';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export const Pagination = ({ page, totalPages, onPageChange }: PaginationProps) => {
  const canPrev = page > 1;
  const canNext = page < totalPages;

  return (
    <div className="pagination-root">
      <button
        className="pagination-button"
        onClick={() => canPrev && onPageChange(page - 1)}
        disabled={!canPrev}
      >
        上一页
      </button>
      <span className="pagination-info">
        第 {page} / {totalPages} 页
      </span>
      <button
        className="pagination-button"
        onClick={() => canNext && onPageChange(page + 1)}
        disabled={!canNext}
      >
        下一页
      </button>
    </div>
  );
};
