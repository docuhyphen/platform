import {UserContactDto} from "../../../../../services/personalContactsApi";
import {PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const SEND_AS_NEW_OPTION_VALUE = "__send_as_new__";

export const formatDisplayName = (c: UserContactDto): string =>
{
    const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
    return name.length > 0 ? `${name} (${c.email})` : c.email;
};

export const normalizeEmail = (value?: string | null): string => (value ?? '').trim().toLowerCase();

export const toPersonPickerItem = (contact: UserContactDto): PersonPickerItem => ({
    id: contact.email,
    email: contact.email,
    firstName: contact.firstName,
    lastName: contact.lastName,
    avatarUrl: contact.avatarUrl,
});
