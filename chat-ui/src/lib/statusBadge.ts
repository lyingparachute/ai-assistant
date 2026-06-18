import type { SourceContributionStatus } from './types';

const STATUS_BADGE_CLASSES: Record<SourceContributionStatus, string> = {
  USED: 'badge badge-used',
  UNAVAILABLE: 'badge badge-unavailable',
  INSUFFICIENT: 'badge badge-insufficient',
};

export function statusBadgeClass(status: SourceContributionStatus): string {
  return STATUS_BADGE_CLASSES[status] ?? 'badge';
}
