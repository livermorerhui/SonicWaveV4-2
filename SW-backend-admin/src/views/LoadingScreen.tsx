import './LoadingScreen.css';

interface LoadingScreenProps {
  message?: string;
}

export const LoadingScreen = ({ message = '加载中...' }: LoadingScreenProps) => {
  return (
    <div className="loading-shell">
      <div className="loading-card">
        <div className="loading-spinner" />
        <span>{message}</span>
      </div>
    </div>
  );
};
