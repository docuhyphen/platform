export type PickerTab = 'PERSONAL' | 'ORG' | 'APP';
export type SortOrder = 'default' | 'nameAsc' | 'nameDesc';

export const PAGE_SIZE = 6;

export const tabLabel: Record<PickerTab, string> = {
    PERSONAL: 'My Blueprints',
    ORG: 'Organization',
    APP: 'Platform',
};

export const sortOrderLabel: Record<SortOrder, string> = {
    default: 'Recently updated',
    nameAsc: 'Name (A-Z)',
    nameDesc: 'Name (Z-A)',
};
