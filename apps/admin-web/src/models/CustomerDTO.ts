export interface CustomerDTO {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  height: number | null;
  weight: number | null;
  createdAt: string;
  updatedAt: string;
}

export type CustomerDetail = CustomerDTO;
