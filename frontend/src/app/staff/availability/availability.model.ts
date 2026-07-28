export type RuleType = 'WORKING' | 'BREAK';

/** PRD §8.14. */
export interface AvailabilityRule {
  id: number;
  providerId: number;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  ruleType: RuleType;
}

export interface AvailabilityRuleRequest {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  ruleType: RuleType;
}

/** PRD §8.15. */
export interface AffectedAppointment {
  confirmationToken: string;
  startDatetime: string;
  status: string;
}

export interface Unavailability {
  id: number;
  providerId: number;
  startDatetime: string;
  endDatetime: string;
  reason: string;
  createdBy: string;
  affectedAppointments: AffectedAppointment[];
}

export interface UnavailabilityRequest {
  startDatetime: string;
  endDatetime: string;
  reason: string;
}

/** PRD §8.16. */
export interface Holiday {
  id: number;
  holidayDate: string;
  name: string;
  isRecurringAnnually: boolean;
}

export interface HolidayRequest {
  holidayDate: string;
  name: string;
  isRecurringAnnually: boolean;
}
