// file: apps/admin-web/src/views/MusicAdminPage.tsx

import { useState } from 'react';
import { useMusicAdminVM } from '@/viewmodels/useMusicAdminVM';
import './MusicAdminPage.css';

export const MusicAdminPage = () => {
  const {
    categories,
    tracks,
    selectedCategoryId,
    isLoading,
    categoryLoading,
    trackLoading,
    error,
    visibleTracks,
    categoryCounts,
    uncategorizedCount,
    reloadAll,
    setSelectedCategoryId,
    createCategory,
    renameCategory,
    deleteCategory,
    changeTrackCategory,
    uploadMusic,
    deleteTrack,
    renameTrack
  } = useMusicAdminVM();

  const [newCategoryName, setNewCategoryName] = useState('');
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [editingCategoryName, setEditingCategoryName] = useState('');
  const [showUploadForm, setShowUploadForm] = useState(false);
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadArtist, setUploadArtist] = useState('');
  const [uploadCategoryId, setUploadCategoryId] = useState<string>('');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [editingTrackId, setEditingTrackId] = useState<number | null>(null);
  const [editingTrackTitle, setEditingTrackTitle] = useState('');
  const [editingTrackArtist, setEditingTrackArtist] = useState('');

  const handleNewCategorySubmit = (event: React.FormEvent) => {
    event.preventDefault();
    const value = newCategoryName.trim();
    if (!value) return;
    void createCategory(value).catch(() => {});
    setNewCategoryName('');
  };

  const handleStartEditCategory = (id: number, name: string) => {
    setEditingCategoryId(id);
    setEditingCategoryName(name);
  };

  const handleCommitEditCategory = () => {
    if (editingCategoryId == null) return;
    const value = editingCategoryName.trim();
    if (!value) {
      setEditingCategoryId(null);
      setEditingCategoryName('');
      return;
    }
    void renameCategory(editingCategoryId, value).catch(() => {});
    setEditingCategoryId(null);
    setEditingCategoryName('');
  };

  const handleDeleteCategoryClick = (id: number) => {
    const confirmed = window.confirm('确定要删除这个分类吗？该分类下的音乐将变为“未分类”。');
    if (!confirmed) return;
    void deleteCategory(id).catch(() => {});
  };

  const handleTrackCategoryChange = (trackId: number, value: string) => {
    const categoryId = value === '' ? null : Number(value);
    if (categoryId !== null && Number.isNaN(categoryId)) {
      return;
    }
    void changeTrackCategory(trackId, categoryId).catch(() => {});
  };

  const handleToggleUploadForm = () => {
    setShowUploadForm(prev => !prev);
  };

  const handleUploadFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setUploadFile(file);
  };

  const handleUploadSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!uploadTitle.trim() || !uploadFile) {
      return;
    }
    const categoryId =
      uploadCategoryId === '' ? undefined : Number.parseInt(uploadCategoryId, 10);

    void uploadMusic({
      title: uploadTitle.trim(),
      artist: uploadArtist.trim() || undefined,
      categoryId: Number.isNaN(categoryId as number) ? undefined : categoryId,
      file: uploadFile
    }).catch(() => {});

    setUploadTitle('');
    setUploadArtist('');
    setUploadCategoryId('');
    setUploadFile(null);
  };

  const handleDeleteTrackClick = (trackId: number) => {
    const confirmed = window.confirm('确定要删除这首音乐吗？删除后无法恢复。');
    if (!confirmed) return;
    void deleteTrack(trackId).catch(() => {});
  };

  const handleStartEditTrack = (track: { id: number; title: string; artist: string }) => {
    setEditingTrackId(track.id);
    setEditingTrackTitle(track.title || '');
    setEditingTrackArtist(track.artist || '');
  };

  const handleCancelEditTrack = () => {
    setEditingTrackId(null);
    setEditingTrackTitle('');
    setEditingTrackArtist('');
  };

  const handleSaveEditTrack = () => {
    if (editingTrackId == null) return;
    void renameTrack({
      id: editingTrackId,
      title: editingTrackTitle,
      artist: editingTrackArtist
    }).catch(() => {});
    handleCancelEditTrack();
  };

  const isBusy = isLoading || categoryLoading || trackLoading;

  return (
    <div className="music-admin-root">
      <div className="music-admin-header">
        <div>
          <h1 className="music-admin-title">音乐管理</h1>
          <p className="music-admin-subtitle">管理云端音乐分类与曲目</p>
        </div>
        <div className="music-admin-header-actions">
          <button type="button" onClick={() => reloadAll()} disabled={isBusy}>
            刷新
          </button>
          <button type="button" onClick={handleToggleUploadForm} disabled={isBusy}>
            {showUploadForm ? '收起上传' : '上传音乐'}
          </button>
        </div>
      </div>

      {error && <div className="music-admin-error">错误：{error}</div>}

      {isLoading ? (
        <div className="music-admin-loading">正在加载音乐数据...</div>
      ) : (
        <div className="music-admin-body">
          <section className="music-admin-column music-admin-categories">
            <h2>分类</h2>
            <div className="music-admin-category-list">
              <button
                type="button"
                className={
                  selectedCategoryId === 'all'
                    ? 'music-admin-category-item music-admin-category-item--active'
                    : 'music-admin-category-item'
                }
                onClick={() => setSelectedCategoryId('all')}
              >
                <span>全部</span>
                <span className="music-admin-category-count">{tracks.length}</span>
              </button>

              <button
                type="button"
                className={
                  selectedCategoryId === null
                    ? 'music-admin-category-item music-admin-category-item--active'
                    : 'music-admin-category-item'
                }
                onClick={() => setSelectedCategoryId(null)}
              >
                <span>未分类</span>
                <span className="music-admin-category-count">{uncategorizedCount}</span>
              </button>

              {categories.map(category => {
                const isActive = selectedCategoryId === category.id;
                const count = categoryCounts[category.id] ?? 0;
                const isEditing = editingCategoryId === category.id;

                return (
                  <div
                    key={category.id}
                    className={
                      isActive
                        ? 'music-admin-category-item music-admin-category-item--active'
                        : 'music-admin-category-item'
                    }
                  >
                    {isEditing ? (
                      <input
                        className="music-admin-category-edit-input"
                        value={editingCategoryName}
                        onChange={e => setEditingCategoryName(e.target.value)}
                        onBlur={handleCommitEditCategory}
                        onKeyDown={e => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            handleCommitEditCategory();
                          }
                          if (e.key === 'Escape') {
                            setEditingCategoryId(null);
                            setEditingCategoryName('');
                          }
                        }}
                        autoFocus
                      />
                    ) : (
                      <>
                        <button
                          type="button"
                          className="music-admin-category-main"
                          onClick={() => setSelectedCategoryId(category.id)}
                        >
                          <span className="music-admin-category-name">{category.name}</span>
                          <span className="music-admin-category-count">{count}</span>
                        </button>
                        <div className="music-admin-category-actions">
                          <button
                            type="button"
                            onClick={() => handleStartEditCategory(category.id, category.name)}
                            disabled={isBusy}
                          >
                            重命名
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteCategoryClick(category.id)}
                            disabled={isBusy}
                          >
                            删除
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                );
              })}
            </div>

            <form className="music-admin-new-category" onSubmit={handleNewCategorySubmit}>
              <input
                type="text"
                placeholder="新建分类名称"
                value={newCategoryName}
                onChange={e => setNewCategoryName(e.target.value)}
                disabled={isBusy}
              />
              <button type="submit" disabled={isBusy || !newCategoryName.trim()}>
                添加
              </button>
            </form>
          </section>

          <section className="music-admin-column music-admin-tracks">
            <h2>曲目列表</h2>

            {showUploadForm && (
              <form className="music-admin-upload-form" onSubmit={handleUploadSubmit}>
                <div className="music-admin-upload-row">
                  <label>
                    标题
                    <input
                      type="text"
                      value={uploadTitle}
                      onChange={e => setUploadTitle(e.target.value)}
                      required
                      disabled={isBusy}
                    />
                  </label>
                  <label>
                    艺术家
                    <input
                      type="text"
                      value={uploadArtist}
                      onChange={e => setUploadArtist(e.target.value)}
                      disabled={isBusy}
                    />
                  </label>
                </div>
                <div className="music-admin-upload-row">
                  <label>
                    分类
                    <select
                      value={uploadCategoryId}
                      onChange={e => setUploadCategoryId(e.target.value)}
                      disabled={isBusy}
                    >
                      <option value="">未分类</option>
                      {categories.map(category => (
                        <option key={category.id} value={String(category.id)}>
                          {category.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    音乐文件
                    <input
                      type="file"
                      accept="audio/*"
                      onChange={handleUploadFileChange}
                      disabled={isBusy}
                      required
                    />
                  </label>
                </div>
                <div className="music-admin-upload-actions">
                  <button
                    type="submit"
                    disabled={isBusy || !uploadTitle.trim() || !uploadFile}
                  >
                    提交上传
                  </button>
                </div>
              </form>
            )}
            {visibleTracks.length === 0 ? (
              <div className="music-admin-empty">当前筛选下暂无曲目</div>
            ) : (
              <table className="music-admin-table">
                <thead>
                  <tr>
                    <th>标题</th>
                    <th>艺术家</th>
                    <th>分类</th>
                    <th>上传时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleTracks.map(track => (
                    <tr key={track.id}>
                      <td>
                        {editingTrackId === track.id ? (
                          <input
                            className="music-admin-track-edit-input"
                            value={editingTrackTitle}
                            onChange={e => setEditingTrackTitle(e.target.value)}
                            disabled={isBusy}
                          />
                        ) : (
                          track.title
                        )}
                      </td>
                      <td>
                        {editingTrackId === track.id ? (
                          <input
                            className="music-admin-track-edit-input"
                            value={editingTrackArtist}
                            onChange={e => setEditingTrackArtist(e.target.value)}
                            disabled={isBusy}
                          />
                        ) : (
                          track.artist
                        )}
                      </td>
                      <td>
                        {track.categoryId == null
                          ? '未分类'
                          : categories.find(c => c.id === track.categoryId)?.name ?? '未知分类'}
                      </td>
                      <td>
                        {track.createdAt
                          ? new Intl.DateTimeFormat('zh-CN', {
                              dateStyle: 'medium',
                              timeStyle: 'short'
                            }).format(new Date(track.createdAt))
                          : '-'}
                      </td>
                      <td>
                        <div className="music-admin-track-actions">
                          <select
                            value={track.categoryId == null ? '' : String(track.categoryId)}
                            onChange={e => handleTrackCategoryChange(track.id, e.target.value)}
                            disabled={isBusy}
                          >
                            <option value="">未分类</option>
                            {categories.map(category => (
                              <option key={category.id} value={category.id}>
                                {category.name}
                              </option>
                            ))}
                          </select>
                          {editingTrackId === track.id ? (
                            <>
                              <button
                                type="button"
                                onClick={handleSaveEditTrack}
                                disabled={isBusy || !editingTrackTitle.trim()}
                              >
                                保存
                              </button>
                              <button
                                type="button"
                                onClick={handleCancelEditTrack}
                                disabled={isBusy}
                              >
                                取消
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleStartEditTrack(track)}
                              disabled={isBusy}
                            >
                              编辑
                            </button>
                          )}

                          <button
                            type="button"
                            onClick={() => handleDeleteTrackClick(track.id)}
                            disabled={isBusy}
                          >
                            删除
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      )}
    </div>
  );
};
