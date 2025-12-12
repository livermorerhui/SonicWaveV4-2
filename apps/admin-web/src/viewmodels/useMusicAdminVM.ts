// file: apps/admin-web/src/viewmodels/useMusicAdminVM.ts

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/viewmodels/AuthProvider';
import {
  fetchMusicCategories,
  fetchMusicTracks,
  createMusicCategory,
  updateMusicCategory,
  deleteMusicCategory,
  updateMusicTrackCategory,
  uploadMusicTrack,
  deleteMusicTrack,
  updateMusicTrackMetadata
} from '@/services/api';
import type { MusicCategoryDTO, MusicTrackDTO } from '@/models/Music';

type CategoryFilter = 'all' | null | number;

interface MusicAdminVM {
  categories: MusicCategoryDTO[];
  tracks: MusicTrackDTO[];
  selectedCategoryId: CategoryFilter;
  isLoading: boolean;
  categoryLoading: boolean;
  trackLoading: boolean;
  error: string | null;
  visibleTracks: MusicTrackDTO[];
  categoryCounts: Record<number, number>;
  uncategorizedCount: number;
  reloadAll: () => Promise<void>;
  setSelectedCategoryId: (id: CategoryFilter) => void;
  createCategory: (name: string) => Promise<void>;
  renameCategory: (id: number, name: string) => Promise<void>;
  deleteCategory: (id: number) => Promise<void>;
  changeTrackCategory: (trackId: number, categoryId: number | null) => Promise<void>;
  uploadMusic: (params: {
    title: string;
    artist?: string;
    categoryId?: number | null;
    file: File;
  }) => Promise<void>;
  deleteTrack: (trackId: number) => Promise<void>;
  renameTrack: (params: {
    id: number;
    title: string;
    artist: string;
  }) => Promise<void>;
}

export const useMusicAdminVM = (): MusicAdminVM => {
  const { token } = useAuth();
  const [categories, setCategories] = useState<MusicCategoryDTO[]>([]);
  const [tracks, setTracks] = useState<MusicTrackDTO[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<CategoryFilter>('all');
  const [isLoading, setIsLoading] = useState(false);
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [trackLoading, setTrackLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const safeToken = token ?? null;

  const reloadAll = useCallback(async () => {
    if (!safeToken) {
      // No token yet (still bootstrapping); do not treat as error to avoid crashing.
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const [catRes, trackRes] = await Promise.all([
        fetchMusicCategories(safeToken),
        fetchMusicTracks(safeToken)
      ]);

      setCategories(catRes.categories ?? []);
      setTracks(trackRes.tracks ?? []);
    } catch (err) {
      const message = err instanceof Error ? err.message : '加载音乐数据失败';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [safeToken]);

  useEffect(() => {
    // When token becomes available, try to load.
    void reloadAll();
  }, [reloadAll]);

  const categoryCounts = useMemo(() => {
    const counts: Record<number, number> = {};
    for (const track of tracks) {
      const cid = track.categoryId;
      if (cid == null) continue;
      counts[cid] = (counts[cid] ?? 0) + 1;
    }
    return counts;
  }, [tracks]);

  const uncategorizedCount = useMemo(
    () => tracks.filter(track => track.categoryId == null).length,
    [tracks]
  );

  const visibleTracks = useMemo(() => {
    if (selectedCategoryId === 'all') {
      return tracks;
    }
    if (selectedCategoryId === null) {
      return tracks.filter(track => track.categoryId == null);
    }
    return tracks.filter(track => track.categoryId === selectedCategoryId);
  }, [tracks, selectedCategoryId]);

  const createCategory = useCallback(
    async (name: string) => {
      if (!safeToken) return;
      const trimmed = name.trim();
      if (!trimmed) return;

      setCategoryLoading(true);
      setError(null);
      try {
        const res = await createMusicCategory({ name: trimmed }, safeToken);
        setCategories(prev => [...prev, res.category]);
      } catch (err) {
        const message = err instanceof Error ? err.message : '创建分类失败';
        setError(message);
        throw err;
      } finally {
        setCategoryLoading(false);
      }
    },
    [safeToken]
  );

  const renameCategory = useCallback(
    async (id: number, name: string) => {
      if (!safeToken) return;
      const trimmed = name.trim();
      if (!trimmed) return;

      setCategoryLoading(true);
      setError(null);
      try {
        const res = await updateMusicCategory(id, { name: trimmed }, safeToken);
        setCategories(prev => prev.map(c => (c.id === id ? res.category : c)));
      } catch (err) {
        const message = err instanceof Error ? err.message : '重命名分类失败';
        setError(message);
        throw err;
      } finally {
        setCategoryLoading(false);
      }
    },
    [safeToken]
  );

  const deleteCategory = useCallback(
    async (id: number) => {
      if (!safeToken) return;

      setCategoryLoading(true);
      setError(null);
      try {
        await deleteMusicCategory(id, safeToken);
        setCategories(prev => prev.filter(c => c.id !== id));
        // Locally mark all tracks in that category as uncategorized
        setTracks(prev =>
          prev.map(track =>
            track.categoryId === id ? { ...track, categoryId: null, categoryName: null } : track
          )
        );
      } catch (err) {
        const message = err instanceof Error ? err.message : '删除分类失败';
        setError(message);
        throw err;
      } finally {
        setCategoryLoading(false);
      }
    },
    [safeToken]
  );

  const changeTrackCategory = useCallback(
    async (trackId: number, categoryId: number | null) => {
      if (!safeToken) return;

      setTrackLoading(true);
      setError(null);
      try {
        const res = await updateMusicTrackCategory(trackId, { categoryId }, safeToken);
        const updated = res.track;
        setTracks(prev =>
          prev.map(track => (track.id === updated.id ? { ...track, ...updated } : track))
        );
      } catch (err) {
        const message = err instanceof Error ? err.message : '更新曲目分类失败';
        setError(message);
        throw err;
      } finally {
        setTrackLoading(false);
      }
    },
    [safeToken]
  );

  const uploadMusic = useCallback(
    async (params: { title: string; artist?: string; categoryId?: number | null; file: File }) => {
      if (!safeToken) return;

      setTrackLoading(true);
      setError(null);
      try {
        await uploadMusicTrack(
          {
            title: params.title,
            artist: params.artist,
            categoryId: params.categoryId,
            file: params.file
          },
          safeToken
        );
        await reloadAll();
      } catch (err) {
        const message = err instanceof Error ? err.message : '上传音乐失败';
        setError(message);
        throw err;
      } finally {
        setTrackLoading(false);
      }
    },
    [safeToken, reloadAll]
  );

  const renameTrack = useCallback(
    async (params: { id: number; title: string; artist: string }) => {
      if (!safeToken) return;

      const title = params.title.trim();
      const artist = params.artist.trim();

      if (!title && !artist) {
        return;
      }

      setTrackLoading(true);
      setError(null);
      try {
        const res = await updateMusicTrackMetadata(
          params.id,
          { title, artist },
          safeToken
        );
        const updated = res.track;
        setTracks(prev =>
          prev.map(track => (track.id === updated.id ? { ...track, ...updated } : track))
        );
      } catch (err) {
        const message = err instanceof Error ? err.message : '更新曲目信息失败';
        setError(message);
        throw err;
      } finally {
        setTrackLoading(false);
      }
    },
    [safeToken]
  );

  const deleteTrack = useCallback(
    async (trackId: number) => {
      if (!safeToken) return;

      setTrackLoading(true);
      setError(null);
      try {
        await deleteMusicTrack(trackId, safeToken);
        await reloadAll();
      } catch (err) {
        const message = err instanceof Error ? err.message : '删除音乐失败';
        setError(message);
        throw err;
      } finally {
        setTrackLoading(false);
      }
    },
    [safeToken, reloadAll]
  );

  return {
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
  };
};
