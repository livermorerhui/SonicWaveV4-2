import './Tag.css';

interface TagProps {
  label: string;
  tone?: 'info' | 'success' | 'warning' | 'danger';
}

const toneToClass = {
  info: 'tag-info',
  success: 'tag-success',
  warning: 'tag-warning',
  danger: 'tag-danger'
} as const;

export const Tag = ({ label, tone = 'info' }: TagProps) => {
  return <span className={`tag-root ${toneToClass[tone]}`}>{label}</span>;
};
