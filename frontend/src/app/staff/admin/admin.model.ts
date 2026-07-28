/** PRD §8.12. */
export interface AppointmentTypeAdmin {
  id: number;
  code: string;
  displayName: string;
  durationMinutes: number;
  bufferMinutes: number;
  requiresApproval: boolean;
  isActive: boolean;
}

export interface AppointmentTypeRequest {
  code: string;
  displayName: string;
  durationMinutes: number;
  bufferMinutes: number;
  requiresApproval: boolean;
  isActive: boolean;
}

/** PRD §8.13. */
export interface ProviderAdmin {
  id: number;
  firstName: string;
  lastName: string;
  specialty: string;
  email: string;
  timezone: string;
  isActive: boolean;
  appointmentTypeIds: number[];
}

export interface ProviderRequest {
  firstName: string;
  lastName: string;
  specialty: string;
  email: string;
  timezone: string;
  isActive: boolean;
}

/** PRD §8.17. */
export interface FeatureFlag {
  flagName: string;
  isEnabled: boolean;
  updatedBy: string;
  updatedAt: string;
}
