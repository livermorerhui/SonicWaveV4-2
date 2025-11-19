export interface ErrorDetails {
  field?: string;
  message: string;
}

export interface ErrorEnvelope {
  error: {
    code: string;
    message?: string;
    traceId?: string;
    details?: ErrorDetails[];
  };
}
