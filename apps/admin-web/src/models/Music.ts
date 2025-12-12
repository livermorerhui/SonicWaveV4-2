// file: apps/admin-web/src/models/Music.ts

export interface MusicCategoryDTO {
  id: number;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface MusicTrackDTO {
  id: number;
  title: string;
  artist: string;
  fileKey: string;
  categoryId: number | null;
  categoryName?: string | null;
  createdAt: string;
}
