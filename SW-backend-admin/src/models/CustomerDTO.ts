export interface CustomerDTO {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  createdAt: string;
  updatedAt: string;
}
