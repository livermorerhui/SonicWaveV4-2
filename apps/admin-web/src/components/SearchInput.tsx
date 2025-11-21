import { FormEvent } from 'react';
import './SearchInput.css';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onReset?: () => void;
  placeholder?: string;
}

export const SearchInput = ({ value, onChange, onSubmit, onReset, placeholder = '搜索' }: SearchInputProps) => {
  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form className="search-root" onSubmit={handleSubmit}>
      <input
        className="search-input"
        value={value}
        onChange={event => onChange(event.target.value)}
        placeholder={placeholder}
      />
      <div className="search-actions">
        <button type="submit" className="search-button">
          搜索
        </button>
        {onReset && (
          <button type="button" className="search-reset" onClick={onReset}>
            重置
          </button>
        )}
      </div>
    </form>
  );
};
